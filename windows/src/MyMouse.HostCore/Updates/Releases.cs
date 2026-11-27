using System.Text.Json;

namespace MyMouse.HostCore.Updates;

/// <summary>Un fichero de una release de GitHub. <see cref="Sha256"/> viene del campo <c>digest</c> de la API cuando existe.</summary>
public sealed record ReleaseAsset(string Name, long Size, string DownloadUrl, string? Sha256);

/// <summary>Una release de GitHub, ya validada: etiqueta <c>vX.Y[.Z]</c> y no borrador.</summary>
public sealed record ReleaseInfo(string Tag, AppVersion Version, string Title, string Notes, string PageUrl, bool Prerelease, IReadOnlyList<ReleaseAsset> Assets);

public enum AssetKind
{
    /// <summary>El instalador del PC: <c>MyMouse-Setup-*.exe</c>.</summary>
    WindowsInstaller,

    /// <summary>La versión portátil: <c>MyMouse-Windows-*.zip</c>.</summary>
    WindowsZip,

    /// <summary>El APK del móvil: <c>MyMouse-*.apk</c>.</summary>
    AndroidApk,
}

/// <summary>Lógica pura sobre la respuesta de <c>GET /repos/…/releases</c>: parseo, elección de la más alta y elección del fichero.</summary>
public static class Releases
{
    /// <summary>Repositorio público del que salen las releases.</summary>
    public const string Repository = "DiegoTorresMijarra/MyMouse";

    public const string ListUrl = "https://api.github.com/repos/" + Repository + "/releases?per_page=10";

    public const string UserAgent = "MyMouse-Updater";

    /// <summary>
    /// Convierte el JSON de la API en releases válidas. Se ignoran los borradores y las etiquetas que no son <c>vX.Y[.Z]</c>. Un JSON que no es una
    /// lista (p. ej. el error de límite de peticiones, un objeto) lanza <see cref="FormatException"/>.
    /// </summary>
    public static IReadOnlyList<ReleaseInfo> Parse(string json)
    {
        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(json);
        }
        catch (JsonException ex)
        {
            throw new FormatException("La respuesta de GitHub no es JSON válido", ex);
        }

        using (doc)
        {
            if (doc.RootElement.ValueKind != JsonValueKind.Array)
                throw new FormatException("La respuesta de GitHub no es una lista de releases");

            var list = new List<ReleaseInfo>();
            foreach (var item in doc.RootElement.EnumerateArray())
            {
                if (item.ValueKind != JsonValueKind.Object) continue;
                if (Bool(item, "draft")) continue;
                var tag = Str(item, "tag_name");
                if (AppVersion.ParseTag(tag) is not { } version) continue;
                var title = Str(item, "name");
                list.Add(new ReleaseInfo(
                    tag, version,
                    string.IsNullOrWhiteSpace(title) ? tag : title,
                    Str(item, "body"),
                    Str(item, "html_url"),
                    Bool(item, "prerelease"),
                    ParseAssets(item)));
            }
            return list;
        }
    }

    private static List<ReleaseAsset> ParseAssets(JsonElement release)
    {
        var assets = new List<ReleaseAsset>();
        if (!release.TryGetProperty("assets", out var array) || array.ValueKind != JsonValueKind.Array) return assets;
        foreach (var a in array.EnumerateArray())
        {
            if (a.ValueKind != JsonValueKind.Object) continue;
            var name = Str(a, "name");
            var url = Str(a, "browser_download_url");
            if (name.Length == 0 || url.Length == 0) continue;
            var size = a.TryGetProperty("size", out var s) && s.ValueKind == JsonValueKind.Number && s.TryGetInt64(out var n) ? n : 0;
            var digest = Str(a, "digest");
            var sha = digest.StartsWith("sha256:", StringComparison.OrdinalIgnoreCase) && digest.Length == 7 + 64 ? digest[7..].ToLowerInvariant() : null;
            assets.Add(new ReleaseAsset(name, size, url, sha));
        }
        return assets;
    }

    /// <summary>La release de versión más alta (no se usa <c>/releases/latest</c>: todas son prerelease). Null si no hay ninguna.</summary>
    public static ReleaseInfo? Highest(IEnumerable<ReleaseInfo> releases) =>
        releases.Aggregate((ReleaseInfo?)null, (best, r) => best is null || r.Version > best.Version ? r : best);

    /// <summary>La release de una versión concreta (por números: <c>5.0</c> = <c>5.0.0</c>), o null.</summary>
    public static ReleaseInfo? Find(IEnumerable<ReleaseInfo> releases, AppVersion version) =>
        releases.FirstOrDefault(r => r.Version.SameNumbers(version));

    /// <summary>El fichero de la release para ese destino, o null si no está. Entre varios, el primero.</summary>
    public static ReleaseAsset? PickAsset(ReleaseInfo release, AssetKind kind) =>
        release.Assets.FirstOrDefault(a => Matches(a.Name, kind));

    public static bool Matches(string name, AssetKind kind) => kind switch
    {
        AssetKind.WindowsInstaller => name.StartsWith("MyMouse-Setup-", StringComparison.OrdinalIgnoreCase) && name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase),
        AssetKind.WindowsZip => name.StartsWith("MyMouse-Windows-", StringComparison.OrdinalIgnoreCase) && name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase),
        AssetKind.AndroidApk => name.StartsWith("MyMouse-", StringComparison.OrdinalIgnoreCase) && name.EndsWith(".apk", StringComparison.OrdinalIgnoreCase),
        _ => false,
    };

    /// <summary>Solo se descarga de GitHub por HTTPS (el enlace de la API es de <c>github.com</c>; GitHub redirige luego a su CDN).</summary>
    public static bool IsTrustedDownloadUrl(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
        && uri.Host.Equals("github.com", StringComparison.OrdinalIgnoreCase);

    private static string Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";

    private static bool Bool(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.True;
}
