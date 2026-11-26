using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Live;
using MyMouse.HostCore.Shapes;

namespace MyMouse.Host.Ui;

/// <summary>Lienzo del gesto en vivo: dibuja el trazo que llega del móvil, encajado de forma suave, y lo colorea al terminar.</summary>
internal sealed class StrokeCanvas : FrameworkElement
{
    private const double Padding = 28;

    private static readonly Brush Glow = MakeGlow();

    private static Brush MakeGlow()
    {
        var brush = new RadialGradientBrush { Center = new Point(0.5, 0.35), GradientOrigin = new Point(0.5, 0.35), RadiusX = 0.8, RadiusY = 0.8 };
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x1F, 255, 255, 255), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x05, 255, 255, 255), 1));
        brush.Freeze();
        return brush;
    }
    private readonly LiveStrokeModel _model;
    private StrokeFit _fit;
    private int _generation = -1;

    /// <summary>Puntos de la forma ideal para superponerla tenue al reconocer una (null = ninguna).</summary>
    public Func<string, IReadOnlyList<PointF>?> IdealShape { get; set; } = _ => null;

    public StrokeCanvas(LiveStrokeModel model)
    {
        _model = model;
        IsHitTestVisible = false;
    }

    /// <summary>Un paso de la animación: el encaje se acerca al objetivo (y salta a él si empieza un trazo nuevo).</summary>
    public void Advance()
    {
        var target = LiveStrokeModel.ComputeFit(_model.Points, (float)ActualWidth, (float)ActualHeight, (float)Padding);
        if (_generation != _model.Generation)
        {
            _generation = _model.Generation;
            _fit = target;
        }
        else
        {
            const float k = 0.22f;
            _fit = new StrokeFit(
                _fit.Scale + (target.Scale - _fit.Scale) * k,
                _fit.OffsetX + (target.OffsetX - _fit.OffsetX) * k,
                _fit.OffsetY + (target.OffsetY - _fit.OffsetY) * k);
        }
        InvalidateVisual();
    }

    private Brush StrokeBrush() => _model.Phase == LivePhase.Finished
        ? _model.Result.Kind switch
        {
            LiveResultKind.Executed => Theme.Green,
            LiveResultKind.NoAction => Theme.Amber,
            LiveResultKind.Failed => Theme.Red,
            LiveResultKind.NotRecognized or LiveResultKind.Cancelled => Theme.Gray,
            _ => Theme.Accent,
        }
        : Theme.Accent;

    protected override void OnRender(DrawingContext dc)
    {
        var w = ActualWidth;
        var h = ActualHeight;
        dc.DrawRectangle(Theme.Transparent, null, new Rect(0, 0, w, h));
        // Un ligero brillo en el lienzo.
        dc.DrawRoundedRectangle(Glow, new Pen(Theme.Line, 1), new Rect(12.5, 4.5, Math.Max(0, w - 25), Math.Max(0, h - 9)), Theme.CornerRadius, Theme.CornerRadius);

        if (_model.Phase == LivePhase.Waiting)
        {
            DrawCentered(dc, "Dibuja una forma…", 14, Theme.Dim, w, h);
            return;
        }

        var brush = StrokeBrush();
        if (_model.Phase == LivePhase.Finished && _model.Result.Shape is { } shape &&
            _model.Result.Kind is LiveResultKind.Executed or LiveResultKind.NoAction &&
            IdealShape(shape) is { } ideal &&
            ShapeThumb.Fit(ideal, w, h, Padding + 10) is { } idealGeometry)
        {
            // La forma ideal, tenue, debajo del trazo.
            var faint = new SolidColorBrush(Color.FromArgb(46, 255, 255, 255));
            dc.DrawGeometry(null, new Pen(faint, 10) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round }, idealGeometry);
        }

        var points = _model.Points;
        if (points.Count == 0) return;
        Point Map(PointF p) => new(p.X * _fit.Scale + _fit.OffsetX, p.Y * _fit.Scale + _fit.OffsetY);
        var pen = new Pen(brush, 4) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round };
        if (points.Count >= 2)
        {
            var geometry = new StreamGeometry();
            using (var ctx = geometry.Open())
            {
                ctx.BeginFigure(Map(points[0]), false, false);
                ctx.PolyLineTo(points.Skip(1).Select(Map).ToList(), true, true);
            }
            geometry.Freeze();
            dc.DrawGeometry(null, pen, geometry);
        }
        if (_model.Phase == LivePhase.Drawing) dc.DrawEllipse(brush, null, Map(points[^1]), 6, 6); // la cabeza del trazo
    }

    private void DrawCentered(DrawingContext dc, string text, double size, Brush brush, double w, double h)
    {
        var formatted = new FormattedText(text, CultureInfo.CurrentUICulture, FlowDirection.LeftToRight,
            new Typeface(Theme.Font, FontStyles.Normal, FontWeights.Normal, FontStretches.Normal), size, brush,
            VisualTreeHelper.GetDpi(this).PixelsPerDip);
        dc.DrawText(formatted, new Point((w - formatted.Width) / 2, (h - formatted.Height) / 2));
    }
}

