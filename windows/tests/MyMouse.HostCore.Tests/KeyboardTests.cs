using MyMouse.HostCore.Keyboard;
using MyMouse.HostCore.Phone;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Tabla HID a scancode (docs/spec-v6-teclado.md §2).</summary>
public class HidScancodesTests
{
    [Theory]
    [InlineData(0x04, 0x1E, false)] // A
    [InlineData(0x1D, 0x2C, false)] // Z
    [InlineData(0x10, 0x32, false)] // M
    [InlineData(0x1E, 0x02, false)] // 1
    [InlineData(0x27, 0x0B, false)] // 0
    [InlineData(0x28, 0x1C, false)] // Intro
    [InlineData(0x29, 0x01, false)] // Esc
    [InlineData(0x2A, 0x0E, false)] // Retroceso
    [InlineData(0x2B, 0x0F, false)] // Tab
    [InlineData(0x2C, 0x39, false)] // Espacio
    [InlineData(0x35, 0x29, false)] // º / `
    [InlineData(0x39, 0x3A, false)] // Bloq Mayús
    [InlineData(0x3A, 0x3B, false)] // F1
    [InlineData(0x43, 0x44, false)] // F10
    [InlineData(0x44, 0x57, false)] // F11
    [InlineData(0x45, 0x58, false)] // F12
    [InlineData(0x46, 0x37, true)] // Impr Pant
    [InlineData(0x49, 0x52, true)] // Insert
    [InlineData(0x4A, 0x47, true)] // Inicio
    [InlineData(0x4C, 0x53, true)] // Supr
    [InlineData(0x4D, 0x4F, true)] // Fin
    [InlineData(0x4F, 0x4D, true)] // →
    [InlineData(0x50, 0x4B, true)] // ←
    [InlineData(0x51, 0x50, true)] // ↓
    [InlineData(0x52, 0x48, true)] // ↑
    [InlineData(0x53, 0x45, false)] // Bloq Num
    [InlineData(0x54, 0x35, true)] // / del numpad
    [InlineData(0x55, 0x37, false)] // * del numpad
    [InlineData(0x58, 0x1C, true)] // Intro del numpad
    [InlineData(0x59, 0x4F, false)] // 1 del numpad
    [InlineData(0x5F, 0x47, false)] // 7 del numpad
    [InlineData(0x62, 0x52, false)] // 0 del numpad
    [InlineData(0x63, 0x53, false)] // . del numpad
    [InlineData(0x64, 0x56, false)] // <> de la ISO
    [InlineData(0x65, 0x5D, true)] // Menú
    [InlineData(0xE0, 0x1D, false)] // Ctrl
    [InlineData(0xE1, 0x2A, false)] // Shift izq.
    [InlineData(0xE2, 0x38, false)] // Alt
    [InlineData(0xE3, 0x5B, true)] // Win
    [InlineData(0xE5, 0x36, false)] // Shift dcha.
    [InlineData(0xE6, 0x38, true)] // AltGr = Alt dcha.
    public void KnownUsages_MapToTheirScancode(byte usage, int code, bool extended)
    {
        Assert.True(HidScancodes.TryGet(usage, out var sc));
        Assert.Equal(new ScanCode((ushort)code, extended), sc);
    }

    [Theory]
    [InlineData(0x00)]
    [InlineData(0x01)] // errores del informe HID
    [InlineData(0x48)] // Pausa: no se puede con un scancode
    [InlineData(0x66)]
    [InlineData(0xA0)]
    [InlineData(0xE8)]
    [InlineData(0xFF)]
    public void UnknownUsages_AreRejected(byte usage) => Assert.False(HidScancodes.TryGet(usage, out _));

    [Fact]
    public void EveryUsageTheMobileKeyboardSends_IsKnown()
    {
        // Los usos de KeyboardModel (core): letras, números, puntuación, F1–F12, edición, flechas, numpad, ISO, Menú y modificadores.
        var used = new List<byte>();
        used.AddRange(Enumerable.Range(0x04, 0x46 - 0x04).Select(i => (byte)i)); // 0x04–0x45
        used.AddRange([0x46, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52]);
        used.AddRange(Enumerable.Range(0x53, 0x65 - 0x53 + 1).Select(i => (byte)i));
        used.AddRange([0xE0, 0xE1, 0xE2, 0xE3, 0xE5, 0xE6]);
        foreach (var u in used) Assert.True(HidScancodes.TryGet(u, out _), $"uso 0x{u:X2}");
    }

