using System.Diagnostics;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using MyMouse.Host.Ui;
using MyMouse.HostCore;
using MyMouse.HostCore.Apps;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Live;
using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Shell;
using MyMouse.HostCore.Shapes;
using MyMouse.HostCore.Theming;
using MyMouse.HostCore.Toolbox;
using MyMouse.HostCore.Updates;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;
using Drawing = System.Drawing;
using WinForms = System.Windows.Forms;

namespace MyMouse.Host;

/// <summary>
/// La aplicación: icono de la bandeja (WinForms, solo para <c>NotifyIcon</c>), servidor, adb y las ventanas WPF
/// (toolbox, gesto en vivo y editor). Todo lo que toca la interfaz ocurre en el hilo de la interfaz: los avisos de los
/// servicios, que llegan de otros hilos, se pasan con <see cref="Post"/>.
/// </summary>
internal sealed class HostApp : IDisposable
{
    private readonly Dispatcher _ui = Dispatcher.CurrentDispatcher;
    private readonly WinForms.NotifyIcon _icon;
    private readonly WinForms.ToolStripMenuItem _pauseItem;
    private readonly CancellationTokenSource _cts = new();
    private readonly AdbReverseKeeper _adb;
    private readonly GestureConfigFile _file;
    private readonly DispatcherTimer _foregroundTimer;
    private readonly WidgetService _widgets;
    private readonly string _layoutPath = Path.Combine(HostConfig.Directory, "toolbox.json");

    private ToolboxWindow? _toolbox;
    private LiveGestureWindow? _live;
    private EditorWindow? _editor;
    private ToolboxLayout _layout;
    private readonly ThemeSyncService _themeSync;
    private bool _adoptingTheme;
    private Action? _balloonClick;
    private volatile bool _paused;
    private bool _connected;

