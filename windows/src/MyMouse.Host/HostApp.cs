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
using MyMouse.HostCore.Shapes;
using MyMouse.HostCore.Toolbox;
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
    private Action? _balloonClick;
    private volatile bool _paused;
    private bool _connected;

    public HostApp(HostConfig config)
    {
        _layout = ToolboxLayout.Load(_layoutPath);
        ConfigStorage = new GestureConfigStorage(GestureConfigFile.FilePath);
        StartMenu = StartMenuScanner.ForCurrentUser();

        // El icono de la bandeja: clic izquierdo abre o cierra la toolbox; el derecho, un menú mínimo.
        _pauseItem = new WinForms.ToolStripMenuItem("Pausar", null, (_, _) => TogglePause());
        var menu = new WinForms.ContextMenuStrip();
        menu.Items.Add("Abrir MyMouse", null, (_, _) => Toolbox.Open(fromPhone: false));
        menu.Items.Add(_pauseItem);
        menu.Items.Add(new WinForms.ToolStripSeparator());
        menu.Items.Add("Salir", null, (_, _) => Quit());
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
        Executor = new WindowsActionExecutor(() => Post(TogglePause));
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
            if (!Layout.ShowLiveGesture) Post(() => Notify($"No se pudo ejecutar '{name}': {ex.Message}"));
        };

        Volume = new WasapiVolumeController(FileLog.Write);
        Media = new WindowsMediaController(Executor, FileLog.Write);
        _widgets = new WidgetService(Executor, Volume, Media);
        _widgets.Failed += (what, ex) =>
        {
            FileLog.Write($"Error en el widget '{what}': {ex.Message}");
            Post(() => Notify($"No se pudo ejecutar '{what}': {ex.Message}"));
        };
        Volume.Changed += () => Post(() => StateChanged?.Invoke());
        Media.Changed += () => Post(() => StateChanged?.Invoke());

        Phone = new PhoneHub();
        Shapes = new ShapeSyncService(Phone, Path.Combine(HostConfig.Directory, "shapes-cache.json"));
        Shapes.Changed += () => Post(() => StateChanged?.Invoke());
        Shapes.NewShape += shape => Post(() => OnNewShape(shape.Name));
        Live = new LiveStrokeModel();
        Gestures.Outcome += o => Post(() => Live.OnOutcome(
            o.Status, o.Name, o.Binding?.HelpText, o.Binding is { } binding ? ActionText.Summary(binding.Action) : null, o.Error?.Message));
        Phone.Received += message => Post(() => OnPhoneMessage(message));
        Phone.Changed += () => Post(() =>
        {
            if (!Phone.SupportsToolbox) Live.Hide();
            StateChanged?.Invoke();
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

        var server = new HostServer(new SendInputInjector(), () => _paused, config.Port, Gestures, _widgets, Phone);
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
        _ = RunLoggedAsync("servidor", () => server.RunAsync(_cts.Token));
        _ = RunLoggedAsync("adb", () => _adb.RunAsync(_cts.Token));
    }

    // ---- Estado que leen las ventanas ----

    public WindowsActionExecutor Executor { get; }
    public GestureService Gestures { get; }
    public WasapiVolumeController Volume { get; }
    public WindowsMediaController Media { get; }
    public PhoneHub Phone { get; }
    public ShapeSyncService Shapes { get; }
    public LiveStrokeModel Live { get; }
    public GestureConfigStorage ConfigStorage { get; }
    public IStartMenuApps StartMenu { get; }

    /// <summary>Última configuración cargada de <c>gestures.json</c>.</summary>
    public GestureConfig Config { get; private set; } = GestureConfig.Empty;

    public bool Paused => _paused;

    public string StatusText { get; private set; } = "";

    /// <summary>Línea secundaria bajo el estado (null si no hace falta): en "Sin dispositivo", recuerda el modo Bluetooth.</summary>
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
            if (old.GlassEffect != value.GlassEffect) RecreateGlassWindows();
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

    /// <summary>
    /// El "Efecto cristal" se decide al crear cada ventana (con o sin <c>AllowsTransparency</c>): al cambiarlo se destruyen la toolbox y el
    /// gesto en vivo y se crean de nuevo al usarlas. Si la toolbox estaba abierta, se vuelve a abrir.
    /// </summary>
    private void RecreateGlassWindows()
    {
        var reopen = _toolbox is { IsOpen: true };
        _toolbox?.Destroy();
        _toolbox = null;
        _live?.Destroy();
        _live = null;
        if (reopen) Toolbox.Open(fromPhone: false);
    }

    public bool EditorOpen => _editor is { IsVisible: true };

    private ToolboxWindow Toolbox => _toolbox ??= new ToolboxWindow(this);

    private LiveGestureWindow LiveWindow => _live ??= new LiveGestureWindow(
        Live,
        () => _toolbox is { IsOpen: true, IsDocked: true } t ? t.CardHeightDip + 8 : 0,
        ShapePoints,
        name => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true),
        Layout.GlassEffect);

    /// <summary>Puntos de la forma ideal (catálogo) o de la primera muestra (forma propia del móvil), en 0–1.</summary>
    public IReadOnlyList<PointF>? ShapePoints(string name)
    {
        if (ShapeCatalog.ById.TryGetValue(name, out var shape)) return shape.Points;
        var own = Shapes.Shapes.FirstOrDefault(s => s.Name == name);
        return own is { Samples.Count: > 0 } ? ShapeGeometry.FromWire(own.Samples[0]) : null;
    }

    // ---- Del móvil ----

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
        Notify($"Nueva forma «{ShapeCatalog.Label(name)}»: ¿qué quieres que haga?", () => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));
    }

    // ---- Acciones de las ventanas ----

    public void TogglePause()
    {
        _paused = !_paused;
        _pauseItem.Text = _paused ? "Reanudar" : "Pausar";
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

    public void OpenLogs()
    {
        Directory.CreateDirectory(FileLog.Directory);
        Process.Start(new ProcessStartInfo(FileLog.Directory) { UseShellExecute = true });
    }

    public void ShowHelp()
    {
        Dialogs.Message(null, "MyMouse",
            "Abre y cierra esta toolbox con un clic en el icono de la bandeja, o desde el móvil con 4 dedos hacia arriba y hacia abajo.\n\n" +
            "Modo gestos: mantén 4 dedos quietos en el trackpad y dibuja una forma; aquí verás el trazo y lo que hace.\n\n" +
            "«Gestos y widgets» abre el editor: asigna acciones a las formas, crea formas nuevas con el ratón y configura el panel del móvil.");
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
        StatusHint = null;
        var (status, icon, brush) = (_paused, _connected, _adb.Status) switch
        {
            (true, _, _) => ("En pausa", TrayIcons.Paused, Theme.Gray),
            (_, true, _) => (_adb.DeviceName is { } name ? $"Conectado · {name} por USB" : "Conectado", TrayIcons.Connected, Theme.Green),
            (_, _, AdbStatus.AdbNotFound) => ("Sin dispositivo (adb no encontrado: ver config.json)", TrayIcons.NoDevice, Theme.Red),
            (_, _, AdbStatus.NoDevice) => ("Sin dispositivo (¿cable? ¿depuración USB?)", TrayIcons.NoDevice, Theme.Red),
            _ => ("Esperando al móvil", TrayIcons.Waiting, Theme.Amber),
        };
        StatusText = status;
        StatusBrush = brush;
        if (!_paused && !_connected && _adb.Status is AdbStatus.NoDevice or AdbStatus.AdbNotFound)
            StatusHint = SinDispositivoHint;
        _icon.Icon = icon;
        // El tooltip de NotifyIcon admite como máximo 127 caracteres.
        _icon.Text = ($"MyMouse: {status}").Length > 120 ? ("MyMouse: " + status)[..120] : "MyMouse: " + status;
        StateChanged?.Invoke();
    }

    /// <summary>Es normal que el host diga "Sin dispositivo" en el modo Bluetooth (docs/spec-v4.5-estilo.md §4).</summary>
    public const string SinDispositivoHint = "¿Usas el modo Bluetooth? En ese modo el móvil funciona como ratón sin MyMouse en el PC";

    private readonly Dictionary<string, DateTime> _lastUnassignedNotice = [];

    private void NotifyUnassigned(string name)
    {
        // Un aviso por forma cada 30 s como mucho.
        if (_lastUnassignedNotice.TryGetValue(name, out var last) && DateTime.UtcNow - last < TimeSpan.FromSeconds(30)) return;
        _lastUnassignedNotice[name] = DateTime.UtcNow;
        Notify($"Forma '{name}' sin asignar. Asígnala en la toolbox → Gestos.", () => OpenEditor(EditorTab.Gestures, gesture: name, focusAction: true));
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
        _file.Dispose();
        Shapes.Dispose();
        _widgets.Dispose();
        Media.Dispose();
        Volume.Dispose();
        _icon.Visible = false;
        _icon.Dispose();
    }
}
