using System.Globalization;
using System.Text.RegularExpressions;

namespace MyMouse.HostCore.Updates;

/// <summary>
/// Versión de MyMouse (docs/spec-v5.1-actualizaciones.md §3): <c>X.Y</c> o <c>X.Y.Z</c>. Las releases usan <c>5.0</c>, <c>5.0.1</c>, <c>5.1</c>
/// (etiqueta <c>v5.1</c>) y las compilaciones de rama <c>0.0-dev.N</c>, que son versiones "dev": no avisan nunca. Comparar versiones
/// es comparar (mayor, menor, parche) con el parche ausente como 0, de modo que <c>5.0</c> y <c>5.0.0</c> son iguales.
/// </summary>
public readonly partial record struct AppVersion(int Major, int Minor, int Patch, bool IsDev) : IComparable<AppVersion>
{
    // v?X.Y[.Z][.W] [-sufijo] [+metadatos]. El cuarto número (1.0.0.0 de AssemblyVersion) se admite y se ignora.
    [GeneratedRegex(@"^\s*[vV]?(\d{1,6})\.(\d{1,6})(?:\.(\d{1,6}))?(?:\.\d{1,6})?(?:-([0-9A-Za-z.\-]+))?(?:\+[0-9A-Za-z.\-]+)?\s*$")]
    private static partial Regex LocalPattern();

    // Etiqueta de una release: exactamente vX.Y o vX.Y.Z.
    [GeneratedRegex(@"^v(\d{1,6})\.(\d{1,6})(?:\.(\d{1,6}))?$")]
    private static partial Regex TagPattern();

    /// <summary>
    /// Versión propia o del otro dispositivo: admite <c>v</c> inicial, sufijo (<c>-dev.12</c>) y metadatos (<c>+hash</c>). Es "dev" si lleva un
    /// sufijo con "dev" o es la <c>0.0</c>. Devuelve <c>null</c> si no se entiende.
    /// </summary>
    public static AppVersion? Parse(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return null;
        var m = LocalPattern().Match(text);
        if (!m.Success) return null;
        var (major, minor, patch) = Numbers(m);
        if (major is null) return null;
        var suffix = m.Groups[4].Success ? m.Groups[4].Value : "";
        var dev = suffix.Contains("dev", StringComparison.OrdinalIgnoreCase) || (major == 0 && minor == 0);
        return new AppVersion(major.Value, minor!.Value, patch!.Value, dev);
    }

    /// <summary>Etiqueta de una release de GitHub: solo <c>vX.Y</c> o <c>vX.Y.Z</c>. Lo demás (borradores raros, <c>nightly</c>, <c>v5.1-rc1</c>) es <c>null</c>.</summary>
    public static AppVersion? ParseTag(string? tag)
    {
        if (string.IsNullOrEmpty(tag)) return null;
        var m = TagPattern().Match(tag);
        if (!m.Success) return null;
        var (major, minor, patch) = Numbers(m);
        return major is null ? null : new AppVersion(major.Value, minor!.Value, patch!.Value, false);
    }

    private static (int? Major, int? Minor, int? Patch) Numbers(Match m)
    {
        if (!int.TryParse(m.Groups[1].Value, NumberStyles.None, CultureInfo.InvariantCulture, out var major)) return (null, null, null);
        if (!int.TryParse(m.Groups[2].Value, NumberStyles.None, CultureInfo.InvariantCulture, out var minor)) return (null, null, null);
        var patch = 0;
        if (m.Groups[3].Success && !int.TryParse(m.Groups[3].Value, NumberStyles.None, CultureInfo.InvariantCulture, out patch)) return (null, null, null);
        return (major, minor, patch);
    }

    /// <summary>Compara solo los números (la marca "dev" no cuenta).</summary>
    public int CompareTo(AppVersion other)
    {
        var c = Major.CompareTo(other.Major);
        if (c != 0) return c;
        c = Minor.CompareTo(other.Minor);
        return c != 0 ? c : Patch.CompareTo(other.Patch);
    }

    public static bool operator <(AppVersion a, AppVersion b) => a.CompareTo(b) < 0;
    public static bool operator >(AppVersion a, AppVersion b) => a.CompareTo(b) > 0;
    public static bool operator <=(AppVersion a, AppVersion b) => a.CompareTo(b) <= 0;
    public static bool operator >=(AppVersion a, AppVersion b) => a.CompareTo(b) >= 0;

    /// <summary>¿Mismos números? (<c>5.0</c> y <c>5.0.0</c> son iguales; <c>record struct</c> distinguiría por <see cref="IsDev"/>.)</summary>
    public bool SameNumbers(AppVersion other) => CompareTo(other) == 0;

    /// <summary>Texto para mostrar: <c>5.1</c>, o <c>5.0.1</c> si hay parche.</summary>
    public string Display => Patch == 0 ? $"{Major}.{Minor}" : $"{Major}.{Minor}.{Patch}";

    public override string ToString() => Display;
}