    public HostApp(HostConfig config)
    {
        _layout = ToolboxLayout.Load(_layoutPath);
        Theme.GlassEffect = _layout.GlassEffect;
        Theme.Set(_layout.Theme);
        ConfigStorage = new GestureConfigStorage(GestureConfigFile.FilePath);
        StartMenu = StartMenuScanner.ForCurrentUser();

        // El icono de la bandeja: clic izquierdo abre o cierra la toolbox; el derecho, un menú mínimo.
        _pauseItem = new WinForms.ToolStripMenuItem(Loc.T("pause"), null, (_, _) => TogglePause());
        var menu = new WinForms.ContextMenuStrip();
        menu.Items.Add(Loc.T("open_mymouse"), null, (_, _) => Toolbox.Open(fromPhone: false));
        menu.Items.Add(_pauseItem);
        menu.Items.Add(new WinForms.ToolStripSeparator());
        menu.Items.Add(Loc.T("exit"), null, (_, _) => Quit());
        _icon = new WinForms.NotifyIcon { Icon = TrayIcons.NoDevice, ContextMenuStrip = menu, Visible = true };
        _icon.MouseClick += (_, e) =>
        {
            if (e.Button == WinForms.MouseButtons.Left) Toolbox.Toggle();
        };
        _icon.BalloonTipClicked += (_, _) =>
        {
            var click = _balloonClick;
            _balloonClick = null;
            click?.Invoke();
        };
        _icon.BalloonTipClosed += (_, _) => _balloonClick = null;

        // Servicios.
        Executor = new WindowsActionExecutor(() => Post(TogglePause), OpenPhoneKeyboard);
        Gestures = new GestureService(Executor);
        Gestures.Unassigned += name =>
        {
            FileLog.Write($"Forma sin asignar: {name}");
            // La tarjeta del gesto en vivo sustituye al globo; sin ella, se sigue avisando una vez por forma cada 30 s.
            if (!Layout.ShowLiveGesture) Post(() => NotifyUnassigned(name));
        };
        Gestures.Failed += (name, ex) =>
        {
            FileLog.Write($"Error al ejecutar la forma '{name}': {ex.Message}");
            if (!Layout.ShowLiveGesture) Post(() => Notify(Loc.F("ha_could_not_run", name, ex.Message)));
        };

        Volume = new WasapiVolumeController(FileLog.Write);
        Media = new WindowsMediaController(Executor, FileLog.Write);
        _widgets = new WidgetService(Executor, Volume, Media);
        _widgets.Failed += (what, ex) =>
        {
            FileLog.Write($"Error en el widget '{what}': {ex.Message}");
            Post(() => Notify(Loc.F("ha_could_not_run", what, ex.Message)));
        };
        Volume.Changed += () => Post(() => StateChanged?.Invoke());
        Media.Changed += () => Post(() => StateChanged?.Invoke());

        // Actualizaciones: los avisos van a la toolbox y a la bandeja; el globo, una vez por versión.
        Updates = new UpdateController(
            text => Post(() => Notify(text, () => Toolbox.Open(fromPhone: false))),
            () => Post(Quit));
        Updates.Changed += () => Post(() =>
        {
            UpdateStatus(); // el punto azul de la bandeja
        });

        Phone = new PhoneHub { HostAppVersion = AppInfo.Version };
        Shapes = new ShapeSyncService(Phone, Path.Combine(HostConfig.Directory, "shapes-cache.json"));
        Shapes.Changed += () => Post(() => StateChanged?.Invoke());
        Shapes.NewShape += shape => Post(() => OnNewShape(shape.Name));
        // Tema sincronizado con el móvil: gana el cambio más reciente. El instante del adoptado se guarda tal cual, así no se reenvía.
        _themeSync = new ThemeSyncService(Phone, () =>
        {
            var layout = _layout;
            return new SyncedTheme(layout.Theme.ThemeId, layout.Theme.AccentId, layout.Theme.Stars, layout.ThemeChangedAtMs);
        });
        _themeSync.Adopted += t => Post(() =>
        {
            _adoptingTheme = true;
            try
            {
                Layout = Layout.WithTheme(new ThemeChoice(t.ThemeId, t.AccentId, t.Stars, Layout.Theme.Twinkle), t.ChangedAtMs);
            }
            finally
            {
                _adoptingTheme = false;
            }
            FileLog.Write($"Tema del móvil: {t.ThemeId}, acento {t.AccentId}");
        });
        Live = new LiveStrokeModel();
        Gestures.Outcome += o => Post(() => Live.OnOutcome(
            o.Status, o.Name, o.Binding?.HelpText, o.Binding is { } binding ? ActionText.Summary(binding.Action) : null, o.Error?.Message));
        Phone.Received += message => Post(() => OnPhoneMessage(message));
        Phone.Changed += () => Post(() =>
        {
            if (!Phone.Connected) Live.Hide();
            Updates.Manager.SetPhoneVersion(Phone.PhoneAppVersion); // avisos de versión cruzados; dispara su propio Changed
            UpdateStatus(); // "Modo teclado" en la línea de estado, o fuera si el móvil se desconecta
        });
        // Modo teclado: una tarjeta corta como la del gesto en vivo, que respeta "Mostrar gesto en vivo".
        Phone.KeyboardModeChanged += on => Post(() =>
        {
            if (!Layout.ShowLiveGesture) return;
            _ = LiveWindow;
            if (on) Live.ShowNotice(Loc.T("keyboard_mode_title"), Loc.T("keyboard_mode_note"));
            else Live.ShowNotice(Loc.T("mouse_mode_title"));
        });

        // gestures.json: se recarga solo al guardarlo (también cuando lo guarda el editor).
        _file = new GestureConfigFile();
        _file.Loaded += loaded =>
        {
            Gestures.SetConfig(loaded);
            _widgets.SetWidgets(loaded.Widgets);
            Post(() =>
            {
                Config = loaded;
                StateChanged?.Invoke();
            });
        };
        _file.Problem += message => Post(() => Notify(message));
        _file.Start();

        // La chuleta del móvil cambia con la app activa si hay reglas por aplicación.
        _foregroundTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        _foregroundTimer.Tick += (_, _) => Gestures.SetForegroundApp(WindowsActionExecutor.ForegroundProcessName());
        _foregroundTimer.Start();

        var server = new HostServer(new SendInputInjector(), () => _paused, config.Port, Gestures, _widgets, Phone, new WindowsKeyboardState());
        server.Log += FileLog.Write;
        server.ConnectionChanged += connected => Post(() =>
        {
            _connected = connected;
            UpdateStatus();
        });

        _adb = new AdbReverseKeeper(config.AdbPath, config.Port);
        _adb.Log += FileLog.Write;
        _adb.StatusChanged += _ => Post(UpdateStatus);

        UpdateStatus();
        Updates.Start();
        if (_layout.AlwaysShowIcon) ApplyIconPromotion(true);
        _ = RunLoggedAsync("servidor", () => server.RunAsync(_cts.Token));
        _ = RunLoggedAsync("adb", () => _adb.RunAsync(_cts.Token));
    }

