using System.Text.Json;

namespace MyMouse.HostCore.Updates;

/// <summary>Ajustes de las actualizaciones, en <c>%APPDATA%\MyMouse\updates.json</c>.</summary>
public sealed record UpdateSettings
{
    /// <summary>"Buscar actualizaciones automáticamente": al arrancar (con retraso) y cada 12 h.</summary>
    public bool AutoCheck { get; init; } = true;

    /// <summary>Versión que el usuario omitió ("Omitir"): no se vuelve a avisar de ella, sí de la siguiente.</summary>
    public string? SkippedVersion { get; init; }

    /// <summary>Última versión de la que ya se mostró el globo de la bandeja (uno por versión).</summary>
    public string? NotifiedVersion { get; init; }

    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    /// <summary>Lee el fichero; si no existe o está roto, los valores por defecto.</summary>
    public static UpdateSettings Load(string path)
    {
        try
        {
            if (File.Exists(path))
                return JsonSerializer.Deserialize<UpdateSettings>(File.ReadAllText(path)) ?? new UpdateSettings();
        }
        catch (Exception ex) when (ex is IOException or JsonException or UnauthorizedAccessException)
        {
            // Se usan los valores por defecto.
        }
        return new UpdateSettings();
    }

    public void Save(string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, JsonSerializer.Serialize(this, JsonOptions));
    }
}
