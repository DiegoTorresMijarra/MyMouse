namespace MyMouse.HostCore.Shell;

/// <summary>
/// El registro de Windows visto por la lógica del icono de la barra de tareas. Todas las rutas son de <c>HKEY_CURRENT_USER</c> y
/// relativas a ella. Existe para poder probar la búsqueda de la clave sin tocar el registro real.
/// </summary>
public interface IRegistry
{
    /// <summary>Nombres de las subclaves de <paramref name="path"/> (vacío si no existe).</summary>
    IReadOnlyList<string> GetSubKeyNames(string path);

    /// <summary>Valor de texto, o null si la clave o el valor no existen.</summary>
    string? GetString(string path, string name);

    /// <summary>Valor DWORD, o null si la clave o el valor no existen.</summary>
    int? GetDword(string path, string name);

    void SetDword(string path, string name, int value);
}

public enum PromotionOutcome
{
    /// <summary>Se encontró el icono y quedó como se pedía.</summary>
    Done,

    /// <summary>Windows no tiene (todavía) la clave del icono: Windows 10, o el icono aún no se ha registrado.</summary>
    NoKey,

    /// <summary>El registro no se pudo escribir.</summary>
    Failed,
}

/// <summary>
/// "Mostrar siempre el icono en la barra de tareas" (docs/spec-v4.7-universo.md §3.4). Windows 11 no deja que una app saque su icono
/// de la bandeja oculta con una API pública, pero guarda un valor por icono en
/// <c>HKCU\Control Panel\NotifyIconSettings\&lt;id&gt;</c>: <c>ExecutablePath</c> dice de qué programa es e <c>IsPromoted</c> = 1 lo
/// deja visible. Aquí solo está la lógica de buscar la subclave y fijar el valor; el registro real va detrás de <see cref="IRegistry"/>.
/// </summary>
public static class TaskbarIconPromotion
{
    public const string BasePath = @"Control Panel\NotifyIconSettings";
    public const string ExecutablePathValue = "ExecutablePath";
    public const string IsPromotedValue = "IsPromoted";

    /// <summary>Windows 11 (compilación 22000 o posterior): el primero con la clave <c>NotifyIconSettings</c>.</summary>
    public const int FirstBuildWithKey = 22000;

    /// <summary>Segundos que se espera antes del segundo (y último) intento.</summary>
    public static readonly TimeSpan RetryDelay = TimeSpan.FromSeconds(10);

    public const int MaxAttempts = 2;

    public static bool IsSupported(int windowsBuild) => windowsBuild >= FirstBuildWithKey;

    /// <summary>¿Hay que volver a intentarlo? Solo si no había clave y quedan intentos.</summary>
    public static bool ShouldRetry(PromotionOutcome outcome, int attemptsDone) => outcome == PromotionOutcome.NoKey && attemptsDone < MaxAttempts;

    /// <summary>Ruta comparable: sin comillas ni espacios en los extremos, con <c>\</c> y sin <c>\</c> final.</summary>
    public static string Normalize(string path) => path.Trim().Trim('"').Replace('/', '\\').TrimEnd('\\');

    /// <summary>Subclaves de <c>NotifyIconSettings</c> cuyo <c>ExecutablePath</c> es <paramref name="exePath"/> (sin distinguir mayúsculas).</summary>
    public static IReadOnlyList<string> FindKeys(IRegistry registry, string exePath)
    {
        var wanted = Normalize(exePath);
        if (wanted.Length == 0) return [];
        var found = new List<string>();
        foreach (var name in registry.GetSubKeyNames(BasePath))
        {
            var path = $@"{BasePath}\{name}";
            if (registry.GetString(path, ExecutablePathValue) is { } value &&
                string.Equals(Normalize(value), wanted, StringComparison.OrdinalIgnoreCase))
                found.Add(path);
        }
        return found;
    }

    /// <summary>Pone <c>IsPromoted</c> a 1 (visible) o a 0 en todas las subclaves del programa.</summary>
    public static PromotionOutcome SetPromoted(IRegistry registry, string exePath, bool promoted)
    {
        var keys = FindKeys(registry, exePath);
        if (keys.Count == 0) return PromotionOutcome.NoKey;
        try
        {
            foreach (var key in keys)
                if (registry.GetDword(key, IsPromotedValue) != (promoted ? 1 : 0))
                    registry.SetDword(key, IsPromotedValue, promoted ? 1 : 0);
            return PromotionOutcome.Done;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
        {
            return PromotionOutcome.Failed;
        }
    }

    /// <summary>true si todas las subclaves del programa tienen <c>IsPromoted</c> = 1; null si no hay ninguna.</summary>
    public static bool? IsPromoted(IRegistry registry, string exePath)
    {
        var keys = FindKeys(registry, exePath);
        return keys.Count == 0 ? null : keys.All(k => registry.GetDword(k, IsPromotedValue) == 1);
    }
}