    // ---- Estado que leen las ventanas ----

    public WindowsActionExecutor Executor { get; }
    public GestureService Gestures { get; }
    public WasapiVolumeController Volume { get; }
    public WindowsMediaController Media { get; }
    public PhoneHub Phone { get; }
    public UpdateController Updates { get; }
    public ShapeSyncService Shapes { get; }
    public LiveStrokeModel Live { get; }
    public GestureConfigStorage ConfigStorage { get; }
    public IStartMenuApps StartMenu { get; }

    /// <summary>Última configuración cargada de <c>gestures.json</c>.</summary>
    public GestureConfig Config { get; private set; } = GestureConfig.Empty;

    public bool Paused => _paused;

    public string StatusText { get; private set; } = "";

    /// <summary>Línea secundaria bajo el estado (null si no hace falta): en Sin dispositivo, recuerda el modo Bluetooth.</summary>
    public string? StatusHint { get; private set; }

    public Brush StatusBrush { get; private set; } = Theme.Gray;

    /// <summary>Cambió algo que muestran las ventanas: estado, configuración, formas, música o volumen.</summary>
    public event Action? StateChanged;

    public ToolboxLayout Layout
    {
        get => _layout;
        set
        {
            var old = _layout;
            _layout = value;
            try
            {
                value.Save(_layoutPath);
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                FileLog.Write($"No se pudo guardar toolbox.json: {ex.Message}");
            }
            if (!value.ShowLiveGesture) Live.Hide();
            Theme.GlassEffect = value.GlassEffect;
            if (old.Theme != value.Theme) Theme.Set(value.Theme); // acento y fondos de todas las ventanas, al momento
            if (old.ThemeChangedAtMs != value.ThemeChangedAtMs && !_adoptingTheme) _themeSync.LocalChanged(); // al móvil
            if (old.AlwaysShowIcon != value.AlwaysShowIcon) ApplyIconPromotion(value.AlwaysShowIcon, userAction: true);
            _toolbox?.Rebuild();
            StateChanged?.Invoke();
        }
    }

    public bool AutostartEnabled
    {
        get
        {
            try
            {
                return Autostart.IsEnabled;
            }
            catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
            {
                return false;
            }
        }
        set
        {
            try
            {
                Autostart.Set(value);
                FileLog.Write($"Iniciar con Windows: {(value ? "sí" : "no")}");
            }
            catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
            {
                FileLog.Write($"No se pudo cambiar el inicio con Windows: {ex.Message}");
            }
        }
    }

    // ---- Icono siempre visible en la barra de tareas (docs/spec-v4.7-universo.md §3.4) ----

    private readonly IRegistry _registry = new WindowsRegistry();
    private DispatcherTimer? _promotionRetry;

