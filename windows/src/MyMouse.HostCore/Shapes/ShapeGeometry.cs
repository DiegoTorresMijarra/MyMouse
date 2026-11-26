using MyMouse.Protocol;

namespace MyMouse.HostCore.Shapes;

/// <summary>Geometría de formas: remuestreo y paso a y desde las coordenadas del protocolo (0–65535).</summary>
public static class ShapeGeometry
{
    public static float PathLength(IReadOnlyList<PointF> points)
    {
        var sum = 0f;
        for (var i = 1; i < points.Count; i++) sum += Dist(points[i - 1], points[i]);
        return sum;
    }

    private static float Dist(PointF a, PointF b) => MathF.Sqrt((a.X - b.X) * (a.X - b.X) + (a.Y - b.Y) * (a.Y - b.Y));

    /// <summary>Remuestrea el trazo a <paramref name="n"/> puntos equidistantes a lo largo del recorrido (como <c>Geometry.resample</c> del móvil).</summary>
    public static List<PointF> Resample(IReadOnlyList<PointF> points, int n)
    {
        if (points.Count == 0) throw new ArgumentException("Trazo vacío", nameof(points));
        if (points.Count == 1) return [.. Enumerable.Repeat(points[0], n)];
        var interval = PathLength(points) / (n - 1);
        if (interval == 0f) return [.. Enumerable.Repeat(points[0], n)];

        var src = new List<PointF>(points);
        var output = new List<PointF> { src[0] };
        var acc = 0f;
        var i = 1;
        while (i < src.Count)
        {
            var a = src[i - 1];
            var b = src[i];
            var d = Dist(a, b);
            if (acc + d >= interval && d > 0f)
            {
                var t = (interval - acc) / d;
                var q = new PointF(a.X + t * (b.X - a.X), a.Y + t * (b.Y - a.Y));
                output.Add(q);
                src.Insert(i, q);
                acc = 0f;
            }
            else
            {
                acc += d;
            }
            i++;
        }
        while (output.Count < n) output.Add(src[^1]);
        return output.Count > n ? output.GetRange(0, n) : output;
    }

    /// <summary>
    /// Remuestrea a 32 puntos y escala a 0–65535 con la misma escala en los dos ejes (el lado largo ocupa todo el rango),
    /// como hace el móvil en <c>SHAPE_INFO</c>.
    /// </summary>
    public static List<StrokePoint> ToWire(IReadOnlyList<PointF> sample)
    {
        var pts = Resample(sample, ProtocolInfo.ShapeSamplePoints);
        var minX = pts.Min(p => p.X);
        var minY = pts.Min(p => p.Y);
        var side = Math.Max(pts.Max(p => p.X) - minX, pts.Max(p => p.Y) - minY);
        var scale = side > 0f ? 65535f / side : 0f;
        return [.. pts.Select(p => new StrokePoint(Clamp((p.X - minX) * scale), Clamp((p.Y - minY) * scale)))];
    }

    private static ushort Clamp(float v) => (ushort)Math.Clamp((int)MathF.Round(v), 0, 65535);

    /// <summary>Puntos del protocolo como coordenadas 0–1 (misma escala en los dos ejes: la forma no se deforma).</summary>
    public static List<PointF> FromWire(IReadOnlyList<StrokePoint> points) =>
        [.. points.Select(p => new PointF(p.X / 65535f, p.Y / 65535f))];

    /// <summary>Caja que contiene los puntos, o null si no hay.</summary>
    public static (float MinX, float MinY, float MaxX, float MaxY)? Bounds(IReadOnlyList<PointF> points) =>
        points.Count == 0
            ? null
            : (points.Min(p => p.X), points.Min(p => p.Y), points.Max(p => p.X), points.Max(p => p.Y));
}

/// <summary>Nombres de las formas propias.</summary>
public static class ShapeNames
{
    /// <summary>
    /// Nombre completo (<c>custom:firma</c>) a partir de lo que escribe el usuario: sin espacios sobrantes, los huecos
    /// pasan a guiones y en minúsculas (igual que en el móvil). Null si queda vacío o pasa de 64 bytes.
    /// </summary>
    public static string? Normalize(string typed)
    {
        var name = System.Text.RegularExpressions.Regex.Replace(typed.Trim(), @"\s+", "-").ToLowerInvariant();
        if (name.Length == 0) return null;
        var full = ShapeCatalog.CustomPrefix + name;
        return System.Text.Encoding.UTF8.GetByteCount(full) > ProtocolInfo.MaxGestureNameBytes ? null : full;
    }
}
