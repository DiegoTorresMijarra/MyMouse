using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Shapes;
using MyMouse.HostCore.Toolbox;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;
using Path = System.IO.Path;

namespace MyMouse.Host.Ui;

/// <summary>Una tarjeta de la toolbox: su vista y cómo actualizarla cuando cambia el estado.</summary>
internal interface IToolboxCard
{
    FrameworkElement View { get; }

    /// <summary>Actualiza la vista con el estado actual. Devuelve true si ha cambiado de alto (hay que recolocar la ventana).</summary>
    bool Refresh();
}

/// <summary>Un menú pequeño del tema oscuro que se abre sobre un control (el menú de Ajustes).</summary>
internal static class MiniMenu
{
    public static void Show(UIElement target, IEnumerable<(string Text, bool? Checked, Action Click)> items)
    {
        var panel = new StackPanel { Margin = new Thickness(4) };
        var popup = new Popup
        {
            PlacementTarget = target,
            Placement = PlacementMode.Top,
            StaysOpen = false,
            AllowsTransparency = true,
            IsOpen = false,
            Child = new Border
            {
                Background = Theme.CardBg,
                BorderBrush = Theme.Line,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Margin = new Thickness(0, 0, 0, 6),
                Child = panel,
            },
        };
        foreach (var (text, isChecked, click) in items)
        {
            var label = (isChecked is null ? "" : isChecked == true ? "✓  " : "     ") + text;
            var button = Theme.FlatButton(label, Theme.CardBg);
            button.HorizontalContentAlignment = HorizontalAlignment.Left;
            button.Click += (_, _) =>
            {
                popup.IsOpen = false;
                click();
            };
            panel.Children.Add(button);
        }
        popup.IsOpen = true;
    }
}

/// <summary>
/// La toolbox: panel flotante de 400 px anclado sobre la bandeja, con las tarjetas de <see cref="ToolboxLayout"/>
/// (docs/spec-v4-toolbox.md §2). Se abre con un clic en el icono o con 4 dedos ↑ en el móvil (sin robar el foco) y se cierra con
/// Esc, un clic fuera, el icono o 4 dedos ↓.
/// </summary>
internal sealed class ToolboxWindow : FloatingWindow
{
    private const double PanelWidth = 400;

    private readonly HostApp _app;
    private readonly StackPanel _stack = new() { Margin = new Thickness(0, 8, 0, 0) };
    private readonly ScrollViewer _scroll;
    private List<IToolboxCard> _cards = [];
    private bool _noActivate;
    private DateTime _closedByFocusLossAt = DateTime.MinValue;

    public ToolboxWindow(HostApp app)
        : base(PanelWidth)
    {
        _app = app;
        Card.Width = PanelWidth;
        _scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _stack,
            Focusable = false,
        };
        Card.Child = _scroll;
        Card.Padding = new Thickness(0, 0, 0, 2);

