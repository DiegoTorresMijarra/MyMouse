using MyMouse.HostCore.Theming;
using MyMouse.HostCore.Toolbox;
using MyMouse.HostCore.Universe;

namespace MyMouse.HostCore.Tests;

public class ThemeCatalogTests
{
    [Fact]
    public void HasTheSevenThemesAndUniverseIsTheDefault()
    {
        Assert.Equal(["universe", "midnight", "graphite", "ocean", "aurora", "oled", "nebula"], ThemeCatalog.All.Select(t => t.Id));
        Assert.Equal("universe", ThemeCatalog.DefaultId);
        Assert.Equal("universe", ThemeCatalog.File.DefaultTheme);
        Assert.Equal("universe", new ThemeChoice().Theme.Id);
        Assert.Equal("universe", ThemeCatalog.Resolve(null).Id);
        Assert.Equal(["steel", "lavender", "turquoise", "green", "amber", "pink", "white"], ThemeCatalog.Accents.Select(a => a.Id));
    }

    /// <summary>Los mismos valores que comprueba <c>ThemesTest</c> en Kotlin: Universo es sobrio y Nebulosa es la paleta de la v4.7.</summary>
    [Fact]
    public void UniverseIsSoberAndNebulaIsTheOldPalette()
    {
        var universe = ThemeCatalog.Resolve(null);
        Assert.Equal([0x05070D, 0x0A0F1C, 0x0E1424], universe.Base);
        Assert.Equal(2, universe.Nebulas.Count);
        Assert.DoesNotContain(universe.Nebulas, n => n.Color is 0x7C3AED or 0xDB2777);
        Assert.Equal(0x7AA2F7, universe.Accent);
        Assert.Equal([0x070616, 0x120D2E, 0x1B0F36], ThemeCatalog.Find("nebula")!.Base);
        Assert.Equal(0x7C8CFF, ThemeCatalog.Find("nebula")!.Accent);
        Assert.Equal(3, ThemeCatalog.Find("nebula")!.Nebulas.Count);
        Assert.Single(ThemeCatalog.Find("oled")!.Base);
    }

