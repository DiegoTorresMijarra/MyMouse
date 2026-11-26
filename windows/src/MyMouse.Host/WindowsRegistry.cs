using Microsoft.Win32;
using MyMouse.HostCore.Shell;

namespace MyMouse.Host;

/// <summary>El registro real de Windows (<c>HKEY_CURRENT_USER</c>) para <see cref="IRegistry"/>. Lo que no se puede leer se trata como si no existiera.</summary>
internal sealed class WindowsRegistry : IRegistry
{
    public IReadOnlyList<string> GetSubKeyNames(string path)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(path);
            return key?.GetSubKeyNames() ?? [];
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
        {
            return [];
        }
    }

    public string? GetString(string path, string name)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(path);
            return key?.GetValue(name) as string;
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
        {
            return null;
        }
    }

    public int? GetDword(string path, string name)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(path);
            return key?.GetValue(name) is int value ? value : null;
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
        {
            return null;
        }
    }

    public void SetDword(string path, string name, int value)
    {
        using var key = Registry.CurrentUser.OpenSubKey(path, writable: true) ?? throw new IOException($"No existe la clave {path}");
        key.SetValue(name, value, RegistryValueKind.DWord);
    }
}