        PreviewKeyDown += (_, e) =>
        {
            if (e.Key != Key.Escape) return;
            e.Handled = true;
            Close();
        };
        PreviewMouseDown += (_, _) =>
        {
            // Abierta desde el móvil no tenía el foco: al hacer clic dentro se activa y empieza a comportarse como una ventana normal.
            if (_noActivate) SetNoActivate(false);
        };
        Deactivated += (_, _) =>
        {
            if (!IsVisible || _noActivate || _app.EditorOpen) return;
            _closedByFocusLossAt = DateTime.UtcNow;
            Close();
        };
        _app.StateChanged += () =>
        {
            if (IsVisible) RefreshCards();
        };
    }

    public bool IsOpen => IsVisible;

    /// <summary>Alto de la tarjeta visible (sin la sombra), para colocar encima el gesto en vivo.</summary>
    public double CardHeightDip => Math.Max(0, WindowHeightDip - 2 * ShadowMargin);

    /// <summary>
    /// Abre la toolbox. <paramref name="fromPhone"/>: sin activarla, para no quitarle el foco a la app en la que se trabaja.
    /// </summary>
    public void Open(bool fromPhone)
    {
        if (IsVisible) return;
        Build();
        CancelPendingHide();
        SetNoActivate(fromPhone);
        PlaceNow();
        AnimateIn();
        if (!fromPhone) Activate();
        _app.RaiseToolboxChanged();
    }

    /// <summary>Cierra la toolbox con la animación de salida.</summary>
    public new void Close()
    {
        if (!IsVisible) return;
        AnimateOut(_app.RaiseToolboxChanged);
    }

    /// <summary>Clic en el icono de la bandeja: abre, o cierra si ya estaba abierta (también si el propio clic le quitó el foco).</summary>
    public void Toggle()
    {
        if (IsVisible)
        {
            Close();
        }
        else if (DateTime.UtcNow - _closedByFocusLossAt > TimeSpan.FromMilliseconds(300))
        {
            Open(fromPhone: false);
        }
        // Si se cerró hace un instante por perder el foco, el clic en el icono era para cerrarla: no se vuelve a abrir.
    }

    private void SetNoActivate(bool on)
    {
        _noActivate = on;
        NativeUi.SetExStyle(Handle, NativeUi.WS_EX_NOACTIVATE, on);
        if (!on && IsVisible) Activate();
    }

    private void Build()
    {
        _stack.Children.Clear();
        _cards = [];
        foreach (var kind in _app.Layout.Visible)
        {
            IToolboxCard card = kind switch
            {
                ToolboxCard.Options => new OptionsCard(_app),
                ToolboxCard.Music => new MusicCard(_app),
                ToolboxCard.Gestures => new GesturesCard(_app),
                _ => new WidgetsCard(_app),
            };
            card.Refresh();
            _cards.Add(card);
            _stack.Children.Add(card.View);
        }
    }

    private void PlaceNow()
    {
        var max = MaxCardHeightDip();
        _scroll.MaxHeight = max;
        Place(maxHeightDip: max);
    }

    private void RefreshCards()
    {
        var resized = false;
        foreach (var card in _cards) resized |= card.Refresh();
        if (resized) PlaceNow();
    }

    /// <summary>Se cambió la distribución en "Personalizar tarjetas": se reconstruye con la ventana abierta.</summary>
    public void Rebuild()
    {
        if (!IsVisible) return;
        Build();
        PlaceNow();
    }
}

internal static class CardChrome
{
    public static Border Wrap(UIElement content) => new()
    {
        Background = Theme.CardBg,
        CornerRadius = new CornerRadius(10),
        Padding = new Thickness(12),
        Margin = new Thickness(8, 0, 8, 8),
        Child = content,
    };

    public static TextBlock Title(string text) => Theme.Label(text, 11, Theme.Dim, FontWeights.SemiBold);

    /// <summary>Una fila pulsable (fondo que se ilumina al pasar el ratón) con un contenido.</summary>
    public static Button Row(UIElement content, Action click)
    {
        var button = Theme.FlatButton(content, Theme.CardBg, padding: new Thickness(6, 5, 6, 5));
        button.HorizontalContentAlignment = HorizontalAlignment.Stretch;
        button.Click += (_, _) => click();
        return button;
    }
}

/// <summary>Cabecera con el estado y los botones de siempre. No se puede quitar.</summary>
internal sealed class OptionsCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly Ellipse _dot = new() { Width = 9, Height = 9, VerticalAlignment = VerticalAlignment.Center };
    private readonly TextBlock _status = Theme.Label("", 12, Theme.Dim);
    private readonly Button _pause = Theme.FlatButton("Pausar");

    public OptionsCard(HostApp app)
    {
        _app = app;
        var logo = new Grid { Width = 26, Height = 32, Margin = new Thickness(2, 0, 12, 0) };
        logo.Children.Add(new Border
        {
            BorderBrush = Theme.Text,
            BorderThickness = new Thickness(2),
            CornerRadius = new CornerRadius(10),
            Background = Theme.Transparent,
        });
        logo.Children.Add(new Border { Width = 2, Height = 8, Background = Theme.Accent, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 5, 0, 0) });

        var statusRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 0) };
        _dot.Margin = new Thickness(0, 0, 6, 0);
        statusRow.Children.Add(_dot);
        statusRow.Children.Add(_status);
        var titles = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        titles.Children.Add(Theme.Label("MyMouse", 17, weight: FontWeights.SemiBold));
        titles.Children.Add(statusRow);
        var header = new StackPanel { Orientation = Orientation.Horizontal };
        header.Children.Add(logo);
        header.Children.Add(titles);

        _pause.Click += (_, _) => _app.TogglePause();
        var editor = Theme.FlatButton("Gestos y widgets");
        editor.Click += (_, _) => _app.OpenEditor(EditorTab.Gestures);
        var help = Theme.FlatButton("Ayuda");
        help.Click += (_, _) => _app.ShowHelp();
        var settings = Theme.FlatButton("Ajustes");
        settings.Click += (_, _) => MiniMenu.Show(settings, SettingsItems());
        var quit = Theme.FlatButton("Salir");
        quit.Click += (_, _) => _app.Quit();

        var buttons = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
        foreach (var b in new[] { _pause, editor, help, settings, quit })
        {
            b.Margin = new Thickness(0, 0, 6, 6);
            buttons.Children.Add(b);
        }

        var stack = new StackPanel();
        stack.Children.Add(header);
        stack.Children.Add(buttons);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    private IEnumerable<(string, bool?, Action)> SettingsItems() =>
    [
        ("Iniciar con Windows", _app.AutostartEnabled, () => _app.AutostartEnabled = !_app.AutostartEnabled),
        ("Mostrar el gesto en vivo", _app.Layout.ShowLiveGesture, () => _app.Layout = _app.Layout.WithLiveGesture(!_app.Layout.ShowLiveGesture)),
        ("Personalizar tarjetas…", null, _app.CustomizeCards),
        ("Abrir la carpeta de logs", null, _app.OpenLogs),
    ];

    public bool Refresh()
    {
        _dot.Fill = _app.StatusBrush;
        _status.Text = _app.StatusText;
        _pause.Content = _app.Paused ? "Reanudar" : "Pausar";
        return false;
    }
}

