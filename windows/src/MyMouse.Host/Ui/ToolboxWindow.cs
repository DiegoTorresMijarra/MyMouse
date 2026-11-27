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
using MyMouse.HostCore.Updates;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;
using Path = System.IO.Path;

namespace MyMouse.Host.Ui;

/// <summary>Una tarjeta de la toolbox: su vista y cómo actualizarla cuando cambia el estado.</summary>
internal interface IToolboxCard
{
    FrameworkElement View { get; }

    /// <summary>La tarjeta avisa de que ha cambiado de alto por una acción del usuario (buscar, plegar): hay que recolocar la ventana.</summary>
    Action? Resized { get; set; }

    /// <summary>Actualiza la vista con el estado actual. Devuelve true si ha cambiado de alto (hay que recolocar la ventana).</summary>
    bool Refresh();
}

/// <summary>Un menú pequeño de cristal que se abre junto a un control (el menú de Ajustes).</summary>
internal static class MiniMenu
{
    public static void Show(UIElement target, IEnumerable<(string Text, bool? Checked, Action Click)> items,
        PlacementMode placement = PlacementMode.Top)
    {
        var panel = new StackPanel { Margin = new Thickness(4) };
        var popup = new Popup
        {
            PlacementTarget = target,
            Placement = placement,
            StaysOpen = false,
            AllowsTransparency = true,
            IsOpen = false,
            Child = new Border
            {
                Background = Theme.PanelSolid,
                BorderBrush = Theme.Line,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(Theme.RowRadius + 2),
                Margin = new Thickness(0, 6, 0, 6),
                Child = panel,
            },
        };
        foreach (var (text, isChecked, click) in items)
        {
            var label = (isChecked is null ? "" : isChecked == true ? "✓  " : "     ") + text;
            var button = Theme.FlatButton(label, Theme.Transparent);
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
/// La toolbox (docs/spec-v4-toolbox.md §2 y docs/spec-v4.5-estilo.md §2): un panel flotante de 400 px anclado sobre la bandeja, con una
/// barra superior (⚙ ajustes, ⤢ maximizar, ✕ cerrar) y las tarjetas de <see cref="ToolboxLayout"/>. Maximizada pasa a ser una ventana
/// normal centrada, redimensionable y con las tarjetas en dos columnas. Se abre con un clic en el icono o con 4 dedos ↑ en el móvil
/// (sin robar el foco) y se cierra con Esc, un clic fuera (solo anclada), el icono, ✕ o 4 dedos ↓.
/// </summary>
internal sealed class ToolboxWindow : FloatingWindow
{
    private const double PanelWidth = 400;
    private const double TopBarHeight = 40;

    private readonly HostApp _app;
    private readonly StackPanel _stack = new() { Margin = new Thickness(12, 4, 12, 12) };
    private readonly Grid _columns = new() { Margin = new Thickness(12, 4, 12, 12) };
    private readonly StackPanel _left = new() { Margin = new Thickness(0, 0, 6, 0) };
    private readonly StackPanel _right = new() { Margin = new Thickness(6, 0, 0, 0) };
    private readonly ScrollViewer _scroll;
    private readonly Button _maximizeButton;
    private readonly TextBlock _maximizeGlyph;
    private List<IToolboxCard> _cards = [];
    private bool _noActivate;
    private bool _maximized;
    private bool _destroying;
    private DateTime _closedByFocusLossAt = DateTime.MinValue;

    public ToolboxWindow(HostApp app)
        : base(PanelWidth)
    {
        _app = app;
        Title = "MyMouse";
        Card.Width = PanelWidth;

        _columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        _columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        Grid.SetColumn(_right, 1);
        _columns.Children.Add(_left);
        _columns.Children.Add(_right);

        _scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Focusable = false,
        };

        // Barra superior de 40 px: logo y nombre a la izquierda; ⚙ ⤢ ✕ de 28 × 28 a la derecha.
        var settings = Theme.IconButton("", tooltip: "Ajustes");
        settings.Click += (_, _) => MiniMenu.Show(settings, SettingsItems(), PlacementMode.Bottom);
        _maximizeButton = Theme.IconButton("", tooltip: "Maximizar");
        _maximizeGlyph = (TextBlock)_maximizeButton.Content;
        _maximizeButton.Click += (_, _) => ToggleMaximized();
        var close = Theme.IconButton("", tooltip: "Cerrar");
        close.Click += (_, _) => Close();

        var icons = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
        foreach (var b in new[] { settings, _maximizeButton, close })
        {
            b.Margin = new Thickness(2, 0, 0, 0);
            icons.Children.Add(b);
        }
        var brand = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };
        brand.Children.Add(Logo(20));
        var name = Theme.Label("MyMouse", 13, weight: FontWeights.SemiBold);
        name.FontFamily = Theme.DisplayFont;
        name.Margin = new Thickness(10, 0, 0, 0);
        name.VerticalAlignment = VerticalAlignment.Center;
        brand.Children.Add(name);
        var bar = new DockPanel { Height = TopBarHeight, Background = Theme.Transparent };
        DockPanel.SetDock(icons, Dock.Right);
        bar.Children.Add(icons);
        bar.Children.Add(brand);
        // Arrastrar la barra mueve la ventana solo cuando está maximizada; anclada no se mueve.
        bar.MouseLeftButtonDown += (_, e) =>
        {
            if (!_maximized || e.ButtonState != MouseButtonState.Pressed) return;
            if (_noActivate) SetNoActivate(false);
            try
            {
                DragMove();
            }
            catch (InvalidOperationException)
            {
            }
        };

        var root = new DockPanel();
        DockPanel.SetDock(bar, Dock.Top);
        root.Children.Add(bar);
        root.Children.Add(_scroll);
        CardContent = root;

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
            if (!IsVisible || _noActivate || _maximized || _app.EditorOpen || _app.ThemePanelOpen) return;
            _closedByFocusLossAt = DateTime.UtcNow;
            Close();
        };
        Closing += (_, e) =>
        {
            // Alt+F4 sobre la ventana maximizada: se oculta, no se destruye.
            if (_destroying) return;
            e.Cancel = true;
            Close();
        };
        _app.StateChanged += () =>
        {
            if (IsVisible) RefreshCards();
        };
    }

