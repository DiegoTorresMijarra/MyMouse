using MyMouse.HostCore.Universe;

namespace MyMouse.HostCore.Tests;

public class StarFieldTests
{
    [Fact]
    public void SameSeedGivesSameStars()
    {
        Assert.Equal(StarField.Generate(90), StarField.Generate(90));
        Assert.NotEqual(StarField.Generate(20, seed: 1), StarField.Generate(20, seed: 2));
    }

    [Fact]
    public void ShorterFieldIsAPrefixSoResizingDoesNotMoveStars()
    {
        var big = StarField.Generate(120);
        Assert.Equal(big.Take(60), StarField.Generate(60));
    }

    [Fact]
    public void MatchesTheReferenceValuesOfTheAndroidSide()
    {
        // Los mismos valores están en StarFieldTest.kt (core): los dos lados dibujan el mismo cielo.
        var s = StarField.Generate(3);
        Assert.Equal(0.19447365f, s[0].X, 1e-6f);
        Assert.Equal(0.71386205f, s[0].Y, 1e-6f);
        Assert.Equal(1.14943442f, s[0].Radius, 1e-5f);
        Assert.Equal(0.56145599f, s[0].Opacity, 1e-6f);
        Assert.Equal(4.5371262f, s[0].PeriodSeconds, 1e-5f);
        Assert.Equal(1.7658669f, s[0].Phase, 1e-5f);
        Assert.Equal(0.06382243f, s[1].X, 1e-6f);
        Assert.Equal(0.76248036f, s[2].X, 1e-6f);
    }

    [Fact]
    public void ValuesStayInTheirRanges()
    {
        foreach (var s in StarField.Generate(400))
        {
            Assert.InRange(s.X, 0f, 1f);
            Assert.InRange(s.Y, 0f, 1f);
            Assert.InRange(s.Radius, 0.5f, 1.6f);
            Assert.InRange(s.Opacity, 0.35f, 1f);
            Assert.InRange(s.PeriodSeconds, 2f, 5f);
            Assert.InRange(s.Phase, 0f, (float)(2 * Math.PI));
        }
    }

    [Fact]
    public void AboutTenPercentHaveAHaloAndAboutTwelveOfNinetyTwinkle()
    {
        var stars = StarField.Generate(90);
        Assert.Equal(13, stars.Count(s => s.Twinkle));
        Assert.Equal(7, stars.Count(s => s.Halo));
        Assert.InRange(StarField.Generate(2000).Count(s => s.Halo), 150, 250);
    }

    [Fact]
    public void DensityFollowsTheArea()
    {
        Assert.Equal(90, StarField.CountFor(400, 600));
        Assert.Equal(45, StarField.CountFor(300, 400));
        Assert.Equal(StarField.MinCount, StarField.CountFor(0, 0));
        Assert.Equal(StarField.MinCount, StarField.CountFor(40, 40));
        Assert.Equal(StarField.MaxCount, StarField.CountFor(4000, 4000));
    }

    [Fact]
    public void SteadyStarsDoNotTwinkle()
    {
        var s = StarField.Generate(10).First(x => !x.Twinkle);
        foreach (var t in new[] { 0.0, 1.3, 7.7 }) Assert.Equal(s.Opacity, StarField.OpacityAt(s, t));
    }

    [Fact]
    public void TwinklingStarsOscillateBetweenFloorAndTheirOpacity()
    {
        var s = StarField.Generate(10).First(x => x.Twinkle);
        var values = Enumerable.Range(0, 2001).Select(i => StarField.OpacityAt(s, i / 100.0)).ToList();
        Assert.All(values, v => Assert.InRange(v, s.Opacity * StarField.TwinkleFloor - 1e-4f, s.Opacity + 1e-4f));
        Assert.True(values.Max() > s.Opacity * 0.99f);
        Assert.True(values.Min() < s.Opacity * (StarField.TwinkleFloor + 0.02f));
    }

    [Fact]
    public void TwinkleIsPeriodic()
    {
        var s = StarField.Generate(10).First(x => x.Twinkle);
        foreach (var t in new[] { 0.0, 0.7, 3.1 })
            Assert.Equal(StarField.OpacityAt(s, t), StarField.OpacityAt(s, t + s.PeriodSeconds), 1e-4f);
    }

    [Fact]
    public void DifferentStarsHaveDifferentPhases()
    {
        var twinkling = StarField.Generate(90).Where(s => s.Twinkle).ToList();
        Assert.Equal(twinkling.Count, twinkling.Select(s => s.Phase).Distinct().Count());
    }
}