/// <summary>Lo que suena (carátula, título, artista, controles) y el volumen del sistema.</summary>
internal sealed class MusicCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly Border _cover;
    private readonly TextBlock _glyph = Theme.Label("♪", 22, Theme.Dim);
    private readonly TextBlock _title = Theme.Label("", 14, weight: FontWeights.SemiBold);
    private readonly TextBlock _artist = Theme.Label("", 12, Theme.Dim);
    private readonly Button _play = Theme.FlatButton("⏯", fontSize: 16);
    private readonly Button _mute = Theme.FlatButton("🔊", fontSize: 14);
    private readonly TextBlock _percent = Theme.Label("", 12, Theme.Dim);
    private readonly MiniSlider _slider = new();
    private byte[]? _shownCover;

    public MusicCard(HostApp app)
    {
        _app = app;
        _cover = new Border
        {
            Width = 60,
            Height = 60,
            CornerRadius = new CornerRadius(8),
            Background = Theme.Hover,
            Margin = new Thickness(0, 0, 12, 0),
            Child = new Grid { Children = { _glyph } },
        };
        _glyph.HorizontalAlignment = HorizontalAlignment.Center;
        _glyph.VerticalAlignment = VerticalAlignment.Center;

        var previous = Theme.FlatButton("⏮", fontSize: 16);
        var next = Theme.FlatButton("⏭", fontSize: 16);
        previous.Click += (_, _) => _app.SendMedia(MediaCommand.Previous);
        _play.Click += (_, _) => _app.SendMedia(MediaCommand.PlayPause);
        next.Click += (_, _) => _app.SendMedia(MediaCommand.Next);
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
        foreach (var b in new[] { previous, _play, next })
        {
            b.Margin = new Thickness(0, 0, 6, 0);
            b.MinWidth = 40;
            controls.Children.Add(b);
        }

        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        texts.Children.Add(_title);
        texts.Children.Add(_artist);
        texts.Children.Add(controls);
        var top = new DockPanel();
        DockPanel.SetDock(_cover, Dock.Left);
        top.Children.Add(_cover);
        top.Children.Add(texts);

        _mute.Click += (_, _) => _app.ToggleMute();
        _mute.Width = 40;
        _slider.UserChanged += level => _app.SetVolume(level);
        _percent.Width = 38;
        _percent.TextAlignment = TextAlignment.Right;
        var volume = new DockPanel { Margin = new Thickness(0, 10, 0, 0) };
        DockPanel.SetDock(_mute, Dock.Left);
        DockPanel.SetDock(_percent, Dock.Right);
        volume.Children.Add(_mute);
        volume.Children.Add(_percent);
        _slider.Margin = new Thickness(8, 0, 8, 0);
        volume.Children.Add(_slider);

        var stack = new StackPanel();
        stack.Children.Add(CardChrome.Title("MÚSICA"));
        stack.Children.Add(new Border { Height = 8 });
        stack.Children.Add(top);
        stack.Children.Add(volume);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public bool Refresh()
    {
        var media = _app.Media.Current();
        var nothing = media.Title.Length == 0 && !media.Playing;
        _title.Text = nothing ? "Nada en reproducción" : media.Title;
        _title.Foreground = nothing ? Theme.Dim : Theme.Text;
        _artist.Text = media.Artist;
        _artist.Visibility = media.Artist.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        _play.IsEnabled = !nothing;

        var cover = nothing ? null : _app.Media.CurrentThumbnail;
        if (!ReferenceEquals(cover, _shownCover))
        {
            _shownCover = cover;
            _cover.Background = Theme.Hover;
            _glyph.Visibility = Visibility.Visible;
            if (cover is not null)
            {
                try
                {
                    var image = new BitmapImage();
                    image.BeginInit();
                    image.CacheOption = BitmapCacheOption.OnLoad;
                    image.StreamSource = new MemoryStream(cover);
                    image.EndInit();
                    image.Freeze();
                    _cover.Background = new ImageBrush(image) { Stretch = Stretch.UniformToFill };
                    _glyph.Visibility = Visibility.Collapsed;
                }
                catch (Exception ex) when (ex is NotSupportedException or InvalidOperationException or IOException)
                {
                    FileLog.Write($"Carátula no válida: {ex.Message}");
                }
            }
        }

        var volume = _app.Volume.Get();
        _slider.Value = volume.Level;
        _percent.Text = volume.Muted ? "mute" : $"{volume.Level} %";
        _mute.Content = volume.Muted || volume.Level == 0 ? "🔇" : "🔊";
        return false;
    }
}

