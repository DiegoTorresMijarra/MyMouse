using MyMouse.HostCore.Apps;
using MyMouse.HostCore.Gestures;

namespace MyMouse.HostCore.Tests;

public class ChordRecorderTests
{
    private const ushort Ctrl = 0xA2, Shift = 0xA0, Alt = 0xA4, Win = 0x5B, T = 0x54, F5 = 0x74;

    private static string Record(Action<ChordRecorder> keys)
    {
        var r = new ChordRecorder();
        keys(r);
        return r.Text;
    }

    [Fact]
    public void CtrlShiftT()
    {
        var r = new ChordRecorder();
        r.KeyDown(Ctrl);
        Assert.Equal("Ctrl+…", r.Text);
        r.KeyDown(Shift);
        Assert.Equal("Ctrl+Shift+…", r.Text);
        r.KeyDown(T);
        Assert.True(r.IsComplete);
        Assert.Equal("Ctrl+Shift+T", r.Text);
        Assert.Equal(Modifiers.Ctrl | Modifiers.Shift, r.Result!.Modifiers);
        Assert.Equal(T, r.Result.VirtualKey);
    }

    [Fact]
    public void ModifierOrderDoesNotMatter_AndKeyRepeatsAreIgnored()
    {
        Assert.Equal("Ctrl+Shift+T", Record(r => { r.KeyDown(Shift); r.KeyDown(Ctrl); r.KeyDown(Ctrl); r.KeyDown(T); r.KeyDown(T); r.KeyDown(0x55); }));
    }

    [Fact]
    public void FunctionKeysAndWin()
    {
        Assert.Equal("F5", Record(r => r.KeyDown(F5)));
        Assert.Equal("Win+D", Record(r => { r.KeyDown(Win); r.KeyDown(0x44); }));
        Assert.Equal("Alt+Left", Record(r => { r.KeyDown(Alt); r.KeyDown(0x25); }));
    }

    [Fact]
    public void AReleasedModifierIsNotPartOfTheChord()
    {
        Assert.Equal("Shift+T", Record(r => { r.KeyDown(Ctrl); r.KeyDown(Shift); r.KeyUp(Ctrl); r.KeyDown(T); }));
    }

    [Fact]
    public void OnlyModifiers_CompleteOnRelease()
    {
        var r = new ChordRecorder();
        r.KeyDown(Win);
        Assert.False(r.IsComplete);
        r.KeyUp(Win);
        Assert.Equal("Win", r.Text);
        Assert.Equal("Win", string.Join(",", KeyChord.Parse(r.Text)));

        r.Reset();
        r.KeyDown(Ctrl);
        r.KeyDown(Shift);
        r.KeyUp(Shift);
        Assert.False(r.IsComplete); // aún hay una pulsada
        r.KeyUp(Ctrl);
        Assert.Equal("Ctrl+Shift", r.Text);
        Assert.Equal("Ctrl+Shift", string.Join(",", KeyChord.Parse(r.Text)));
    }

    [Fact]
    public void UnknownKeysAreIgnored_AndResetStartsAgain()
    {
        var r = new ChordRecorder();
        r.KeyDown(0xFF);
        Assert.False(r.IsComplete);
        r.KeyDown(T);
        Assert.Equal("T", r.Text);
        r.KeyDown(Ctrl); // tras completar no cambia
        Assert.Equal("T", r.Text);
        r.Reset();
        Assert.Equal("", r.Text);
        Assert.Null(r.Result);
    }

    [Theory]
    [InlineData((ushort)0x41, "A")]
    [InlineData((ushort)0x39, "9")]
    [InlineData((ushort)0x70, "F1")]
    [InlineData((ushort)0x87, "F24")]
    [InlineData((ushort)0x21, "PageUp")]
    [InlineData((ushort)0x0D, "Enter")]
    [InlineData((ushort)0x1B, "Esc")]
    [InlineData((ushort)0xBB, "Plus")]
    [InlineData((ushort)0xB3, "PlayPause")]
    public void NameFor_AndParseAgreeOnEveryKey(ushort vk, string name)
    {
        Assert.Equal(name, KeyChord.NameFor(vk));
        Assert.Equal(vk, KeyChord.Parse(name)[0].VirtualKey);
    }