    public bool IsOpen => IsVisible;

    /// <summary>Anclada sobre la bandeja (no maximizada): solo entonces el gesto en vivo se coloca encima.</summary>
    public bool IsDocked => !_maximized;

    /// <summary>Alto de la tarjeta visible, para colocar encima el gesto en vivo.</summary>
    public double CardHeightDip => Math.Max(0, WindowHeightDip - 2 * ShadowMargin);

    /// <summary>Abre la toolbox. <paramref name="fromPhone"/>: sin activarla, para no quitarle el foco a la app en la que se trabaja.</summary>
    public void Open(bool fromPhone)
    {
        if (IsVisible) return;
        _maximized = _app.Layout.Maximized;
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

    /// <summary>Cierra la ventana de verdad.</summary>
    public void Destroy()
    {
        _destroying = true;
        base.Close();
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

    private void ToggleMaximized() => _app.Layout = _app.Layout.WithMaximized(!_app.Layout.Maximized); // Rebuild() la recoloca

    private IEnumerable<(string, bool?, Action)> SettingsItems() =>
    [
        ("Iniciar con Windows", _app.AutostartEnabled, () => _app.AutostartEnabled = !_app.AutostartEnabled),
        ("Mostrar el gesto en vivo", _app.Layout.ShowLiveGesture, () => _app.Layout = _app.Layout.WithLiveGesture(!_app.Layout.ShowLiveGesture)),
        ("Efecto cristal", _app.Layout.GlassEffect, () => _app.Layout = _app.Layout.WithGlass(!_app.Layout.GlassEffect)),
        ("Mostrar siempre el icono en la barra de tareas", _app.Layout.AlwaysShowIcon, () => _app.Layout = _app.Layout.WithAlwaysShowIcon(!_app.Layout.AlwaysShowIcon)),
        ("Buscar actualizaciones automáticamente", _app.Updates.Manager.AutoCheck, () => _app.Updates.Manager.AutoCheck = !_app.Updates.Manager.AutoCheck),
        ("Buscar actualizaciones ahora", null, () => _ = CheckUpdatesNowAsync()),
        ($"Versión instalada: {_app.Updates.Manager.LocalText}", null, () => { }),
        ("Tema…", null, _app.ChooseTheme),
        ("Personalizar tarjetas…", null, _app.CustomizeCards),
        ("Abrir la carpeta de logs", null, _app.OpenLogs),
    ];

    /// <summary>Ajustes → "Buscar actualizaciones ahora": el resultado se dice con un cuadro; si hay una versión nueva, sale el banner.</summary>
    private async Task CheckUpdatesNowAsync()
    {
        var updates = _app.Updates;
        var outcome = await updates.CheckAsync(manual: true);
        var text = outcome switch
        {
            CheckOutcome.Available => null, // el banner de la toolbox ya lo dice
            CheckOutcome.UpToDate => $"Ya tienes la última versión (MyMouse {updates.Manager.LocalText}).",
            CheckOutcome.DevBuild => $"Esta es una versión de desarrollo ({updates.Manager.LocalText}): no avisa de versiones nuevas.",
            CheckOutcome.Skipped => "Hay una versión más nueva, pero la omitiste.",
            _ => "No se pudo comprobar si hay una versión nueva. Revisa la conexión a Internet e inténtalo de nuevo.",
        };
        if (text is not null) Dialogs.Message(null, "MyMouse", text);
    }

    private void SetNoActivate(bool on)
    {
        _noActivate = on;
        NativeUi.SetExStyle(Handle, NativeUi.WS_EX_NOACTIVATE, on);
        if (!on && IsVisible) Activate();
    }

    /// <summary>El icono de la app (el mismo del exe y de la bandeja) a <paramref name="height"/> px. Sin él, el contorno de un ratón.</summary>
    public static FrameworkElement Logo(double height)
    {
        if (AppIcon.Frame is { } icon)
        {
            var image = new Image { Source = icon, Width = height, Height = height, VerticalAlignment = VerticalAlignment.Center };
            RenderOptions.SetBitmapScalingMode(image, BitmapScalingMode.HighQuality);
            return image;
        }
        var scale = height / 32;
        var logo = new Grid { Width = 26 * scale, Height = height, VerticalAlignment = VerticalAlignment.Center };
        logo.Children.Add(new Border
        {
            BorderBrush = Theme.Text,
            BorderThickness = new Thickness(Math.Max(1.5, 2 * scale)),
            CornerRadius = new CornerRadius(10 * scale),
            Background = Theme.Transparent,
        });
        return logo;
    }

    private void Build()
    {
        _stack.Children.Clear();
        _left.Children.Clear();
        _right.Children.Clear();
        var built = new Dictionary<ToolboxCard, IToolboxCard>();
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
            card.Resized = OnCardResized;
            built[kind] = card;
            _cards.Add(card);
        }

        // El aviso de actualización va arriba de todo y no cuenta como tarjeta del diseño: se oculta solo si no hay nada que avisar.
        var update = new UpdateCard(_app);
        update.Refresh();
        update.Resized = OnCardResized;
        _cards.Add(update);

        if (_maximized)
        {
            // Dos columnas: Opciones y Música a la izquierda, Gestos a la derecha y Widgets debajo de la columna más baja.
            var columnWidth = 480 - 24;
            double HeightOf(ToolboxCard c)
            {
                var view = built[c].View;
                view.Measure(new Size(columnWidth, double.PositiveInfinity));
                return view.DesiredSize.Height;
            }
            var (left, right) = ToolboxColumns.Split(_app.Layout.Visible, HeightOf);
            foreach (var c in left) _left.Children.Add(built[c].View);
            foreach (var c in right) _right.Children.Add(built[c].View);
            _left.Children.Insert(0, update.View);
            TrimLastMargin(_left);
            TrimLastMargin(_right);
        }
        else
        {
            _stack.Children.Add(update.View);
            foreach (var kind in _app.Layout.Visible) _stack.Children.Add(built[kind].View);
            TrimLastMargin(_stack);
        }
    }

    /// <summary>Sin hueco tras la última tarjeta: el relleno inferior de la ventana es igual al superior (12 px).</summary>
    private static void TrimLastMargin(StackPanel panel)
    {
        if (panel.Children.Count > 0 && panel.Children[^1] is FrameworkElement last) last.Margin = new Thickness(0);
    }

    private void ApplyMode()
    {
        if (_maximized)
        {
            Topmost = false;
            ShowInTaskbar = true;
            NativeUi.SetExStyle(Handle, NativeUi.WS_EX_TOOLWINDOW, false);
            SetResizable(true);
            Card.Width = double.NaN;
            _scroll.Content = _columns;
            _scroll.MaxHeight = double.PositiveInfinity;
            _maximizeGlyph.Text = ""; // restaurar
            _maximizeButton.ToolTip = "Restaurar";
        }
        else
        {
            Topmost = true;
            ShowInTaskbar = false;
            NativeUi.SetExStyle(Handle, NativeUi.WS_EX_TOOLWINDOW, true);
            SetResizable(false);
            Card.Width = PanelWidth;
            Width = PanelWidth + 2 * ShadowMargin;
            _scroll.Content = _stack;
            _maximizeGlyph.Text = ""; // maximizar
            _maximizeButton.ToolTip = "Maximizar";
        }
    }

    private void PlaceNow()
    {
        // El contenido se quita de un contenedor antes de ponerlo en el otro.
        _scroll.Content = null;
        ApplyMode();
        if (_maximized)
        {
            var work = WorkAreaDip();
            var (w, h) = ToolboxColumns.WindowSize(work.Width, work.Height);
            PlaceCentered(w - 2 * ShadowMargin, h - 2 * ShadowMargin);
        }
        else
        {
            var max = MaxCardHeightDip();
            _scroll.MaxHeight = max - TopBarHeight;
            Place(maxHeightDip: max);
        }
    }

    private void OnCardResized()
    {
        if (IsVisible && !_maximized) PlaceNow();
    }

    private void RefreshCards()
    {
        var resized = false;
        foreach (var card in _cards) resized |= card.Refresh();
        if (resized && !_maximized) PlaceNow();
    }

    /// <summary>Se cambió la distribución o el modo: se reconstruye con la ventana abierta.</summary>
    public void Rebuild()
    {
        if (!IsVisible) return;
        _maximized = _app.Layout.Maximized;
        Build();
        PlaceNow();
        if (_maximized && _noActivate) SetNoActivate(false);
    }
}

internal static class CardChrome
{
    /// <summary>Tarjeta de cristal: blanco al 5 % con brillo superior, borde de 1 px al 10 % y radio 12. Sin sombra.</summary>
    public static Border Wrap(UIElement content) => new()
    {
        Background = Theme.CardGlass,
        BorderBrush = Theme.Line,
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(Theme.CornerRadius),
        Padding = new Thickness(12),
        Margin = new Thickness(0, 0, 0, 8),
        Child = content,
    };

