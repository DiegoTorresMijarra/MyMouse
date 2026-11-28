using System.ComponentModel;
using System.Diagnostics;
using System.Net.Http;
using System.Windows.Threading;
using MyMouse.HostCore.Shell;
using MyMouse.HostCore.Updates;

namespace MyMouse.Host;

/// <summary>
/// Las actualizaciones del PC (docs/spec-v5.1-actualizaciones.md §3 y §4): comprueba GitHub al arrancar (con 10 s de retraso) y cada 12 h,
/// descarga el instalador con progreso y lo lanza en silencio. Todo ocurre en el hilo de la interfaz.
/// </summary>
internal sealed class UpdateController : IDisposable
{
    private static readonly TimeSpan FirstCheckDelay = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan CheckInterval = TimeSpan.FromHours(12);

    private static string SettingsPath => Path.Combine(HostConfig.Directory, "updates.json");

    private readonly HttpClient _http = UpdateChecker.CreateClient();
    private readonly UpdateChecker _checker;
    private readonly DispatcherTimer _timer = new();
    private readonly Action<string> _notify;
    private readonly Action _quit;
    private readonly CancellationTokenSource _cts = new();

    public UpdateController(Action<string> notify, Action quit)
    {
        _notify = notify;
        _quit = quit;
        _checker = new UpdateChecker(_http);
        Manager = new UpdateManager(AppInfo.Version, _checker.FetchAsync, UpdateSettings.Load(SettingsPath), s => s.Save(SettingsPath), FileLog.Write);
        Manager.Changed += () => Changed?.Invoke();
        _timer.Tick += async (_, _) => await OnTimerAsync();
    }

    public UpdateManager Manager { get; }

    /// <summary>Cambió algo que muestran las ventanas o la bandeja (avisos, descarga).</summary>
    public event Action? Changed;

    /// <summary>Descargando o lanzando el instalador.</summary>
    public bool Busy { get; private set; }

    /// <summary>Progreso de la descarga, 0–1.</summary>
    public double Progress { get; private set; }

    /// <summary>Último fallo de la descarga, para el banner; null si no hay.</summary>
    public string? Error { get; private set; }

    /// <summary>¿Se instaló con el instalador? Entonces Actualizar lo hace solo; si es el zip portátil, abre la página de la release.</summary>
    public bool IsInstalled
    {
        get
        {
            var registry = new WindowsRegistry();
            var location = registry.GetString(InstallDetector.UninstallKey, "Inno Setup: App Path");
            return InstallDetector.IsInstalled(Environment.ProcessPath, Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), location);
        }
    }

    public void Start()
    {
        CleanOldDownloads();
        Schedule(FirstCheckDelay);
    }

    private void Schedule(TimeSpan delay)
    {
        _timer.Stop();
        _timer.Interval = delay;
        _timer.Start();
    }

    private async Task OnTimerAsync()
    {
        _timer.Stop();
        try
        {
            if (Manager.Settings.AutoCheck && !Busy) await CheckAsync(manual: false);
        }
        finally
        {
            Schedule(CheckInterval);
        }
    }

    /// <summary>Comprueba ahora. Con <paramref name="manual"/> (Ajustes → "Buscar ahora") el resultado se muestra en un cuadro de diálogo.</summary>
    public async Task<CheckOutcome> CheckAsync(bool manual)
    {
        CheckOutcome outcome;
        try
        {
            outcome = await Manager.CheckAsync(manual, _cts.Token);
        }
        catch (OperationCanceledException)
        {
            return CheckOutcome.Failed;
        }
        if (outcome == CheckOutcome.Available && Manager.ShouldNotify)
        {
            Manager.MarkNotified();
            _notify(Loc.F("uc_new_version", Manager.Offer!.Version.Display));
        }
        return outcome;
    }

    /// <summary>Actualizar en el banner. Instalada con el instalador: descarga y lanza el instalador en silencio, y se cierra. Portátil: abre la página.</summary>
    public async Task InstallAsync(ReleaseInfo release)
    {
        if (Busy) return;
        Error = null;
        var asset = Releases.PickAsset(release, AssetKind.WindowsInstaller);
        if (!IsInstalled || asset is null)
        {
            OpenPage(release);
            return;
        }

        Busy = true;
        Progress = 0;
        Changed?.Invoke();
        var path = Path.Combine(Path.GetTempPath(), asset.Name);
        try
        {
            var progress = new Progress<double>(p =>
            {
                Progress = p;
                Changed?.Invoke();
            });
            FileLog.Write($"Descargando {asset.Name} ({asset.Size} bytes)");
            await _checker.DownloadAsync(asset, path, progress, _cts.Token);
            FileLog.Write($"Lanzando el instalador: {path} {InstallDetector.SilentArguments}");
            Process.Start(new ProcessStartInfo(path, InstallDetector.SilentArguments) { UseShellExecute = false })?.Dispose();
            // El instalador espera a que MyMouse se cierre (AppMutex) y lo vuelve a abrir al terminar: salimos ya.
            _quit();
        }
        catch (Exception ex) when (ex is HttpRequestException or IOException or InvalidDataException or Win32Exception or OperationCanceledException or UnauthorizedAccessException)
        {
            FileLog.Write($"No se pudo actualizar: {ex.Message}");
            Error = ex is InvalidDataException ? Loc.T("uc_invalid") : Loc.T("uc_download_failed");
            Busy = false;
            Changed?.Invoke();
        }
    }

    public void OpenPage(ReleaseInfo release)
    {
        if (!Uri.TryCreate(release.PageUrl, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps) return;
        try
        {
            Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true })?.Dispose();
        }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException)
        {
            FileLog.Write($"No se pudo abrir {uri}: {ex.Message}");
        }
    }

    public void Skip()
    {
        Error = null;
        Manager.SkipOffered();
    }

    /// <summary>Borra los instaladores que quedaron en %TEMP% de actualizaciones anteriores (si alguno sigue en uso, se queda).</summary>
    private static void CleanOldDownloads()
    {
        try
        {
            foreach (var file in Directory.GetFiles(Path.GetTempPath(), "MyMouse-Setup-*.exe*"))
            {
                try { File.Delete(file); } catch (IOException) { } catch (UnauthorizedAccessException) { }
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Solo es limpieza.
        }
    }

    public void Dispose()
    {
        _timer.Stop();
        _cts.Cancel();
        _cts.Dispose();
        _http.Dispose();
    }
}
