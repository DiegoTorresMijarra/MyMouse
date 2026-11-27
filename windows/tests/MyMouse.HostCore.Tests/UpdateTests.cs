using System.Net;
using System.Security.Cryptography;
using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Updates;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class AppVersionTests
{
    [Theory]
    [InlineData("5.0", "5.0.1")]
    [InlineData("5.0.1", "5.1")]
    [InlineData("5.1", "10.0")]
    [InlineData("4.8.3", "5.0")]
    [InlineData("9.9", "10.0")]
    [InlineData("5.0.9", "5.0.10")]
    [InlineData("0.0-dev.3", "5.0")]
    public void Orders(string lower, string higher)
    {
        var a = AppVersion.Parse(lower)!.Value;
        var b = AppVersion.Parse(higher)!.Value;
        Assert.True(a < b);
        Assert.True(b > a);
        Assert.False(a >= b);
    }

    [Theory]
    [InlineData("5.0", "5.0.0")]
    [InlineData("v5.0", "5.0")]
    [InlineData("5.0", "5.0.0.0")]
    [InlineData("5.1+abc123", "5.1")]
    public void SameNumbers(string a, string b) =>
        Assert.True(AppVersion.Parse(a)!.Value.SameNumbers(AppVersion.Parse(b)!.Value));

    [Theory]
    [InlineData("0.0-dev.42", true)]
    [InlineData("0.0-dev", true)]
    [InlineData("0.0", true)]
    [InlineData("5.1-dev.7", true)]
    [InlineData("5.0", false)]
    [InlineData("5.0.1", false)]
    [InlineData("v5.1", false)]
    public void DetectsDevVersions(string text, bool dev) => Assert.Equal(dev, AppVersion.Parse(text)!.Value.IsDev);

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("abc")]
    [InlineData("5")]
    [InlineData("5.x")]
    [InlineData("99999999999.1")]
    public void RejectsGarbage(string? text) => Assert.Null(AppVersion.Parse(text));

    [Theory]
    [InlineData("v5.0", "5.0")]
    [InlineData("v5.0.1", "5.0.1")]
    [InlineData("v10.0", "10.0")]
    public void ParsesTags(string tag, string display) => Assert.Equal(display, AppVersion.ParseTag(tag)!.Value.Display);

    [Theory]
    [InlineData("5.0")]
    [InlineData("nightly")]
    [InlineData("v5.1-rc1")]
    [InlineData("v5")]
    [InlineData("")]
    [InlineData(null)]
    public void IgnoresOtherTags(string? tag) => Assert.Null(AppVersion.ParseTag(tag));
}

