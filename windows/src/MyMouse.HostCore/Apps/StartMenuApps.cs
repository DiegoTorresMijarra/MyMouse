using System.Globalization;
using System.Text;

namespace MyMouse.HostCore.Apps;

/// <summary>Una app instalada: su nombre y la ruta de su acceso directo del menú Inicio (se puede abrir tal cual).</summary>
public sealed record InstalledApp(string Name, string Path);

/// <summary>Origen de la lista "Apps instaladas" del editor (docs/spec-v4-toolbox.md §4.1). Detrás de una interfaz para poder probar y sustituir.</summary>
public interface IStartMenuApps
{
    IReadOnlyList<InstalledApp> List();
}

/// <summary>Lee los accesos directos (<c>.lnk</c>) del menú Inicio de las carpetas dadas: la del usuario y la común.</summary>
public sealed class StartMenuScanner(IEnumerable<string> roots) : IStartMenuApps
{
    private static readonly string[] Ignored = ["uninstall", "desinstalar", "readme", "léeme", "leeme", "help", "ayuda", "manual"];

    /// <summary>Las carpetas habituales de Windows: <c>%AppData%\…\Start Menu\Programs</c> y <c>%ProgramData%\…</c>.</summary>
    public static StartMenuScanner ForCurrentUser() => new(
    [
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu), "Programs"),
    ]);

    public IReadOnlyList<InstalledApp> List()
    {
        var apps = new Dictionary<string, InstalledApp>(StringComparer.OrdinalIgnoreCase);
        foreach (var root in roots)
        {
            IEnumerable<string> files;
            try
            {
                if (!Directory.Exists(root)) continue;
                files = Directory.EnumerateFiles(root, "*.lnk", new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true });
                foreach (var file in files)
                {
                    var name = Path.GetFileNameWithoutExtension(file);
                    if (name.Length == 0 || Ignored.Any(i => name.Contains(i, StringComparison.OrdinalIgnoreCase))) continue;
                    apps.TryAdd(name, new InstalledApp(name, file)); // gana la primera carpeta (la del usuario)
                }
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                // Carpeta ilegible: se sigue con las demás.
            }
        }
        return [.. apps.Values.OrderBy(a => a.Name, StringComparer.CurrentCultureIgnoreCase)];
    }
}

/// <summary>Búsqueda en la lista de apps: sin mayúsculas ni acentos, todas las palabras deben aparecer; primero las que empiezan por lo escrito.</summary>
public static class AppSearch
{
    public static IReadOnlyList<InstalledApp> Filter(IReadOnlyList<InstalledApp> apps, string query)
    {
        var tokens = Fold(query).Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (tokens.Length == 0) return apps;
        return
        [
            .. apps
                .Select(a => (App: a, Name: Fold(a.Name)))
                .Where(x => tokens.All(t => x.Name.Contains(t, StringComparison.Ordinal)))
                .OrderBy(x => x.Name.StartsWith(tokens[0], StringComparison.Ordinal) ? 0 : 1)
                .ThenBy(x => x.App.Name, StringComparer.CurrentCultureIgnoreCase)
                .Select(x => x.App),
        ];
    }

    private static string Fold(string text)
    {
        var decomposed = text.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(decomposed.Length);
        foreach (var c in decomposed)
            if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark) sb.Append(char.ToLowerInvariant(c));
        return sb.ToString().Trim();
    }
}