    /// <summary>
    /// Pone el icono de la bandeja como visible (o no) en Windows 11 con <c>IsPromoted</c>. Si Windows aún no ha registrado el icono,
    /// reintenta a los 10 s y luego se rinde con una línea en el log. <paramref name="userAction"/>: lo pidió el usuario desde Ajustes,
    /// así que si no se puede se le explica el paso manual.
    /// </summary>
    private void ApplyIconPromotion(bool wanted, int attempt = 1, bool userAction = false)
    {
        _promotionRetry?.Stop();
        _promotionRetry = null;
        if (!TaskbarIconPromotion.IsSupported(Environment.OSVersion.Version.Build))
        {
            FileLog.Write("Icono visible en la barra de tareas: solo en Windows 11. En Windows 10, arrastra el icono desde la flecha ˄ a la barra.");
            if (userAction && wanted) Dialogs.Message(null, "MyMouse", ManualIconHelp);
            return;
        }
        if (Environment.ProcessPath is not { } exe) return;

        var outcome = TaskbarIconPromotion.SetPromoted(_registry, exe, wanted);
        FileLog.Write($"Icono visible en la barra de tareas ({(wanted ? "sí" : "no")}): {outcome}");
        if (TaskbarIconPromotion.ShouldRetry(outcome, attempt))
        {
            _promotionRetry = new DispatcherTimer { Interval = TaskbarIconPromotion.RetryDelay };
            _promotionRetry.Tick += (_, _) => ApplyIconPromotion(wanted, attempt + 1, userAction);
            _promotionRetry.Start();
        }
        else if (outcome != PromotionOutcome.Done)
        {
            FileLog.Write("No se pudo cambiar la visibilidad del icono: Windows no tiene la clave NotifyIconSettings de MyMouse. Me rindo.");
            if (userAction && wanted) Dialogs.Message(null, "MyMouse", ManualIconHelp);
        }
    }

    private static string ManualIconHelp =>
        Loc.T("ha_manual_icon_help");

    public bool EditorOpen => _editor is { IsVisible: true };

    private ToolboxWindow Toolbox => _toolbox ??= new ToolboxWindow(this);