public class ReleasesTests
{
    private static string Fixture => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "github-releases.json"));

    [Fact]
    public void ParsesTheRealisticFixtureSkippingDraftsAndOddTags()
    {
        var list = Releases.Parse(Fixture);
        Assert.Equal(["v5.0", "v5.0.1", "v4.8.3", "v4.8"], list.Select(r => r.Tag));
        Assert.All(list, r => Assert.True(r.Prerelease));
        Assert.Equal("v4.8", list[3].Title); // name vacío: se usa la etiqueta
        Assert.Contains("Bluetooth completo", list[0].Notes);
        Assert.Equal("https://github.com/DiegoTorresMijarra/MyMouse/releases/tag/v5.0", list[0].PageUrl);
    }

    [Fact]
    public void PicksTheHighestVersionNotTheNewestInTheList()
    {
        var latest = Releases.Highest(Releases.Parse(Fixture))!;
        Assert.Equal("v5.0.1", latest.Tag);
    }

    [Fact]
    public void HighestOfNothingIsNull() => Assert.Null(Releases.Highest([]));

    [Fact]
    public void FindsByVersionNumbers()
    {
        var list = Releases.Parse(Fixture);
        Assert.Equal("v5.0", Releases.Find(list, AppVersion.Parse("5.0.0")!.Value)!.Tag);
        Assert.Null(Releases.Find(list, AppVersion.Parse("5.2")!.Value));
    }

    [Fact]
    public void PicksTheRightAssetForEachTarget()
    {
        var release = Releases.Parse(Fixture).First(r => r.Tag == "v5.0");
        var setup = Releases.PickAsset(release, AssetKind.WindowsInstaller)!;
        Assert.Equal("MyMouse-Setup-5.0.exe", setup.Name);
        Assert.Equal(83886080 + 103, setup.Size);
        Assert.Equal("https://github.com/DiegoTorresMijarra/MyMouse/releases/download/v5.0/MyMouse-Setup-5.0.exe", setup.DownloadUrl);
        Assert.Equal(64, setup.Sha256!.Length);
        Assert.Equal("MyMouse-Windows-5.0.zip", Releases.PickAsset(release, AssetKind.WindowsZip)!.Name);
        Assert.Null(Releases.PickAsset(release, AssetKind.WindowsZip)!.Sha256); // sin digest en la API
        Assert.Equal("MyMouse-5.0.apk", Releases.PickAsset(release, AssetKind.AndroidApk)!.Name);
    }

    [Fact]
    public void ApkPatternDoesNotMatchTheInstallerOrZip()
    {
        Assert.False(Releases.Matches("MyMouse-Setup-5.0.exe", AssetKind.AndroidApk));
        Assert.False(Releases.Matches("MyMouse-Windows-5.0.zip", AssetKind.AndroidApk));
        Assert.False(Releases.Matches("MyMouse-5.0.apk", AssetKind.WindowsInstaller));
        Assert.True(Releases.Matches("MyMouse-Setup-0.0-dev.3.exe", AssetKind.WindowsInstaller));
    }

    [Fact]
    public void ReleaseWithoutAssetsHasNoAsset()
    {
        var json = """[{"tag_name":"v5.1","draft":false,"prerelease":true,"name":"v5.1","body":"","html_url":"https://x","assets":[]}]""";
        var release = Releases.Parse(json).Single();
        Assert.Null(Releases.PickAsset(release, AssetKind.WindowsInstaller));
    }

    [Theory]
    [InlineData("{\"message\":\"API rate limit exceeded\"}")]
    [InlineData("no es json")]
    [InlineData("")]
    public void ErrorsAreFormatExceptions(string json) => Assert.Throws<FormatException>(() => Releases.Parse(json));

    [Theory]
    [InlineData("https://github.com/DiegoTorresMijarra/MyMouse/releases/download/v5.0/MyMouse-Setup-5.0.exe", true)]
    [InlineData("http://github.com/x/y", false)]
    [InlineData("https://evil.example.com/MyMouse-Setup-5.0.exe", false)]
    [InlineData("https://github.com.evil.example/x", false)]
    [InlineData("nope", false)]
    public void OnlyTrustsGitHubOverHttps(string url, bool trusted) => Assert.Equal(trusted, Releases.IsTrustedDownloadUrl(url));
}

public class UpdatePolicyTests
{
    private static ReleaseInfo Release(string tag) =>
        Releases.Parse($$"""[{"tag_name":"{{tag}}","draft":false,"prerelease":true,"name":"{{tag}}","body":"","html_url":"https://x","assets":[]}]""").Single();

    [Theory]
    [InlineData("5.0", "v5.1", null, true)]
    [InlineData("5.0", "v5.0", null, false)]
    [InlineData("5.1", "v5.0", null, false)]
    [InlineData("5.0", "v5.0.1", null, true)]
    [InlineData("0.0-dev.12", "v5.1", null, false)] // las versiones dev nunca avisan
    [InlineData("0.0", "v5.1", null, false)]
    [InlineData("", "v5.1", null, false)]
    [InlineData("5.0", "v5.1", "5.1", false)] // omitida
    [InlineData("5.0", "v5.2", "5.1", true)] // la siguiente sí avisa
    [InlineData("5.0", "v5.1", "5.1.0", false)]
    [InlineData("5.0", "v5.1", "basura", true)]
    public void DecidesWhetherToOffer(string local, string tag, string? skipped, bool expected) =>
        Assert.Equal(expected, UpdatePolicy.ShouldOffer(AppVersion.Parse(local), Release(tag), skipped));

    [Fact]
    public void NothingToOfferWithoutRelease() => Assert.False(UpdatePolicy.ShouldOffer(AppVersion.Parse("5.0"), null, null));

    [Fact]
    public void PcSeesNewerPhone()
    {
        var n = UpdatePolicy.CrossDevice(VersionSide.Pc, "5.0", "5.2");
        Assert.Equal(CrossDeviceKind.OtherNewer, n.Kind);
        Assert.Equal("El móvil tiene MyMouse 5.2 y este PC la 5.0: actualízalo.", n.Message);
    }

    [Fact]
    public void PcSeesOlderPhone()
    {
        var n = UpdatePolicy.CrossDevice(VersionSide.Pc, "5.2", "5.0");
        Assert.Equal(CrossDeviceKind.OtherOlder, n.Kind);
        Assert.Equal("El móvil tiene una versión anterior (5.0): actualízalo desde el móvil.", n.Message);
    }

