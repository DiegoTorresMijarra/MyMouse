using System.Net.Sockets;
using MyMouse.Protocol;

// Cliente de prueba: simula al móvil para probar el host sin Android.
// Uso: dotnet run --project src/MyMouse.TestClient [puerto]
// Mueve el cursor en círculo, hace clic derecho (y clic izquierdo para cerrar el menú), scroll y zoom (Ctrl + rueda).
// Lista las formas configuradas y envía una forma sin asignar. Imprime los widgets configurados y el estado de volumen y música,
// y prueba un toque en un widget inexistente. Abre y cierra la toolbox, simula el gesto en vivo (trazo en espiral sin reconocer) y envía una forma propia
// de prueba ("custom:test-client"). Con --formas se queda 60 s contestando a los SHAPE_ADD y SHAPE_DELETE que mande el PC
// (para probar "+ Nueva forma" en el editor sin el móvil).

var port = args.FirstOrDefault(a => int.TryParse(a, out _)) is { } p ? int.Parse(p) : ProtocolInfo.DefaultPort;
var waitForShapes = args.Contains("--formas");

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
    ShapeAddMessage a => $"SHAPE_ADD '{a.Name}' ({a.Samples.Count} muestras, force={a.Force})",
    ShapeDeleteMessage d => $"SHAPE_DELETE '{d.Name}'",
    MediaStateMessage s => s.Playing || s.Title.Length > 0 ? $"{(s.Playing ? "▶" : "⏸")} {s.Title} — {s.Artist}" : "Nada en reproducción",
    _ => m.ToString() ?? m.GetType().Name,
};

await Send(new HelloMessage(ProtocolInfo.CurrentVersion));
var ack = await reader.ReadAsync();
Console.WriteLine($"Handshake: {ack}");
if (ack is not HelloAckMessage)
    return 1;

if (await reader.ReadAsync() is GestureListMessage list)
{
    Console.WriteLine($"Formas configuradas en el PC ({list.Entries.Count}):");
    foreach (var e in list.Entries) Console.WriteLine($"  {(e.Pinned ? "*" : " ")} {e.Name,-12} {e.Help}");
}

if (await reader.ReadAsync() is WidgetLayoutMessage layout)
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

// Una forma que no existe: comprueba el viaje de ida y vuelta sin ejecutar nada en el PC.
await Send(new GestureMessage("test-client"));
Console.WriteLine($"Forma 'test-client': {await Expect<GestureResultMessage>()} (lo esperado es Unassigned y un aviso en la bandeja)");

// Un widget que no existe: comprueba el viaje de ida y vuelta sin ejecutar nada en el PC.
await Send(new WidgetPressMessage(250));
Console.WriteLine($"Widget 250: {await Expect<WidgetResultMessage>()} (lo esperado es Error)");

// Una forma propia de prueba: el PC la dibuja en la tarjeta Gestos (y, la primera vez, no avisa; las siguientes, sí).
var square = Enumerable.Range(0, 32).Select(i =>
{
    var t = i / 31.0 * 4;
    var (x, y) = t switch { < 1 => (t, 0.0), < 2 => (1.0, t - 1), < 3 => (3 - t, 1.0), _ => (0.0, 4 - t) };
    return new StrokePoint((ushort)(x * 65535), (ushort)(y * 65535));
}).ToList();
await Send(new ShapeInfoMessage("custom:test-client", [square, square, square]));
await Send(new ShapeListEndMessage(1));

Console.WriteLine("Toolbox: abrir (1,5 s) y cerrar...");
await Send(new ToolboxMessage(true));
await Task.Delay(1500);
await Send(new ToolboxMessage(false));
await Task.Delay(400);

Console.WriteLine("Gesto en vivo: un trazo en espiral sin reconocer...");
await Send(new GestureModeMessage(true, 3000));
await Task.Delay(500);
for (var i = 0; i < 60; i++)
{
    var t = i / 59.0 * 3.2 * Math.PI;
    var r = 0.15 + 0.35 * i / 59.0;
    await Send(new StrokePointsMessage([new StrokePoint((ushort)((0.5 + r * Math.Cos(t)) * 65535), (ushort)((0.5 + r * Math.Sin(t)) * 65535))]));
    await Task.Delay(16);
}
await Send(new StrokeEndMessage(StrokeResult.NotRecognized));
await Send(new GestureModeMessage(false, 0));
await Task.Delay(2000);

if (waitForShapes)
{
    Console.WriteLine("Esperando 60 s a SHAPE_ADD / SHAPE_DELETE del PC (abre el editor → + Nueva forma)...");
    var shapes = new Dictionary<string, ShapeInfoMessage> { ["custom:test-client"] = new("custom:test-client", []) };
    using var wait = new CancellationTokenSource(TimeSpan.FromSeconds(60));
    var nextPing = DateTime.UtcNow;
    Task<Message?>? pending = null; // una sola lectura a la vez: se conserva entre vueltas
    try
    {
        while (!wait.IsCancellationRequested)
        {
            if (DateTime.UtcNow >= nextPing)
            {
                await Send(PingMessage.Instance);
                nextPing = DateTime.UtcNow.AddSeconds(1);
            }
            pending ??= reader.ReadAsync(wait.Token).AsTask();
            if (await Task.WhenAny(pending, Task.Delay(500, wait.Token)) != pending) continue;
            var received = await pending;
            pending = null;
            switch (received)
            {
                case ShapeAddMessage add:
                    Console.WriteLine($"  PC → {Describe(add)}");
                    var duplicate = shapes.ContainsKey(add.Name);
                    if (!duplicate) shapes[add.Name] = new ShapeInfoMessage(add.Name, add.Samples);
                    await Send(new ShapeResultMessage(add.Name, duplicate ? ShapeStatus.DuplicateName : ShapeStatus.Saved, ""));
                    foreach (var s in shapes.Values.Where(s => s.Samples.Count > 0)) await Send(s);
                    await Send(new ShapeListEndMessage((byte)shapes.Values.Count(s => s.Samples.Count > 0)));
                    break;
                case ShapeDeleteMessage delete:
                    Console.WriteLine($"  PC → {Describe(delete)}");
                    var removed = shapes.Remove(delete.Name);
                    await Send(new ShapeResultMessage(delete.Name, removed ? ShapeStatus.Deleted : ShapeStatus.NotFound, ""));
                    foreach (var s in shapes.Values.Where(s => s.Samples.Count > 0)) await Send(s);
                    await Send(new ShapeListEndMessage((byte)shapes.Values.Count(s => s.Samples.Count > 0)));
                    break;
                case null:
                    return 0;
            }
        }
    }
    catch (OperationCanceledException)
    {
    }
}

await Send(PingMessage.Instance);
Console.WriteLine($"Ping: {await Expect<PongMessage>()}");
Console.WriteLine("Hecho.");
return 0;
