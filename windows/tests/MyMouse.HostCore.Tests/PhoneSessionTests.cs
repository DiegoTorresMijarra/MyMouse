using System.Net;
using System.Net.Sockets;
using MyMouse.HostCore.Phone;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Mensajes de la v5 en la sesión: llegan a la toolbox, exigen versión 5 y se pueden enviar al móvil.</summary>
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
        var (reason, _) = await Run(hub, Bytes([new HelloMessage(5), .. messages]), paused: true);

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(messages, received);
    }

    [Fact]
    public async Task Handshake_NegotiatesTheHighestVersionThatBothKnow()
    {
        var (_, output) = await Run(new PhoneHub(), Bytes(new HelloMessage(10)));
        Assert.Equal(MessageCodec.Encode(new HelloAckMessage(ProtocolInfo.CurrentVersion)), output.Take(2).ToArray());
        Assert.Equal(8, ProtocolInfo.CurrentVersion);
    }

    [Fact]
    public async Task V6_SendsTheHostVersionRightAfterTheHandshakeAndKeepsThePhones()
    {
        var hub = new PhoneHub { HostAppVersion = "5.1" };
        string? seen = null;
        hub.Changed += () => seen ??= hub.PhoneAppVersion;
        var (reason, output) = await Run(hub, Bytes(new HelloMessage(6), new AppVersionMessage("5.0")));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(Bytes(new HelloAckMessage(6), new HostAppVersionMessage("5.1")), output);
        Assert.Equal("5.0", seen);
        Assert.Null(hub.PhoneAppVersion); // la sesión terminó
    }

    [Fact]
    public async Task V5Client_GetsNoHostVersion()
    {
        var hub = new PhoneHub { HostAppVersion = "5.1" };
        var (_, output) = await Run(hub, Bytes(new HelloMessage(5)));
        Assert.Equal(Bytes(new HelloAckMessage(5)), output);
    }

    [Fact]
    public async Task AppVersion_WithOlderProtocol_IsAProtocolError()
    {
        var (reason, _) = await Run(new PhoneHub(), Bytes(new HelloMessage(5), new AppVersionMessage("5.0")));
        Assert.Equal(SessionEndReason.ProtocolError, reason);
    }

    [Fact]
    public async Task EmptyHostVersion_IsNotSent()
    {
        var (_, output) = await Run(new PhoneHub(), Bytes(new HelloMessage(6)));
        Assert.Equal(Bytes(new HelloAckMessage(6)), output);
    }

    [Theory]
    [InlineData(4)]
    [InlineData(3)]
    [InlineData(1)]
    public async Task V5Messages_WithOlderVersion_AreProtocolErrors(int version)
    {
        var hub = new PhoneHub();
        var received = new List<Message>();
        hub.Received += received.Add;
        var (reason, _) = await Run(hub, Bytes(new HelloMessage((byte)version), new ToolboxMessage(true)));

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Empty(received);
    }

    [Fact]
    public async Task V5Messages_WithoutHub_AreIgnored()
    {
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(new HelloMessage(5), new ToolboxMessage(true))), output, new NullInjector());
        Assert.Equal(SessionEndReason.ClientClosed, await session.RunAsync(CancellationToken.None));
    }

    [Fact]
    public async Task HelloV4_DoesNotAttachTheHub_V5Does()
    {
        var hub = new PhoneHub();
        var changes = new List<byte>();
        hub.Changed += () => changes.Add(hub.Version);

        await Run(hub, Bytes(new HelloMessage(4)));
        Assert.Empty(changes);

        await Run(hub, Bytes(new HelloMessage(5)));
        Assert.Equal(new byte[] { 5, 0 }, changes); // conectado y desconectado
        Assert.False(hub.SupportsToolbox);
    }

    [Fact]
    public async Task Hub_SendsShapeMessagesToTheConnectedPhone()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var hub = new PhoneHub();
        var connected = new TaskCompletionSource();
        hub.Changed += () => { if (hub.SupportsToolbox) connected.TrySetResult(); };
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
        await stream.WriteAsync(MessageCodec.Encode(new HelloMessage(5)), cts.Token);
        Assert.Equal(new HelloAckMessage(5), await reader.ReadAsync(cts.Token));
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