    /// <summary>Lo que el móvil lee de <c>shared/themes.json</c> es lo mismo que lleva incrustado el PC.</summary>
    [Fact]
    public void EmbeddedResourceMatchesTheSharedFile()
    {
        var onDisk = ThemeCatalog.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "themes.json")));
        Assert.Equal(onDisk.DefaultTheme, ThemeCatalog.File.DefaultTheme);
        Assert.Equal(onDisk.Accents, ThemeCatalog.File.Accents);
        Assert.Equal(onDisk.Themes.Count, ThemeCatalog.All.Count);
        foreach (var (a, b) in onDisk.Themes.Zip(ThemeCatalog.All))
        {
            Assert.Equal(a.Id, b.Id);
            Assert.Equal(a.Name, b.Name);
            Assert.Equal(a.Base, b.Base);
            Assert.Equal(a.Nebulas, b.Nebulas);
            Assert.Equal((a.Stars, a.StarDensity, a.TwinkleDepth, a.Accent), (b.Stars, b.StarDensity, b.TwinkleDepth, b.Accent));
        }
    }

    [Fact]
    public void AutoAccentIsTheThemeAccentAndAnOverrideWins()
    {
        foreach (var theme in ThemeCatalog.All)
        {
            Assert.Equal(theme.Accent, ThemeCatalog.AccentColor(theme, ThemeCatalog.AccentAuto));
            Assert.Equal(theme.Accent, ThemeCatalog.AccentColor(theme, "desconocido"));
            Assert.Equal(0xF5B94A, ThemeCatalog.AccentColor(theme, "amber"));
        }
        Assert.Equal(0x7AA2F7, new ThemeChoice().Accent);
        Assert.Equal(0xF472B6, new ThemeChoice("graphite", "pink").Accent);
        Assert.Equal(0x9AA4B2, new ThemeChoice("graphite").Accent);
    }

    [Fact]
    public void StarsOnlyShowInThemesThatHaveThemAndWhenChecked()
    {
        Assert.True(new ThemeChoice().ShowStars);
        Assert.False(new ThemeChoice(Stars: false).ShowStars);
        foreach (var id in new[] { "midnight", "graphite", "oled" }) Assert.False(new ThemeChoice(id).ShowStars, id);
        foreach (var id in new[] { "universe", "ocean", "aurora", "nebula" }) Assert.True(new ThemeChoice(id).ShowStars, id);
        Assert.True(new ThemeChoice().Twinkles);
        Assert.False(new ThemeChoice(Twinkle: false).Twinkles);
        Assert.False(new ThemeChoice(Stars: false).Twinkles);
    }

    [Fact]
    public void NormalizedFallsBackToUniverseAndAuto()
    {
        var n = new ThemeChoice("no-existe", "no-existe").Normalized();
        Assert.Equal("universe", n.ThemeId);
        Assert.Equal(ThemeCatalog.AccentAuto, n.AccentId);
        Assert.Equal(new ThemeChoice("ocean", "pink"), new ThemeChoice("ocean", "pink").Normalized());
    }

    [Fact]
    public void StarCountScalesWithTheThemeDensity()
    {
        Assert.Equal(90, ThemeCatalog.StarCount(ThemeCatalog.Find("nebula")!, 400, 600));
        Assert.Equal(72, ThemeCatalog.StarCount(ThemeCatalog.Find("universe")!, 400, 600));
        Assert.Equal(45, ThemeCatalog.StarCount(ThemeCatalog.Find("ocean")!, 400, 600));
        Assert.Equal(0, ThemeCatalog.StarCount(ThemeCatalog.Find("oled")!, 400, 600));
        Assert.Equal(StarField.MinCount, StarField.CountFor(72, 96, 0.5));
        Assert.Equal(StarField.MaxCount, StarField.CountFor(4000, 4000, 1));
        Assert.Equal(StarField.CountFor(400, 600), StarField.CountFor(400, 600, 1));
    }

    [Fact]
    public void TwinkleDepthBlendsBetweenStaticAndFullTwinkle()
    {
        var star = StarField.Generate(40).First(s => s.Twinkle);
        var full = StarField.OpacityAt(star, 1.3);
        Assert.Equal(full, StarField.OpacityAt(star, 1.3, 1f), 1e-6);
        Assert.Equal(star.Opacity, StarField.OpacityAt(star, 1.3, 0f), 1e-6);
        Assert.Equal(star.Opacity + (full - star.Opacity) * 0.6f, StarField.OpacityAt(star, 1.3, 0.6f), 1e-6);
        var still = StarField.Generate(40).First(s => !s.Twinkle);
        Assert.Equal(still.Opacity, StarField.OpacityAt(still, 1.3, 0.6f), 1e-6);
    }

    [Fact]
    public void DerivedColors()
    {
        Assert.Equal(0x000000, ThemeCatalog.Mix(0, 0xFFFFFF, 0f));
        Assert.Equal(0xFFFFFF, ThemeCatalog.Mix(0, 0xFFFFFF, 1f));
        Assert.Equal(0x7F7F7F, ThemeCatalog.Mix(0, 0xFFFFFF, 0.5f));
        foreach (var a in ThemeCatalog.Accents)
            Assert.True(Luma(ThemeCatalog.AccentEnd(a.Color)) < Luma(ThemeCatalog.AccentStart(a.Color)) && Luma(ThemeCatalog.AccentStart(a.Color)) < 0.6, a.Id);
        Assert.Equal("#0A0F1C", ThemeCatalog.Hex(0x0A0F1C));
        Assert.Equal(0x7AA2F7, ThemeCatalog.ParseHex("#7AA2F7"));
        Assert.Null(ThemeCatalog.ParseHex("7AA2F7"));
        Assert.Null(ThemeCatalog.ParseHex("#7AA2F"));
        Assert.Null(ThemeCatalog.ParseHex("#GGGGGG"));
    }

    private static double Luma(int rgb) => 0.2126 * (rgb >> 16 & 0xFF) / 255 + 0.7152 * (rgb >> 8 & 0xFF) / 255 + 0.0722 * (rgb & 0xFF) / 255;

    private const string Valid = """
        {"version":1,"defaultTheme":"a","accents":[{"id":"x","name":"X","color":"#112233"}],
         "themes":[{"id":"a","name":"A","base":["#000000","#101010"],"nebulas":[{"color":"#222222","opacity":0.5,"x":0,"y":1,"r":0.7}],
         "stars":true,"starDensity":1,"twinkleDepth":1,"accent":"#445566"}]}
        """;

    private static string Sample(string find = "", string replace = "") => find.Length == 0 ? Valid : Valid.Replace(find, replace);

    [Fact]
    public void ParsesAValidFile()
    {
        var f = ThemeCatalog.Parse(Sample());
        Assert.Equal("a", f.DefaultTheme);
        Assert.Equal([new AccentOption("x", "X", 0x112233)], f.Accents);
        var t = f.Themes.Single();
        Assert.Equal([0x000000, 0x101010], t.Base);
        Assert.Equal(new Nebula(0x222222, 0.5f, 0f, 1f, 0.7f), t.Nebulas.Single());
        Assert.Equal(0x445566, t.Accent);
    }

    [Theory]
    [InlineData("no es json", "")]
    [InlineData("[]", "")]
    [InlineData("\"defaultTheme\":\"a\"", "\"defaultTheme\":\"zz\"")]
    [InlineData("#445566", "445566")]
    [InlineData("\"opacity\":0.5", "\"opacity\":1.5")]
    [InlineData("\"r\":0.7", "\"r\":0")]
    [InlineData("[\"#000000\",\"#101010\"]", "[]")]
    [InlineData("\"id\":\"x\"", "\"id\":\"auto\"")]
    public void RejectsInvalidFiles(string find, string replace)
    {
        var text = find == "no es json" ? find : find == "[]" ? "[]" : Sample(find, replace);
        Assert.Throws<FormatException>(() => ThemeCatalog.Parse(text));
    }
}

