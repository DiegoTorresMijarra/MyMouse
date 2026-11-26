using System.Diagnostics;
using MyMouse.HostCore;

namespace MyMouse.Host;

/// <summary>Aplicación de bandeja: icono de estado, menú y ciclo de vida del servidor y de adb.</summary>
internal sealed class TrayContext : ApplicationContext
{
    private readonly NotifyIcon _icon;
    private readonly ToolStripMenuItem _pauseItem;
    private readonly CancellationTokenSource _cts = new();
    private readonly SynchronizationContext _ui;
    private readonly AdbReverseKeeper _adb;

    private volatile bool _paused;
    private bool _connected;

    public TrayContext(HostConfig config)
    {
        _pauseItem = new ToolStripMenuItem("Pausar", null, (_, _) => TogglePause());
        var menu = new ContextMenuStrip();
        menu.Items.Add(_pauseItem);
        menu.Items.Add("Abrir carpeta de logs", null, (_, _) => OpenLogs());
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

        var server = new HostServer(new SendInputInjector(), () => _paused, config.Port);
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
        _icon.Visible = false;
        _icon.Dispose();
        base.ExitThreadCore();
    }
}
