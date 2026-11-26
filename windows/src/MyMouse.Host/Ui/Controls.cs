using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using MyMouse.HostCore.Shapes;

namespace MyMouse.Host.Ui;

/// <summary>Miniatura de una forma: una polilínea en coordenadas 0–1 encajada en el control, con un punto verde donde empieza.</summary>
internal sealed class ShapeThumb : FrameworkElement
{
    private IReadOnlyList<PointF>? _points;

    public ShapeThumb(IReadOnlyList<PointF>? points = null, double size = 36)
    {
        _points = points;
        Width = Height = size;
        SnapsToDevicePixels = false;
        IsHitTestVisible = false;
    }

    public Brush Stroke { get; set; } = Theme.Text;

    public double Thickness { get; set; } = 2;

    public bool ShowStart { get; set; } = true;

    public IReadOnlyList<PointF>? Points
    {
        get => _points;
        set
        {
            _points = value;
            InvalidateVisual();
        }
    }

    /// <summary>Geometría de <paramref name="points"/> encajada en un cuadrado de <paramref name="width"/> × <paramref name="height"/> con margen.</summary>
    public static Geometry? Fit(IReadOnlyList<PointF>? points, double width, double height, double padding) =>
        Fit(points, width, height, padding, out _);

    public static Geometry? Fit(IReadOnlyList<PointF>? points, double width, double height, double padding, out Point start)
    {
        start = default;
        if (points is null || points.Count < 2) return null;
        var minX = points.Min(p => p.X);
        var minY = points.Min(p => p.Y);
        var w = points.Max(p => p.X) - minX;
        var h = points.Max(p => p.Y) - minY;
        var side = Math.Max(Math.Max(w, h), 1e-4);
        var scale = Math.Min(width - 2 * padding, height - 2 * padding) / side;
        var ox = (width - w * scale) / 2;
        var oy = (height - h * scale) / 2;
        Point Map(PointF p) => new(ox + (p.X - minX) * scale, oy + (p.Y - minY) * scale);

        start = Map(points[0]);
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(start, false, false);
            ctx.PolyLineTo(points.Skip(1).Select(Map).ToList(), true, true);
        }
        geometry.Freeze();
        return geometry;
    }

    protected override void OnRender(DrawingContext dc)
    {
        var geometry = Fit(_points, ActualWidth, ActualHeight, 5, out var start);
        if (geometry is null) return;
        var pen = new Pen(Stroke, Thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round };
        dc.DrawGeometry(null, pen, geometry);
        if (ShowStart) dc.DrawEllipse(Theme.Green, null, start, 2.5, 2.5); // dónde empieza el trazo
    }
}

/// <summary>Barra horizontal de 0 a 100 (volumen): se arrastra con el ratón. <see cref="UserChanged"/> solo salta cuando la mueve el usuario.</summary>
internal sealed class MiniSlider : FrameworkElement
{
    private double _value;
    private bool _dragging;

    public MiniSlider()
    {
        Height = 20;
        Cursor = Cursors.Hand;
        MinWidth = 60;
    }

    public event Action<int>? UserChanged;

    public double Value
    {
        get => _value;
        set
        {
            if (_dragging) return; // mientras se arrastra, manda el ratón
            _value = Math.Clamp(value, 0, 100);
            InvalidateVisual();
        }
    }

    protected override void OnRender(DrawingContext dc)
    {
        var w = ActualWidth;
        var cy = ActualHeight / 2;
        dc.DrawRoundedRectangle(Theme.Line, null, new Rect(0, cy - 2, w, 4), 2, 2);
        var x = w * _value / 100;
        dc.DrawRoundedRectangle(Theme.Accent, null, new Rect(0, cy - 2, x, 4), 2, 2);
        dc.DrawEllipse(Brushes.White, null, new Point(Math.Clamp(x, 6, w - 6), cy), 6, 6);
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        _dragging = true;
        CaptureMouse();
        Apply(e.GetPosition(this).X);
        e.Handled = true;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (_dragging) Apply(e.GetPosition(this).X);
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        _dragging = false;
        ReleaseMouseCapture();
    }

    private void Apply(double x)
    {
        _value = Math.Clamp(x / Math.Max(1, ActualWidth) * 100, 0, 100);
        InvalidateVisual();
        UserChanged?.Invoke((int)Math.Round(_value));
    }
}

/// <summary>Ventana flotante sin bordes del tema de la v4: tarjeta redondeada con sombra y animación de entrada y salida.</summary>
internal abstract class FloatingWindow : Window
{
    protected const double ShadowMargin = 16;
    protected const double EdgeGap = 12;
    protected const double SlideDistance = 24;

