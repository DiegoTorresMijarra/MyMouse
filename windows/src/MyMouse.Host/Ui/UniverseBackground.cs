using System.Diagnostics;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using MyMouse.HostCore.Theming;
using MyMouse.HostCore.Universe;

namespace MyMouse.Host.Ui;

/// <summary>
/// El fondo "universo" de un tema (docs/spec-v4.7-universo.md §2, docs/spec-v4.8-temas.md §2): degradado diagonal de 1–3 colores,
/// de 0 a 3 nebulosas y estrellas con semilla fija (en coordenadas 0–1, para que no salten al redimensionar). Sin desenfoque. Usa el
/// tema del PC (<see cref="Theme.Current"/>) y se redibuja al cambiarlo, salvo que se le dé un <see cref="Fixed"/> (las muestras del
/// panel Tema). El parpadeo solo corre mientras el control es visible (<see cref="DispatcherTimer"/> de ~30 fps). La lógica de las
/// estrellas y de los temas está en <c>HostCore</c> y tiene tests.
/// </summary>
internal sealed class UniverseBackground : FrameworkElement
{
    private static readonly Brush[] WhiteByAlpha = new Brush[256];
    private static readonly Dictionary<byte, Brush> HaloByAlpha = [];

    private readonly DispatcherTimer _timer = new(DispatcherPriority.Background) { Interval = TimeSpan.FromMilliseconds(33) };
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private IReadOnlyList<Star> _stars = [];
    private Brush? _base;
    private Brush[] _nebulas = [];
    private (string Theme, double W, double H)? _built;
    private bool _animated = true;
    private ThemeChoice? _fixed;

    public UniverseBackground()
    {
        IsHitTestVisible = false;
        SnapsToDevicePixels = false;
        _timer.Tick += (_, _) => InvalidateVisual();
        IsVisibleChanged += (_, _) => UpdateTimer();
        // El evento es estático: solo se escucha mientras el control está en pantalla, para que una ventana cerrada no quede enganchada.
        Loaded += (_, _) =>
        {
            Theme.Changed += OnThemeChanged;
            OnThemeChanged();
        };
        Unloaded += (_, _) => Theme.Changed -= OnThemeChanged;
    }

    /// <summary>Radio de las esquinas (la ventana sin marco del sistema las dibuja ella misma).</summary>
    public double CornerRadius { get; set; }

    /// <summary>Si las estrellas parpadean. En el PC, sí (si el tema y el ajuste lo permiten); las muestras no.</summary>
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

    /// <summary>Un tema fijo en lugar del del PC (las muestras del panel Tema).</summary>
    public ThemeChoice? Fixed
    {
        get => _fixed;
        set
        {
            _fixed = value;
            _built = null;
            UpdateTimer();
            InvalidateVisual();
        }
    }

    private ThemeChoice Choice => _fixed ?? Theme.Current;

    private void OnThemeChanged()
    {
        _built = null;
        UpdateTimer();
        InvalidateVisual();
    }

    private void UpdateTimer()
    {
        if (_animated && IsVisible && Choice.Twinkles) _timer.Start();
        else _timer.Stop();
    }

    private static Color Hex(int rgb) => Color.FromRgb((byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);

    private static Brush MakeBase(ThemeDef theme)
    {
        var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
        // Una base de un solo color (OLED) se pasa como dos iguales.
        var colors = theme.Base.Count == 1 ? [theme.Base[0], theme.Base[0]] : theme.Base.ToArray();
        for (var i = 0; i < colors.Length; i++) brush.GradientStops.Add(new GradientStop(Hex(colors[i]), i / (double)(colors.Length - 1)));
        brush.Freeze();
        return brush;
    }

    /// <summary>Nebulosa: degradado radial absoluto de <paramref name="center"/> con el color al <paramref name="opacity"/> y suave hasta 0.</summary>
    private static Brush NebulaBrush(Point center, double radius, int color, double opacity)
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
        var choice = Choice;
        var theme = choice.Theme;
        var rect = new Rect(0, 0, w, h);
        var clip = CornerRadius > 0;
        if (clip) dc.PushClip(new RectangleGeometry(rect, CornerRadius, CornerRadius));

        if (_built is not { } built || built.Theme != theme.Id || built.W != w || built.H != h)
        {
            _base = MakeBase(theme);
            _nebulas = [.. theme.Nebulas.Select(n => NebulaBrush(new Point(n.X * w, n.Y * h), n.R * w, n.Color, n.Opacity))];
            _built = (theme.Id, w, h);
        }
        dc.DrawRectangle(_base, null, rect);
        foreach (var nebula in _nebulas) dc.DrawRectangle(nebula, null, rect);

        if (choice.ShowStars)
        {
            var count = ThemeCatalog.StarCount(theme, w, h);
            if (_stars.Count != count) _stars = StarField.Generate(count);
            var seconds = _animated ? _clock.Elapsed.TotalSeconds : 0;
            foreach (var star in _stars)
            {
                var opacity = StarField.OpacityAt(star, seconds, theme.TwinkleDepth);
                var center = new Point(star.X * w, star.Y * h);
                if (star.Halo)
                {
                    var r = 3.5 + star.Radius;
                    dc.DrawEllipse(Halo(opacity), null, center, r, r);
                }
                dc.DrawEllipse(White(opacity), null, center, star.Radius, star.Radius);
            }
        }

        if (clip) dc.Pop();
    }
}
