using Microsoft.Extensions.Time.Testing;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

/// <summary>Controladores falsos de volumen y música, y ejecutor que anota las acciones.</summary>
internal sealed class FakeVolume : IVolumeController
{
    public VolumeInfo Info { get; set; } = new(40, false);
    public List<string> Calls { get; } = [];
    public event Action? Changed;
    public VolumeInfo Get() => Info;
    public void SetLevel(int level) { Calls.Add($"level {level}"); Info = Info with { Level = level }; }
    public void SetMuted(bool muted) { Calls.Add($"mute {muted}"); Info = Info with { Muted = muted }; }
    public void RaiseChanged() => Changed?.Invoke();
}

internal sealed class FakeMedia : IMediaController
{
    public MediaInfo Info { get; set; } = MediaInfo.Nothing;
    public List<MediaCommand> Sent { get; } = [];
    public event Action? Changed;
    public MediaInfo Current() => Info;
    public void Send(MediaCommand command) => Sent.Add(command);
    public void RaiseChanged() => Changed?.Invoke();
}

internal sealed class FakeExecutor : IActionExecutor
{
    public List<GestureAction> Executed { get; } = [];
    public bool Fail { get; set; }

    public void Execute(GestureAction action)
    {
        if (Fail) throw new InvalidOperationException("falla");
        Executed.Add(action);
    }
}

public class WidgetConfigTests
{
    [Fact]
    public void DefaultJson_HasTheSpecWidgets()
    {
        var c = GestureConfig.Parse(GestureConfig.DefaultJson);

        Assert.Empty(c.Warnings);
        Assert.True(c.HasWidgetsSection);
        Assert.Equal(
            [WidgetKind.Media, WidgetKind.Volume, WidgetKind.Button, WidgetKind.Button, WidgetKind.Button, WidgetKind.Button, WidgetKind.Button, WidgetKind.Button],
            c.Widgets.Select(w => w.Kind));
        Assert.Equal(new ButtonWidget("Chrome", "🌐", new LaunchAction("chrome.exe", null, true)), c.Widgets[2]);
        Assert.Equal(new ButtonWidget("Bloquear", "🔒", new MyMouseAction(MyMouseCommand.Lock)), c.Widgets[6]);
        Assert.Equal(new ButtonWidget("Teclado", "⌨️", new MyMouseAction(MyMouseCommand.Keyboard)), c.Widgets[7]);
        Assert.Equal("✂️", ((ButtonWidget)c.Widgets[5]).Icon);
    }

    [Fact]
    public void WithoutWidgetsSection_ThereIsNoPanel()
    {
        var v1 = GestureConfig.Parse("""{ "bindings": [] }""");
        Assert.Empty(v1.Widgets);
        Assert.False(v1.HasWidgetsSection);
        Assert.Empty(v1.Warnings);

        var empty = GestureConfig.Parse("""{ "bindings": [], "widgets": [] }""");
        Assert.Empty(empty.Widgets);
        Assert.True(empty.HasWidgetsSection);
    }

    [Fact]
    public void BadWidgets_AreIgnoredWithWarnings()
    {
        var c = GestureConfig.Parse("""
            { "bindings": [], "widgets": [
              { "type": "media" },
              { "type": "media" },
              { "type": "volume" },
              { "type": "volume" },
              { "type": "slider" },
              { "type": "button", "icon": "A", "action": { "type": "keys", "keys": "Ctrl+C" } },
              { "type": "button", "label": "Sin acción" },
              { "type": "button", "label": "Mala", "action": { "type": "keys", "keys": "Ctrl+Patata" } },
              { "type": "button", "label": "Icono largo", "icon": "12345678 9", "action": { "type": "keys", "keys": "Ctrl+C" } },
              { "type": "button", "label": "Ok", "action": { "type": "keys", "keys": "Ctrl+C" } },
              "texto"
            ] }
            """);

        Assert.Equal(["media", "volume", "Ok"], c.Widgets.Select(w => w is ButtonWidget b ? b.Label : w.Kind.ToString().ToLowerInvariant()));
        Assert.Equal(8, c.Warnings.Count);
        Assert.Contains("Widget 2:", c.Warnings[0]);
        Assert.Contains("media", c.Warnings[0]);
        Assert.Contains("slider", c.Warnings[2]);
        Assert.Contains("Patata", c.Warnings[5]);
        Assert.Contains("icono", c.Warnings[6]);
    }