/// <summary>Formas con acción (miniatura, ayuda y chincheta), "+ Nueva forma" y las formas sin acción al final, atenuadas.</summary>
internal sealed class GesturesCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly StackPanel _rows = new();
    private string _signature = "";

    public GesturesCard(HostApp app)
    {
        _app = app;
        var add = Theme.AccentButton("+ Nueva forma");
        add.Padding = new Thickness(10, 3, 10, 3);
        add.FontSize = 12;
        add.Click += (_, _) => _app.OpenEditor(EditorTab.Gestures, newShape: true);
        var header = new DockPanel();
        DockPanel.SetDock(add, Dock.Right);
        header.Children.Add(add);
        header.Children.Add(new Border { Child = CardChrome.Title("GESTOS"), VerticalAlignment = VerticalAlignment.Center });

        var stack = new StackPanel();
        stack.Children.Add(header);
        _rows.Margin = new Thickness(0, 8, 0, 0);
        stack.Children.Add(_rows);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public bool Refresh()
    {
        var config = _app.Config;
        var withAction = config.Bindings.Select(b => b.Gesture).Distinct(StringComparer.Ordinal).ToList();
        var customWithout = _app.Shapes.Shapes.Select(s => s.Name).Where(n => !config.HasAction(n)).ToList();
        var catalogWithout = ShapeCatalog.All.Count(s => !config.HasAction(s.Id));
        var signature = string.Join("|", withAction.Select(g => g + ":" + HelpFor(config, g))) + "#" + string.Join("|", customWithout) + "#" + catalogWithout;
        if (signature == _signature) return false;
        _signature = signature;

        _rows.Children.Clear();
        foreach (var gesture in withAction) _rows.Children.Add(BuildRow(config, gesture));
        foreach (var name in customWithout) _rows.Children.Add(BuildUnassignedRow(name));
        if (catalogWithout > 0)
        {
            var more = CardChrome.Row(
                Theme.Label($"{catalogWithout} formas más sin acción · Asignar…", 12, Theme.Dim),
                () => _app.OpenEditor(EditorTab.Gestures, onlyUnassigned: true));
            _rows.Children.Add(more);
        }
        if (_rows.Children.Count == 0) _rows.Children.Add(Theme.Label("Todavía no hay formas.", 12, Theme.Dim));
        return true;
    }

    private static string HelpFor(GestureConfig config, string gesture)
    {
        var (general, app) = config.RulesOf(gesture);
        return (general ?? app.FirstOrDefault())?.HelpText ?? "";
    }

    private UIElement BuildRow(GestureConfig config, string gesture)
    {
        var (general, appRules) = config.RulesOf(gesture);
        var binding = general ?? appRules.FirstOrDefault();
        var help = binding?.HelpText ?? gesture;
        var detail = ActionText.Summary(binding?.Action) + (general is null ? " (solo en algunas apps)" : "");
        var pinned = config.Bindings.Any(b => b.Gesture == gesture && b.Pinned);

        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0) };
        texts.Children.Add(Theme.Label(help, 13));
        texts.Children.Add(Theme.Label(detail, 11, Theme.Dim));
        var row = new DockPanel();
        var thumb = new ShapeThumb(_app.ShapePoints(gesture), 34) { VerticalAlignment = VerticalAlignment.Center };
        DockPanel.SetDock(thumb, Dock.Left);
        row.Children.Add(thumb);
        if (pinned)
        {
            var pin = Theme.Label("📌", 12, Theme.Dim);
            pin.VerticalAlignment = VerticalAlignment.Center;
            DockPanel.SetDock(pin, Dock.Right);
            row.Children.Add(pin);
        }
        row.Children.Add(texts);
        return CardChrome.Row(row, () => _app.OpenEditor(EditorTab.Gestures, gesture: gesture));
    }

    private UIElement BuildUnassignedRow(string name)
    {
        var row = new DockPanel { Opacity = 0.6 };
        var thumb = new ShapeThumb(_app.ShapePoints(name), 34) { VerticalAlignment = VerticalAlignment.Center, Stroke = Theme.Dim };
        DockPanel.SetDock(thumb, Dock.Left);
        row.Children.Add(thumb);
        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0) };
        texts.Children.Add(Theme.Label(ShapeCatalog.Label(name), 13));
        texts.Children.Add(Theme.Label("Sin acción · Asignar", 11, Theme.Amber));
        row.Children.Add(texts);
        return CardChrome.Row(row, () => _app.OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));
    }
}

