namespace MyMouse.HostCore.Shapes;

/// <summary>Punto en coordenadas de pantalla (y crece hacia abajo). Normalmente en el rango 0–1.</summary>
public readonly record struct PointF(float X, float Y);

/// <summary>
/// Las 15 formas incluidas, portadas de <c>ShapeCatalog.kt</c> (docs/spec-v1-gestos.md §2) para dibujar sus miniaturas
/// ideales en el PC. Un test compara estas coordenadas con las de Kotlin (<c>shape-catalog.txt</c>): si cambian allí,
/// deben cambiar aquí. Las líneas llevan solo dos puntos.
/// </summary>
public static class ShapeCatalog
{
    public sealed record Shape(string Id, string Label, IReadOnlyList<PointF> Points, bool Closed = false);

    public const string LineRight = "line-right";
    public const string LineLeft = "line-left";
    public const string LineUp = "line-up";
    public const string LineDown = "line-down";

    public static IReadOnlyList<Shape> All { get; } =
    [
        new(LineRight, "Line →", [new(0f, 0.5f), new(1f, 0.5f)]),
        new(LineLeft, "Line ←", [new(1f, 0.5f), new(0f, 0.5f)]),
        new(LineUp, "Line ↑", [new(0.5f, 1f), new(0.5f, 0f)]),
        new(LineDown, "Line ↓", [new(0.5f, 0f), new(0.5f, 1f)]),
        new("v", "V", [new(0f, 0f), new(0.5f, 1f), new(1f, 0f)]),
        new("caret", "Λ", [new(0f, 1f), new(0.5f, 0f), new(1f, 1f)]),
        new("lt", "<", [new(0.8f, 0f), new(0f, 0.5f), new(0.8f, 1f)]),
        new("gt", ">", [new(0f, 0f), new(0.8f, 0.5f), new(0f, 1f)]),
        new("circle", "Circle", Circle(), Closed: true),
        new("triangle", "Triangle", [new(0.5f, 0f), new(0f, 0.9f), new(1f, 0.9f), new(0.5f, 0f)], Closed: true),
        new("square", "Square", [new(0f, 0f), new(0f, 1f), new(1f, 1f), new(1f, 0f), new(0f, 0f)], Closed: true),
        new("check", "✓", [new(0f, 0.55f), new(0.3f, 1f), new(1f, 0f)]),
        new("z", "Z", [new(0f, 0f), new(1f, 0f), new(0f, 1f), new(1f, 1f)]),
        new("s", "S", SCurve()),
        new("l", "L", [new(0f, 0f), new(0f, 1f), new(0.6f, 1f)]),
    ];

    public static IReadOnlyDictionary<string, Shape> ById { get; } = All.ToDictionary(s => s.Id, StringComparer.Ordinal);

    public static IReadOnlyList<string> Ids { get; } = [.. All.Select(s => s.Id)];

    /// <summary>Prefijo de las formas propias.</summary>
    public const string CustomPrefix = "custom:";

    private static readonly HashSet<string> Translated = ["line-right", "line-left", "line-up", "line-down", "circle", "triangle", "square"];

    /// <summary>Texto para mostrar: el nombre del catálogo (traducido; V, Z, ✓… son símbolos) o el nombre propio sin prefijo.</summary>
    public static string Label(string name) =>
        ById.TryGetValue(name, out var s) ? (Translated.Contains(s.Id) ? CoreLoc.T("shape_" + s.Id.Replace('-', '_')) : s.Label) : name.StartsWith(CustomPrefix, StringComparison.Ordinal) ? name[CustomPrefix.Length..] : name;

    // Empieza arriba y gira en sentido antihorario, como se suele dibujar una "O".
    private static PointF[] Circle() =>
    [
        .. Enumerable.Range(0, 33).Select(i =>
        {
            var a = -Math.PI / 2 - 2 * Math.PI * i / 32;
            return new PointF((float)(0.5 + 0.5 * Math.Cos(a)), (float)(0.5 + 0.5 * Math.Sin(a)));
        }),
    ];

    private static PointF[] SCurve() =>
    [
        new(0.9f, 0.1f), new(0.6f, 0f), new(0.3f, 0f), new(0.1f, 0.12f), new(0.12f, 0.35f),
        new(0.5f, 0.5f), new(0.88f, 0.65f), new(0.9f, 0.88f), new(0.7f, 1f), new(0.4f, 1f), new(0.1f, 0.9f),
    ];
}