/// <summary>
/// La tarjeta del gesto en vivo (docs/spec-v4-toolbox.md §3): 260 × 300, siempre encima, sin foco y transparente para el ratón
/// (salvo el botón "Asignar", cuando lo hay). Sube desde la esquina de la bandeja al activarse el modo gestos y baja al terminar.
/// </summary>
internal sealed class LiveGestureWindow : FloatingWindow
{
    private const double CardWidth = 260;
    private const double CardHeight = 300;

    private readonly LiveStrokeModel _model;
    private readonly Func<double> _liftDip;
    private readonly Action<string> _assign;
    private readonly StrokeCanvas _canvas;
    private readonly Border _countdown;
    private readonly TextBlock _headline;
    private readonly Border _chip;
    private readonly TextBlock _detail;
    private readonly Button _assignButton;
    private readonly DispatcherTimer _timer;
    private int _ticks;

    /// <param name="model">Estado del gesto en vivo.</param>
    /// <param name="liftDip">Cuánto subirla desde la esquina (para ponerla sobre la toolbox si está abierta).</param>
    /// <param name="idealShape">Puntos de la forma ideal de un nombre, para superponerla tenue.</param>
    /// <param name="assign">Se pulsa "Asignar" en una forma sin acción: abre el editor con esa forma.</param>
    public LiveGestureWindow(LiveStrokeModel model, Func<double> liftDip, Func<string, IReadOnlyList<PointF>?> idealShape, Action<string> assign, bool glass)
        : base(CardWidth, glass)
    {
        _model = model;
        _liftDip = liftDip;
        _assign = assign;
        Focusable = false;
        // Sin foco, siempre encima, fuera de Alt+Tab y transparente para el ratón (se quita solo con el botón Asignar).
        NativeUi.SetExStyle(Handle, NativeUi.WS_EX_NOACTIVATE | NativeUi.WS_EX_TRANSPARENT | NativeUi.WS_EX_TOPMOST, true);

        Card.Width = CardWidth;
        Card.Height = CardHeight;
        _canvas = new StrokeCanvas(model) { IdealShape = idealShape };
        _countdown = new Border { Height = 3, Background = Theme.Accent, HorizontalAlignment = HorizontalAlignment.Left, CornerRadius = new CornerRadius(1.5) };
        _headline = Theme.Label("", 16, weight: FontWeights.SemiBold);
        _headline.TextWrapping = TextWrapping.Wrap;
        _headline.TextTrimming = TextTrimming.None;
        _headline.FontFamily = Theme.DisplayFont;
        // El resultado va en un chip de color (el color del resultado al 22 % de opacidad).
        _chip = new Border { CornerRadius = new CornerRadius(Theme.RowRadius), Padding = new Thickness(10, 3, 10, 4), HorizontalAlignment = HorizontalAlignment.Left, Child = _headline };
        _detail = Theme.Label("", 12, Theme.Dim);
        _detail.TextWrapping = TextWrapping.Wrap;
        _detail.Margin = new Thickness(2, 6, 0, 0);
        _assignButton = Theme.AccentButton("Asignar");
        _assignButton.HorizontalAlignment = HorizontalAlignment.Left;
        _assignButton.Margin = new Thickness(0, 6, 0, 0);
        _assignButton.Visibility = Visibility.Collapsed;
        _assignButton.Click += (_, _) =>
        {
            if (_model.Result.Shape is { } shape) _assign(shape);
            _model.Hide();
        };

        var result = new StackPanel { Margin = new Thickness(16, 6, 16, 12), VerticalAlignment = VerticalAlignment.Top };
        result.Children.Add(_chip);
        result.Children.Add(_detail);
        result.Children.Add(_assignButton);

        var grid = new Grid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(104) });
        _countdown.Margin = new Thickness(14, 12, 14, 0);
        Grid.SetRow(_countdown, 0);
        Grid.SetRow(_canvas, 1);
        Grid.SetRow(result, 2);
        grid.Children.Add(_countdown);
        grid.Children.Add(_canvas);
        grid.Children.Add(result);
        Card.Child = grid;

        _timer = new DispatcherTimer(DispatcherPriority.Render) { Interval = TimeSpan.FromMilliseconds(16) };
        _timer.Tick += OnTick;
        _model.Changed += OnModelChanged;
    }

    /// <summary>Quita la ventana de verdad (se vuelve a crear al cambiar el "Efecto cristal").</summary>
    public void Destroy()
    {
        _model.Changed -= OnModelChanged;
        _timer.Stop();
        Close();
    }

    private void OnModelChanged()
    {
        if (_model.Visible)
        {
            if (!IsVisible)
            {
                CancelPendingHide();
                Place(_liftDip());
                AnimateIn();
                _timer.Start();
            }
            else if (_closing)
            {
                // Reutilizada mientras bajaba: vuelve a subir.
                _closing = false;
                CancelPendingHide();
                Place(_liftDip());
                AnimateIn();
                _timer.Start();
            }
            Update();
        }
        else if (IsVisible && !_closing)
        {
            _closing = true;
            _timer.Stop();
            AnimateOut(() => _closing = false);
        }
    }

    private bool _closing;

    private void OnTick(object? sender, EventArgs e)
    {
        _canvas.Advance();
        if (_model.Phase == LivePhase.Waiting)
            _countdown.Width = Math.Max(0, (CardWidth - 28) * _model.WaitRemaining());
        if (++_ticks % 6 == 0) _model.Tick();
    }

    private void Update()
    {
        var clickable = _model.CanAssign;
        NativeUi.SetExStyle(Handle, NativeUi.WS_EX_TRANSPARENT, !clickable);
        _assignButton.Visibility = clickable ? Visibility.Visible : Visibility.Collapsed;
        _countdown.Visibility = _model.Phase == LivePhase.Waiting ? Visibility.Visible : Visibility.Hidden;

        var r = _model.Result;
        (string head, string detail, Brush color) = _model.Phase switch
        {
            LivePhase.Waiting => ("Modo gestos", "Dibuja una forma con un dedo en el trackpad del móvil", Theme.Text),
            LivePhase.Drawing => ("…", "", Theme.Dim),
            LivePhase.Finished => r.Kind switch
            {
                LiveResultKind.Executed => (r.Help ?? ShapeCatalog.Label(r.Shape ?? ""), r.Action ?? "", Theme.Green),
                LiveResultKind.NoAction => ($"Forma «{ShapeCatalog.Label(r.Shape ?? "")}»: sin acción", "", Theme.Amber),
                LiveResultKind.NotRecognized => ("?", "No se ha reconocido la forma", Theme.Gray),
                LiveResultKind.Cancelled => ("Cancelado", "", Theme.Gray),
                LiveResultKind.Failed => ("No se ha podido ejecutar", r.Error ?? "", Theme.Red),
                _ => ("…", "", Theme.Dim),
            },
            _ => ("", "", Theme.Text),
        };
        _headline.Text = head;
        _headline.Foreground = color;
        _chip.Background = color is SolidColorBrush solid && !ReferenceEquals(color, Theme.Text) && !ReferenceEquals(color, Theme.Dim)
            ? new SolidColorBrush(Color.FromArgb(0x38, solid.Color.R, solid.Color.G, solid.Color.B))
            : Theme.Transparent;
        _chip.Visibility = head.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        _headline.FontSize = r.Kind == LiveResultKind.NotRecognized && _model.Phase == LivePhase.Finished ? 28 : 16;
        _detail.Text = detail;
        _detail.Visibility = detail.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
    }
}
