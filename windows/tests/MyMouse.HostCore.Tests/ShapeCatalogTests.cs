using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class ShapeCatalogTests
{
    /// <summary>Compara las coordenadas con <c>shape-catalog.txt</c>, el fichero que también comprueba el test de Kotlin contra su <c>ShapeCatalog</c>.</summary>
    [Fact]
    public void MatchesTheKotlinDefinitions()
    {
        var rows = File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "shape-catalog.txt"))
            .Where(l => l.Length > 0 && !l.StartsWith('#')).ToList();
        Assert.Equal(ShapeCatalog.All.Select(s => s.Id), rows.Select(r => r.Split('|')[0]));

        foreach (var (shape, row) in ShapeCatalog.All.Zip(rows))
        {
            var parts = row.Split('|');
            Assert.Equal(parts[1] == "1", shape.Closed);
            var expected = parts[2].Split(';').Select(p => p.Split(',')).Select(p => (X: float.Parse(p[0], System.Globalization.CultureInfo.InvariantCulture), Y: float.Parse(p[1], System.Globalization.CultureInfo.InvariantCulture))).ToList();
            Assert.Equal(expected.Count, shape.Points.Count);
            for (var i = 0; i < expected.Count; i++)
            {
                Assert.True(Math.Abs(expected[i].X - shape.Points[i].X) < 1e-6f && Math.Abs(expected[i].Y - shape.Points[i].Y) < 1e-6f,
                    $"{shape.Id}[{i}]: {expected[i]} vs {shape.Points[i]}");
            }
        }
    }

    [Fact]
    public void HasThe15ShapesAndLabels()
    {
        Assert.Equal(15, ShapeCatalog.All.Count);
        Assert.Equal("Círculo", ShapeCatalog.Label("circle"));
        Assert.Equal("firma", ShapeCatalog.Label("custom:firma"));
        Assert.Equal("desconocida", ShapeCatalog.Label("desconocida"));
    }
}

public class ShapeGeometryTests
{
    [Fact]
    public void Resample_GivesNEquidistantPoints()
    {
        var line = new PointF[] { new(0, 0), new(10, 0) };
        var r = ShapeGeometry.Resample(line, 11);
        Assert.Equal(11, r.Count);
        for (var i = 0; i < 11; i++) Assert.Equal(i, r[i].X, 3);
    }

    [Fact]
    public void ToWire_Has32Points_KeepsProportions_AndStartsAtTheOrigin()
    {
        var wire = ShapeGeometry.ToWire([new PointF(10, 10), new PointF(210, 10), new PointF(210, 110)]);
        Assert.Equal(ProtocolInfo.ShapeSamplePoints, wire.Count);
        Assert.Equal(new StrokePoint(0, 0), wire[0]);
        Assert.Equal(65535, wire.Max(p => p.X));
        Assert.InRange(wire.Max(p => p.Y), 32700, 32800);
    }

    [Fact]
    public void ToWire_OfADot_DoesNotCrash()
    {
        var wire = ShapeGeometry.ToWire([new PointF(5, 5), new PointF(5, 5)]);
        Assert.All(wire, p => Assert.Equal(new StrokePoint(0, 0), p));
    }

    [Fact]
    public void FromWire_IsTheInverseOnTheNormalizedBox()
    {
        var back = ShapeGeometry.FromWire([new StrokePoint(0, 0), new StrokePoint(65535, 32768)]);
        Assert.Equal(1f, back[1].X);
        Assert.Equal(0.5f, back[1].Y, 3);
    }

    [Theory]
    [InlineData("firma", "custom:firma")]
    [InlineData("  Mi Firma  ", "custom:mi-firma")]
    [InlineData("a   b\tc", "custom:a-b-c")]
    [InlineData("Café", "custom:café")]
    public void Names_AreNormalizedLikeOnThePhone(string typed, string expected) =>
        Assert.Equal(expected, ShapeNames.Normalize(typed));

    [Fact]
    public void Names_EmptyOrTooLong_AreRejected()
    {
        Assert.Null(ShapeNames.Normalize("   "));
        Assert.Null(ShapeNames.Normalize(new string('x', 60)));
        Assert.NotNull(ShapeNames.Normalize(new string('x', 50)));
    }
}
