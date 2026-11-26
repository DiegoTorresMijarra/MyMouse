using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Widgets en la sesión: handshake v3, toques, volumen, música, pausa y compatibilidad con clientes v2.</summary>
public class ClientSessionWidgetTests
{
    private sealed class NullInjector : IInputInjector
    {
        public void MoveRelative(int dx, int dy) { }
        public void SetButton(MouseButton button, bool pressed) { }
        public void Scroll(int vertical, int horizontal) { }
        public void Zoom(int delta) { }
        public void SystemGesture(SystemGestureCommand command) { }
    }

    private readonly FakeExecutor _executor = new();
    private readonly FakeVolume _volume = new();
    private readonly FakeMedia _media = new();

    private WidgetService Widgets()
    {
        var service = new WidgetService(_executor, _volume, _media);
        service.SetWidgets(GestureConfig.Parse("""
            { "bindings": [], "widgets": [
              { "type": "media" },
              { "type": "volume" },
              { "type": "button", "label": "Copiar", "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } }
            ] }
            """).Widgets);
        return service;
    }

    private static byte[] Bytes(params Message[] messages) => messages.SelectMany(MessageCodec.Encode).ToArray();

    private static readonly WidgetLayoutMessage Layout = new([
        new WidgetLayoutEntry(0, WidgetKind.Media, "", ""),
        new WidgetLayoutEntry(1, WidgetKind.Volume, "", ""),
        new WidgetLayoutEntry(2, WidgetKind.Button, "Copiar", "📋"),
    ]);