    public static TextBlock Title(string text) => Theme.CardTitle(text);

    /// <summary>Botón secundario de cristal para las cabeceras de tarjeta ("+ Nueva forma").</summary>
    public static Button HeaderButton(string text)
    {
        var b = Theme.FlatButton(text, Theme.Hover, padding: new Thickness(10, 3, 10, 4));
        b.FontSize = 12;
        return b;
    }

    /// <summary>Una fila pulsable (hover de cristal) con un contenido, alineada a la izquierda.</summary>
    public static Button Row(UIElement content, Action click)
    {
        var button = Theme.FlatButton(content, Theme.Transparent, padding: new Thickness(6, 5, 6, 5));
        button.HorizontalContentAlignment = HorizontalAlignment.Stretch;
        button.Click += (_, _) => click();
        return button;
    }
}

/// <summary>Estado y botones de siempre, en una fila de iconos con etiqueta. No se puede quitar.</summary>
internal sealed class OptionsCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly StackPanel _pillHost = new();
    private readonly TextBlock _hint = Theme.Label("", 12, Theme.Dim);
    private readonly TextBlock _pauseGlyph = Theme.Glyph("", 16);
    private readonly TextBlock _pauseLabel = Theme.Label("Pausar", 11);
    private string _shown = "";

    public OptionsCard(HostApp app)
    {
        _app = app;
        _hint.TextWrapping = TextWrapping.Wrap;
        _hint.TextTrimming = TextTrimming.None;
        _hint.Margin = new Thickness(2, 8, 0, 0); // explicación bajo la píldora: texto secundario legible

        var pause = Tile(_pauseGlyph, _pauseLabel);
        pause.Click += (_, _) => _app.TogglePause();
        var editor = Tile(Theme.Glyph("", 16), Theme.Label("Gestos", 11));
        editor.ToolTip = "Gestos y widgets";
        editor.Click += (_, _) => _app.OpenEditor(EditorTab.Gestures);
        var help = Tile(Theme.Glyph("", 16), Theme.Label("Ayuda", 11));
        help.Click += (_, _) => _app.ShowHelp();
        var quit = Tile(Theme.Glyph("", 16), Theme.Label("Salir", 11));
        quit.ToolTip = "Salir de MyMouse";
        quit.Click += (_, _) => _app.Quit();

        var tiles = new UniformGrid { Columns = 4, Margin = new Thickness(-3, 12, -3, 0) };
        foreach (var t in new[] { pause, editor, help, quit }) tiles.Children.Add(t);

        var stack = new StackPanel();
        stack.Children.Add(_pillHost);
        stack.Children.Add(_hint);
        stack.Children.Add(tiles);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public Action? Resized { get; set; }

    /// <summary>Botón de icono con etiqueta debajo, en cristal elevado.</summary>
    private static Button Tile(TextBlock glyph, TextBlock label)
    {
        label.HorizontalAlignment = HorizontalAlignment.Center;
        label.Foreground = Theme.Dim;
        glyph.HorizontalAlignment = HorizontalAlignment.Center;
        var content = new StackPanel { Margin = new Thickness(0, 2, 0, 0) };
        content.Children.Add(glyph);
        label.Margin = new Thickness(0, 4, 0, 0);
        content.Children.Add(label);
        var b = Theme.FlatButton(content, Theme.Hover, padding: new Thickness(4, 8, 4, 8));
        b.Margin = new Thickness(3);
        b.HorizontalContentAlignment = HorizontalAlignment.Stretch;
        return b;
    }

    public bool Refresh()
    {
        var hint = _app.StatusHint ?? "";
        var key = _app.StatusText + "|" + _app.Paused + "|" + hint;
        _pauseGlyph.Text = _app.Paused ? "" : "";
        _pauseLabel.Text = _app.Paused ? "Reanudar" : "Pausar";
        if (key == _shown) return false;
        _shown = key;
        // El estado va en una "píldora" con punto de color.
        _pillHost.Children.Clear();
        _pillHost.Children.Add(Theme.StatusPill(_app.StatusBrush, _app.StatusText));
        _hint.Text = hint;
        _hint.Visibility = hint.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        return true; // la línea secundaria puede cambiar el alto
    }
}

/// <summary>Lo que suena (carátula, título, artista, controles) y el volumen del sistema.</summary>
internal sealed class MusicCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly Border _cover;
    private readonly TextBlock _glyph = Theme.Glyph("", 20, Theme.Dim);
    private readonly TextBlock _title = Theme.Label("", 14, weight: FontWeights.SemiBold);
    private readonly TextBlock _artist = Theme.Label("", 12, Theme.Dim);
    private readonly TextBlock _playGlyph = new() { FontSize = 16, Foreground = Theme.Text, Text = "" };
    private readonly TextBlock _muteGlyph = new() { FontSize = 14, Foreground = Theme.Text, Text = "" };
    private readonly Button _play;
    private readonly Button _mute;
    private readonly TextBlock _percent = Theme.Label("", 12, Theme.Dim);
    private readonly MiniSlider _slider = new();
    private byte[]? _shownCover;

