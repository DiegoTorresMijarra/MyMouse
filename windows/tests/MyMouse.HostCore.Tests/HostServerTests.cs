using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using MyMouse.HostCore;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class HostServerTests
{
    private sealed class RecordingInjector : IInputInjector
    {
        public ConcurrentQueue<string> Calls { get; } = new();
        public void MoveRelative(int dx, int dy) => Calls.Enqueue($"move {dx},{dy}");
        public void SetButton(MouseButton button, bool pressed) => Calls.Enqueue($"{button} {(pressed ? "down" : "up")}");
        public void Scroll(int vertical, int horizontal) => Calls.Enqueue($"scroll {vertical},{horizontal}");
        public void Zoom(int delta) => Calls.Enqueue($"zoom {delta}");
        public void SystemGesture(SystemGestureCommand command) => Calls.Enqueue($"system {command}");
    }

    private static int FreePort()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var port = ((IPEndPoint)l.LocalEndpoint).Port;
        l.Stop();
        return port;
    }

    private static async Task<(TcpClient client, NetworkStream stream, MessageReader reader)> ConnectAsync(int port)
    {
        var client = new TcpClient { NoDelay = true };
        for (var attempt = 0; ; attempt++)
        {
            try
            {
                await client.ConnectAsync(IPAddress.Loopback, port);
                break;
            }
            catch (SocketException) when (attempt < 50)
            {
                await Task.Delay(20);
            }
        }

        var stream = client.GetStream();
        var reader = new MessageReader(stream);
        await stream.WriteAsync(MessageCodec.Encode(new HelloMessage(1)));
        Assert.Equal(new HelloAckMessage(1), await reader.ReadAsync());
        return (client, stream, reader);
    }

    [Fact]
    public async Task EndToEnd_OverTcp()
    {
        var port = FreePort();
        var injector = new RecordingInjector();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var server = new HostServer(injector, () => false, port);
        var serverTask = server.RunAsync(cts.Token);

        var (client, stream, reader) = await ConnectAsync(port);
        using (client)
        {
            await stream.WriteAsync(MessageCodec.Encode(new MoveMessage(3, 4)));
            await stream.WriteAsync(MessageCodec.Encode(new ButtonMessage(MouseButton.Left, true)));
            await stream.WriteAsync(MessageCodec.Encode(PingMessage.Instance));
            Assert.Equal(PongMessage.Instance, await reader.ReadAsync());
        }

        // Al cerrar el cliente, el host suelta el botón.
        await WaitUntil(() => injector.Calls.Contains("Left up"));
        Assert.Equal(["move 3,4", "Left down", "Left up"], injector.Calls);

        cts.Cancel();
        await serverTask;
    }

    [Fact]
    public async Task NewClient_ReplacesPrevious()
    {
        var port = FreePort();
        var injector = new RecordingInjector();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var server = new HostServer(injector, () => false, port);
        var serverTask = server.RunAsync(cts.Token);

        var (first, firstStream, firstReader) = await ConnectAsync(port);
        await firstStream.WriteAsync(MessageCodec.Encode(new ButtonMessage(MouseButton.Left, true)));
        await WaitUntil(() => injector.Calls.Contains("Left down"));

        var (second, _, _) = await ConnectAsync(port);

        // La primera sesión se cancela y suelta su botón.
        await WaitUntil(() => injector.Calls.Contains("Left up"));
        Assert.Null(await firstReader.ReadAsync().AsTask().WaitAsync(TimeSpan.FromSeconds(5)));

        first.Dispose();
        second.Dispose();
        cts.Cancel();
        await serverTask;
    }

    private static async Task WaitUntil(Func<bool> condition)
    {
        for (var i = 0; i < 250 && !condition(); i++)
            await Task.Delay(20);
        Assert.True(condition());
    }
}
