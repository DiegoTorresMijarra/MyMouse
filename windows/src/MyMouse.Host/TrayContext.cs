using System.Diagnostics;
using MyMouse.HostCore;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Widgets;

namespace MyMouse.Host;

/// <summary>Aplicación de bandeja: icono de estado, menú y ciclo de vida del servidor y de adb.</summary>
internal sealed class TrayContext : ApplicationContext
{
    private readonly NotifyIcon _icon;
    private readonly ToolStripMenuItem _pauseItem;
    private readonly CancellationTokenSource _cts = new();
    private readonly SynchronizationContext _ui;
    private readonly AdbReverseKeeper _adb;
    private readonly GestureService _gestures;
    private readonly WasapiVolumeController _volume;
    private readonly WindowsMediaController _media;
    private readonly WidgetService _widgets;
    private readonly GestureConfigFile _gestureFile;
    private readonly System.Windows.Forms.Timer _foregroundTimer;
    private readonly Dictionary<string, DateTime> _lastUnassignedNotice = [];

    private volatile bool _paused;
    private bool _connected;

    public TrayContext(HostConfig config)
    {
        _pauseItem = new ToolStripMenuItem("Pausar", null, (_, _) => TogglePause());
        var menu = new ContextMenuStrip();
        menu.Items.Add(_pauseItem);
        menu.Items.Add("Editar gestos y widgets…", null, (_, _) => EditGestures());
        menu.Items.Add("Abrir carpeta de logs", null, (_, _) => OpenLogs());
        var autostartItem = new ToolStripMenuItem("Iniciar con Windows") { CheckOnClick = true };
        try
        {
            autostartItem.Checked = Autostart.IsEnabled;
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
        {
            autostartItem.Enabled = false;
        }
        autostartItem.CheckedChanged += (_, _) =>
        {
            try
            {
                Autostart.Set(autostartItem.Checked);
                FileLog.Write($"Iniciar con Windows: {(autostartItem.Checked ? "sí" : "no")}");
            }
            catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
            {
                FileLog.Write($"No se pudo cambiar el inicio con Windows: {ex.Message}");
            }
        };
        menu.Items.Add(autostartItem);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Salir", null, (_, _) => ExitThread());

        // Crear el primer control instala el contexto de sincronización de WinForms.
        _ui = SynchronizationContext.Current ?? new WindowsFormsSynchronizationContext();

        _icon = new NotifyIcon
        {
            Icon = TrayIcons.NoDevice,
            ContextMenuStrip = menu,
            Visible = true,
        };

        // Las formas y los botones del panel comparten ejecutor.
        var executor = new WindowsActionExecutor(() => _ui.Post(_ => TogglePause(), null));
        _gestures = new GestureService(executor);
        _gestures.Unassigned += name => _ui.Post(_ => NotifyUnassigned(name), null);
        _gestures.Failed += (name, ex) =>
        {
            FileLog.Write($"Error al ejecutar la forma '{name}': {ex.Message}");
            _ui.Post(_ => Notify($"No se pudo ejecutar '{name}': {ex.Message}", ToolTipIcon.Warning), null);
        };

        _volume = new WasapiVolumeController(FileLog.Write);
        _media = new WindowsMediaController(executor, FileLog.Write);
        _widgets = new WidgetService(executor, _volume, _media);
        _widgets.Failed += (what, ex) =>
        {
            FileLog.Write($"Error en el widget '{what}': {ex.Message}");
            _ui.Post(_ => Notify($"No se pudo ejecutar '{what}': {ex.Message}", ToolTipIcon.Warning), null);
        };

        _gestureFile = new GestureConfigFile();
        _gestureFile.Loaded += config =>
        {
            _gestures.SetConfig(config);
            _widgets.SetWidgets(config.Widgets);
        };
        _gestureFile.Problem += message => _ui.Post(_ => Notify(message, ToolTipIcon.Warning), null);
        _gestureFile.Start();

        // La chuleta del móvil cambia con la app activa si hay reglas por aplicación.
        _foregroundTimer = new System.Windows.Forms.Timer { Interval = 500 };
        _foregroundTimer.Tick += (_, _) => _gestures.SetForegroundApp(WindowsActionExecutor.ForegroundProcessName());
        _foregroundTimer.Start();

        var server = new HostServer(new SendInputInjector(), () => _paused, config.Port, _gestures, _widgets);
        server.Log += FileLog.Write;
        server.ConnectionChanged += connected => _ui.Post(_ => { _connected = connected; UpdateStatus(); }, null);

        _adb = new AdbReverseKeeper(config.AdbPath, config.Port);
        _adb.Log += FileLog.Write;
        _adb.StatusChanged += _ => _ui.Post(_ => UpdateStatus(), null);

        UpdateStatus();
        _ = RunLoggedAsync("servidor", () => server.RunAsync(_cts.Token));
        _ = RunLoggedAsync("adb", () => _adb.RunAsync(_cts.Token));
    }

    private void TogglePause()
    {
        _paused = !_paused;
        _pauseItem.Text = _paused ? "Reanudar" : "Pausar";
        UpdateStatus();
    }

    private void UpdateStatus()
    {
        var (status, icon) = (_paused, _connected, _adb.Status) switch
        {
            (true, _, _) => ("En pausa", TrayIcons.Paused),
            (_, true, _) => ("Conectado", TrayIcons.Connected),
            (_, _, AdbStatus.AdbNotFound) => ("adb no encontrado (ver config.json)", TrayIcons.NoDevice),
            (_, _, AdbStatus.NoDevice) => ("Sin móvil (¿cable? ¿depuración USB?)", TrayIcons.NoDevice),
            _ => ("Esperando a la app del móvil", TrayIcons.Waiting),
        };
        _icon.Icon = icon;
        // El tooltip de NotifyIcon admite como máximo 127 caracteres.
        _icon.Text = $"MyMouse: {status}";
    }

    private void NotifyUnassigned(string name)
    {
        FileLog.Write($"Forma sin asignar: {name}");
        // Un aviso por forma cada 30 s como mucho.
        if (_lastUnassignedNotice.TryGetValue(name, out var last) && DateTime.UtcNow - last < TimeSpan.FromSeconds(30)) return;
        _lastUnassignedNotice[name] = DateTime.UtcNow;
        Notify($"Forma '{name}' sin asignar. Añádela en \"Editar gestos y widgets…\".", ToolTipIcon.Info);
    }

    private void Notify(string text, ToolTipIcon icon)
    {
        // El texto del globo admite como máximo 255 caracteres.
        _icon.ShowBalloonTip(4000, "MyMouse", text.Length > 250 ? text[..250] + "…" : text, icon);
    }

    private static void EditGestures()
    {
        Process.Start(new ProcessStartInfo("notepad.exe", $"\"{GestureConfigFile.FilePath}\"") { UseShellExecute = true });
    }

    private static void OpenLogs()
    {
        Directory.CreateDirectory(FileLog.Directory);
        Process.Start(new ProcessStartInfo(FileLog.Directory) { UseShellExecute = true });
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

    protected override void ExitThreadCore()
    {
        _cts.Cancel();
        _foregroundTimer.Dispose();
        _gestureFile.Dispose();
        _widgets.Dispose();
        _media.Dispose();
        _volume.Dispose();
        _icon.Visible = false;
        _icon.Dispose();
        base.ExitThreadCore();
    }
}