    [Fact]
    public void EveryNameableKey_RoundTripsThroughTheFileFormat()
    {
        for (ushort vk = 1; vk < 0xFF; vk++)
        {
            if (KeyChord.TryModifier(vk, out _)) continue;
            var name = KeyChord.NameFor(vk);
            if (name is null) continue;
            var stroke = new KeyStroke(Modifiers.Ctrl | Modifiers.Alt, vk, name);
            var back = KeyChord.Parse(stroke.ToString()).Single();
            Assert.Equal(vk, back.VirtualKey);
            Assert.Equal(stroke.Modifiers, back.Modifiers);
        }
        Assert.Null(KeyChord.NameFor(0xFF));
    }
}

public class StartMenuTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("mymouse-start").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    private string Touch(string relative)
    {
        var path = Path.Combine(_dir, relative);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, "");
        return path;
    }

    [Fact]
    public void Scanner_ListsShortcutsRecursively_DedupesAndSkipsUninstallers()
    {
        var user = Touch("user/Programs/Visual Studio Code.lnk");
        Touch("user/Programs/Notas.txt");
        Touch("user/Programs/Git/Git Bash.lnk");
        Touch("user/Programs/Git/Uninstall Git.lnk");
        Touch("common/Programs/Visual Studio Code.lnk"); // repetido: gana el del usuario
        Touch("common/Programs/Chrome.lnk");

        IStartMenuApps scanner = new StartMenuScanner([Path.Combine(_dir, "user"), Path.Combine(_dir, "common"), Path.Combine(_dir, "no-existe")]);
        var apps = scanner.List();

        Assert.Equal(["Chrome", "Git Bash", "Visual Studio Code"], apps.Select(a => a.Name));
        Assert.Equal(Path.GetFullPath(user), Path.GetFullPath(apps.Single(a => a.Name == "Visual Studio Code").Path));
    }

    private static readonly IReadOnlyList<InstalledApp> Apps =
    [
        new("Visual Studio Code", "a.lnk"), new("Código de barras", "b.lnk"), new("Chrome", "c.lnk"),
        new("Google Chrome Canary", "d.lnk"), new("Calculadora", "e.lnk"),
    ];

    [Fact]
    public void Search_IgnoresCaseAndAccents_AndAllWordsMustMatch()
    {
        Assert.Equal(["Código de barras"], AppSearch.Filter(Apps, "codigo").Select(a => a.Name));
        Assert.Equal(["Visual Studio Code"], AppSearch.Filter(Apps, "STUDIO code").Select(a => a.Name));
        Assert.Empty(AppSearch.Filter(Apps, "firefox"));
        Assert.Same(Apps, AppSearch.Filter(Apps, "  "));
    }

    [Fact]
    public void Search_PutsThoseStartingWithTheQueryFirst()
    {
        Assert.Equal(["Chrome", "Google Chrome Canary"], AppSearch.Filter(Apps, "chrome").Select(a => a.Name));
    }
}

public class AdbModelTests
{
    [Fact]
    public void ParseModel_ReadsTheFirstDevice()
    {
        const string output = "List of devices attached\r\nABC123\tdevice usb:1-2 product:marble model:Poco_X8_Pro_Max device:marble transport_id:1\r\n\r\n";
        Assert.Equal("Poco X8 Pro Max", AdbReverseKeeper.ParseModel(output));
    }

    [Theory]
    [InlineData("List of devices attached\r\n\r\n")]
    [InlineData("List of devices attached\r\nABC123\tunauthorized usb:1-2\r\n")]
    [InlineData("")]
    public void ParseModel_WithoutADevice_IsNull(string output) => Assert.Null(AdbReverseKeeper.ParseModel(output));
}
