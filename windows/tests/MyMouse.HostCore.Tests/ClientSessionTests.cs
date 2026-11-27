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
        public void Zoom(int delta) => Calls.Add($"zoom {delta}");
        public void SystemGesture(SystemGestureCommand command) => Calls.Add($"system {command}");
        public void SetKey(byte usage, bool down) => Calls.Add($"key {usage:X2} {(down ? "down" : "up")}");
    }

    private sealed class FakeGestures : IGestureService
    {
        public List<string> Executed { get; } = [];
        public GestureListMessage List { get; set; } = new("", [new GestureListEntry("z", "Deshacer", true)]);
        public GestureStatus Execute(string name)
        {
            Executed.Add(name);
            return name == "z" ? GestureStatus.Executed : GestureStatus.Unassigned;
        }
        public GestureListMessage CurrentList() => List;
        public event Action? ListChanged;
        public void RaiseListChanged() => ListChanged?.Invoke();
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
        var (reason, _, output, _) = await Run(Bytes(new HelloMessage(0)));

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
            new ScrollMessage(120, -40),
            new ZoomMessage(-120)));

        Assert.Equal(["move 5,-3", "Left down", "Left up", "scroll 120,-40", "zoom -120"], injector.Calls);
    }

    [Fact]
    public async Task HelloV2_AcksV2AndSendsGestureList()
    {
        var gestures = new FakeGestures();
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(new HelloMessage(2))), output, new FakeInjector(), gestures: gestures);
        await session.RunAsync(CancellationToken.None);

        Assert.Equal(2, session.Version);
        Assert.Equal(Bytes(new HelloAckMessage(2), gestures.List), output.ToArray());
    }

    // ---- Gestos de Windows (protocolo v4) ----

    [Fact]
    public async Task SystemGestures_AreInjected()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(4),
            new SystemGestureMessage(SystemGestureCommand.TaskView),
            new SystemGestureMessage(SystemGestureCommand.ShowDesktop),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchNext),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchPrevious),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchEnd)));

        Assert.Equal(
            ["system TaskView", "system ShowDesktop", "system AppSwitchStart", "system AppSwitchNext", "system AppSwitchPrevious", "system AppSwitchEnd"],
            injector.Calls);
    }

    [Fact]
    public async Task SystemGesture_WithOlderProtocol_IsProtocolError()
    {
        var (reason, injector, _, session) = await Run(Bytes(new HelloMessage(3), new SystemGestureMessage(SystemGestureCommand.TaskView)));

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Equal(3, session.Version);
        Assert.Empty(injector.Calls);
    }

    [Fact]
    public async Task AppSwitch_IsReleasedWhenTheSessionEndsMidSwitch()
    {
        // El cliente se va tras AppSwitchStart y AppSwitchNext: el host no puede dejar Alt pulsado.
        var (reason, injector, _, _) = await Run(Bytes(
            new HelloMessage(4),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchNext)));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(["system AppSwitchStart", "system AppSwitchNext", "system AppSwitchEnd"], injector.Calls);
    }

    [Fact]
    public async Task AppSwitch_IsReleasedOnProtocolError()
    {
        var (reason, injector, _, _) = await Run(Bytes(
            new HelloMessage(4),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart),
            new HelloMessage(4)));

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Equal(["system AppSwitchStart", "system AppSwitchEnd"], injector.Calls);
    }

    [Fact]
    public async Task AppSwitch_StepsWithoutStartAreIgnoredAndEndIsNotRepeated()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(4),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchNext),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchPrevious),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchEnd),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchEnd)));

        Assert.Equal(["system AppSwitchStart", "system AppSwitchEnd"], injector.Calls);
    }

    [Fact]
    public async Task SystemGestures_WhilePaused_AreIgnored()
    {
        var (_, injector, _, _) = await Run(Bytes(
            new HelloMessage(4),
            new SystemGestureMessage(SystemGestureCommand.TaskView),
            new SystemGestureMessage(SystemGestureCommand.AppSwitchStart)), paused: true);

        Assert.Empty(injector.Calls);
    }

    [Fact]
    public async Task Pause_DuringAppSwitch_ReleasesAlt()
    {
        var injector = new FakeInjector();
        var paused = false;
        var input = new PipeStream();
        var session = new ClientSession(input, new MemoryStream(), injector, () => paused);
        var run = session.RunAsync(CancellationToken.None);

        void Write(params Message[] messages) => input.Write(Bytes(messages));
        Write(new HelloMessage(4), new SystemGestureMessage(SystemGestureCommand.AppSwitchStart));
        await WaitFor(() => injector.Calls.Contains("system AppSwitchStart"));

        paused = true;
        Write(new MoveMessage(1, 1)); // cualquier mensaje con el host en pausa suelta lo pulsado
        await WaitFor(() => injector.Calls.Contains("system AppSwitchEnd"));
        input.Complete();

        Assert.Equal(SessionEndReason.ClientClosed, await run);
        Assert.Equal(["system AppSwitchStart", "system AppSwitchEnd"], injector.Calls);
    }

    private static async Task WaitFor(Func<bool> condition)
    {
        for (var i = 0; i < 200 && !condition(); i++) await Task.Delay(10);
        Assert.True(condition(), "condición no cumplida a tiempo");
    }

    /// <summary>Flujo de lectura alimentado a mano: permite enviar mensajes en varios pasos y cerrarlo al final.</summary>
    private sealed class PipeStream : Stream
    {
        private readonly System.Threading.Channels.Channel<byte[]> _chunks = System.Threading.Channels.Channel.CreateUnbounded<byte[]>();
        private byte[] _current = [];
        private int _pos;

        public void Write(byte[] bytes) => _chunks.Writer.TryWrite(bytes);
        public void Complete() => _chunks.Writer.TryComplete();

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            while (_pos >= _current.Length)
            {
                if (!await _chunks.Reader.WaitToReadAsync(cancellationToken).ConfigureAwait(false)) return 0;
                _chunks.Reader.TryRead(out var next);
                _current = next ?? [];
                _pos = 0;
            }
            var n = Math.Min(buffer.Length, _current.Length - _pos);
            _current.AsMemory(_pos, n).CopyTo(buffer);
            _pos += n;
            return n;
        }

        public override Task<int> ReadAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken) =>
            ReadAsync(buffer.AsMemory(offset, count), cancellationToken).AsTask();

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() { }
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }

    [Fact]
    public async Task HelloFromNewerClient_NegotiatesHostVersion()
    {
        var (_, _, output, session) = await Run(Bytes(new HelloMessage(9)));
        Assert.Equal(ProtocolInfo.CurrentVersion, session.Version);
        Assert.Equal(MessageCodec.Encode(new HelloAckMessage(ProtocolInfo.CurrentVersion)), output);
    }

    [Fact]
    public async Task Gesture_IsExecutedAndAnswered()
    {
        var gestures = new FakeGestures();
        var output = new MemoryStream();
        var input = Bytes(new HelloMessage(2), new GestureMessage("z"), new GestureMessage("circle"));
        var session = new ClientSession(new MemoryStream(input), output, new FakeInjector(), gestures: gestures);
        await session.RunAsync(CancellationToken.None);

        Assert.Equal(["z", "circle"], gestures.Executed);
        Assert.Equal(Bytes(
            new HelloAckMessage(2), gestures.List,
            new GestureResultMessage(GestureStatus.Executed),
            new GestureResultMessage(GestureStatus.Unassigned)), output.ToArray());
    }

    [Fact]
    public async Task Gesture_WhilePaused_IsNotExecuted()
    {
        var gestures = new FakeGestures();
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(new HelloMessage(2), new GestureMessage("z"))),
            output, new FakeInjector(), () => true, gestures: gestures);
        await session.RunAsync(CancellationToken.None);

        Assert.Empty(gestures.Executed);
        Assert.EndsWith(Convert.ToHexString(MessageCodec.Encode(new GestureResultMessage(GestureStatus.Error))),
            Convert.ToHexString(output.ToArray()));
    }

    [Fact]
    public async Task Gesture_WithProtocolV1_IsProtocolError()
    {
        var (reason, _, _, _) = await Run(Bytes(new HelloMessage(1), new GestureMessage("z")));
        Assert.Equal(SessionEndReason.ProtocolError, reason);
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
