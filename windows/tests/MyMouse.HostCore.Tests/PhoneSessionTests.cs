using System.Net;
using System.Net.Sockets;
using MyMouse.HostCore.Phone;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Mensajes de la toolbox en la sesión: llegan a la toolbox y se pueden enviar al móvil.</summary>
public class PhoneSessionTests
{
    private sealed class NullInjector : IInputInjector
    {
        public void MoveRelative(int dx, int dy) { }
        public void SetButton(MouseButton button, bool pressed) { }
        public void Scroll(int vertical, int horizontal) { }
        public void Zoom(int delta) { }
        public void SystemGesture(SystemGestureCommand command) { }
        public void SetKey(byte usage, bool down) { }
    }

    private static byte[] Bytes(params Message[] messages) => messages.SelectMany(MessageCodec.Encode).ToArray();

    private static readonly IReadOnlyList<StrokePoint> Pts = [new StrokePoint(1, 2), new StrokePoint(3, 4)];

    private static async Task<(SessionEndReason, byte[])> Run(PhoneHub hub, byte[] input, bool paused = false)
    {
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(input), output, new NullInjector(), () => paused, phone: hub);
        return (await session.RunAsync(CancellationToken.None), output.ToArray());
    }

    [Fact]
    public async Task V5Messages_ReachTheHub_EvenWhenPaused()
    {
        var hub = new PhoneHub();
        var received = new List<Message>();
        hub.Received += received.Add;

        var messages = new Message[]
        {
            new ToolboxMessage(true), new GestureModeMessage(true, 3000), new StrokePointsMessage(Pts),
            new StrokeEndMessage(StrokeResult.Recognized), new ShapeInfoMessage("a", [Pts]), new ShapeListEndMessage(1),
            new ShapeResultMessage("a", ShapeStatus.Saved, ""),
        };
        var (reason, _) = await Run(hub, Bytes([new HelloMessage(1), .. messages]), paused: true);

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(messages, received);
    }

    [Fact]
    public async Task V6_SendsTheHostVersionRightAfterTheHandshakeAndKeepsThePhones()
    {
        var hub = new PhoneHub { HostAppVersion = "5.1" };
        string? seen = null;
        hub.Changed += () => seen ??= hub.PhoneAppVersion;
        var (reason, output) = await Run(hub, Bytes(new HelloMessage(1), new AppVersionMessage("5.0")));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(Bytes(new HelloAckMessage(1), new HostAppVersionMessage("5.1")), output);
        Assert.Equal("5.0", seen);
        Assert.Null(hub.PhoneAppVersion); // la sesión terminó
    }

    [Fact]
    public async Task EmptyHostVersion_IsNotSent()
    {
        var (_, output) = await Run(new PhoneHub(), Bytes(new HelloMessage(1)));
        Assert.Equal(Bytes(new HelloAckMessage(1)), output);
    }

    [Fact]
    public async Task V5Messages_WithoutHub_AreIgnored()
    {
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(new HelloMessage(1), new ToolboxMessage(true))), output, new NullInjector());
        Assert.Equal(SessionEndReason.ClientClosed, await session.RunAsync(CancellationToken.None));
    }

    [Fact]
    public async Task Hello_AttachesTheHub_UntilTheSessionEnds()
    {
        var hub = new PhoneHub();
        var changes = new List<byte>();
        hub.Changed += () => changes.Add(hub.Version);

        await Run(hub, Bytes(new HelloMessage(1)));
        Assert.Equal(new byte[] { 1, 0 }, changes); // conectado y desconectado
        Assert.False(hub.Connected);
    }

    [Fact]
    public async Task Hub_SendsShapeMessagesToTheConnectedPhone()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var hub = new PhoneHub();
        var connected = new TaskCompletionSource();
        hub.Changed += () => { if (hub.Connected) connected.TrySetResult(); };
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));

        var serverTask = Task.Run(async () =>
        {
            using var server = await listener.AcceptTcpClientAsync(cts.Token);
            var session = new ClientSession(server.GetStream(), new NullInjector(), phone: hub);
            return await session.RunAsync(cts.Token);
        });

        using var client = new TcpClient();
        await client.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, cts.Token);
        var stream = client.GetStream();
        var reader = new MessageReader(stream);
        await stream.WriteAsync(MessageCodec.Encode(new HelloMessage(1)), cts.Token);
        Assert.Equal(new HelloAckMessage(1), await reader.ReadAsync(cts.Token));
        await connected.Task.WaitAsync(cts.Token);

        var add = new ShapeAddMessage("custom:a", [Pts], false);
        Assert.True(await hub.SendAsync(add));
        Assert.Equal(add, await reader.ReadAsync(cts.Token));
        Assert.True(await hub.SendAsync(new ShapeDeleteMessage("custom:a")));
        Assert.Equal(new ShapeDeleteMessage("custom:a"), await reader.ReadAsync(cts.Token));

        client.Close();
        await serverTask;
        Assert.False(await hub.SendAsync(add)); // ya no hay móvil
        listener.Stop();
    }
}
