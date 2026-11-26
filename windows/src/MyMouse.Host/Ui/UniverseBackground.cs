using System.Diagnostics;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using MyMouse.HostCore.Universe;

namespace MyMouse.Host.Ui;

/// <summary>
/// El fondo "universo" (docs/spec-v4.7-universo.md §2): degradado diagonal, tres nebulosas y estrellas con semilla fija (en
/// coordenadas 0–1, para que no salten al redimensionar); unas 13 parpadean. Sin desenfoque. El parpadeo solo corre mientras el
/// control es visible (<see cref="DispatcherTimer"/> de ~30 fps). La lógica de las estrellas está en <c>HostCore</c> y tiene tests.
/// </summary>
internal sealed class UniverseBackground : FrameworkElement
{
    private static readonly Brush BaseBrush = MakeBase();
    private static readonly Brush[] WhiteByAlpha = new Brush[256];
    private static readonly Dictionary<byte, Brush> HaloByAlpha = [];

    private readonly DispatcherTimer _timer = new(DispatcherPriority.Background) { Interval = TimeSpan.FromMilliseconds(33) };
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private IReadOnlyList<Star> _stars = [];
    private (Brush Violet, Brush Blue, Brush Magenta, double W, double H)? _nebulas;
    private bool _animated = true;

    public UniverseBackground()
    {
        IsHitTestVisible = false;
        SnapsToDevicePixels = false;
        _timer.Tick += (_, _) => InvalidateVisual();
        IsVisibleChanged += (_, _) => UpdateTimer();
    }

    /// <summary>Radio de las esquinas (la ventana sin marco del sistema las dibuja ella misma).</summary>
    public double CornerRadius { get; set; }

    /// <summary>Si las estrellas parpadean. En el trackpad del móvil es estático; en el PC, no.</summary>
    public bool Animated
    {
        get => _animated;
        set
        {
            _animated = value;
            UpdateTimer();
            InvalidateVisual();
        }
    }

    private void UpdateTimer()
    {
        if (_animated && IsVisible) _timer.Start();
        else _timer.Stop();
    }

    private static Brush MakeBase()
    {
        var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
        brush.GradientStops.Add(new GradientStop(Hex("#070616"), 0));
        brush.GradientStops.Add(new GradientStop(Hex("#120D2E"), 0.5));
        brush.GradientStops.Add(new GradientStop(Hex("#1B0F36"), 1));
        brush.Freeze();
        return brush;
    }

    private static Color Hex(string hex) => (Color)ColorConverter.ConvertFromString(hex);

    /// <summary>Nebulosa: degradado radial absoluto de <paramref name="center"/> con el color al <paramref name="opacity"/> y suave hasta 0.</summary>
    private static Brush Nebula(Point center, double radius, string color, double opacity)
    {
        var c = Hex(color);
        Color At(double k) => Color.FromArgb((byte)Math.Round(255 * opacity * k), c.R, c.G, c.B);
        var brush = new RadialGradientBrush
        {
            MappingMode = BrushMappingMode.Absolute,
            Center = center,
            GradientOrigin = center,
            RadiusX = radius,
            RadiusY = radius,
        };
        brush.GradientStops.Add(new GradientStop(At(1), 0));
        brush.GradientStops.Add(new GradientStop(At(0.45), 0.45));
        brush.GradientStops.Add(new GradientStop(At(0), 1));
        brush.Freeze();
        return brush;
    }

    private static Brush White(float opacity)
    {
        var a = (byte)Math.Clamp((int)Math.Round(opacity * 255), 0, 255);
        return WhiteByAlpha[a] ??= Freeze(new SolidColorBrush(Color.FromArgb(a, 255, 255, 255)));
    }

    /// <summary>Halo suave de una estrella: radial blanco que se desvanece (alfa cuantizado para reutilizar los pinceles).</summary>
    private static Brush Halo(float opacity)
    {
        var a = (byte)(Math.Clamp((int)Math.Round(opacity * 255 * 0.55 / 16), 0, 15) * 16);
        if (HaloByAlpha.TryGetValue(a, out var cached)) return cached;
        var brush = new RadialGradientBrush();
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(a, 255, 255, 255), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0, 255, 255, 255), 1));
        return HaloByAlpha[a] = Freeze(brush);
    }

    private static T Freeze<T>(T brush) where T : Freezable
    {
        brush.Freeze();
        return brush;
    }

    protected override void OnRender(DrawingContext dc)
    {
        var w = ActualWidth;
        var h = ActualHeight;
        if (w <= 0 || h <= 0) return;
        var rect = new Rect(0, 0, w, h);
        var clip = CornerRadius > 0;
        if (clip) dc.PushClip(new RectangleGeometry(rect, CornerRadius, CornerRadius));

        dc.DrawRectangle(BaseBrush, null, rect);

        if (_nebulas is not { } n || n.W != w || n.H != h)
        {
            _nebulas = (
                Nebula(new Point(0.10 * w, 0.06 * h), 0.70 * w, "#7C3AED", 0.35), // violeta arriba a la izquierda
                Nebula(new Point(0.90 * w, 0.95 * h), 0.60 * w, "#2563EB", 0.28), // azul abajo a la derecha
                Nebula(new Point(0.85 * w, 0.50 * h), 0.40 * w, "#DB2777", 0.18), // magenta en el centro-derecha
                w, h);
            n = _nebulas.Value;
        }
        dc.DrawRectangle(n.Violet, null, rect);
        dc.DrawRectangle(n.Blue, null, rect);
        dc.DrawRectangle(n.Magenta, null, rect);

        var count = StarField.CountFor(w, h);
        if (_stars.Count != count) _stars = StarField.Generate(count);
        var seconds = _clock.Elapsed.TotalSeconds;
        foreach (var star in _stars)
        {
            var opacity = StarField.OpacityAt(star, seconds);
            var center = new Point(star.X * w, star.Y * h);
            if (star.Halo)
            {
                var r = 3.5 + star.Radius;
                dc.DrawEllipse(Halo(opacity), null, center, r, r);
            }
            dc.DrawEllipse(White(opacity), null, center, star.Radius, star.Radius);
        }

        if (clip) dc.Pop();
    }
}
