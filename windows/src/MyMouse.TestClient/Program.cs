using System.Net.Sockets;
using MyMouse.Protocol;

// Cliente de prueba: simula al móvil para probar el host sin Android.
// Uso: dotnet run --project src/MyMouse.TestClient [puerto]
// Mueve el cursor en círculo, hace clic derecho (y clic izquierdo para cerrar el menú) hace scroll y zoom (Ctrl + rueda)
// y, con protocolo v2, lista las formas configuradas y envía una forma sin asignar.

var port = args.Length > 0 ? int.Parse(args[0]) : ProtocolInfo.DefaultPort;

using var client = new TcpClient { NoDelay = true };
await client.ConnectAsync("127.0.0.1", port);
var stream = client.GetStream();
var reader = new MessageReader(stream);

async Task Send(Message m) => await stream.WriteAsync(MessageCodec.Encode(m));

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
    Console.WriteLine($"Forma 'test-client': {await reader.ReadAsync()} (lo esperado es Unassigned y un aviso en la bandeja)");
}

await Send(PingMessage.Instance);
Console.WriteLine($"Ping: {await reader.ReadAsync()}");
Console.WriteLine("Hecho.");
return 0;
