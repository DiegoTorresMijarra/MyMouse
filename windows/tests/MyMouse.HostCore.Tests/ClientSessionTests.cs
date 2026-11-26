using MyMouse.HostCore;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class ClientSessionTests
{
    private sealed class FakeInjector : IInputInjector
    {
        public List<string> Calls { get; } = [];
        public void MoveRelative(int dx, int dy) => Calls.Add($"move {dx},{dy}");
        public void SetButton(MouseButton button, bool pressed) => Calls.Add($"{button} {(pressed ? "down" : "up")}");
        public void Scroll(int vertical, int horizontal) => Calls.Add($"scroll {vertical},{horizontal}");
    }

    private static byte[] Bytes(params Message[] messages) => messages.SelectMany(MessageCodec.Encode).ToArray();

    private static async Task<(SessionEndReason reason, FakeInjector injector, byte[] output, ClientSession session)> Run(
        byte[] input, bool paused = false)
    {
        var injector = new FakeInjector();
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(input), output, injector, () => paused);
        var reason = await session.RunAsync(CancellationToken.None);
        return (reason, injector, output.ToArray(), session);
    }

    [Fact]
    public async Task Handshake_RepliesWithAck()
    {
        var (reason, _, output, _) = await Run(Bytes(new HelloMessage(1)));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(MessageCodec.Encode(new HelloAckMessage(1)), output);
    }

    [Fact]
    public async Task MessageBeforeHello_IsProtocolError()
    {
        var (reason, injector, _, _) = await Run(Bytes(new MoveMessage(1, 1)));

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Empty(injector.Calls);
    }

    [Fact]
    public async Task UnsupportedVersion_IsProtocolError()
    {
        var (reason, _, output, _) = await Run(Bytes(new HelloMessage(99)));

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Empty(output);
    }

    [Fact]
    public async Task InputMessages_AreInjected()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(1),
            new MoveMessage(5, -3),
            new ButtonMessage(MouseButton.Left, true),
            new ButtonMessage(MouseButton.Left, false),
            new ScrollMessage(120, -40)));

        Assert.Equal(["move 5,-3", "Left down", "Left up", "scroll 120,-40"], injector.Calls);
    }

    [Fact]
    public async Task Ping_RepliesPong()
    {
        var (_, _, output, _) = await Run(Bytes(new HelloMessage(1), PingMessage.Instance));

        Assert.Equal(Bytes(new HelloAckMessage(1), PongMessage.Instance), output);
    }

    [Fact]
    public async Task Disconnect_ReleasesPressedButtons()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(1),
            new ButtonMessage(MouseButton.Left, true),
            new ButtonMessage(MouseButton.Right, true)));

        Assert.Equal(["Left down", "Right down"], injector.Calls.Take(2));
        Assert.Equal(["Left up", "Right up"], injector.Calls.Skip(2).Order());
    }

    [Fact]
    public async Task DuplicateButtonEvents_AreIgnored()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(1),
            new ButtonMessage(MouseButton.Left, false),
            new ButtonMessage(MouseButton.Left, true),
            new ButtonMessage(MouseButton.Left, true),
            new ButtonMessage(MouseButton.Left, false)));

        Assert.Equal(["Left down", "Left up"], injector.Calls);
    }

    [Fact]
    public async Task Paused_IgnoresInputButAnswersPing()
    {
        var (_, injector, output, _) = await Run(Bytes(
            new HelloMessage(1),
            new MoveMessage(5, 5),
            PingMessage.Instance), paused: true);

        Assert.Empty(injector.Calls);
        Assert.Equal(Bytes(new HelloAckMessage(1), PongMessage.Instance), output);
    }

    [Fact]
    public async Task Silence_TimesOut()
    {
        var session = new ClientSession(new SilentStream(), new MemoryStream(), new FakeInjector(),
            idleTimeout: TimeSpan.FromMilliseconds(200));

        var reason = await session.RunAsync(CancellationToken.None).WaitAsync(TimeSpan.FromSeconds(5));

        Assert.Equal(SessionEndReason.Timeout, reason);
    }

    /// <summary>Flujo que nunca entrega datos: la lectura espera hasta ser cancelada.</summary>
    private sealed class SilentStream : Stream
    {
        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            await Task.Delay(Timeout.Infinite, cancellationToken);
            return 0;
        }

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() { }
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