    public MusicCard(HostApp app)
    {
        _app = app;
        _cover = new Border
        {
            Width = 56,
            Height = 56,
            CornerRadius = new CornerRadius(10),
            Background = Theme.Hover,
            Margin = new Thickness(0, 0, 12, 0),
            Child = new Grid { Children = { _glyph } },
        };
        _glyph.HorizontalAlignment = HorizontalAlignment.Center;
        _glyph.VerticalAlignment = VerticalAlignment.Center;

        var previous = Theme.RoundButton(new TextBlock { FontSize = 14, Foreground = Theme.Text, Text = "" });
        _play = Theme.RoundButton(_playGlyph);
        var next = Theme.RoundButton(new TextBlock { FontSize = 14, Foreground = Theme.Text, Text = "" });
        previous.Click += (_, _) => _app.SendMedia(MediaCommand.Previous);
        _play.Click += (_, _) => _app.SendMedia(MediaCommand.PlayPause);
        next.Click += (_, _) => _app.SendMedia(MediaCommand.Next);
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
        foreach (var b in new[] { previous, _play, next })
        {
            b.Margin = new Thickness(0, 0, 8, 0);
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

        _mute = Theme.RoundButton(_muteGlyph, 32);
        _mute.Click += (_, _) => _app.ToggleMute();
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
        stack.Children.Add(CardChrome.Title("Música"));
        stack.Children.Add(new Border { Height = 8 });
        stack.Children.Add(top);
        stack.Children.Add(volume);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public Action? Resized { get; set; }

    public bool Refresh()
    {
        var media = _app.Media.Current();
        var nothing = media.Title.Length == 0 && !media.Playing;
        _title.Text = nothing ? "Nada en reproducción" : media.Title;
        _title.Foreground = nothing ? Theme.Dim : Theme.Text;
        _artist.Text = media.Artist;
        _artist.Visibility = media.Artist.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        _play.IsEnabled = !nothing;
        _playGlyph.Text = media.Playing ? "" : "";

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
        _muteGlyph.Text = volume.Muted || volume.Level == 0 ? "" : "";
        return false;
    }
}

/// <summary>
/// Las formas en secciones (docs/spec-v4.5-estilo.md §2.4): "Fijadas", "Asignadas" y "Sin acción" (plegada, con contador), con
/// la fila de <see cref="GestureRows"/>, "+ Nueva forma" de cristal y una búsqueda opcional.
/// </summary>
internal sealed class GesturesCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly Border _view;
    private readonly StackPanel _rows = new() { Margin = new Thickness(0, 8, 0, 0) };
    private readonly TextBox _search;
    private string _signature = "";
    private string _filter = "";
    private bool _unassignedOpen;

    public GesturesCard(HostApp app)
    {
        _app = app;
        var add = CardChrome.HeaderButton("+ Nueva forma");
        add.Click += (_, _) => _app.OpenEditor(EditorTab.Gestures, newShape: true);
        var searchButton = Theme.IconButton("", 26, "Buscar");
        searchButton.Margin = new Thickness(0, 0, 6, 0);
        _search = Theme.Input();
        _search.Margin = new Thickness(0, 8, 0, 0);
        _search.Visibility = Visibility.Collapsed;
        _search.TextChanged += (_, _) =>
        {
            _filter = _search.Text.Trim();
            if (Refresh()) Resized?.Invoke();
        };
        searchButton.Click += (_, _) =>
        {
            var show = _search.Visibility != Visibility.Visible;
            _search.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
            if (show) _search.Focus();
            else _search.Text = "";
            Resized?.Invoke();
        };

        var right = new StackPanel { Orientation = Orientation.Horizontal };
        right.Children.Add(searchButton);
        right.Children.Add(add);
        var header = new DockPanel();
        DockPanel.SetDock(right, Dock.Right);
        header.Children.Add(right);
        header.Children.Add(new Border { Child = CardChrome.Title("Gestos"), VerticalAlignment = VerticalAlignment.Center });

        var stack = new StackPanel();
        stack.Children.Add(header);
        stack.Children.Add(_search);
        stack.Children.Add(_rows);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public Action? Resized { get; set; }

    public bool Refresh()
    {
        var config = _app.Config;
        var sections = GestureSections.Build(config, _app.Shapes.Shapes.Select(s => s.Name)).Filter(_filter);
        var open = _unassignedOpen || _filter.Length > 0; // al buscar se abre la sección para ver las coincidencias
        var signature = string.Join("|", sections.Pinned.Concat(sections.Assigned).Concat(sections.Unassigned)
            .Select(r => $"{r.Gesture}:{r.Title}:{ActionChip.Format(r.Action).Text}:{r.Pinned}:{r.OnlyInSomeApps}")) + $"#{open}#{_filter}";
        if (signature == _signature) return false;
        _signature = signature;

        _rows.Children.Clear();
        void Add(string title, IReadOnlyList<GestureRow> rows)
        {
            if (rows.Count == 0) return;
            _rows.Children.Add(GestureRows.SectionHeader(title));
            foreach (var row in rows) _rows.Children.Add(BuildRow(row));
        }
        Add("Fijadas", sections.Pinned);
        Add("Asignadas", sections.Assigned);
        if (sections.Unassigned.Count > 0)
        {
            _rows.Children.Add(GestureRows.SectionHeader(sections.UnassignedTitle, open, () =>
            {
                _unassignedOpen = !_unassignedOpen;
                _signature = "";
                Refresh();
                Resized?.Invoke();
            }));
            if (open) foreach (var row in sections.Unassigned) _rows.Children.Add(BuildRow(row));
        }
        if (sections.IsEmpty) _rows.Children.Add(Theme.Label(_filter.Length > 0 ? $"Nada coincide con «{_filter}»." : "Todavía no hay formas.", 12, Theme.Dim));
        return true;
    }

    private Button BuildRow(GestureRow row) =>
        GestureRows.Row(_app, row, () => _app.OpenEditor(EditorTab.Gestures, gesture: row.Gesture, focusAction: !row.HasAction));
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
        var add = CardChrome.HeaderButton("+ Añadir");
        add.Click += (_, _) => _app.OpenEditor(EditorTab.Widgets, newWidget: true);
        var header = new DockPanel();
        DockPanel.SetDock(add, Dock.Right);
        header.Children.Add(add);
        header.Children.Add(new Border { Child = CardChrome.Title("Widgets del móvil"), VerticalAlignment = VerticalAlignment.Center });
        var stack = new StackPanel();
        stack.Children.Add(header);
        _rows.Margin = new Thickness(0, 8, 0, 0);
        stack.Children.Add(_rows);
        _view = CardChrome.Wrap(stack);
    }

    public FrameworkElement View => _view;

    public Action? Resized { get; set; }

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


/// <summary>
/// Avisos de versión (docs/spec-v5.1-actualizaciones.md §4 y §6): una tarjeta con el acento del tema encima de las Opciones por cada aviso, con
/// "Novedades", "Actualizar" y "Omitir", y la barra de progreso mientras se descarga. Invisible si no hay nada que avisar.
/// </summary>
internal sealed class UpdateCard : IToolboxCard
{
    private readonly HostApp _app;
    private readonly StackPanel _view = new();
    private readonly List<ProgressBar> _bars = [];
    private string _signature = "";

    public UpdateCard(HostApp app) => _app = app;

    public FrameworkElement View => _view;

    public Action? Resized { get; set; }

    public bool Refresh()
    {
        var updates = _app.Updates;
        var banners = updates.Manager.Banners;
        var signature = string.Join("|", banners.Select(b => $"{b.Kind}:{b.Text}:{b.CanInstall}:{b.Release?.Tag}")) + $"#{updates.Busy}#{updates.Error}#{updates.IsInstalled}";
        foreach (var bar in _bars) bar.Value = updates.Progress * 100; // el progreso no reconstruye
        if (signature == _signature) return false;
        _signature = signature;

        _view.Children.Clear();
        _bars.Clear();
        foreach (var banner in banners) _view.Children.Add(Build(banner));
        _view.Visibility = banners.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
        return true;
    }

    private Border Build(UpdateBanner banner)
    {
        var updates = _app.Updates;
        var stack = new StackPanel();
        var title = Theme.Label(banner.Text, 13, banner.Kind == BannerKind.PhoneOlder ? Theme.Dim : Theme.Text, FontWeights.SemiBold);
        title.TextWrapping = TextWrapping.Wrap;
        title.TextTrimming = TextTrimming.None;
        stack.Children.Add(title);

        if (banner.Release is { } release)
        {
            var notes = Theme.Label("Novedades", 12, Theme.Accent);
            notes.Cursor = Cursors.Hand;
            notes.TextDecorations = TextDecorations.Underline;
            notes.Margin = new Thickness(0, 4, 0, 0);
            notes.HorizontalAlignment = HorizontalAlignment.Left;
            notes.MouseLeftButtonUp += (_, _) => updates.OpenPage(release);
            stack.Children.Add(notes);
        }

        if (updates.Error is { } error && banner.CanInstall)
        {
            var failed = Theme.Label(error, 12, Theme.Red);
            failed.TextWrapping = TextWrapping.Wrap;
            failed.TextTrimming = TextTrimming.None;
            failed.Margin = new Thickness(0, 4, 0, 0);
            stack.Children.Add(failed);
        }

        if (banner.CanInstall && banner.Release is { } target)
        {
            if (updates.Busy)
            {
                var bar = new ProgressBar { Height = 4, Minimum = 0, Maximum = 100, Value = updates.Progress * 100, Margin = new Thickness(0, 10, 0, 0),
                    Foreground = Theme.Accent, Background = Theme.Hover, BorderThickness = new Thickness(0) };
                _bars.Add(bar);
                stack.Children.Add(bar);
                var busy = Theme.Label("Descargando e instalando…", 12, Theme.Dim);
                busy.Margin = new Thickness(0, 6, 0, 0);
                stack.Children.Add(busy);
            }
            else
            {
                var update = Theme.AccentButton(updates.IsInstalled ? "Actualizar" : "Ver descarga");
                update.Click += (_, _) => _ = updates.InstallAsync(target);
                var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 10, 0, 0) };
                row.Children.Add(update);
                if (banner.CanSkip)
                {
                    var skip = Theme.FlatButton("Omitir");
                    skip.Margin = new Thickness(8, 0, 0, 0);
                    skip.Click += (_, _) => updates.Skip();
                    row.Children.Add(skip);
                }
                stack.Children.Add(row);
            }
        }

        var card = CardChrome.Wrap(stack);
        card.BorderBrush = banner.Kind == BannerKind.PhoneOlder ? Theme.Line : Theme.Accent;
        return card;
    }
}