    [Fact]
    public void PhoneSideMessages()
    {
        Assert.Equal("El PC tiene MyMouse 5.2 y este móvil la 5.0: actualízalo.", UpdatePolicy.CrossDevice(VersionSide.Phone, "5.0", "5.2").Message);
        Assert.Equal("El PC tiene una versión anterior (5.0): actualízalo desde el PC.", UpdatePolicy.CrossDevice(VersionSide.Phone, "5.2", "5.0").Message);
    }

    [Theory]
    [InlineData("5.0", "5.0")]
    [InlineData("5.0", "5.0.0")]
    [InlineData("0.0-dev.3", "5.2")]
    [InlineData("5.2", "0.0-dev.3")]
    [InlineData("5.0", "")]
    [InlineData("5.0", null)]
    [InlineData("5.0", "basura")]
    public void NoCrossNotice(string mine, string? other) =>
        Assert.Equal(CrossDeviceKind.None, UpdatePolicy.CrossDevice(VersionSide.Pc, mine, other).Kind);

    [Fact]
    public void TrimsNotes()
    {
        Assert.Equal("a\n\nb", UpdatePolicy.TrimNotes("a  \r\n\r\n\r\n\r\nb\r\n"));
        var trimmed = UpdatePolicy.TrimNotes(string.Join(" ", Enumerable.Repeat("palabra", 200)), 100);
        Assert.True(trimmed.Length <= 101);
        Assert.EndsWith("…", trimmed);
    }
}

public class UpdateManagerTests
{
    private static IReadOnlyList<ReleaseInfo> Fixture() =>
        Releases.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "github-releases.json")));

    private static UpdateManager Make(string local, out List<UpdateSettings> saved, IReadOnlyList<ReleaseInfo>? releases = null, UpdateSettings? settings = null)
    {
        var list = new List<UpdateSettings>();
        saved = list;
        return new UpdateManager(local, _ => Task.FromResult(releases ?? Fixture()), settings ?? new UpdateSettings(), list.Add);
    }

    [Fact]
    public async Task OffersTheHighestRelease()
    {
        var m = Make("5.0", out _);
        Assert.Equal(CheckOutcome.Available, await m.CheckAsync());
        Assert.Equal("v5.0.1", m.Offer!.Tag);
        var banner = Assert.Single(m.Banners);
        Assert.Equal(BannerKind.NewVersion, banner.Kind);
        Assert.Equal("Nueva versión 5.0.1 disponible", banner.Text);
        Assert.True(banner.CanInstall && banner.CanSkip);
        Assert.True(m.HasUpdate);
    }

    [Fact]
    public async Task UpToDate()
    {
        var m = Make("5.0.1", out _);
        Assert.Equal(CheckOutcome.UpToDate, await m.CheckAsync());
        Assert.Null(m.Offer);
        Assert.Empty(m.Banners);
    }

    [Fact]
    public async Task DevBuildNeverPrompts()
    {
        var m = Make("0.0-dev.99", out _);
        Assert.Equal(CheckOutcome.DevBuild, await m.CheckAsync());
        Assert.Null(m.Offer);
        m.SetPhoneVersion("5.0");
        Assert.Empty(m.Banners);
        Assert.False(m.HasUpdate);
    }

    [Fact]
    public async Task SkipHidesThatVersionButNotTheNext()
    {
        var m = Make("5.0", out var saved);
        await m.CheckAsync();
        m.SkipOffered();
        Assert.Null(m.Offer);
        Assert.Equal("5.0.1", saved.Last().SkippedVersion);
        Assert.Equal(CheckOutcome.Skipped, await m.CheckAsync());

        // Aparece una release posterior: avisa otra vez.
        var more = Fixture().Append(Releases.Parse("""[{"tag_name":"v5.1","draft":false,"prerelease":true,"name":"v5.1","body":"","html_url":"https://x","assets":[]}]""").Single()).ToList();
        var m2 = Make("5.0", out _, more, new UpdateSettings { SkippedVersion = "5.0.1" });
        Assert.Equal(CheckOutcome.Available, await m2.CheckAsync());
        Assert.Equal("v5.1", m2.Offer!.Tag);
    }

    [Fact]
    public async Task ManualCheckUnskipsTheSkippedVersion()
    {
        var m = Make("5.0", out var saved, settings: new UpdateSettings { SkippedVersion = "5.0.1" });
        Assert.Equal(CheckOutcome.Skipped, await m.CheckAsync());
        Assert.Equal(CheckOutcome.Available, await m.CheckAsync(manual: true));
        Assert.Null(saved.Last().SkippedVersion);
        Assert.Equal("v5.0.1", m.Offer!.Tag);
    }

    [Fact]
    public async Task NetworkFailureIsSilent()
    {
        var log = new List<string>();
        var m = new UpdateManager("5.0", _ => throw new HttpRequestException("sin red"), new UpdateSettings(), _ => { }, log.Add);
        Assert.Equal(CheckOutcome.Failed, await m.CheckAsync());
        Assert.Single(log);
        Assert.Null(m.Offer);
    }

    [Fact]
    public async Task NotifiesOncePerVersion()
    {
        var m = Make("5.0", out var saved);
        await m.CheckAsync();
        Assert.True(m.ShouldNotify);
        m.MarkNotified();
        Assert.False(m.ShouldNotify);
        Assert.Equal("5.0.1", saved.Last().NotifiedVersion);
    }

    [Fact]
    public async Task PhoneNewerShowsInstallBannerWhenNothingOffered()
    {
        var m = Make("5.0", out _, settings: new UpdateSettings { SkippedVersion = "5.0.1" });
        await m.CheckAsync();
        Assert.Null(m.Offer);
        m.SetPhoneVersion("4.8.3"); // más antiguo: informa
        var old = Assert.Single(m.Banners);
        Assert.Equal(BannerKind.PhoneOlder, old.Kind);
        Assert.False(old.CanInstall);
        Assert.False(m.HasUpdate);

        m.SetPhoneVersion("5.0.1");
        var banner = Assert.Single(m.Banners);
        Assert.Equal(BannerKind.PhoneNewer, banner.Kind);
        Assert.Equal("v5.0.1", banner.Release!.Tag);
        Assert.True(banner.CanInstall);
        Assert.Equal("El móvil tiene MyMouse 5.0.1 y este PC la 5.0: actualízalo.", banner.Text);

        m.SetPhoneVersion("7.0"); // no está en GitHub: solo texto
        Assert.False(Assert.Single(m.Banners).CanInstall);
    }

    [Fact]
    public async Task PhoneNewerIsCoveredByTheOfferBanner()
    {
        var m = Make("5.0", out _);
        await m.CheckAsync();
        m.SetPhoneVersion("5.0.1");
        Assert.Equal([BannerKind.NewVersion], m.Banners.Select(b => b.Kind));
        m.SetPhoneVersion("4.8");
        Assert.Equal([BannerKind.NewVersion, BannerKind.PhoneOlder], m.Banners.Select(b => b.Kind));
    }

    [Fact]
    public void AutoCheckIsSaved()
    {
        var m = Make("5.0", out var saved);
        m.AutoCheck = false;
        Assert.False(saved.Last().AutoCheck);
    }
}

