using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Toolbox;

namespace MyMouse.HostCore.Tests;

public class ActionChipTests
{
    private static GestureAction Launch(string target, string? args = null) => new LaunchAction(target, args, true);

    [Theory]
    [InlineData(@"C:\Program Files\Microsoft Office\root\Office16\OUTLOOK.EXE", "OUTLOOK.EXE")]
    [InlineData("C:/Users/yo/Desktop/notas.txt", "notas.txt")]
    [InlineData("chrome.exe", "chrome.exe")]
    [InlineData(@"C:\Carpeta\", "Carpeta")]
    [InlineData("https://www.github.com/anthropics/x?y=1", "github.com")]
    [InlineData("https://user@example.org:8080/a", "example.org:8080")]
    [InlineData("ms-settings:", "ms-settings:")]
    public void Launch_ShowsOnlyTheFileName(string target, string expected)
    {
        var chip = ActionChip.Format(Launch(target));
        Assert.Equal(expected, chip.Text);
        Assert.Equal(target, chip.Tooltip);
    }

    [Fact]
    public void Launch_TooltipIncludesArguments()
    {
        var chip = ActionChip.Format(Launch(@"C:\a\b\app.exe", "--x 1"));
        Assert.Equal("app.exe", chip.Text);
        Assert.Equal(@"C:\a\b\app.exe --x 1", chip.Tooltip);
    }

    [Fact]
    public void LongNames_AreShortened()
    {
        var chip = ActionChip.Format(Launch(@"C:\x\" + new string('a', 60) + ".exe"));
        Assert.Equal(ActionChip.MaxLength, chip.Text.Length);
        Assert.EndsWith("…", chip.Text);
    }

    [Fact]
    public void OtherKinds()
    {
        Assert.Equal("Ctrl+Z", ActionChip.Format(new KeysAction(KeyChord.Parse("Ctrl+Z"))).Text);
        Assert.Equal("Ctrl+K, Ctrl+C", ActionChip.Format(new KeysAction(KeyChord.Parse("Ctrl+K, Ctrl+C"))).Text);
        Assert.Equal("\"hola mundo\"", ActionChip.Format(new TextAction("hola\nmundo")).Text);
        Assert.Equal("PlayPause", ActionChip.Format(new MediaAction("PlayPause", KeyChord.Parse("PlayPause")[0])).Text);
        Assert.Equal("Pause", ActionChip.Format(new MyMouseAction(MyMouseCommand.Pause)).Text);
        Assert.Equal("Lock", ActionChip.Format(new MyMouseAction(MyMouseCommand.Lock)).Text);
        Assert.Equal("Sin acción", ActionChip.Format(null).Text);
        Assert.Null(ActionChip.Format(new KeysAction(KeyChord.Parse("Ctrl+Z"))).Tooltip);
    }
}

public class GestureSectionsTests
{
    private const string Json = """
        { "bindings": [
          { "gesture": "circle", "action": { "type": "keys", "keys": "Ctrl+Z" } },
          { "gesture": "check", "help": "Guardar", "pin": true, "action": { "type": "keys", "keys": "Ctrl+S" } },
          { "gesture": "custom:firma", "action": { "type": "launch", "target": "C:\\a\\outlook.exe" } },
          { "gesture": "zigzag", "app": "code.exe", "action": { "type": "keys", "keys": "F5" } }
        ] }
        """;

    [Fact]
    public void Groups_PinnedAssignedUnassigned()
    {
        var config = GestureConfig.Parse(Json);
        var s = GestureSections.Build(config, ["custom:firma", "custom:otra"]);
        Assert.Equal(["check"], s.Pinned.Select(r => r.Gesture));
        Assert.Equal(["circle", "custom:firma", "zigzag"], s.Assigned.Select(r => r.Gesture));
        Assert.Equal("custom:otra", s.Unassigned[0].Gesture); // las propias primero
        Assert.DoesNotContain(s.Unassigned, r => r.Gesture is "circle" or "check" or "zigzag");
        Assert.All(s.Unassigned, r => Assert.False(r.HasAction));
        Assert.Equal($"Sin acción · {s.Unassigned.Count}", s.UnassignedTitle);
        Assert.True(s.Assigned.Single(r => r.Gesture == "zigzag").OnlyInSomeApps);
        Assert.True(s.Assigned.Single(r => r.Gesture == "custom:firma").IsCustom);
        Assert.Equal("Guardar", s.Pinned[0].Title);
    }

    [Fact]
    public void EmptyConfig_AllCatalogShapesAreUnassigned()
    {
        var s = GestureSections.Build(GestureConfig.Empty, []);
        Assert.Empty(s.Pinned);
        Assert.Empty(s.Assigned);
        Assert.Equal(MyMouse.HostCore.Shapes.ShapeCatalog.All.Count, s.Unassigned.Count);
        Assert.False(s.IsEmpty);
    }

    [Fact]
    public void RowFor_WorksForShapesWithAndWithoutAction()
    {
        var config = GestureConfig.Parse(Json);
        Assert.True(GestureSections.RowFor(config, "circle").HasAction);
        var none = GestureSections.RowFor(config, "custom:nueva");
        Assert.False(none.HasAction);
        Assert.True(none.IsCustom);
        Assert.Equal("nueva", none.Title);
    }

    [Fact]
    public void Filter_MatchesTitleNameAndAction()
    {
        var s = GestureSections.Build(GestureConfig.Parse(Json), []);
        Assert.Equal(["check"], s.Filter("guardar").Pinned.Select(r => r.Gesture));
        Assert.Empty(s.Filter("guardar").Assigned);
        Assert.Equal(["circle"], s.Filter("ctrl+z").Assigned.Select(r => r.Gesture));
        Assert.Equal(["custom:firma"], s.Filter("outlook").Assigned.Select(r => r.Gesture));
        Assert.Same(s, s.Filter("  "));
    }
}

public class ToolboxColumnsTests
{
    private static double H(ToolboxCard c) => c switch
    {
        ToolboxCard.Options => 120, ToolboxCard.Music => 180, ToolboxCard.Gestures => 600, _ => 150,
    };

    [Fact]
    public void Default_OptionsAndMusicLeft_GesturesRight()
    {
        var (l, r) = ToolboxColumns.Split(ToolboxLayout.Default.Visible, H);
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Music], l);
        Assert.Equal([ToolboxCard.Gestures], r);
    }

    [Fact]
    public void Widgets_GoUnderTheShorterColumn()
    {
        var (l, r) = ToolboxColumns.Split([ToolboxCard.Options, ToolboxCard.Music, ToolboxCard.Gestures, ToolboxCard.Widgets], H);
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Music, ToolboxCard.Widgets], l);
        Assert.Equal([ToolboxCard.Gestures], r);
        var (l2, r2) = ToolboxColumns.Split([ToolboxCard.Options, ToolboxCard.Gestures, ToolboxCard.Widgets], c => c == ToolboxCard.Gestures ? 100 : 400);
        Assert.Equal([ToolboxCard.Options], l2);
        Assert.Equal([ToolboxCard.Gestures, ToolboxCard.Widgets], r2);
    }