    [Fact]
    public void LabelAndIconLimits()
    {
        var ok = new string('x', 40);
        var c = GestureConfig.Parse($$"""
            { "bindings": [], "widgets": [
              { "type": "button", "label": "{{ok}}", "icon": "🌐🌐", "action": { "type": "text", "text": "a" } },
              { "type": "button", "label": "{{ok}}x", "action": { "type": "text", "text": "a" } },
              { "type": "button", "label": "a", "icon": "🌐🌐🌐", "action": { "type": "text", "text": "a" } }
            ] }
            """);
        Assert.Single(c.Widgets);
        Assert.Equal(2, c.Warnings.Count);
    }

    [Fact]
    public void MoreThan32Widgets_AreIgnoredWithOneWarning()
    {
        var widgets = string.Join(",", Enumerable.Range(0, 40).Select(i =>
            $$"""{ "type": "button", "label": "B{{i}}", "action": { "type": "text", "text": "a" } }"""));
        var c = GestureConfig.Parse($$"""{ "bindings": [], "widgets": [{{widgets}}] }""");

        Assert.Equal(32, c.Widgets.Count);
        Assert.Single(c.Warnings);
    }

    [Fact]
    public void WidgetsThatIsNotAList_WarnsAndKeepsBindings()
    {
        var c = GestureConfig.Parse("""{ "bindings": [ { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" } } ], "widgets": 3 }""");
        Assert.Single(c.Bindings);
        Assert.Empty(c.Widgets);
        Assert.Single(c.Warnings);
    }
}

public class WidgetServiceTests
{
    private readonly FakeExecutor _executor = new();
    private readonly FakeVolume _volume = new();
    private readonly FakeMedia _media = new();
    private readonly FakeTimeProvider _time = new();

    private WidgetService Service(string widgetsJson = DefaultWidgets)
    {
        var service = new WidgetService(_executor, _volume, _media, _time);
        service.SetWidgets(GestureConfig.Parse($$"""{ "bindings": [], "widgets": {{widgetsJson}} }""").Widgets);
        return service;
    }

    private const string DefaultWidgets = """
        [
          { "type": "media" },
          { "type": "volume" },
          { "type": "button", "label": "Copiar", "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } }
        ]
        """;

    [Fact]
    public void Layout_NumbersWidgetsByPosition()
    {
        var service = Service();

        Assert.Equal(new WidgetLayoutMessage([
            new WidgetLayoutEntry(0, WidgetKind.Media, "", ""),
            new WidgetLayoutEntry(1, WidgetKind.Volume, "", ""),
            new WidgetLayoutEntry(2, WidgetKind.Button, "Copiar", "📋"),
        ]), service.CurrentLayout());
        Assert.Equal((byte)0, service.MediaWidgetId);
        Assert.Equal((byte)1, service.VolumeWidgetId);
    }

    [Fact]
    public void SetWidgets_RaisesLayoutChanged()
    {
        var service = new WidgetService(_executor, _volume, _media, _time);
        var raised = 0;
        service.LayoutChanged += () => raised++;
        service.SetWidgets([new VolumeWidget()]);
        Assert.Equal(1, raised);
    }

    [Fact]
    public void Press_ExecutesButtonsOnly()
    {
        var service = Service();

        Assert.Equal(GestureStatus.Executed, service.Press(2));
        Assert.Equal(["Ctrl+C"], _executor.Executed.Select(a => a.DefaultHelp));
        Assert.Equal(GestureStatus.Error, service.Press(0)); // media
        Assert.Equal(GestureStatus.Error, service.Press(1)); // volume
        Assert.Equal(GestureStatus.Error, service.Press(9)); // no existe
        Assert.Single(_executor.Executed);
    }

