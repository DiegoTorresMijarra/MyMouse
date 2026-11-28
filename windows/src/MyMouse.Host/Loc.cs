using System.Globalization;
using System.Resources;

namespace MyMouse.Host;

/// <summary>
/// Textos de la interfaz del PC: Strings.resx (inglés, el idioma por defecto) y Strings.es.resx (español). El idioma sigue al de Windows.
/// Los logs (<see cref="FileLog"/>) siguen en español: no son para el usuario.
/// </summary>
internal static class Loc
{
    private static readonly ResourceManager Manager = new("MyMouse.Host.Strings", typeof(Loc).Assembly);

    /// <summary>Idioma forzado (para pruebas); null = el de Windows.</summary>
    public static CultureInfo? Culture { get; set; }

    public static string T(string key) => Manager.GetString(key, Culture ?? CultureInfo.CurrentUICulture) ?? key;

    public static string F(string key, params object?[] args) => string.Format(Culture ?? CultureInfo.CurrentCulture, T(key), args);
}
