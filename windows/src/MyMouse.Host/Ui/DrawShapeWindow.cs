using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.Host.Ui;

/// <summary>Superficie donde se dibuja con el ratón o el touchpad: muestra las muestras ya hechas, atenuadas, y el trazo en curso.</summary>
internal sealed class InkSurface : FrameworkElement
{
    private readonly List<Point> _current = [];
    private readonly List<IReadOnlyList<Point>> _done = [];

    public InkSurface()
    {
        Width = Height = 300;
        Cursor = Cursors.Pen;
        Focusable = false;
    }

    /// <summary>Un trazo terminado (con al menos 2 puntos).</summary>
    public event Action<IReadOnlyList<Point>>? StrokeDone;

    public void SetDone(IEnumerable<IReadOnlyList<Point>> samples)
    {
        _done.Clear();
        _done.AddRange(samples);
        InvalidateVisual();
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        _current.Clear();
        _current.Add(e.GetPosition(this));
        CaptureMouse();
        InvalidateVisual();
        e.Handled = true;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (!IsMouseCaptured) return;
        _current.Add(e.GetPosition(this));
        InvalidateVisual();
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        if (!IsMouseCaptured) return;
        ReleaseMouseCapture();
        _current.Add(e.GetPosition(this));
        var stroke = _current.ToList();
        _current.Clear();
        InvalidateVisual();
        if (stroke.Count >= 2) StrokeDone?.Invoke(stroke);
    }

    protected override void OnRender(DrawingContext dc)
    {
        dc.DrawRoundedRectangle(Theme.CardBg, new Pen(Theme.Line, 1), new Rect(0.5, 0.5, ActualWidth - 1, ActualHeight - 1), 10, 10);
        var faint = new SolidColorBrush(Color.FromArgb(90, Theme.AccentColor.R, Theme.AccentColor.G, Theme.AccentColor.B));
        foreach (var sample in _done) Draw(dc, sample, new Pen(faint, 4) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round });
        Draw(dc, _current, new Pen(Theme.Accent, 4) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round });
    }

    private static void Draw(DrawingContext dc, IReadOnlyList<Point> points, Pen pen)
    {
        if (points.Count < 2) return;
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(points[0], false, false);
            ctx.PolyLineTo(points.Skip(1).ToList(), true, true);
        }
        geometry.Freeze();
        dc.DrawGeometry(null, pen, geometry);
    }
}

/// <summary>
/// "+ Nueva forma" en el PC (docs/spec-v4-toolbox.md §4.2): se da un nombre y se dibujan 3 muestras con el ratón o el touchpad.
/// Al terminar se envían al móvil (<c>SHAPE_ADD</c>), que comprueba si se confunde con otra y contesta.
/// </summary>
internal sealed class DrawShapeWindow : Window
{
    public const int Samples = 3;
    private const double MinExtent = 60;

