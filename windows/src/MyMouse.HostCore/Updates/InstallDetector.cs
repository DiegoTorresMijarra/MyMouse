namespace MyMouse.HostCore.Updates;

/// <summary>¿Se instaló MyMouse con el instalador (se puede actualizar en silencio) o es la versión portátil (zip)?</summary>
public static class InstallDetector
{
    /// <summary>Misma clave que <c>AppId</c> de installer/MyMouse.iss, con el sufijo <c>_is1</c> que usa Inno Setup para la clave de desinstalación.</summary>
    public const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\{779D29A3-9DE7-4C7F-B206-F386F45F1C5F}_is1";

    /// <summary>
    /// Instalada si el exe está bajo <c>%LOCALAPPDATA%\Programs\MyMouse</c> o la clave de desinstalación existe y su carpeta de instalación es la del exe.
    /// (Solo la clave no basta: un zip descomprimido en otra carpeta de alguien que también tiene el instalador es portátil.)
    /// </summary>
    public static bool IsInstalled(string? exePath, string localAppData, string? uninstallInstallLocation)
    {
        if (string.IsNullOrEmpty(exePath)) return false;
        var normalizedExe = Normalize(exePath);
        var exeDir = normalizedExe[..Math.Max(0, normalizedExe.LastIndexOf('\\'))];
        var defaultDir = Normalize(localAppData) + @"\Programs\MyMouse";
        if (exeDir.Equals(defaultDir, StringComparison.OrdinalIgnoreCase)) return true;
        return !string.IsNullOrEmpty(uninstallInstallLocation)
            && Normalize(uninstallInstallLocation).Equals(exeDir, StringComparison.OrdinalIgnoreCase);
    }

    private static string Normalize(string? path) =>
        (path ?? "").Replace('/', '\\').TrimEnd('\\');

    /// <summary>
    /// Argumentos del instalador para actualizar en silencio. <c>/RELAUNCH=1</c> activa la entrada [Run] de MyMouse.iss que vuelve a abrir MyMouse al
    /// terminar. No se pasa <c>/RESTARTAPPLICATIONS</c>: el Restart Manager de Windows solo reinicia apps que se registraron para ello y MyMouse
    /// no lo hace, así que el relanzado lo hace el propio instalador (determinista).
    /// </summary>
    public const string SilentArguments = "/SILENT /CLOSEAPPLICATIONS /RELAUNCH=1";
}