    private LiveGestureWindow LiveWindow => _live ??= new LiveGestureWindow(
        Live,
        () => _toolbox is { IsOpen: true, IsDocked: true } t ? t.CardHeightDip + 8 : 0,
        ShapePoints,
        name => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));

    /// <summary>Puntos de la forma ideal (catálogo) o de la primera muestra (forma propia del móvil), en 0–1.</summary>
    public IReadOnlyList<PointF>? ShapePoints(string name)
    {
        if (ShapeCatalog.ById.TryGetValue(name, out var shape)) return shape.Points;
        var own = Shapes.Shapes.FirstOrDefault(s => s.Name == name);
        return own is { Samples.Count: > 0 } ? ShapeGeometry.FromWire(own.Samples[0]) : null;
    }

    // ---- Del móvil ----

    /// <summary>Acción Abrir teclado del móvil (widget o gesto): pide al móvil que abra su teclado (<c>KEYBOARD_OPEN</c>).</summary>
    private void OpenPhoneKeyboard()
    {
        if (!Phone.Connected)
            throw new InvalidOperationException(Loc.T("err_phone_not_connected"));
        _ = Phone.SendAsync(KeyboardOpenMessage.Instance);
    }

    private void OnPhoneMessage(Message message)
    {
        switch (message)
        {
            case ToolboxMessage t:
                if (t.Show) Toolbox.Open(fromPhone: true);
                else Toolbox.Close();
                break;
            case GestureModeMessage g when Layout.ShowLiveGesture:
                _ = LiveWindow; // se crea aquí, para que escuche al modelo antes del primer cambio
                Live.OnGestureMode(g.Active, TimeSpan.FromMilliseconds(g.StartTimeoutMs));
                break;
            case StrokePointsMessage p when Layout.ShowLiveGesture:
                Live.OnPoints(p.Points);
                break;
            case StrokeEndMessage e when Layout.ShowLiveGesture:
                Live.OnStrokeEnd(e.Result);
                break;
        }
    }

    private void OnNewShape(string name)
    {
        if (Config.HasAction(name)) return;
        FileLog.Write($"Forma nueva del móvil: {name}");
        Notify(Loc.F("ha_new_shape", ShapeCatalog.Label(name)), () => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));
    }

    // ---- Acciones de las ventanas ----

    public void TogglePause()
    {
        _paused = !_paused;
        _pauseItem.Text = _paused ? Loc.T("resume") : Loc.T("pause");
        UpdateStatus();
    }

    public void OpenEditor(EditorTab tab, string? gesture = null, bool focusAction = false, bool onlyUnassigned = false,
        int? widgetIndex = null, bool newWidget = false, bool newShape = false)
    {
        if (_editor is null)
        {
            _editor = new EditorWindow(this);
            _editor.Closed += (_, _) => _editor = null;
        }
        _toolbox?.Close();
        _editor.Present(tab, gesture, focusAction, onlyUnassigned, widgetIndex, newWidget, newShape);
    }

    /// <summary>El editor ha guardado gestures.json: se usa ya (el vigilante del fichero lo recargará en un momento).</summary>
    public void NotifyConfigSavedByEditor()
    {
        try
        {
            Config = ConfigStorage.Load();
        }
        catch (FormatException)
        {
            return;
        }
        StateChanged?.Invoke();
    }

    public void RaiseToolboxChanged() => StateChanged?.Invoke();

    /// <summary>"← Volver" del editor: la toolbox vuelve a abrirse.</summary>
    public void ReopenToolbox() => Toolbox.Open(fromPhone: false);

    public void SendMedia(MediaCommand command)
    {
        try
        {
            Media.Send(command);
        }
        catch (Exception ex)
        {
            FileLog.Write($"No se pudo enviar la orden de música: {ex.Message}");
        }
    }

    public void SetVolume(int level) => Volume.SetLevel(Math.Clamp(level, 0, 100));

    public void ToggleMute() => Volume.SetMuted(!Volume.Get().Muted);

    public void CustomizeCards() => new CustomizeCardsWindow(this).Show();

    /// <summary>Ajustes → Tema… (docs/spec-v4.8-temas.md §3).</summary>
    public void ChooseTheme()
    {
        if (_themeWindow is { IsVisible: true } open)
        {
            open.Activate();
            return;
        }
        _themeWindow = new ThemeWindow(this);
        _themeWindow.Closed += (_, _) =>
        {
            _themeWindow = null;
            if (_toolbox is { IsVisible: true } toolbox) toolbox.Activate(); // vuelve a cerrarse sola al perder el foco
        };
        _themeWindow.Show();
    }

    private ThemeWindow? _themeWindow;

    /// <summary>El panel Tema… está abierto: la toolbox no se cierra al perder el foco, para verse cambiar con el tema.</summary>
    public bool ThemePanelOpen => _themeWindow is { IsVisible: true };

    public void OpenLogs()
    {
        Directory.CreateDirectory(FileLog.Directory);
        Process.Start(new ProcessStartInfo(FileLog.Directory) { UseShellExecute = true });
    }

    public void ShowHelp()
    {
        Dialogs.Message(null, "MyMouse",
            Loc.T("ha_help"));
    }

    public void Quit()
    {
        Dispose();
        System.Windows.Application.Current.Shutdown();
    }

    // ---- Bandeja ----

    private void Post(Action action)
    {
        try
        {
            _ui.BeginInvoke(action);
        }
        catch (Exception ex) when (ex is InvalidOperationException or TaskCanceledException)
        {
            // La aplicación se está cerrando.
        }
    }

    private void UpdateStatus()
    {
        if (_disposed) return;
        StatusHint = null;
        // Texto corto en la píldora; la explicación larga va debajo, como texto secundario (docs/spec-v4.7-universo.md §3.2).
        var (status, icon, brush) = (_paused, _connected, _adb.Status) switch
        {
            (true, _, _) => (Loc.T("status_paused"), TrayIcons.Paused, Theme.Gray),
            (_, true, _) => ((_adb.DeviceName is { } name ? Loc.F("status_connected_name", name) : Loc.T("status_connected")) + (Phone.KeyboardMode ? Loc.T("status_keyboard_suffix") : ""), TrayIcons.Connected, Theme.Green),
            (_, _, AdbStatus.AdbNotFound) => (Loc.T("status_no_device"), TrayIcons.NoDevice, Theme.Red),
            (_, _, AdbStatus.NoDevice) => (Loc.T("status_no_device"), TrayIcons.NoDevice, Theme.Red),
            _ => (Loc.T("status_waiting"), TrayIcons.Waiting, Theme.Amber),
        };
        StatusText = status;
        StatusBrush = brush;
        if (!_paused && !_connected)
        {
            StatusHint = _adb.Status switch
            {
                AdbStatus.AdbNotFound => SinAdbHint,
                AdbStatus.NoDevice => SinDispositivoHint,
                _ => EsperandoHint,
            };
        }
        _icon.Icon = Updates.Manager.HasUpdate ? TrayIcons.WithUpdateDot(icon) : icon;
        // El tooltip de NotifyIcon admite como máximo 127 caracteres.
        _icon.Text = ($"MyMouse: {status}").Length > 120 ? ("MyMouse: " + status)[..120] : "MyMouse: " + status;
        StateChanged?.Invoke();
    }

    /// <summary>Es normal que el host diga Sin dispositivo en el modo Bluetooth (docs/spec-v4.5-estilo.md §4).</summary>
    public static string SinDispositivoHint => Loc.T("hint_no_device");

    public static string SinAdbHint => Loc.T("hint_no_adb");

    public static string EsperandoHint => Loc.T("hint_waiting");

    private readonly Dictionary<string, DateTime> _lastUnassignedNotice = [];

    private void NotifyUnassigned(string name)
    {
        // Un aviso por forma cada 30 s como mucho.
        if (_lastUnassignedNotice.TryGetValue(name, out var last) && DateTime.UtcNow - last < TimeSpan.FromSeconds(30)) return;
        _lastUnassignedNotice[name] = DateTime.UtcNow;
        Notify(Loc.F("ha_unassigned", name), () => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));
    }

    /// <summary>
    /// Aviso de Windows. Se usa el globo de <c>NotifyIcon</c>: los toast de Windows de un ejecutable sin empaquetar necesitan registrar
    /// un identificador de aplicación y un acceso directo, y no merece la pena (docs/spec-v4-toolbox.md §6). El clic ejecuta <paramref name="onClick"/>.
    /// </summary>
    private void Notify(string text, Action? onClick = null)
    {
        _balloonClick = onClick;
        // El texto del globo admite como máximo 255 caracteres.
        _icon.ShowBalloonTip(5000, "MyMouse", text.Length > 250 ? text[..250] + "…" : text, WinForms.ToolTipIcon.Info);
    }

    private static async Task RunLoggedAsync(string name, Func<Task> run)
    {
        try
        {
            await run();
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            FileLog.Write($"Error fatal en {name}: {ex}");
        }
    }

    private bool _disposed;

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _cts.Cancel();
        _foregroundTimer.Stop();
        _promotionRetry?.Stop();
        Updates.Dispose();
        _file.Dispose();
        Shapes.Dispose();
        _themeSync.Dispose();
        _widgets.Dispose();
        Media.Dispose();
        Volume.Dispose();
        _icon.Visible = false;
        _icon.Dispose();
    }
}
