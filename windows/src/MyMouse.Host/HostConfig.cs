using System.Text.Json;
using MyMouse.Protocol;

namespace MyMouse.Host;

/// <summary>Configuración en %APPDATA%\MyMouse\config.json. Se crea con valores por defecto si no existe.</summary>
internal sealed record HostConfig
{
    public int Port { get; init; } = ProtocolInfo.DefaultPort;
    public string? AdbPath { get; init; }

    public static string Directory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MyMouse");

    private static string FilePath => Path.Combine(Directory, "config.json");

    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public static HostConfig Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<HostConfig>(File.ReadAllText(FilePath)) ?? new HostConfig();

            System.IO.Directory.CreateDirectory(Directory);
            var config = new HostConfig();
            File.WriteAllText(FilePath, JsonSerializer.Serialize(config, JsonOptions));
            return config;
        }
        catch (Exception ex) when (ex is IOException or JsonException or UnauthorizedAccessException)
        {
            FileLog.Write($"No se pudo leer la configuración, se usan valores por defecto: {ex.Message}");
            return new HostConfig();
        }
    }
}
