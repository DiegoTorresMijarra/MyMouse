using System.Text.Json;
using MyMouse.HostCore.Gestures;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Edición y escritura de gestures.json: ida y vuelta, reglas por forma, widgets y guardado seguro.</summary>
public class GestureConfigEditTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("mymouse-config").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    private const string Rich = """
        {
          // comentario que se pierde al reescribir
          "bindings": [
            { "gesture": "circle", "action": { "type": "launch", "target": "C:\\Program Files\\App\\app.exe", "args": "--x \"y\"", "focusIfRunning": true }, "help": "Abrir app", "pin": true },
            { "gesture": "z", "action": { "type": "keys", "keys": "ctrl + z" }, "help": "Deshacer" },
            { "gesture": "check", "action": { "type": "keys", "keys": "Ctrl+K, Ctrl+C" } },
            { "gesture": "check", "action": { "type": "keys", "keys": "F5" }, "help": "Recargar", "app": "chrome.exe" },
            { "gesture": "v", "action": { "type": "media", "key": "playpause" } },
            { "gesture": "s", "action": { "type": "text", "text": "Hola\nmundo — ñandú \"así\"" } },
            { "gesture": "custom:firma", "action": { "type": "mymouse", "command": "lock" } },
            { "gesture": "gt", "action": { "type": "mymouse", "command": "pause" }, "pin": true }
          ],
          "widgets": [
            { "type": "media" },
            { "type": "volume" },
            { "type": "button", "label": "Copiar", "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } },
            { "type": "button", "label": "Chrome", "action": { "type": "launch", "target": "https://example.com/a?b=1&c=2" } }
          ]
        }
        """;

    private static void AssertSameConfig(GestureConfig a, GestureConfig b)
    {
        Assert.Equal(a.Bindings.Count, b.Bindings.Count);
        for (var i = 0; i < a.Bindings.Count; i++) Assert.Equal(a.Bindings[i], b.Bindings[i]);
        Assert.Equal(a.Widgets.Count, b.Widgets.Count);
        for (var i = 0; i < a.Widgets.Count; i++) Assert.Equal(a.Widgets[i], b.Widgets[i]);
        Assert.Equal(a.HasWidgetsSection, b.HasWidgetsSection);
    }

    [Fact]
    public void ParseWriteParse_GivesTheSame()
    {
        var first = GestureConfig.Parse(Rich);
        Assert.Empty(first.Warnings);
        var json = first.ToJson();
        var second = GestureConfig.Parse(json);
        Assert.Empty(second.Warnings);
        AssertSameConfig(first, second);
        // Y escribirlo otra vez no cambia nada.
        Assert.Equal(json, second.ToJson());
    }

    [Fact]
    public void Written_IsCleanJson_WithoutComments_AndWithReadableText()
    {
        var json = GestureConfig.Parse(Rich).ToJson();
        Assert.DoesNotContain("comentario", json);
        Assert.Contains("ñandú", json);
        Assert.Contains("\"keys\": \"Ctrl+Z\"", json); // formato canónico
        using var doc = JsonDocument.Parse(json); // JSON estricto, sin comentarios
        Assert.Equal(8, doc.RootElement.GetProperty("bindings").GetArrayLength());
    }

    [Fact]
    public void DefaultJson_RoundTrips()
    {
        var config = GestureConfig.Parse(GestureConfig.DefaultJson);
        AssertSameConfig(config, GestureConfig.Parse(config.ToJson()));
    }

    [Fact]
    public void V1File_WithoutWidgets_KeepsWithoutWidgetsSection()
    {
        var config = GestureConfig.Parse("""{ "bindings": [ { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" } } ] }""");
        Assert.False(GestureConfig.Parse(config.ToJson()).HasWidgetsSection);
        Assert.True(GestureConfig.Parse(config.WithWidgets([]).ToJson()).HasWidgetsSection);
    }

    [Fact]
    public void KeysAction_ComparesByContent()
    {
        Assert.Equal(new KeysAction(KeyChord.Parse("Ctrl+Z")), new KeysAction(KeyChord.Parse("ctrl+z")));
        Assert.NotEqual(new KeysAction(KeyChord.Parse("Ctrl+Z")), new KeysAction(KeyChord.Parse("Ctrl+Y")));
    }

    [Fact]
    public void WithRules_ReplacesInPlace_KeepingTheOrder()
    {
        var config = GestureConfig.Parse(Rich);
        var edited = config.WithRules("z", [
            new GestureBinding("z", new TextAction("hola"), "Saludar", true, null),
            new GestureBinding("z", new KeysAction(KeyChord.Parse("Ctrl+Y")), null, false, "code.exe"),
        ]);
        Assert.Equal(["circle", "z", "z", "check", "check", "v", "s", "custom:firma", "gt"], edited.Bindings.Select(b => b.Gesture));
        var (general, app) = edited.RulesOf("z");
        Assert.Equal(new TextAction("hola"), general!.Action);
        Assert.Equal("code.exe", Assert.Single(app).App);
        // La configuración original no cambia.
        Assert.Equal(8, config.Bindings.Count);
    }

    [Fact]
    public void WithRules_ForANewGesture_AppendsIt_AndEmptyRemovesIt()
    {
        var config = GestureConfig.Parse(Rich);
        var added = config.WithRules("custom:nueva", [new GestureBinding("x", new TextAction("t"), null, false, null)]);
        Assert.Equal("custom:nueva", added.Bindings[^1].Gesture); // se fuerza el nombre de la forma
        Assert.True(added.HasAction("custom:nueva"));

        var removed = added.WithoutGesture("check"); // quita la general y la de chrome
        Assert.False(removed.HasAction("check"));
        Assert.Equal(added.Bindings.Count - 2, removed.Bindings.Count);
        Assert.Equal(config.Widgets.Count, removed.Widgets.Count);
    }

    [Fact]
    public void RulesOf_SplitsGeneralAndAppRules()
    {
        var (general, app) = GestureConfig.Parse(Rich).RulesOf("check");
        Assert.NotNull(general);
        Assert.Equal("chrome.exe", Assert.Single(app).App);
        Assert.Equal((null, []), GestureConfig.Parse(Rich).RulesOf("nada"), new RulesComparer());
    }

    private sealed class RulesComparer : IEqualityComparer<(GestureBinding?, IReadOnlyList<GestureBinding>)>
    {
        public bool Equals((GestureBinding?, IReadOnlyList<GestureBinding>) a, (GestureBinding?, IReadOnlyList<GestureBinding>) b) =>
            a.Item1 is null && b.Item1 is null && a.Item2.Count == b.Item2.Count;
        public int GetHashCode((GestureBinding?, IReadOnlyList<GestureBinding>) o) => 0;
    }

    [Fact]
    public void WithWidgets_ReplacesTheList_AndSurvivesTheRoundTrip()
    {
        var config = GestureConfig.Parse(Rich);
        var widgets = new WidgetDefinition[]
        {
            new VolumeWidget(),
            new ButtonWidget("Pegar", "📄", new KeysAction(KeyChord.Parse("Ctrl+V"))),
            new MediaWidget(),
        };
        var back = GestureConfig.Parse(config.WithWidgets(widgets).ToJson());
        Assert.Equal(widgets.Length, back.Widgets.Count);
        for (var i = 0; i < widgets.Length; i++) Assert.Equal(widgets[i], back.Widgets[i]);
        Assert.Equal(8, back.Bindings.Count);
    }

    [Fact]
    public void WidgetsWithEmoji_AreReadBackIntact()
    {
        var button = new ButtonWidget("Captura", "✂️", new KeysAction(KeyChord.Parse("Win+Shift+S")));
        var back = GestureConfig.Parse(GestureConfig.Empty.WithWidgets([button]).ToJson());
        Assert.Equal(button, Assert.Single(back.Widgets));
    }

    // ---- Guardado ----

    private GestureConfigStorage Storage() => new(Path.Combine(_dir, "gestures.json"));

    [Fact]
    public void Save_CreatesTheFile_WithoutABackupWhenThereWasNothing()
    {
        var storage = Storage();
        storage.Save(GestureConfig.Parse(Rich));
        Assert.True(File.Exists(storage.Path));
        Assert.False(File.Exists(storage.BackupPath));
        AssertSameConfig(GestureConfig.Parse(Rich), storage.Load());
    }

    [Fact]
    public void Save_BacksUpTheOriginalOnlyTheFirstTime()
    {
        var storage = Storage();
        File.WriteAllText(storage.Path, Rich);
        var config = storage.Load();

        storage.Save(config.WithoutGesture("z"));
        Assert.Equal(Rich, File.ReadAllText(storage.BackupPath)); // el original, con sus comentarios

        storage.Save(config.WithoutGesture("v"));
        Assert.Equal(Rich, File.ReadAllText(storage.BackupPath)); // no se pisa la copia
        Assert.False(storage.Load().HasAction("v"));
        Assert.True(storage.Load().HasAction("z"));
        Assert.False(File.Exists(storage.Path + ".tmp"));
    }

    [Fact]
    public void Save_DoesNotOverwriteAFileWithSyntaxErrors()
    {
        var storage = Storage();
        const string broken = """{ "bindings": [ { "gesture": "z", """;
        File.WriteAllText(storage.Path, broken);

        Assert.Throws<FormatException>(() => storage.Load());
        Assert.Throws<FormatException>(() => storage.Save(GestureConfig.Empty));
        Assert.Equal(broken, File.ReadAllText(storage.Path));
        Assert.False(File.Exists(storage.BackupPath));
    }

    [Fact]
    public void Load_MissingFile_IsEmpty()
    {
        Assert.Empty(Storage().Load().Bindings);
    }

    // ---- Resultado de ejecutar una forma ----

    private sealed class Executor : IActionExecutor
    {
        public Exception? Throw { get; set; }
        public void Execute(GestureAction action)
        {
            if (Throw is not null) throw Throw;
        }
    }

    [Fact]
    public void GestureService_ReportsTheOutcomeOfEachShape()
    {
        var executor = new Executor();
        var service = new GestureService(executor);
        service.SetConfig(GestureConfig.Parse(Rich));
        var outcomes = new List<GestureOutcome>();
        service.Outcome += outcomes.Add;

        service.Execute("z");
        service.Execute("custom:nada");
        executor.Throw = new InvalidOperationException("no");
        service.Execute("gt");

        Assert.Equal([GestureStatus.Executed, GestureStatus.Unassigned, GestureStatus.Error], outcomes.Select(o => o.Status));
        Assert.Equal("Deshacer", outcomes[0].Binding!.HelpText);
        Assert.Null(outcomes[1].Binding);
        Assert.Equal("no", outcomes[2].Error!.Message);
    }

    [Theory]
    [InlineData("keys Ctrl+Z", "Ctrl+Z")]
    [InlineData("launch chrome.exe", "")]
    public void ActionText_Summary(string expected, string _)
    {
        GestureAction action = expected.StartsWith("keys")
            ? new KeysAction(KeyChord.Parse("Ctrl+Z"))
            : new LaunchAction("chrome.exe", null, false);
        Assert.Equal(expected, ActionText.Summary(action));
        Assert.Equal(expected.StartsWith("keys") ? ActionKind.Keys : ActionKind.Launch, ActionText.KindOf(action));
    }

    [Fact]
    public void ActionText_OtherKinds()
    {
        Assert.Equal("sin acción", ActionText.Summary(null));
        Assert.Equal(ActionKind.None, ActionText.KindOf(null));
        Assert.Equal("media Next", ActionText.Summary(new MediaAction("Next", KeyChord.Parse("Next")[0])));
        Assert.Equal("mymouse lock", ActionText.Summary(new MyMouseAction(MyMouseCommand.Lock)));
        Assert.Equal("text \"hola\"", ActionText.Summary(new TextAction("hola")));
        Assert.Equal(6, ActionText.MediaChoices.Count());
    }
}
