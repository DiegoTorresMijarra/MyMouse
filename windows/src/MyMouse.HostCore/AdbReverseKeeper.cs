using System.Diagnostics;
using MyMouse.Protocol;

namespace MyMouse.HostCore;

public enum AdbStatus
{
    Unknown,
    AdbNotFound,
    NoDevice,
    Ready,
}

/// <summary>
/// Ejecuta periódicamente <c>adb reverse tcp:PORT tcp:PORT</c>. Es idempotente y barato,
/// y cubre las reconexiones del cable sin vigilar eventos USB.
/// </summary>
public sealed class AdbReverseKeeper(string? configuredAdbPath, int port = ProtocolInfo.DefaultPort)
{
    private static readonly TimeSpan Interval = TimeSpan.FromSeconds(3);
    private static readonly TimeSpan CommandTimeout = TimeSpan.FromSeconds(5);

    public AdbStatus Status { get; private set; } = AdbStatus.Unknown;

    /// <summary>Modelo del móvil conectado por USB (p. ej. "Poco X8 Pro Max"), o null si no se conoce.</summary>
    public string? DeviceName { get; private set; }
    public event Action<AdbStatus>? StatusChanged;
    public event Action<string>? Log;

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        var adb = FindAdb(configuredAdbPath);
        if (adb is null)
        {
            SetStatus(AdbStatus.AdbNotFound);
            Log?.Invoke("No se encontró adb.exe (ni junto a MyMouse ni en el PATH). Instala MyMouse con el instalador, que lo incluye, o configura su ruta en config.json.");
            return;
        }

        Log?.Invoke($"Usando adb: {adb}");
        using var timer = new PeriodicTimer(Interval);
        do
        {
            var (ok, _) = await RunAdbAsync(adb, $"reverse tcp:{port} tcp:{port}", cancellationToken).ConfigureAwait(false);
            if (ok && DeviceName is null)
            {
                var (_, devices) = await RunAdbAsync(adb, "devices -l", cancellationToken).ConfigureAwait(false);
                DeviceName = ParseModel(devices);
            }
            else if (!ok)
            {
                DeviceName = null;
            }
            SetStatus(ok ? AdbStatus.Ready : AdbStatus.NoDevice);
        }
        while (await timer.WaitForNextTickAsync(cancellationToken).ConfigureAwait(false));
    }

    /// <summary>
    /// Dónde está adb (docs/spec-v5-bluetooth-completo.md §5): primero la ruta puesta a mano en <c>config.json</c>, después **junto al
    /// ejecutable** (el instalador lo trae), luego el PATH y por último el SDK de Android. Los parámetros solo existen para las
    /// pruebas; sin ellos se usa el sistema real.
    /// </summary>
    public static string? FindAdb(
        string? configuredPath,
        string? exeDirectory = null,
        string? pathVariable = null,
        string? localAppData = null,
        Func<string, bool>? fileExists = null,
        bool? windows = null)
    {
        fileExists ??= File.Exists;
        var exe = (windows ?? OperatingSystem.IsWindows()) ? "adb.exe" : "adb";
        var candidates = new List<string?> { configuredPath };

        exeDirectory ??= ExeDirectory();
        if (!string.IsNullOrEmpty(exeDirectory)) candidates.Add(Path.Combine(exeDirectory, exe));

        var path = pathVariable ?? Environment.GetEnvironmentVariable("PATH") ?? "";
        candidates.AddRange(path.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries)
            .Select(dir => Path.Combine(dir.Trim('"'), exe)));

        localAppData ??= Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (!string.IsNullOrEmpty(localAppData))
            candidates.Add(Path.Combine(localAppData, "Android", "Sdk", "platform-tools", exe));

        return candidates.FirstOrDefault(c => !string.IsNullOrWhiteSpace(c) && fileExists(c));
    }

    /// <summary>Carpeta del ejecutable en marcha (con el exe de un solo fichero, <see cref="AppContext.BaseDirectory"/> también vale, pero esto no depende de la extracción).</summary>
    private static string? ExeDirectory() =>
        Path.GetDirectoryName(Environment.ProcessPath) ?? AppContext.BaseDirectory;

    /// <summary>
    /// Modelo del primer dispositivo de la salida de <c>adb devices -l</c> (<c>... device usb:1-2 product:x model:Poco_X8_Pro_Max ...</c>),
    /// con los guiones bajos como espacios. Null si no hay ninguno.
    /// </summary>
    public static string? ParseModel(string devicesOutput)
    {
        foreach (var line in devicesOutput.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (!line.Contains("\tdevice", StringComparison.Ordinal) && !line.Contains(" device ", StringComparison.Ordinal)) continue;
            foreach (var token in line.Split([' ', '\t'], StringSplitOptions.RemoveEmptyEntries))
                if (token.StartsWith("model:", StringComparison.Ordinal) && token.Length > 6) return token[6..].Replace('_', ' ');
        }
        return null;
    }

    private static async Task<(bool Ok, string Output)> RunAdbAsync(string adb, string arguments, CancellationToken cancellationToken)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo(adb, arguments)
            {
                CreateNoWindow = true,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            },
        };

        try
        {
            process.Start();
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(CommandTimeout);
            var output = process.StandardOutput.ReadToEndAsync(timeout.Token);
            await process.WaitForExitAsync(timeout.Token).ConfigureAwait(false);
            return (process.ExitCode == 0, await output.ConfigureAwait(false));
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            try { process.Kill(); } catch (InvalidOperationException) { }
            return (false, "");
        }
        catch (System.ComponentModel.Win32Exception)
        {
            return (false, "");
        }
    }

    private void SetStatus(AdbStatus status)
    {
        if (Status == status) return;
        Status = status;
        StatusChanged?.Invoke(status);
    }
}
