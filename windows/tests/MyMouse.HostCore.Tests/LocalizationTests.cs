using System.Globalization;
using System.Runtime.CompilerServices;
using System.Xml.Linq;
using MyMouse.HostCore;
using MyMouse.HostCore.Shapes;

namespace MyMouse.HostCore.Tests;

/// <summary>El resto de tests comprueban textos en español: se fija el idioma antes de que corra ninguno, sea cual sea el de la máquina.</summary>
internal static class TestCulture
{
    [ModuleInitializer]
    public static void Init() => CoreLoc.Culture = CultureInfo.GetCultureInfo("es");
}

public class LocalizationTests
{
    private static string Src(params string[] parts) =>
        Path.GetFullPath(Path.Combine([AppContext.BaseDirectory, "..", "..", "..", "..", "..", "src", .. parts]));

    private static Dictionary<string, string> Read(string path) =>
        XDocument.Load(path).Root!.Elements("data").ToDictionary(e => e.Attribute("name")!.Value, e => e.Element("value")!.Value);

    [Theory]
    [InlineData("MyMouse.Host", "Strings")]
    [InlineData("MyMouse.HostCore", "CoreStrings")]
    public void SpanishAndEnglishHaveTheSameKeys(string project, string file)
    {
        var en = Read(Src(project, file + ".resx"));
        var es = Read(Src(project, file + ".es.resx"));
        Assert.True(en.Count > 20);
        Assert.Empty(en.Keys.Except(es.Keys));
        Assert.Empty(es.Keys.Except(en.Keys));
    }

    [Theory]
    [InlineData("MyMouse.Host", "Strings")]
    [InlineData("MyMouse.HostCore", "CoreStrings")]
    public void PlaceholdersMatchInBothLanguages(string project, string file)
    {
        var en = Read(Src(project, file + ".resx"));
        var es = Read(Src(project, file + ".es.resx"));
        static string[] Slots(string text) =>
            [.. System.Text.RegularExpressions.Regex.Matches(text, @"\{\d+\}").Select(m => m.Value).Order()];
        foreach (var (key, value) in en) Assert.Equal(Slots(value), Slots(es[key]));
    }

    [Fact]
    public void CoreTextsFollowTheCulture()
    {
        var before = CoreLoc.Culture;
        try
        {
            CoreLoc.Culture = CultureInfo.GetCultureInfo("en");
            Assert.Equal("Circle", ShapeCatalog.Label("circle"));
            CoreLoc.Culture = CultureInfo.GetCultureInfo("es");
            Assert.Equal("Círculo", ShapeCatalog.Label("circle"));
        }
        finally
        {
            CoreLoc.Culture = before;
        }
    }
}
