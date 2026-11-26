using MyMouse.HostCore.Toolbox;

namespace MyMouse.HostCore.Tests;

public class ToolboxLayoutTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("mymouse-toolbox").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    [Fact]
    public void Default_OptionsMusicGestures_WidgetsHidden()
    {
        var l = ToolboxLayout.Default;
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Music, ToolboxCard.Gestures], l.Visible);
        Assert.False(l.IsVisible(ToolboxCard.Widgets));
        Assert.Equal(4, l.Entries.Count);
        Assert.True(l.ShowLiveGesture);
    }

    [Fact]
    public void SetVisible_HidesAndShows_ButNeverTheOptions()
    {
        var l = ToolboxLayout.Default.SetVisible(ToolboxCard.Music, false).SetVisible(ToolboxCard.Widgets, true);
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Gestures, ToolboxCard.Widgets], l.Visible);
        Assert.Same(l, l.SetVisible(ToolboxCard.Options, false));
        Assert.True(l.IsVisible(ToolboxCard.Options));
    }

    [Fact]
    public void Move_ReordersWithinBounds_AndNothingGoesAboveTheOptions()
    {
        var l = ToolboxLayout.Default.Move(ToolboxCard.Gestures, -1);
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Gestures, ToolboxCard.Music], l.Visible);
        // Ya es la primera después de Opciones: no pasa.
        Assert.Equal(l.Visible, l.Move(ToolboxCard.Gestures, -1).Visible);
        // Ni las Opciones se mueven.
        Assert.Equal(l.Visible, l.Move(ToolboxCard.Options, 2).Visible);
        // Bajar la última no hace nada.
        Assert.Equal(l.Entries.Select(e => e.Card), l.Move(ToolboxCard.Widgets, 1).Entries.Select(e => e.Card));
        // Movimientos grandes se recortan.
        Assert.Equal(ToolboxCard.Gestures, l.Move(ToolboxCard.Gestures, 99).Entries[^1].Card);
    }

    [Fact]
    public void SaveAndLoad_RoundTrip()
    {
        var path = Path.Combine(_dir, "sub", "toolbox.json");
        var l = ToolboxLayout.Default.Move(ToolboxCard.Gestures, -1).SetVisible(ToolboxCard.Widgets, true)
            .SetVisible(ToolboxCard.Music, false).WithLiveGesture(false);
        l.Save(path);
        var back = ToolboxLayout.Load(path);
        Assert.Equal(l.Entries, back.Entries);
        Assert.False(back.ShowLiveGesture);
    }

    [Fact]
    public void Load_MissingOrBrokenFile_GivesTheDefault()
    {
        Assert.Equal(ToolboxLayout.Default.Entries, ToolboxLayout.Load(Path.Combine(_dir, "nada.json")).Entries);
        var broken = Path.Combine(_dir, "roto.json");
        File.WriteAllText(broken, "{ no");
        Assert.Equal(ToolboxLayout.Default.Entries, ToolboxLayout.Load(broken).Entries);
    }

    [Fact]
    public void Parse_FixesUpAHandEditedFile()
    {
        // Opciones oculta y en último lugar, una tarjeta repetida, otra desconocida y faltan Widgets y Gestos.
        var l = ToolboxLayout.Parse("""
            { "cards": [ {"id":"music","visible":true}, {"id":"bogus"}, {"id":"music","visible":false},
                         {"id":"options","visible":false} ] }
            """);
        Assert.Equal(ToolboxCard.Options, l.Entries[0].Card);
        Assert.True(l.IsVisible(ToolboxCard.Options));
        Assert.Equal([ToolboxCard.Options, ToolboxCard.Music, ToolboxCard.Gestures], l.Visible);
        Assert.False(l.IsVisible(ToolboxCard.Widgets));
        Assert.Equal(4, l.Entries.Count);
    }
}