    private readonly TranslateTransform _slide = new();
    private int _animationVersion;
    private (int X, int Y, int W, int H) _placement;

    protected FloatingWindow(double cardWidth)
    {
        WindowStyle = WindowStyle.None;
        AllowsTransparency = true;
        Background = Theme.Transparent;
        ShowInTaskbar = false;
        ResizeMode = ResizeMode.NoResize;
        Topmost = true;
        ShowActivated = false;
        SizeToContent = SizeToContent.Manual;
        Width = cardWidth + 2 * ShadowMargin;
        UseLayoutRounding = true;
        TextOptions.SetTextFormattingMode(this, TextFormattingMode.Display);

        Card = new Border
        {
            Background = Theme.Bg,
            BorderBrush = Theme.Line,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(Theme.CornerRadius),
            Margin = new Thickness(ShadowMargin),
            Effect = new DropShadowEffect { BlurRadius = 18, ShadowDepth = 2, Opacity = 0.55, Color = Colors.Black },
            RenderTransform = _slide,
            ClipToBounds = false,
        };
        Content = Card;

        Handle = new WindowInteropHelper(this).EnsureHandle();
        NativeUi.SetExStyle(Handle, NativeUi.WS_EX_TOOLWINDOW, true); // sin entrada en Alt+Tab
    }

    /// <summary>La tarjeta visible (el resto de la ventana es el margen de la sombra).</summary>
    protected Border Card { get; }

    protected IntPtr Handle { get; }

    /// <summary>¿Está la barra de tareas arriba? Entonces la ventana se ancla arriba y se desliza hacia abajo.</summary>
    protected bool AnchoredTop { get; private set; }

    /// <summary>Esquina que se ocupa (por defecto, la de la bandeja) y alto de la tarjeta más el margen de la sombra, en DIP.</summary>
    public double WindowHeightDip { get; private set; }

    /// <summary>
    /// Mide el contenido y coloca la ventana en la esquina de la bandeja, <paramref name="liftDip"/> por encima del borde
    /// (para ponerla sobre otra ventana). Devuelve el borde superior de la tarjeta en píxeles de pantalla.
    /// </summary>
    protected int Place(double liftDip = 0, double maxHeightDip = double.PositiveInfinity)
    {
        var area = NativeUi.GetTrayArea();
        var s = area.Scale;
        AnchoredTop = area.Edge == NativeUi.TaskbarEdge.Top;

        Card.Measure(new Size(Width - 2 * ShadowMargin, double.PositiveInfinity));
        var height = Math.Min(Card.DesiredSize.Height, maxHeightDip) + 2 * ShadowMargin;
        WindowHeightDip = height;
        Card.VerticalAlignment = AnchoredTop ? VerticalAlignment.Top : VerticalAlignment.Bottom;

        int Px(double dip) => (int)Math.Round(dip * s);
        var w = Px(Width);
        var h = Px(height);
        var gap = Px(EdgeGap);
        var shadow = Px(ShadowMargin);
        var lift = Px(liftDip);
        var x = area.Edge == NativeUi.TaskbarEdge.Left ? area.Work.Left + gap - shadow : area.Work.Right - gap - w + shadow;
        var y = AnchoredTop ? area.Work.Top + gap - shadow + lift : area.Work.Bottom - gap - h + shadow - lift;
        Height = height;
        _placement = (x, y, w, h);
        ApplyPlacement();
        return y + shadow;
    }

    /// <summary>WPF puede recolocar la ventana al mostrarla por primera vez: se vuelve a aplicar la posición calculada.</summary>
    private void ApplyPlacement()
    {
        var (x, y, w, h) = _placement;
        if (w > 0) NativeUi.SetWindowPos(Handle, NativeUi.HWND_TOPMOST, x, y, w, h, NativeUi.SWP_NOACTIVATE);
    }

    /// <summary>Alto máximo de una tarjeta para que la ventana no pase del 85 % del área de trabajo, en DIP.</summary>
    protected static double MaxCardHeightDip()
    {
        var area = NativeUi.GetTrayArea();
        return area.Work.Height / area.Scale * 0.85 - 2 * ShadowMargin;
    }

