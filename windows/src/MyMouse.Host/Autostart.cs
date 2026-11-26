using Microsoft.Win32;

namespace MyMouse.Host;

/// <summary>
/// "Iniciar con Windows" (docs/spec-v3-bluetooth.md §3): entrada en HKCU\...\Run, sin permisos de administrador.
/// Si el ejecutable se ha movido, la entrada guardada no coincide y cuenta como desactivado.
/// </summary>
internal static class Autostart
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "MyMouse";

    private static string Command => $"\"{Environment.ProcessPath}\"";

    public static bool IsEnabled
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey);
            return string.Equals(key?.GetValue(ValueName) as string, Command, StringComparison.OrdinalIgnoreCase);
        }
    }

    public static void Set(bool enabled)
    {
        using var key = Registry.CurrentUser.CreateSubKey(RunKey);
        if (enabled) key.SetValue(ValueName, Command);
        else key.DeleteValue(ValueName, throwOnMissingValue: false);
    }
}
