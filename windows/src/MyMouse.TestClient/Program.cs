using System.Net.Sockets;
using MyMouse.Protocol;

// Cliente de prueba: simula al móvil para probar el host sin Android.
// Uso: dotnet run --project src/MyMouse.TestClient [puerto]
// Mueve el cursor en círculo, hace clic derecho (y clic izquierdo para cerrar el menú) hace scroll y zoom (Ctrl + rueda)
// y, con protocolo v2, lista las formas configuradas y envía una forma sin asignar.
// Con protocolo v3 imprime los widgets configurados y el estado de volumen y música, y prueba un toque en un widget inexistente.

var port = args.Length > 0 ? int.Parse(args[0]) : ProtocolInfo.DefaultPort;

using var client = new TcpClient { NoDelay = true };
await client.ConnectAsync("127.0.0.1", port);
var stream = client.GetStream();
var reader = new MessageReader(stream);

async Task Send(Message m) => await stream.WriteAsync(MessageCodec.Encode(m));

/// <summary>Lee mensajes hasta que llega uno del tipo esperado (el host puede intercalar avisos de volumen o música).</summary>
async Task<T?> Expect<T>() where T : Message
{
    while (await reader.ReadAsync() is { } m)
    {
        if (m is T expected) return expected;
        Console.WriteLine($"  (aviso del PC: {Describe(m)})");
    }
    return null;
}

string Describe(Message m) => m switch
{
    VolumeStateMessage v => $"Volumen {v.Level}{(v.Muted ? " (silenciado)" : "")}",
    MediaStateMessage s => s.Playing || s.Title.Length > 0 ? $"{(s.Playing ? "▶" : "⏸")} {s.Title} — {s.Artist}" : "Nada en reproducción",
    _ => m.ToString() ?? m.GetType().Name,
};

await Send(new HelloMessage(ProtocolInfo.CurrentVersion));
var ack = await reader.ReadAsync();
Console.WriteLine($"Handshake: {ack}");
if (ack is not HelloAckMessage helloAck)
    return 1;

if (helloAck.Version >= ProtocolInfo.GesturesVersion && await reader.ReadAsync() is GestureListMessage list)
{
    Console.WriteLine($"Formas configuradas en el PC ({list.Entries.Count}):");
    foreach (var e in list.Entries) Console.WriteLine($"  {(e.Pinned ? "*" : " ")} {e.Name,-12} {e.Help}");
}

if (helloAck.Version >= ProtocolInfo.WidgetsVersion && await reader.ReadAsync() is WidgetLayoutMessage layout)
{
    Console.WriteLine($"Widgets configurados en el PC ({layout.Entries.Count}):");
    foreach (var e in layout.Entries) Console.WriteLine($"  {e.Id,2} {e.Kind,-7} {e.Icon,-4} {e.Label}");
    if (layout.Entries.Any(e => e.Kind == WidgetKind.Volume) && await reader.ReadAsync() is { } volume)
        Console.WriteLine($"Estado inicial: {Describe(volume)}");
    if (layout.Entries.Any(e => e.Kind == WidgetKind.Media) && await reader.ReadAsync() is { } media)
        Console.WriteLine($"Estado inicial: {Describe(media)}");
}

Console.WriteLine("Círculo (2 s)...");
const int steps = 120;
for (var i = 0; i < steps; i++)
{
    var angle = 2 * Math.PI * i / steps;
    await Send(new MoveMessage((short)Math.Round(-Math.Sin(angle) * 8), (short)Math.Round(Math.Cos(angle) * 8)));
    await Task.Delay(16);
}

Console.WriteLine("Clic derecho y clic izquierdo para cerrar el menú...");
await Send(new ButtonMessage(MouseButton.Right, true));
await Send(new ButtonMessage(MouseButton.Right, false));
await Task.Delay(500);
await Send(new MoveMessage(-40, 0));
await Send(new ButtonMessage(MouseButton.Left, true));
await Send(new ButtonMessage(MouseButton.Left, false));

Console.WriteLine("Scroll suave hacia abajo (1 s)...");
for (var i = 0; i < 30; i++)
{
    await Send(new ScrollMessage(-20, 0));
    await Task.Delay(33);
}

Console.WriteLine("Zoom: acercar 2 pasos y alejar 2 pasos...");
for (var i = 0; i < 2; i++) { await Send(new ZoomMessage(120)); await Task.Delay(400); }
for (var i = 0; i < 2; i++) { await Send(new ZoomMessage(-120)); await Task.Delay(400); }

if (helloAck.Version >= ProtocolInfo.GesturesVersion)
{
    // Una forma que no existe: comprueba el viaje de ida y vuelta sin ejecutar nada en el PC.
    await Send(new GestureMessage("test-client"));
    Console.WriteLine($"Forma 'test-client': {await Expect<GestureResultMessage>()} (lo esperado es Unassigned y un aviso en la bandeja)");
}

if (helloAck.Version >= ProtocolInfo.WidgetsVersion)
{
    // Un widget que no existe: comprueba el viaje de ida y vuelta sin ejecutar nada en el PC.
    await Send(new WidgetPressMessage(250));
    Console.WriteLine($"Widget 250: {await Expect<WidgetResultMessage>()} (lo esperado es Error)");
}

await Send(PingMessage.Instance);
Console.WriteLine($"Ping: {await Expect<PongMessage>()}");
Console.WriteLine("Hecho.");
return 0;