public class ToolboxThemeTests
{
    [Fact]
    public void ThemeDefaultsToUniverseAndMigratesFromOldFiles()
    {
        Assert.Equal(new ThemeChoice(), ToolboxLayout.Default.Theme);
        // Un toolbox.json de la v4.7 no tiene "theme": pasa a Universo (la paleta sobria), sin tocar lo demás.
        var old = ToolboxLayout.Parse("""{"cards":[{"id":"options","visible":true}],"liveGesture":false,"glass":true,"maximized":true,"alwaysShowIcon":true}""");
        Assert.Equal("universe", old.Theme.ThemeId);
        Assert.Equal(ThemeCatalog.AccentAuto, old.Theme.AccentId);
        Assert.True(old.Theme.Stars);
        Assert.False(old.ShowLiveGesture);
        Assert.True(old.Maximized);
    }

    [Fact]
    public void ThemeSurvivesSaveAndLoad()
    {
        var layout = ToolboxLayout.Default.WithTheme(new ThemeChoice("aurora", "amber", Stars: false, Twinkle: false), 0);
        var again = ToolboxLayout.Parse(layout.ToJson());
        Assert.Equal(new ThemeChoice("aurora", "amber", false, false), again.Theme);
        // Los demás cambios no pierden el tema.
        Assert.Equal("aurora", again.WithGlass(false).WithMaximized(true).WithLiveGesture(false).WithAlwaysShowIcon(false).Move(ToolboxCard.Music, 1).SetVisible(ToolboxCard.Widgets, true).Theme.ThemeId);
    }

    [Fact]
    public void UnknownThemeOrAccentFallBack()
    {
        var layout = ToolboxLayout.Parse("""{"theme":{"id":"fantasma","accent":"fantasma","stars":false}}""");
        Assert.Equal("universe", layout.Theme.ThemeId);
        Assert.Equal(ThemeCatalog.AccentAuto, layout.Theme.AccentId);
        Assert.False(layout.Theme.Stars);
        Assert.True(layout.Theme.Twinkle);
        Assert.Equal(ToolboxLayout.Default.Theme, ToolboxLayout.Parse("{ roto").Theme);
        Assert.Equal(ToolboxLayout.Default.Theme, ToolboxLayout.Parse("""{"theme":5}""").Theme);
    }
}