    private async Task<(SessionEndReason reason, byte[] output, ClientSession session)> Run(
        IWidgetService? widgets, bool paused, params Message[] input)
    {
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(input)), output, new NullInjector(), () => paused, widgets: widgets);
        var reason = await session.RunAsync(CancellationToken.None);
        return (reason, output.ToArray(), session);
    }

    [Fact]
    public async Task HelloV3_SendsLayoutAndInitialState()
    {
        _media.Info = new MediaInfo(true, "A", "B");
        var (_, output, session) = await Run(Widgets(), false, new HelloMessage(3));

        Assert.Equal(3, session.Version);
        Assert.Equal(Bytes(
            new HelloAckMessage(3), Layout, new VolumeStateMessage(40, false), new MediaStateMessage(true, "A", "B")), output);
    }

    [Fact]
    public async Task HelloV3_SendsLayoutAfterTheGestureList()
    {
        var output = new MemoryStream();
        var gestures = new GestureService(_executor);
        gestures.SetConfig(GestureConfig.Parse("""{ "bindings": [ { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" } } ] }"""));
        var session = new ClientSession(new MemoryStream(Bytes(new HelloMessage(3))), output, new NullInjector(),
            gestures: gestures, widgets: Widgets());
        await session.RunAsync(CancellationToken.None);

        Assert.Equal(Bytes(
            new HelloAckMessage(3), gestures.CurrentList(), Layout, new VolumeStateMessage(40, false), MediaNothing), output.ToArray());
    }

    private static readonly MediaStateMessage MediaNothing = new(false, "", "");

    [Fact]
    public async Task HelloV3_WithoutWidgets_SendsAnEmptyLayout()
    {
        var service = new WidgetService(_executor, _volume, _media);
        var (_, output, _) = await Run(service, false, new HelloMessage(3));

        Assert.Equal(Bytes(new HelloAckMessage(3), new WidgetLayoutMessage([])), output);
    }

    [Fact]
    public async Task ClientV2_KeepsWorking_AndGetsNoWidgetMessages()
    {
        var (reason, output, session) = await Run(Widgets(), false, new HelloMessage(2), PingMessage.Instance);

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(2, session.Version);
        Assert.Equal(Bytes(new HelloAckMessage(2), PongMessage.Instance), output);
    }

    [Fact]
    public async Task ClientV2_VolumeChangesAreNotSent()
    {
        var widgets = Widgets();
        var output = new MemoryStream();
        var input = new ScriptedInput();
        var session = new ClientSession(input, output, new NullInjector(), widgets: widgets);
        var run = session.RunAsync(CancellationToken.None);
        input.Push(Bytes(new HelloMessage(2)));
        await WaitFor(() => output.Length > 0);

        _volume.Info = new VolumeInfo(70, false);
        _volume.RaiseChanged();
        widgets.SetWidgets([new VolumeWidget()]);
        input.Push(Bytes(PingMessage.Instance));
        input.Finish();
        await run;

        Assert.Equal(Bytes(new HelloAckMessage(2), PongMessage.Instance), output.ToArray());
    }

    [Theory]
    [InlineData(0)]
    [InlineData(1)]
    [InlineData(2)]
    [InlineData(3)]
    public async Task WidgetMessagesWithV2_AreProtocolErrors(int which)
    {
        Message message = which switch
        {
            0 => new WidgetPressMessage(2),
            1 => new VolumeSetMessage(50),
            2 => new MediaCommandMessage(MediaCommand.Next),
            _ => new VolumeMuteMessage(true),
        };
        var (reason, _, _) = await Run(Widgets(), false, new HelloMessage(2), message);

        Assert.Equal(SessionEndReason.ProtocolError, reason);
        Assert.Empty(_executor.Executed);
        Assert.Empty(_volume.Calls);
        Assert.Empty(_media.Sent);
    }

    [Fact]
    public async Task WidgetPress_ExecutesAndAnswers()
    {
        var (_, output, _) = await Run(Widgets(), false,
            new HelloMessage(3), new WidgetPressMessage(2), new WidgetPressMessage(0), new WidgetPressMessage(77));

        Assert.Equal(["Ctrl+C"], _executor.Executed.Select(a => a.DefaultHelp));
        Assert.EndsWith(Convert.ToHexString(Bytes(
            new WidgetResultMessage(2, GestureStatus.Executed),
            new WidgetResultMessage(0, GestureStatus.Error),
            new WidgetResultMessage(77, GestureStatus.Error))), Convert.ToHexString(output));
    }

    [Fact]
    public async Task WidgetPress_WhenTheActionFails_AnswersError()
    {
        _executor.Fail = true;
        var (reason, output, _) = await Run(Widgets(), false, new HelloMessage(3), new WidgetPressMessage(2));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.EndsWith(Convert.ToHexString(MessageCodec.Encode(new WidgetResultMessage(2, GestureStatus.Error))), Convert.ToHexString(output));
    }

    [Fact]
    public async Task VolumeAndMediaCommands_AreApplied_WithoutAnswer()
    {
        var (_, output, _) = await Run(Widgets(), false, new HelloMessage(3),
            new VolumeSetMessage(55), new VolumeSetMessage(200), new VolumeMuteMessage(true),
            new MediaCommandMessage(MediaCommand.PlayPause), new MediaCommandMessage(MediaCommand.Previous));

        Assert.Equal(["level 55", "level 100", "mute True"], _volume.Calls);
        Assert.Equal([MediaCommand.PlayPause, MediaCommand.Previous], _media.Sent);
        Assert.Equal(Bytes(new HelloAckMessage(3), Layout, new VolumeStateMessage(40, false), MediaNothing), output);
    }

    [Fact]
    public async Task CommandsWithoutTheWidget_AreIgnored()
    {
        var service = new WidgetService(_executor, _volume, _media);
        service.SetWidgets([new MediaWidget()]);
        var (reason, _, _) = await Run(service, false, new HelloMessage(3), new VolumeSetMessage(55), new VolumeMuteMessage(true));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Empty(_volume.Calls);
    }

    [Fact]
    public async Task WhilePaused_NothingChanges_AndTheClientIsTold()
    {
        var (_, output, _) = await Run(Widgets(), true, new HelloMessage(3),
            new WidgetPressMessage(2), new VolumeSetMessage(55), new VolumeMuteMessage(true), new MediaCommandMessage(MediaCommand.Next));

        Assert.Empty(_executor.Executed);
        Assert.Empty(_volume.Calls);
        Assert.Empty(_media.Sent);
        Assert.EndsWith(Convert.ToHexString(Bytes(
            new WidgetResultMessage(2, GestureStatus.Error),
            new WidgetResultMessage(1, GestureStatus.Error),
            new WidgetResultMessage(1, GestureStatus.Error),
            new WidgetResultMessage(0, GestureStatus.Error))), Convert.ToHexString(output));
    }

    [Fact]
    public async Task ChangesInThePc_AreSentWhileConnected()
    {
        var widgets = Widgets();
        var output = new MemoryStream();
        var input = new ScriptedInput();
        var session = new ClientSession(input, output, new NullInjector(), widgets: widgets);
        var run = session.RunAsync(CancellationToken.None);
        input.Push(Bytes(new HelloMessage(3)));
        var initial = Bytes(new HelloAckMessage(3), Layout, new VolumeStateMessage(40, false), MediaNothing);
        await WaitFor(() => output.Length >= initial.Length);

        _volume.Info = new VolumeInfo(71, true);
        _volume.RaiseChanged();
        _media.Info = new MediaInfo(true, "T", "A");
        _media.RaiseChanged();
        var changes = Bytes(new VolumeStateMessage(71, true), new MediaStateMessage(true, "T", "A"));
        await WaitFor(() => output.Length >= initial.Length + changes.Length);

        widgets.SetWidgets([new VolumeWidget()]);
        var relayout = Bytes(new WidgetLayoutMessage([new WidgetLayoutEntry(0, WidgetKind.Volume, "", "")]), new VolumeStateMessage(71, true));
        await WaitFor(() => output.Length >= initial.Length + changes.Length + relayout.Length);

        input.Finish();
        await run;

        // El orden entre volumen y música no está garantizado (hilos distintos): se comprueba el contenido.
        var all = output.ToArray();
        Assert.Equal(initial, all[..initial.Length]);
        var sentChanges = Convert.ToHexString(all[initial.Length..(initial.Length + changes.Length)]);
        Assert.Contains(Convert.ToHexString(Bytes(new VolumeStateMessage(71, true))), sentChanges);
        Assert.Contains(Convert.ToHexString(Bytes(new MediaStateMessage(true, "T", "A"))), sentChanges);
        Assert.Equal(relayout, all[(initial.Length + changes.Length)..]);
    }

    [Fact]
    public async Task AfterTheSessionEnds_ItNoLongerListensToTheService()
    {
        var widgets = Widgets();
        var (_, output, _) = await Run(widgets, false, new HelloMessage(3));
        var length = output.Length;

        _volume.Info = new VolumeInfo(99, false);
        _volume.RaiseChanged();
        widgets.SetWidgets([]);
        await Task.Delay(100);

        Assert.Equal(length, output.Length);
    }

    private static async Task WaitFor(Func<bool> condition)
    {
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while (!condition())
        {
            if (DateTime.UtcNow > deadline) throw new TimeoutException("La condición no se cumplió");
            await Task.Delay(10);
        }
    }

    /// <summary>Entrada que se alimenta a mano: las lecturas esperan hasta que llegan datos o se termina.</summary>
    private sealed class ScriptedInput : Stream
    {
        private readonly System.Threading.Channels.Channel<byte[]> _chunks = System.Threading.Channels.Channel.CreateUnbounded<byte[]>();
        private byte[] _current = [];
        private int _pos;

        public void Push(byte[] bytes) => _chunks.Writer.TryWrite(bytes);
        public void Finish() => _chunks.Writer.TryComplete();

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            while (_pos >= _current.Length)
            {
                if (!await _chunks.Reader.WaitToReadAsync(cancellationToken)) return 0;
                if (_chunks.Reader.TryRead(out var next)) { _current = next; _pos = 0; }
            }
            var n = Math.Min(buffer.Length, _current.Length - _pos);
            _current.AsMemory(_pos, n).CopyTo(buffer);
            _pos += n;
            return n;
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
