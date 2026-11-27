using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Theming;
using MyMouse.HostCore.Toolbox;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Tema sincronizado (docs/spec-v6.1-tema-sincronizado.md). Mismos casos que ThemeSyncTest en el móvil.</summary>
public class ThemeSyncTests
{
    private static readonly SyncedTheme Universe = new("universe", "auto", true, 1000);

    [Fact]
    public void NewerRemote_Wins() =>
        Assert.Equal(Universe with { ThemeId = "ocean", ChangedAtMs = 2000 },
            ThemeSync.Resolve(Universe, Universe with { ThemeId = "ocean", ChangedAtMs = 2000 }, localIsPhone: false));

    [Fact]
    public void OlderRemote_IsIgnored() =>
        Assert.Null(ThemeSync.Resolve(Universe, Universe with { ThemeId = "ocean", ChangedAtMs = 500 }, localIsPhone: true));

    [Fact]
    public void SameInstant_DifferentLook_PhoneWins()
    {
        var phone = new SyncedTheme("graphite", "auto", true, 0);
        var pc = new SyncedTheme("universe", "auto", true, 0);
        Assert.Equal(phone, ThemeSync.Resolve(pc, phone, localIsPhone: false));
        Assert.Null(ThemeSync.Resolve(phone, pc, localIsPhone: true));
    }

    [Fact]
    public void SameLook_OnlyTakesANewerInstant()
    {
        Assert.Equal(Universe with { ChangedAtMs = 5000 }, ThemeSync.Resolve(Universe, Universe with { ChangedAtMs = 5000 }, localIsPhone: true));
        Assert.Null(ThemeSync.Resolve(Universe, Universe, localIsPhone: false));
    }

    [Fact]
    public void ToolboxLayout_KeepsTheInstant()
    {
        var layout = ToolboxLayout.Default.WithTheme(new ThemeChoice("ocean", "green", Stars: false), 1_764_000_000_000);
        var parsed = ToolboxLayout.Parse(layout.WithGlass(false).ToJson());
        Assert.Equal(1_764_000_000_000, parsed.ThemeChangedAtMs);
        Assert.Equal("ocean", parsed.Theme.ThemeId);
        Assert.Equal(0, ToolboxLayout.Parse("""{ "theme": { "id": "ocean" } }""").ThemeChangedAtMs); // sin instante guardado
    }

    private static byte[] Bytes(params Message[] messages) => messages.SelectMany(MessageCodec.Encode).ToArray();

    private static async Task<(SessionEndReason Reason, List<Message> Sent, List<SyncedTheme> Adopted)> Run(SyncedTheme local, params Message[] input)
    {
        var hub = new PhoneHub();
        using var sync = new ThemeSyncService(hub, () => local);
        var adopted = new List<SyncedTheme>();
        sync.Adopted += adopted.Add;
        var output = new MemoryStream();
        var session = new ClientSession(new MemoryStream(Bytes(input)), output, new NullInjector(), phone: hub);
        var reason = await session.RunAsync(CancellationToken.None);
        var sent = new List<Message>();
        var reader = new MessageReader(new MemoryStream(output.ToArray()));
        while (await reader.ReadAsync() is { } m) sent.Add(m);
        return (reason, sent, adopted);
    }

    [Fact]
    public async Task V8_SendsThePcTheme_AndAdoptsANewerPhoneTheme()
    {
        var (reason, sent, adopted) = await Run(Universe, new HelloMessage(1), new ThemeMessage("ocean", "green", false, 2000));

        Assert.Equal(SessionEndReason.ClientClosed, reason);
        Assert.Contains(new ThemeMessage("universe", "auto", true, 1000, FromHost: true), sent);
        Assert.Equal([new SyncedTheme("ocean", "green", false, 2000)], adopted);
    }

    [Fact]
    public async Task V8_KeepsANewerPcTheme()
    {
        var (_, _, adopted) = await Run(Universe, new HelloMessage(1), new ThemeMessage("ocean", "green", false, 10));
        Assert.Empty(adopted);
    }

    [Fact]
    public async Task HostThemeFromThePhone_IsAProtocolError()
    {
        var (reason, _, _) = await Run(Universe, new HelloMessage(1), new ThemeMessage("ocean", "auto", true, 1, FromHost: true));
        Assert.Equal(SessionEndReason.ProtocolError, reason);
    }

    private sealed class NullInjector : IInputInjector
    {
        public void MoveRelative(int dx, int dy) { }
        public void SetButton(MouseButton button, bool pressed) { }
        public void Scroll(int vertical, int horizontal) { }
        public void Zoom(int delta) { }
        public void SystemGesture(SystemGestureCommand command) { }
        public void SetKey(byte usage, bool down) { }
    }
}
