using System.Reflection;

namespace MyMouse.Host;

/// <summary>La versión de esta compilación (docs/spec-v5.1-actualizaciones.md §3).</summary>
internal static class AppInfo
{
    /// <summary>
    /// La versión informativa del ensamblado: CI la fija con <c>-p:InformationalVersion</c> a la misma que usa el APK (<c>5.0</c> en una release,
    /// <c>0.0-dev.N</c> en el resto). Sin ella (compilación local) vale <c>0.0-dev</c>, de desarrollo. Se quitan los metadatos <c>+commit</c>.
    /// </summary>
    public static string Version { get; } = Read();

    private static string Read()
    {
        var text = Assembly.GetExecutingAssembly().GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
        if (string.IsNullOrWhiteSpace(text)) return "0.0-dev";
        var plus = text.IndexOf('+');
        return plus >= 0 ? text[..plus] : text;
    }
}