public class UpdateSettingsTests
{
    [Fact]
    public void RoundTripsAndToleratesMissingOrBrokenFiles()
    {
        var dir = Path.Combine(Path.GetTempPath(), "mymouse-test-" + Guid.NewGuid().ToString("N"));
        var path = Path.Combine(dir, "updates.json");
        try
        {
            Assert.True(UpdateSettings.Load(path).AutoCheck); // no existe: por defecto
            new UpdateSettings { AutoCheck = false, SkippedVersion = "5.1", NotifiedVersion = "5.2" }.Save(path);
            var loaded = UpdateSettings.Load(path);
            Assert.False(loaded.AutoCheck);
            Assert.Equal("5.1", loaded.SkippedVersion);
            Assert.Equal("5.2", loaded.NotifiedVersion);
            File.WriteAllText(path, "{ roto");
            Assert.True(UpdateSettings.Load(path).AutoCheck);
        }
        finally
        {
            if (Directory.Exists(dir)) Directory.Delete(dir, true);
        }
    }
}

public class InstallDetectorTests
{
    private const string Local = @"C:\Users\ana\AppData\Local";

    [Theory]
    [InlineData(@"C:\Users\ana\AppData\Local\Programs\MyMouse\MyMouse.exe", null, true)]
    [InlineData(@"c:\users\ANA\appdata\local\programs\mymouse\MyMouse.exe", null, true)]
    [InlineData(@"D:\Portable\MyMouse\MyMouse.exe", null, false)]
    [InlineData(@"D:\Portable\MyMouse\MyMouse.exe", @"C:\Users\ana\AppData\Local\Programs\MyMouse\", false)] // la clave existe pero es otra carpeta
    [InlineData(@"D:\Apps\MyMouse\MyMouse.exe", @"D:\Apps\MyMouse\", true)] // instalada en otra carpeta
    [InlineData(null, null, false)]
    public void Detects(string? exe, string? uninstallLocation, bool installed) =>
        Assert.Equal(installed, InstallDetector.IsInstalled(exe, Local, uninstallLocation));

    [Fact]
    public void SilentArgumentsRelaunch()
    {
        Assert.Contains("/SILENT", InstallDetector.SilentArguments);
        Assert.Contains("/CLOSEAPPLICATIONS", InstallDetector.SilentArguments);
        Assert.Contains("/RELAUNCH=1", InstallDetector.SilentArguments);
    }
}

public class UpdateCheckerTests
{
    private sealed class FakeHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
    {
        public List<HttpRequestMessage> Requests { get; } = [];

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests.Add(request);
            return Task.FromResult(respond(request));
        }
    }

    private static HttpClient Client(FakeHandler handler)
    {
        var client = new HttpClient(handler);
        UpdateChecker.Configure(client);
        return client;
    }

    [Fact]
    public async Task FetchesWithUserAgentAndParses()
    {
        var json = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "github-releases.json"));
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) });
        var releases = await new UpdateChecker(Client(handler)).FetchAsync();

        Assert.Equal(4, releases.Count);
        var request = Assert.Single(handler.Requests);
        Assert.Equal(Releases.ListUrl, request.RequestUri!.ToString());
        Assert.Equal("MyMouse-Updater/1", request.Headers.UserAgent.ToString());
        Assert.DoesNotContain("per_page=100", Releases.ListUrl);
    }

    [Fact]
    public async Task HttpErrorThrows()
    {
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.Forbidden));
        await Assert.ThrowsAsync<HttpRequestException>(() => new UpdateChecker(Client(handler)).FetchAsync());
    }

    private static string TempFile() => Path.Combine(Path.GetTempPath(), "mymouse-dl-" + Guid.NewGuid().ToString("N") + ".exe");

    private static ReleaseAsset AssetFor(byte[] data, bool withHash, long? size = null) => new(
        "MyMouse-Setup-5.1.exe", size ?? data.Length,
        "https://github.com/DiegoTorresMijarra/MyMouse/releases/download/v5.1/MyMouse-Setup-5.1.exe",
        withHash ? Convert.ToHexString(SHA256.HashData(data)).ToLowerInvariant() : null);

    [Fact]
    public async Task DownloadsAndReportsProgress()
    {
        var data = new byte[300_000];
        new Random(1).NextBytes(data);
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(data) });
        var path = TempFile();
        var reports = new List<double>();
        try
        {
            await new UpdateChecker(Client(handler)).DownloadAsync(AssetFor(data, withHash: true), path, new Progress<double>(reports.Add));
            Assert.Equal(data, await File.ReadAllBytesAsync(path));
            Assert.False(File.Exists(path + ".part"));
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public async Task SizeMismatchDeletesTheDownload()
    {
        var data = new byte[1000];
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(data) });
        var path = TempFile();
        await Assert.ThrowsAsync<InvalidDataException>(() => new UpdateChecker(Client(handler)).DownloadAsync(AssetFor(data, false, size: 2000), path, null));
        Assert.False(File.Exists(path));
        Assert.False(File.Exists(path + ".part"));
    }

    [Fact]
    public async Task HashMismatchDeletesTheDownload()
    {
        var data = new byte[1000];
        var asset = AssetFor(data, withHash: false) with { Sha256 = new string('0', 64) };
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(data) });
        var path = TempFile();
        await Assert.ThrowsAsync<InvalidDataException>(() => new UpdateChecker(Client(handler)).DownloadAsync(asset, path, null));
        Assert.False(File.Exists(path));
    }

    [Fact]
    public async Task RefusesUntrustedUrls()
    {
        var handler = new FakeHandler(_ => new HttpResponseMessage(HttpStatusCode.OK));
        var asset = new ReleaseAsset("MyMouse-Setup-5.1.exe", 1, "https://evil.example.com/MyMouse-Setup-5.1.exe", null);
        await Assert.ThrowsAsync<InvalidDataException>(() => new UpdateChecker(Client(handler)).DownloadAsync(asset, TempFile(), null));
        Assert.Empty(handler.Requests);
    }
}

public class AppVersionSessionTests
{
    [Fact]
    public void PhoneHubKeepsThePhoneVersionUntilDetached()
    {
        var hub = new PhoneHub();
        Assert.Null(hub.PhoneAppVersion);
        var attachment = hub.Attach(6, (_, _) => ValueTask.CompletedTask);
        hub.SetPhoneAppVersion("5.0");
        Assert.Equal("5.0", hub.PhoneAppVersion);
        attachment.Dispose();
        Assert.Null(hub.PhoneAppVersion);
    }
}
