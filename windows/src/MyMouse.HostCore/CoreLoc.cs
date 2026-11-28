using System.Globalization;
using System.Resources;

namespace MyMouse.HostCore;

/// <summary>
/// Textos de HostCore que llegan a la pantalla (CoreStrings.resx en inglés y CoreStrings.es.resx en español; el idioma sigue al de Windows).
/// Los registros (logs) y los errores técnicos de los ficheros siguen en español.
/// </summary>
public static class CoreLoc
{
    private static readonly ResourceManager Manager = new("MyMouse.HostCore.CoreStrings", typeof(CoreLoc).Assembly);

    /// <summary>Idioma forzado (los tests lo fijan); null = el de Windows.</summary>
    public static CultureInfo? Culture { get; set; }

    public static string T(string key) => Manager.GetString(key, Culture ?? CultureInfo.CurrentUICulture) ?? key;

    public static string F(string key, params object?[] args) => string.Format(Culture ?? CultureInfo.CurrentCulture, T(key), args);
}
