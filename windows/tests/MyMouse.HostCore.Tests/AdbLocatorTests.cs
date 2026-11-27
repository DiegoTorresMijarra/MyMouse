namespace MyMouse.HostCore.Tests;

/// <summary>Dónde busca el host a adb (docs/spec-v5-bluetooth-completo.md §5): config.json, junto al exe, PATH y el SDK, en ese orden.</summary>
public class AdbLocatorTests
{
    private static readonly char Sep = Path.PathSeparator;

    private static string? Find(
        HashSet<string> existing, string? configured = null, string? exeDir = "/app/MyMouse",
        string? path = "", string? localAppData = "/home/u/AppData", bool windows = true) =>
        AdbReverseKeeper.FindAdb(configured, exeDir, path, localAppData, existing.Contains, windows);

    [Fact]
    public void AdbNextToTheExecutableComesBeforeThePath()
    {
        var next = Path.Combine("/app/MyMouse", "adb.exe");
        var inPath = Path.Combine("/tools", "adb.exe");
        Assert.Equal(next, Find([next, inPath], path: "/tools"));
    }

    [Fact]
    public void FallsBackToThePathWhenNotNextToTheExecutable()
    {
        var inPath = Path.Combine("/tools", "adb.exe");
        Assert.Equal(inPath, Find([inPath], path: $"/other{Sep}/tools"));
    }

    [Fact]
    public void ConfiguredPathWinsOverEverything()
    {
        var next = Path.Combine("/app/MyMouse", "adb.exe");
        Assert.Equal("/mi/adb.exe", Find(["/mi/adb.exe", next], configured: "/mi/adb.exe"));
    }

    [Fact]
    public void ConfiguredPathThatDoesNotExistIsSkipped()
    {
        var next = Path.Combine("/app/MyMouse", "adb.exe");
        Assert.Equal(next, Find([next], configured: "/mi/adb.exe"));
    }

    [Fact]
    public void FallsBackToTheAndroidSdk()
    {
        var sdk = Path.Combine("/home/u/AppData", "Android", "Sdk", "platform-tools", "adb.exe");
        Assert.Equal(sdk, Find([sdk]));
    }

    [Fact]
    public void ReturnsNullWhenThereIsNoAdb()
    {
        Assert.Null(Find([]));
        Assert.Null(Find([], configured: "   ", exeDir: null, path: null, localAppData: null));
    }

    [Fact]
    public void UsesTheExecutableNameOfThePlatform()
    {
        var unix = Path.Combine("/app/MyMouse", "adb");
        Assert.Equal(unix, Find([unix], windows: false));
        Assert.Null(Find([Path.Combine("/app/MyMouse", "adb.exe")], windows: false));
    }

    [Fact]
    public void QuotedPathEntriesAreAccepted()
    {
        var inPath = Path.Combine("/tools x", "adb.exe");
        Assert.Equal(inPath, Find([inPath], path: "\"/tools x\""));
    }
}
