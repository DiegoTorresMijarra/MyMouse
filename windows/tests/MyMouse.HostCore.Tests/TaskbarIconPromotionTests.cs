using MyMouse.HostCore.Shell;
using MyMouse.HostCore.Toolbox;

namespace MyMouse.HostCore.Tests;

public class TaskbarIconPromotionTests
{
    private const string Base = TaskbarIconPromotion.BasePath;

    private sealed class FakeRegistry : IRegistry
    {
        public Dictionary<string, Dictionary<string, object>> Keys { get; } = new(StringComparer.OrdinalIgnoreCase);
        public bool FailOnWrite { get; set; }
        public int Writes { get; private set; }

        public FakeRegistry Add(string id, string? exe, int? promoted = null)
        {
            var values = new Dictionary<string, object>(StringComparer.OrdinalIgnoreCase);
            if (exe is not null) values["ExecutablePath"] = exe;
            if (promoted is { } p) values["IsPromoted"] = p;
            Keys[$@"{Base}\{id}"] = values;
            return this;
        }

        public IReadOnlyList<string> GetSubKeyNames(string path) =>
            [.. Keys.Keys.Where(k => k.StartsWith(path + @"\", StringComparison.OrdinalIgnoreCase)).Select(k => k[(path.Length + 1)..])];

        public string? GetString(string path, string name) => Keys.TryGetValue(path, out var v) && v.TryGetValue(name, out var o) ? o as string : null;

        public int? GetDword(string path, string name) => Keys.TryGetValue(path, out var v) && v.TryGetValue(name, out var o) && o is int i ? i : null;

        public void SetDword(string path, string name, int value)
        {
            if (FailOnWrite) throw new UnauthorizedAccessException();
            Writes++;
            Keys[path][name] = value;
        }
    }

    private const string Exe = @"C:\Users\yo\AppData\Local\MyMouse\MyMouse.exe";

    [Fact]
    public void FindsOnlyTheKeysOfThisExecutable()
    {
        var reg = new FakeRegistry()
            .Add("111", @"C:\Windows\explorer.exe")
            .Add("222", Exe)
            .Add("333", null)
            .Add("444", @"C:\Otra\MyMouse.exe");
        Assert.Equal([$@"{Base}\222"], TaskbarIconPromotion.FindKeys(reg, Exe));
    }

    [Theory]
    [InlineData(@"c:\users\YO\appdata\local\mymouse\MYMOUSE.EXE")]
    [InlineData("C:/Users/yo/AppData/Local/MyMouse/MyMouse.exe")]
    [InlineData("\"C:\\Users\\yo\\AppData\\Local\\MyMouse\\MyMouse.exe\"")]
    [InlineData(@"  C:\Users\yo\AppData\Local\MyMouse\MyMouse.exe  ")]
    public void MatchIgnoresCaseSlashesQuotesAndSpaces(string registryValue)
    {
        var reg = new FakeRegistry().Add("1", registryValue);
        Assert.Single(TaskbarIconPromotion.FindKeys(reg, Exe));
    }

    [Fact]
    public void SeveralKeysForTheSameExecutableAreAllChanged()
    {
        var reg = new FakeRegistry().Add("1", Exe, 0).Add("2", Exe).Add("3", @"C:\x.exe", 0);
        Assert.Equal(PromotionOutcome.Done, TaskbarIconPromotion.SetPromoted(reg, Exe, true));
        Assert.Equal(1, reg.GetDword($@"{Base}\1", "IsPromoted"));
        Assert.Equal(1, reg.GetDword($@"{Base}\2", "IsPromoted"));
        Assert.Equal(0, reg.GetDword($@"{Base}\3", "IsPromoted")); // el de otro programa no se toca
        Assert.True(TaskbarIconPromotion.IsPromoted(reg, Exe));
    }

    [Fact]
    public void DisablingSetsZero()
    {
        var reg = new FakeRegistry().Add("1", Exe, 1);
        Assert.Equal(PromotionOutcome.Done, TaskbarIconPromotion.SetPromoted(reg, Exe, false));
        Assert.Equal(0, reg.GetDword($@"{Base}\1", "IsPromoted"));
        Assert.False(TaskbarIconPromotion.IsPromoted(reg, Exe));
    }

    [Fact]
    public void ValueAlreadyRightIsNotWrittenAgain()
    {
        var reg = new FakeRegistry().Add("1", Exe, 1);
        Assert.Equal(PromotionOutcome.Done, TaskbarIconPromotion.SetPromoted(reg, Exe, true));
        Assert.Equal(0, reg.Writes);
    }

    [Fact]
    public void NoKeyMeansNoKeyAndNothingIsCreated()
    {
        var reg = new FakeRegistry().Add("1", @"C:\Windows\explorer.exe");
        Assert.Equal(PromotionOutcome.NoKey, TaskbarIconPromotion.SetPromoted(reg, Exe, true));
        Assert.Null(TaskbarIconPromotion.IsPromoted(reg, Exe));
        Assert.Equal(0, reg.Writes);
        Assert.Equal(PromotionOutcome.NoKey, TaskbarIconPromotion.SetPromoted(new FakeRegistry(), Exe, true));
        Assert.Empty(TaskbarIconPromotion.FindKeys(reg, ""));
    }

    [Fact]
    public void WriteErrorIsReportedNotThrown()
    {
        var reg = new FakeRegistry { FailOnWrite = true }.Add("1", Exe, 0);
        Assert.Equal(PromotionOutcome.Failed, TaskbarIconPromotion.SetPromoted(reg, Exe, true));
    }

    [Fact]
    public void RetriesOnceAfterTenSecondsAndThenGivesUp()
    {
        Assert.Equal(TimeSpan.FromSeconds(10), TaskbarIconPromotion.RetryDelay);
        Assert.True(TaskbarIconPromotion.ShouldRetry(PromotionOutcome.NoKey, 1));
        Assert.False(TaskbarIconPromotion.ShouldRetry(PromotionOutcome.NoKey, 2));
        Assert.False(TaskbarIconPromotion.ShouldRetry(PromotionOutcome.Done, 1));
        Assert.False(TaskbarIconPromotion.ShouldRetry(PromotionOutcome.Failed, 1));
    }

    [Theory]
    [InlineData(19045, false)]
    [InlineData(21999, false)]
    [InlineData(22000, true)]
    [InlineData(26100, true)]
    public void OnlyWindows11HasTheKey(int build, bool supported) => Assert.Equal(supported, TaskbarIconPromotion.IsSupported(build));

    [Fact]
    public void ToolboxSettingDefaultsToOnAndIsPersisted()
    {
        Assert.True(ToolboxLayout.Default.AlwaysShowIcon);
        Assert.True(ToolboxLayout.Parse("{}").AlwaysShowIcon); // un toolbox.json de la v4.6: se activa solo
        var off = ToolboxLayout.Default.WithAlwaysShowIcon(false);
        Assert.False(off.AlwaysShowIcon);
        Assert.False(ToolboxLayout.Parse(off.ToJson()).AlwaysShowIcon);
        Assert.True(ToolboxLayout.Parse(off.WithAlwaysShowIcon(true).ToJson()).AlwaysShowIcon);
        // El resto de ajustes no cambia al tocarlo.
        var layout = ToolboxLayout.Default.WithGlass(false).WithMaximized(true).WithLiveGesture(false).WithAlwaysShowIcon(false);
        var back = ToolboxLayout.Parse(layout.ToJson());
        Assert.False(back.GlassEffect);
        Assert.True(back.Maximized);
        Assert.False(back.ShowLiveGesture);
        Assert.False(back.AlwaysShowIcon);
        Assert.False(layout.WithGlass(true).AlwaysShowIcon);
    }
}