    [Fact]
    public void ScancodesAreUniquePerKey_ExceptTheSharedBackslash()
    {
        var byCode = HidScancodes.Usages.GroupBy(u => { HidScancodes.TryGet(u, out var c); return c; })
            .Where(g => g.Count() > 1).ToList();
        // La \ de ANSI (0x31) y la # europea de ISO (0x32, la Ç en ES) comparten el scancode 0x2B.
        Assert.Single(byCode);
        Assert.Equal(new ScanCode(0x2B, false), byCode[0].Key);
        Assert.Equal([(byte)0x31, (byte)0x32], byCode[0].OrderBy(u => u));
    }

    [Fact]
    public void ModifiersAreExactlyEightUsages()
    {
        Assert.Equal(8, Enumerable.Range(0, 256).Count(i => HidScancodes.IsModifier((byte)i)));
        Assert.All(Enumerable.Range(0xE0, 8), i => Assert.True(HidScancodes.TryGet((byte)i, out _)));
    }

    [Theory]
    [InlineData(0x040A, "es")] // Español (tradicional)
    [InlineData(0x0C0A, "es")] // Español (internacional)
    [InlineData(0x0409, "us")] // Inglés (EE. UU.)
    [InlineData(0x080A, "us")] // Español (México)
    [InlineData(0x040C, "us")] // Francés
    [InlineData(0x0000, "us")]
    public void LayoutId_FromLangId(int langId, string expected) =>
        Assert.Equal(expected, KeyboardLayoutIds.FromLangId((ushort)langId));

    [Theory]
    [InlineData("es-ES", "es")]
    [InlineData("ES-es", "es")]
    [InlineData("es-ES_tradnl", "es")]
    [InlineData("en-US", "us")]
    [InlineData("es-MX", "us")]
    [InlineData("", "us")]
    public void LayoutId_FromCultureName(string name, string expected) => Assert.Equal(expected, KeyboardLayoutIds.FromCultureName(name));
}

public class KeyboardSessionTests
{
    private sealed class Injector : IInputInjector
    {
        public List<string> Calls { get; } = [];
        public void MoveRelative(int dx, int dy) => Calls.Add($"move {dx},{dy}");
        public void SetButton(MouseButton button, bool pressed) => Calls.Add($"{button} {(pressed ? "down" : "up")}");
        public void Scroll(int vertical, int horizontal) { }
        public void Zoom(int delta) { }
        public void SystemGesture(SystemGestureCommand command) { }
        public void SetKey(byte usage, bool down) => Calls.Add($"key {usage:X2} {(down ? "down" : "up")}");
    }

    private sealed class FakeState : IKeyboardState
    {
        public volatile byte Leds;
        public volatile string Layout = "es";
        public byte ReadLeds() => Leds;
        public string ReadLayoutId() => Layout;
    }

    private static byte[] Bytes(params Message[] messages) => messages.SelectMany(MessageCodec.Encode).ToArray();