    [Fact]
    public void WithoutGestures_TheRightColumnIsNotEmpty()
    {
        var (l, r) = ToolboxColumns.Split([ToolboxCard.Options, ToolboxCard.Music], H);
        Assert.Equal([ToolboxCard.Options], l);
        Assert.Equal([ToolboxCard.Music], r);
    }

    [Fact]
    public void WindowSize_960x720_OrEightyFivePercent()
    {
        Assert.Equal((960.0, 720.0), ToolboxColumns.WindowSize(1920, 1040));
        var (w, h) = ToolboxColumns.WindowSize(1000, 600);
        Assert.Equal(850, w, 3);
        Assert.Equal(510, h, 3);
    }
}

public class ToolboxStyleSettingsTests
{
    [Fact]
    public void GlassAndMaximized_DefaultAndRoundTrip()
    {
        Assert.True(ToolboxLayout.Default.GlassEffect);
        Assert.False(ToolboxLayout.Default.Maximized);
        var l = ToolboxLayout.Default.WithGlass(false).WithMaximized(true).SetVisible(ToolboxCard.Music, false);
        var back = ToolboxLayout.Parse(l.ToJson());
        Assert.False(back.GlassEffect);
        Assert.True(back.Maximized);
        Assert.False(back.IsVisible(ToolboxCard.Music));
        Assert.False(l.Move(ToolboxCard.Gestures, -1).GlassEffect);
        Assert.True(l.WithLiveGesture(false).Maximized);
    }

    [Fact]
    public void OldFiles_KeepTheDefaults()
    {
        var l = ToolboxLayout.Parse("""{ "cards": [], "liveGesture": false }""");
        Assert.True(l.GlassEffect);
        Assert.False(l.Maximized);
        Assert.False(l.ShowLiveGesture);
    }
}