    [Fact]
    public void Press_WhenTheActionFails_ReportsErrorAndRaisesFailed()
    {
        var service = Service();
        string? failed = null;
        service.Failed += (what, _) => failed = what;
        _executor.Fail = true;

        Assert.Equal(GestureStatus.Error, service.Press(2));
        Assert.Equal("Copiar", failed);
    }

    [Fact]
    public void Commands_GoToTheControllers_AndVolumeIsClamped()
    {
        var service = Service();

        service.SetVolume(55);
        service.SetVolume(250);
        service.SetVolume(-3);
        service.SetMuted(true);
        service.SendMedia(MediaCommand.Next);

        Assert.Equal(["level 55", "level 100", "level 0", "mute True"], _volume.Calls);
        Assert.Equal([MediaCommand.Next], _media.Sent);
    }

    [Fact]
    public void CurrentState_IsNullWithoutTheWidget()
    {
        var service = Service("""[ { "type": "volume" } ]""");

        Assert.Equal(new VolumeStateMessage(40, false), service.CurrentVolume());
        Assert.Null(service.CurrentMedia());
        Assert.Null(service.MediaWidgetId);
    }

    [Fact]
    public void MediaState_ReflectsTheController()
    {
        var service = Service();
        _media.Info = new MediaInfo(true, "Título", "Artista");
        Assert.Equal(new MediaStateMessage(true, "Título", "Artista"), service.CurrentMedia());
    }

    [Fact]
    public void VolumeChanges_AreLimitedTo100ms_LastOneWins()
    {
        var service = Service();
        var sent = new List<VolumeStateMessage>();
        service.VolumeChanged += sent.Add;

        _volume.Info = new VolumeInfo(41, false);
        _volume.RaiseChanged();
        Assert.Equal([new VolumeStateMessage(41, false)], sent); // el primero pasa al instante

        _time.Advance(TimeSpan.FromMilliseconds(30));
        _volume.Info = new VolumeInfo(42, false);
        _volume.RaiseChanged();
        _time.Advance(TimeSpan.FromMilliseconds(30));
        _volume.Info = new VolumeInfo(43, false);
        _volume.RaiseChanged();
        Assert.Single(sent); // dentro del intervalo no se envía nada

        _time.Advance(TimeSpan.FromMilliseconds(40));
        Assert.Equal([new VolumeStateMessage(41, false), new VolumeStateMessage(43, false)], sent);

        _time.Advance(TimeSpan.FromMilliseconds(200));
        _volume.Info = new VolumeInfo(10, true);
        _volume.RaiseChanged();
        Assert.Equal(new VolumeStateMessage(10, true), sent[^1]);
        Assert.Equal(3, sent.Count);
    }

    [Fact]
    public void UnchangedState_IsNotSentAgain_AndWithoutWidgetNothingIsSent()
    {
        var service = Service();
        var volumes = 0;
        var medias = 0;
        service.VolumeChanged += _ => volumes++;
        service.MediaChanged += _ => medias++;
        service.CurrentVolume(); // el móvil ya conoce este estado

        _volume.RaiseChanged();
        Assert.Equal(0, volumes);

        service.SetWidgets([new VolumeWidget()]);
        _media.Info = new MediaInfo(true, "X", "Y");
        _media.RaiseChanged();
        Assert.Equal(0, medias);
    }

    [Fact]
    public void MediaChanges_AreSent()
    {
        var service = Service();
        var sent = new List<MediaStateMessage>();
        service.MediaChanged += sent.Add;

        _media.Info = new MediaInfo(true, "Canción", "Grupo");
        _media.RaiseChanged();

        Assert.Equal([new MediaStateMessage(true, "Canción", "Grupo")], sent);
    }
}