    private static async Task<(SessionEndReason, Injector, byte[])> Run(byte[] input, bool paused = false, PhoneHub? hub = null)
    {
        var injector = new Injector();
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(input), output, injector, () => paused, phone: hub);
        return (await session.RunAsync(CancellationToken.None), injector, output.ToArray());
    }

    [Fact]
    public async Task Keys_AreInjectedAsPressAndRelease()
    {
        var (reason, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0xE1, true), new KeyMessage(0x04, true), new KeyMessage(0x04, false), new KeyMessage(0xE1, false)));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(["key E1 down", "key 04 down", "key 04 up", "key E1 up"], injector.Calls);
    }

    [Fact]
    public async Task RepeatedPress_IsInjectedAgainWithoutRelease()
    {
        var (_, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0x2A, true), new KeyMessage(0x2A, true), new KeyMessage(0x2A, true), new KeyMessage(0x2A, false)));

        Assert.Equal(["key 2A down", "key 2A down", "key 2A down", "key 2A up"], injector.Calls);
    }

    [Fact]
    public async Task UnknownUsage_IsIgnoredWithoutBreakingTheFlow()
    {
        var (reason, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0x48, true), new KeyMessage(0xFF, true), new KeyMessage(0x04, true), new KeyMessage(0x04, false)));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Equal(["key 04 down", "key 04 up"], injector.Calls);
    }

    [Fact]
    public async Task ReleaseOfAKeyThatIsNotDown_IsIgnored()
    {
        var (_, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0x04, false)));

        Assert.Empty(injector.Calls);
    }

    [Fact]
    public async Task KeysStillDown_AreReleasedWhenTheSessionEnds()
    {
        var (_, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0xE0, true), new KeyMessage(0xE1, true), new KeyMessage(0x06, true)));

        // Las normales primero y los modificadores al final.
        Assert.Equal(["key E0 down", "key E1 down", "key 06 down", "key 06 up", "key E0 up", "key E1 up"], injector.Calls);
    }

    [Fact]
    public async Task KeyboardModeOff_ReleasesPressedKeys()
    {
        var (_, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyboardModeMessage(true), new KeyMessage(0x04, true), new KeyboardModeMessage(false), new KeyMessage(0x05, true), new KeyMessage(0x05, false)));

        Assert.Equal(["key 04 down", "key 04 up", "key 05 down", "key 05 up"], injector.Calls);
    }

    [Fact]
    public async Task WhenPaused_KeysAreNotInjected()
    {
        var (reason, injector, _) = await Run(Bytes(new HelloMessage(1), new KeyMessage(0x04, true), new KeyMessage(0x04, false)), paused: true);

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Empty(injector.Calls);
    }

    [Fact]
    public async Task HostToPhoneKeyboardMessages_AreProtocolErrorsFromTheClient()
    {
        foreach (var m in new Message[] { KeyboardOpenMessage.Instance, new KeyboardLedsMessage(1), new KeyboardLayoutMessage("es") })
        {
            var (reason, _, _) = await Run(Bytes(new HelloMessage(1), m));
            Assert.Equal(SessionEndReason.ProtocolError, reason);
        }
    }

    [Fact]
    public async Task KeyboardMode_IsReflectedInThePhoneHub()
    {
        var hub = new PhoneHub();
        var changes = new List<bool>();
        hub.KeyboardModeChanged += changes.Add;
        var injector = new Injector();
        var (input, written) = DuplexPair();
        var session = new ClientSession(input.Server, input.Server, injector, phone: hub);
        var run = session.RunAsync(CancellationToken.None);

        await WriteAsync(written, new HelloMessage(1), new KeyboardModeMessage(true));
        await WaitFor(() => hub.KeyboardMode);
        Assert.True(hub.Connected);
        await WriteAsync(written, new KeyboardModeMessage(false));
        await WaitFor(() => !hub.KeyboardMode);
        Assert.Equal([true, false], changes);

        await WriteAsync(written, new KeyboardModeMessage(true));
        await WaitFor(() => hub.KeyboardMode);
        written.Dispose();
        await run;
        // Al terminar la sesión no queda ningún móvil.
        Assert.False(hub.KeyboardMode);
        Assert.False(hub.Connected);
    }

    [Fact]
    public async Task LedsAndLayout_AreSentOnEnteringAndThenOnlyOnChanges()
    {
        var state = new FakeState { Leds = 1, Layout = "es" };
        var (pipe, writer) = DuplexPair();
        var sent = new List<Message>();
        var session = new ClientSession(pipe.Server, pipe.Server, new Injector(), keyboard: state, keyboardInterval: TimeSpan.FromMilliseconds(20));
        var run = session.RunAsync(CancellationToken.None);
        var reading = Task.Run(async () =>
        {
            var reader = new MessageReader(pipe.Client);
            while (await reader.ReadAsync() is { } m) lock (sent) sent.Add(m);
        });

        await WriteAsync(writer, new HelloMessage(1), new KeyboardModeMessage(true));
        await WaitFor(() => Snapshot(sent).Count(m => m is KeyboardLedsMessage or KeyboardLayoutMessage) >= 2);
        await Task.Delay(120); // varios ciclos sin cambios: no se repite nada
        Assert.Equal([new KeyboardLedsMessage(1), new KeyboardLayoutMessage("es")], Snapshot(sent).Where(m => m is KeyboardLedsMessage or KeyboardLayoutMessage));

        state.Leds = 3;
        await WaitFor(() => Snapshot(sent).OfType<KeyboardLedsMessage>().Count() == 2);
        state.Layout = "us";
        await WaitFor(() => Snapshot(sent).OfType<KeyboardLayoutMessage>().Count() == 2);
        Assert.Equal(new KeyboardLedsMessage(3), Snapshot(sent).OfType<KeyboardLedsMessage>().Last());
        Assert.Equal(new KeyboardLayoutMessage("us"), Snapshot(sent).OfType<KeyboardLayoutMessage>().Last());

        // Al salir del modo teclado se deja de vigilar.
        await WriteAsync(writer, new KeyboardModeMessage(false));
        await Task.Delay(80);
        var count = Snapshot(sent).Count;
        state.Leds = 7;
        await Task.Delay(120);
        Assert.Equal(count, Snapshot(sent).Count);

        writer.Dispose();
        await run;
        pipe.Server.Dispose();
        await reading;
    }

    [Fact]
    public async Task MonitorDoesNotSendAnEmptyLayout()
    {
        var state = new FakeState { Leds = 0, Layout = "" };
        var sent = new List<Message>();
        using var cts = new CancellationTokenSource();
        var monitor = new KeyboardStateMonitor(state, (m, _) => { lock (sent) sent.Add(m); return ValueTask.CompletedTask; }, TimeSpan.FromMilliseconds(10));
        var run = monitor.RunAsync(cts.Token);
        await WaitFor(() => Snapshot(sent).Count >= 1);
        await Task.Delay(50);
        Assert.Equal([new KeyboardLedsMessage(0)], Snapshot(sent));
        state.Layout = "us";
        await WaitFor(() => Snapshot(sent).Count >= 2);
        cts.Cancel();
        await run;
        Assert.Equal(new KeyboardLayoutMessage("us"), Snapshot(sent)[1]);
    }

    // ---- utilidades: una conexión en memoria ----

    private sealed record Pipe(Stream Server, Stream Client);

    private static (Pipe, Stream) DuplexPair()
    {
        var toServer = new System.IO.Pipes.AnonymousPipeServerStream(System.IO.Pipes.PipeDirection.Out);
        var serverIn = new System.IO.Pipes.AnonymousPipeClientStream(System.IO.Pipes.PipeDirection.In, toServer.ClientSafePipeHandle);
        var toClient = new System.IO.Pipes.AnonymousPipeServerStream(System.IO.Pipes.PipeDirection.Out);
        var clientIn = new System.IO.Pipes.AnonymousPipeClientStream(System.IO.Pipes.PipeDirection.In, toClient.ClientSafePipeHandle);
        return (new Pipe(new Duplex(serverIn, toClient), clientIn), toServer);
    }

    private sealed class Duplex(Stream read, Stream write) : Stream
    {
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() => write.Flush();
        public override int Read(byte[] buffer, int offset, int count) => read.Read(buffer, offset, count);
        public override Task<int> ReadAsync(byte[] buffer, int offset, int count, CancellationToken ct) => read.ReadAsync(buffer, offset, count, ct);
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken ct = default) => read.ReadAsync(buffer, ct);
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => write.Write(buffer, offset, count);
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken ct = default) => write.WriteAsync(buffer, ct);
        protected override void Dispose(bool disposing)
        {
            if (disposing) { read.Dispose(); write.Dispose(); }
            base.Dispose(disposing);
        }
    }

    private static async Task WriteAsync(Stream stream, params Message[] messages)
    {
        foreach (var m in messages) await stream.WriteAsync(MessageCodec.Encode(m));
        await stream.FlushAsync();
    }

    private static List<Message> Snapshot(List<Message> list)
    {
        lock (list) return [.. list];
    }

    private static async Task WaitFor(Func<bool> condition)
    {
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while (!condition())
        {
            if (DateTime.UtcNow > deadline) throw new TimeoutException("Condición no cumplida");
            await Task.Delay(10);
        }
    }
}
