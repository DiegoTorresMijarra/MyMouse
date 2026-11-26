using MyMouse.HostCore.Gestures;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class KeyChordTests
{
    [Theory]
    [InlineData("Ctrl+Shift+T", "Ctrl+Shift+T")]
    [InlineData("ctrl + z", "Ctrl+Z")]
    [InlineData("Win+D", "Win+D")]
    [InlineData("Alt+Left", "Alt+Left")]
    [InlineData("F5", "F5")]
    [InlineData("Ctrl+K, Ctrl+C", "Ctrl+K, Ctrl+C")]
    [InlineData("Ctrl++", "Ctrl+Plus")]
    [InlineData("Win", "Win")]
    [InlineData("supr", "Delete")]
    public void Parse_RoundTrips(string input, string expected)
    {
        Assert.Equal(expected, string.Join(", ", KeyChord.Parse(input)));
    }

    [Fact]
    public void Parse_GivesVirtualKeys()
    {
        var s = KeyChord.Parse("Ctrl+Shift+T").Single();
        Assert.Equal(Modifiers.Ctrl | Modifiers.Shift, s.Modifiers);
        Assert.Equal(0x54, s.VirtualKey);
        Assert.Equal(0x74, KeyChord.Parse("F5")[0].VirtualKey);
        Assert.Equal(0xB3, KeyChord.Parse("PlayPause")[0].VirtualKey);
        Assert.Equal((ushort)0x5B, KeyChord.Parse("Win")[0].VirtualKey);
        Assert.Equal(Modifiers.None, KeyChord.Parse("Win")[0].Modifiers);
    }

    [Theory]
    [InlineData("")]
    [InlineData("Ctrl+Patata")]
    [InlineData("Ctrl+A+B")]
    [InlineData("A+Ctrl")]
    [InlineData("Ctrl+K,")]
    public void Parse_Invalid_Throws(string input)
    {
        Assert.Throws<FormatException>(() => KeyChord.Parse(input));
    }
}

public class GestureConfigTests
{
    private const string Json = """
        {
          // comentario permitido
          "bindings": [
            { "gesture": "circle", "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true }, "help": "Abrir Chrome", "pin": true },
            { "gesture": "check", "action": { "type": "keys", "keys": "Ctrl+S" } },
            { "gesture": "check", "action": { "type": "keys", "keys": "F5" }, "app": "chrome.exe" },
            { "gesture": "v", "action": { "type": "media", "key": "PlayPause" } },
            { "gesture": "bad", "action": { "type": "keys", "keys": "Ctrl+Patata" } },
            { "gesture": "nope", "action": { "type": "teleport" } },
          ]
        }
        """;

    [Fact]
    public void Parse_KeepsValidRulesAndWarnsAboutTheRest()
    {
        var c = GestureConfig.Parse(Json);
        Assert.Equal(4, c.Bindings.Count);
        Assert.Equal(2, c.Warnings.Count);
        Assert.Contains("Patata", c.Warnings[0]);
        Assert.Contains("teleport", c.Warnings[1]);
        Assert.Equal(new LaunchAction("chrome.exe", null, true), c.Bindings[0].Action);
    }

    [Fact]
    public void Parse_InvalidJson_Throws()
    {
        Assert.Throws<FormatException>(() => GestureConfig.Parse("{ bindings: "));
        Assert.Throws<FormatException>(() => GestureConfig.Parse("[]"));
    }

    [Fact]
    public void DefaultJson_IsValid()
    {
        var c = GestureConfig.Parse(GestureConfig.DefaultJson);
        Assert.Empty(c.Warnings);
        Assert.True(c.Bindings.Count >= 10);
    }

    [Fact]
    public void Resolve_PrefersAppRule()
    {
        var c = GestureConfig.Parse(Json);
        Assert.Equal("Ctrl+S", c.Resolve("check", "Code")!.HelpText);
        Assert.Equal("F5", c.Resolve("check", "chrome")!.HelpText);
        Assert.Equal("F5", c.Resolve("check", "CHROME.EXE")!.HelpText);
        Assert.Null(c.Resolve("z", null));
    }

    [Fact]
    public void DefaultHelpTexts()
    {
        Assert.Equal("Abrir chrome", new LaunchAction(@"C:\Program Files\Google\chrome.exe", null, false).DefaultHelp);
        Assert.Equal("Abrir https://github.com", new LaunchAction("https://github.com", null, false).DefaultHelp);
        Assert.Equal("Reproducir / pausar", new MediaAction("PlayPause", KeyChord.Parse("PlayPause")[0]).DefaultHelp);
    }
}

public class GestureServiceTests
{
    private sealed class RecordingExecutor : IActionExecutor
    {
        public List<GestureAction> Executed { get; } = [];
        public bool Fail { get; set; }
        public void Execute(GestureAction action)
        {
            if (Fail) throw new InvalidOperationException("falla");
            Executed.Add(action);
        }
    }

    private static GestureConfig Config() => GestureConfig.Parse("""
        { "bindings": [
          { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" }, "help": "Deshacer" },
          { "gesture": "check", "action": { "type": "keys", "keys": "Ctrl+S" }, "pin": true },
          { "gesture": "check", "action": { "type": "keys", "keys": "F5" }, "app": "chrome" },
          { "gesture": "circle", "action": { "type": "keys", "keys": "Ctrl+T" }, "app": "chrome" }
        ] }
        """);

    [Fact]
    public void Execute_RunsBindingOrReportsUnassigned()
    {
        var executor = new RecordingExecutor();
        var service = new GestureService(executor);
        service.SetConfig(Config());
        string? unassigned = null;
        service.Unassigned += n => unassigned = n;

        Assert.Equal(GestureStatus.Executed, service.Execute("z"));
        Assert.Equal(GestureStatus.Unassigned, service.Execute("circle"));
        Assert.Equal("circle", unassigned);

        service.SetForegroundApp("chrome");
        Assert.Equal(GestureStatus.Executed, service.Execute("circle"));
        Assert.Equal(["Ctrl+Z", "Ctrl+T"], executor.Executed.Select(a => a.DefaultHelp));

        executor.Fail = true;
        Assert.Equal(GestureStatus.Error, service.Execute("z"));
    }

    [Fact]
    public void CurrentList_DependsOnForegroundApp()
    {
        var service = new GestureService(new RecordingExecutor());
        service.SetConfig(Config());
        var changes = 0;
        service.ListChanged += () => changes++;

        var general = service.CurrentList();
        Assert.Equal("", general.Context);
        Assert.Equal(["check", "z"], general.Entries.Select(e => e.Name));
        Assert.True(general.Entries[0].Pinned);

        service.SetForegroundApp("notepad"); // sin reglas propias: la lista no cambia
        Assert.Equal(0, changes);

        service.SetForegroundApp("chrome");
        Assert.Equal(1, changes);
        var chrome = service.CurrentList();
        Assert.Equal("chrome", chrome.Context);
        Assert.Contains(chrome.Entries, e => e is { Name: "check", Help: "F5" });
        Assert.Contains(chrome.Entries, e => e.Name == "circle");
    }
}