    private readonly HostApp _app;
    private readonly TextBox _name = Theme.Input();
    private readonly InkSurface _ink = new();
    private readonly TextBlock _info = Theme.Label("", 13, Theme.Dim);
    private readonly TextBlock _offline = Theme.Label("Conecta el móvil por USB para grabar formas", 12, Theme.Amber);
    private readonly Button _save = Theme.AccentButton("Guardar en el móvil");
    private readonly StackPanel _thumbs = new() { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 8, 0, 0) };
    private readonly List<IReadOnlyList<Point>> _samples = [];
    private bool _busy;

    /// <summary>Nombre completo de la forma guardada en el móvil (<c>custom:firma</c>), o null si se cancela.</summary>
    public string? CreatedName { get; private set; }

    public DrawShapeWindow(HostApp app, Window? owner)
    {
        _app = app;
        Title = "Nueva forma";
        Width = 360;
        SizeToContent = SizeToContent.Height;
        ResizeMode = ResizeMode.NoResize;
        ShowInTaskbar = false;
        Background = Theme.Bg;
        FontFamily = Theme.Font;
        WindowStartupLocation = owner is null ? WindowStartupLocation.CenterScreen : WindowStartupLocation.CenterOwner;
        if (owner is { IsLoaded: true }) Owner = owner;

        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(Theme.Label("Nueva forma", 17, weight: FontWeights.SemiBold));
        var nameLabel = Theme.Label("Nombre (p. ej. firma)", 12, Theme.Dim);
        nameLabel.Margin = new Thickness(0, 12, 0, 4);
        panel.Children.Add(nameLabel);
        panel.Children.Add(_name);
        _info.Margin = new Thickness(0, 12, 0, 8);
        panel.Children.Add(_info);
        panel.Children.Add(_ink);
        panel.Children.Add(_thumbs);

        _offline.Margin = new Thickness(0, 10, 0, 0);
        panel.Children.Add(_offline);

        var again = Theme.FlatButton("Repetir");
        var cancel = Theme.FlatButton("Cancelar");
        again.Margin = new Thickness(0, 0, 8, 0);
        cancel.Margin = new Thickness(8, 0, 0, 0);
        again.Click += (_, _) => Reset();
        cancel.Click += (_, _) => Close();
        _save.Click += async (_, _) => await SaveAsync();
        var buttons = new DockPanel { Margin = new Thickness(0, 14, 0, 0) };
        var right = new StackPanel { Orientation = Orientation.Horizontal };
        right.Children.Add(_save);
        right.Children.Add(cancel);
        DockPanel.SetDock(right, Dock.Right);
        buttons.Children.Add(right);
        buttons.Children.Add(new StackPanel { Orientation = Orientation.Horizontal, Children = { again } });
        panel.Children.Add(buttons);
        Content = new Border { Background = Theme.Bg, Child = panel };

        _ink.StrokeDone += OnStroke;
        _name.TextChanged += (_, _) => UpdateState();
        _app.Shapes.Changed += OnShapesChanged;
        Closed += (_, _) => _app.Shapes.Changed -= OnShapesChanged;
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape && !_busy) Close();
        };
        Loaded += (_, _) => _name.Focus();
        Reset();
    }

    private void OnShapesChanged() => Dispatcher.BeginInvoke(UpdateState);

    private void Reset()
    {
        _samples.Clear();
        _ink.SetDone(_samples);
        RenderThumbs();
        UpdateState();
    }

    private void OnStroke(IReadOnlyList<Point> stroke)
    {
        if (_busy || _samples.Count >= Samples) return;
        if (stroke.Max(p => p.X) - stroke.Min(p => p.X) < MinExtent && stroke.Max(p => p.Y) - stroke.Min(p => p.Y) < MinExtent)
        {
            _info.Text = $"Demasiado pequeña: dibújala más grande (muestra {_samples.Count + 1} de {Samples}).";
            return;
        }
        _samples.Add(stroke);
        _ink.SetDone(_samples);
        RenderThumbs();
        UpdateState();
    }

    private void RenderThumbs()
    {
        _thumbs.Children.Clear();
        foreach (var sample in _samples)
        {
            var points = sample.Select(p => new PointF((float)p.X, (float)p.Y)).ToList();
            _thumbs.Children.Add(new ShapeThumb(points, 44) { Margin = new Thickness(0, 0, 8, 0), Stroke = Theme.Dim });
        }
    }

    private void UpdateState()
    {
        var canEdit = _app.Shapes.CanEdit;
        _offline.Visibility = canEdit ? Visibility.Collapsed : Visibility.Visible;
        _ink.IsEnabled = !_busy;
        if (_busy) return;
        if (_samples.Count < Samples)
            _info.Text = $"Dibújala con el ratón o el touchpad, de un solo trazo (muestra {_samples.Count + 1} de {Samples}).";
        else
            _info.Text = "Lista. Puedes repetirla o guardarla en el móvil.";
        _save.IsEnabled = canEdit && _samples.Count == Samples && ShapeNames.Normalize(_name.Text) is not null;
    }

    private async Task SaveAsync()
    {
        var name = ShapeNames.Normalize(_name.Text);
        if (name is null)
        {
            _info.Text = "Hace falta un nombre (hasta unos 55 caracteres).";
            return;
        }
        var wire = _samples
            .Select(s => (IReadOnlyList<StrokePoint>)ShapeGeometry.ToWire([.. s.Select(p => new PointF((float)p.X, (float)p.Y))]))
            .ToList();

        _busy = true;
        _save.IsEnabled = false;
        _info.Text = "Enviando al móvil…";
        try
        {
            var force = false;
            while (true)
            {
                var result = await _app.Shapes.AddAsync(name, wire, force);
                switch (result?.Status)
                {
                    case ShapeStatus.Saved:
                        CreatedName = name;
                        Close();
                        return;
                    case ShapeStatus.Clashes:
                        var other = ShapeCatalog.Label(result.Other);
                        var choice = Dialogs.Message(this, "Se confunde con otra forma",
                            $"Tu forma se parece a «{other}»: el móvil podría reconocer una por la otra. ¿La guardas igualmente?",
                            "Guardar igualmente", "Repetirla");
                        if (choice == 0)
                        {
                            force = true;
                            continue;
                        }
                        Reset();
                        return;
                    case ShapeStatus.DuplicateName:
                        _info.Text = "Ya hay una forma con ese nombre: elige otro.";
                        return;
                    case ShapeStatus.Invalid:
                        _info.Text = "El móvil no la acepta: dibújala más grande o con otro nombre.";
                        return;
                    default:
                        _info.Text = "El móvil no ha contestado. ¿Sigue conectado por USB?";
                        return;
                }
            }
        }
        finally
        {
            _busy = false;
            UpdateState();
        }
    }
}