/// <summary>Los widgets del panel del móvil, en orden, con "+ Añadir". Oculta por defecto.</summary>
internal sealed class WidgetsCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly StackPanel _rows = new();
    private string _signature = "";

    public WidgetsCard(HostApp app)
    {
        _app = app;
        var add = Theme.AccentButton("+ Añadir");
        add.Padding = new Thickness(10, 3, 10, 3);
        add.FontSize = 12;
        add.Click += (_, _) => _app.OpenEditor(EditorTab.Widgets, newWidget: true);
        var header = new DockPanel();
        DockPanel.SetDock(add, Dock.Right);
        header.Children.Add(add);
        header.Children.Add(new Border { Child = CardChrome.Title("WIDGETS DEL MÓVIL"), VerticalAlignment = VerticalAlignment.Center });
        var stack = new StackPanel();
        stack.Children.Add(header);
        _rows.Margin = new Thickness(0, 8, 0, 0);
        stack.Children.Add(_rows);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public bool Refresh()
    {
        var widgets = _app.Config.Widgets;
        var signature = string.Join("|", widgets.Select(Describe));
        if (signature == _signature) return false;
        _signature = signature;

        _rows.Children.Clear();
        for (var i = 0; i < widgets.Count; i++)
        {
            var index = i;
            var (icon, label) = Parts(widgets[i]);
            var row = new StackPanel { Orientation = Orientation.Horizontal };
            var glyph = Theme.Label(icon, 16);
            glyph.Width = 30;
            row.Children.Add(glyph);
            row.Children.Add(Theme.Label(label, 13));
            _rows.Children.Add(CardChrome.Row(row, () => _app.OpenEditor(EditorTab.Widgets, widgetIndex: index)));
        }
        if (widgets.Count == 0) _rows.Children.Add(Theme.Label("No hay widgets: el móvil no muestra panel.", 12, Theme.Dim));
        return true;
    }

    private static string Describe(WidgetDefinition w) => w switch
    {
        ButtonWidget b => $"b:{b.Icon}:{b.Label}",
        MediaWidget => "media",
        VolumeWidget => "volume",
        _ => "?",
    };

    public static (string Icon, string Label) Parts(WidgetDefinition w) => w switch
    {
        ButtonWidget b => (string.IsNullOrEmpty(b.Icon) ? "▫" : b.Icon, b.Label),
        MediaWidget => ("🎵", "Música"),
        VolumeWidget => ("🔊", "Volumen"),
        _ => ("▫", "?"),
    };
}