    /// <summary>Muestra la ventana subiendo 24 px con fundido (220 ms, suavizado cubic out).</summary>
    protected void AnimateIn(bool animate = true)
    {
        ++_animationVersion;
        var direction = AnchoredTop ? -1 : 1;
        Card.BeginAnimation(OpacityProperty, null);
        _slide.BeginAnimation(TranslateTransform.YProperty, null);
        Card.Opacity = animate ? 0 : 1; // sin parpadeo: empieza invisible antes de mostrarse
        _slide.Y = animate ? SlideDistance * direction : 0;
        Show();
        ApplyPlacement();
        if (!animate) return;
        var duration = TimeSpan.FromMilliseconds(220);
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        Card.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, duration) { EasingFunction = ease });
        _slide.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(SlideDistance * direction, 0, duration) { EasingFunction = ease });
    }

    /// <summary>Oculta la ventana bajando con fundido (160 ms). Si se vuelve a mostrar antes de terminar, no se oculta.</summary>
    protected void AnimateOut(Action? completed = null)
    {
        var version = ++_animationVersion;
        var direction = AnchoredTop ? -1 : 1;
        var duration = TimeSpan.FromMilliseconds(160);
        var ease = new CubicEase { EasingMode = EasingMode.EaseIn };
        var fade = new DoubleAnimation(Card.Opacity, 0, duration) { EasingFunction = ease };
        fade.Completed += (_, _) =>
        {
            if (version != _animationVersion) return;
            Hide();
            completed?.Invoke();
        };
        Card.BeginAnimation(OpacityProperty, fade);
        _slide.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(_slide.Y, SlideDistance * direction, duration) { EasingFunction = ease });
    }

    /// <summary>Interrumpe cualquier animación de salida pendiente (la ventana se va a mostrar otra vez).</summary>
    protected void CancelPendingHide() => _animationVersion++;
}

/// <summary>Diálogos modales del tema oscuro: mensajes con botones y petición de un texto.</summary>
internal static class Dialogs
{
    /// <summary>Muestra un mensaje con botones; devuelve el índice del pulsado (-1 si se cierra la ventana).</summary>
    public static int Message(Window? owner, string title, string text, params string[] buttons)
    {
        if (buttons.Length == 0) buttons = ["Aceptar"];
        var result = -1;
        var window = Create(owner, title, 420);
        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(Theme.Label(title, 16, weight: FontWeights.SemiBold));
        var body = Theme.Label(text, 13, Theme.Dim);
        body.TextWrapping = TextWrapping.Wrap;
        body.TextTrimming = TextTrimming.None;
        body.Margin = new Thickness(0, 8, 0, 16);
        panel.Children.Add(body);
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        for (var i = 0; i < buttons.Length; i++)
        {
            var index = i;
            var b = i == 0 ? Theme.AccentButton(buttons[i]) : Theme.FlatButton(buttons[i]);
            b.Margin = new Thickness(8, 0, 0, 0);
            b.Click += (_, _) => { result = index; window.Close(); };
            row.Children.Add(b);
        }
        panel.Children.Add(row);
        window.Content = Wrap(panel);
        window.ShowDialog();
        return result;
    }

    /// <summary>Pide una línea de texto. Null si se cancela.</summary>
    public static string? Prompt(Window? owner, string title, string label, string initial = "")
    {
        string? result = null;
        var window = Create(owner, title, 380);
        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(Theme.Label(title, 16, weight: FontWeights.SemiBold));
        var l = Theme.Label(label, 13, Theme.Dim);
        l.Margin = new Thickness(0, 8, 0, 6);
        panel.Children.Add(l);
        var input = Theme.Input(initial);
        panel.Children.Add(input);
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 16, 0, 0) };
        var ok = Theme.AccentButton("Aceptar");
        var cancel = Theme.FlatButton("Cancelar");
        cancel.Margin = new Thickness(8, 0, 0, 0);
        ok.Click += (_, _) => { result = input.Text; window.Close(); };
        cancel.Click += (_, _) => window.Close();
        input.KeyDown += (_, e) =>
        {
            if (e.Key == Key.Enter) { result = input.Text; window.Close(); }
        };
        row.Children.Add(ok);
        row.Children.Add(cancel);
        panel.Children.Add(row);
        window.Content = Wrap(panel);
        window.Loaded += (_, _) => { input.Focus(); input.SelectAll(); };
        window.ShowDialog();
        return result;
    }

    private static Window Create(Window? owner, string title, double width)
    {
        var window = new Window
        {
            Title = title,
            Width = width,
            SizeToContent = SizeToContent.Height,
            ResizeMode = ResizeMode.NoResize,
            WindowStyle = WindowStyle.ToolWindow,
            ShowInTaskbar = false,
            Background = Theme.Bg,
            FontFamily = Theme.Font,
            WindowStartupLocation = owner is null ? WindowStartupLocation.CenterScreen : WindowStartupLocation.CenterOwner,
            Topmost = true,
        };
        if (owner is { IsLoaded: true }) window.Owner = owner;
        window.KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape) window.Close();
        };
        return window;
    }

    private static UIElement Wrap(UIElement content) => new Border { Background = Theme.Bg, Child = content };
}
