using MyMouse.HostCore.Gestures;

namespace MyMouse.Host;

/// <summary>
/// %APPDATA%\MyMouse\gestures.json: se crea con un ejemplo la primera vez y se recarga al guardarlo.
/// Si el fichero tiene un error, se mantiene la configuración anterior.
/// </summary>
internal sealed class GestureConfigFile : IDisposable
{
    public static string FilePath => Path.Combine(HostConfig.Directory, "gestures.json");

    private readonly FileSystemWatcher _watcher;
    private readonly System.Threading.Timer _debounce;
    private bool _firstLoad = true;

    /// <summary>Configuración cargada (válida). Se llama también en la primera carga.</summary>
    public event Action<GestureConfig>? Loaded;

    /// <summary>Error o avisos al cargar, para mostrarlos al usuario.</summary>
    public event Action<string>? Problem;

    public GestureConfigFile()
    {
        Directory.CreateDirectory(HostConfig.Directory);
        _debounce = new System.Threading.Timer(_ => Load(), null, Timeout.Infinite, Timeout.Infinite);
        _watcher = new FileSystemWatcher(HostConfig.Directory, "gestures.json")
        {
            NotifyFilter = NotifyFilters.LastWrite | NotifyFilters.FileName | NotifyFilters.Size,
        };
        // Los editores guardan en varios pasos: se espera un poco antes de leer.
        FileSystemEventHandler changed = (_, _) => _debounce.Change(300, Timeout.Infinite);
        _watcher.Changed += changed;
        _watcher.Created += changed;
        _watcher.Renamed += (_, _) => _debounce.Change(300, Timeout.Infinite);
    }

    public void Start()
    {
        if (!File.Exists(FilePath)) File.WriteAllText(FilePath, GestureConfig.DefaultJson);
        Load();
        _watcher.EnableRaisingEvents = true;
    }

    public void Load()
    {
        string json;
        try
        {
            json = File.ReadAllText(FilePath);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Probablemente el editor aún lo tiene abierto: se reintenta.
            _debounce.Change(500, Timeout.Infinite);
            FileLog.Write($"gestures.json no se pudo leer todavía: {ex.Message}");
            return;
        }

        try
        {
            var config = GestureConfig.Parse(json);
            FileLog.Write($"gestures.json cargado: {config.Bindings.Count} reglas, {config.Widgets.Count} widgets");
            foreach (var w in config.Warnings) FileLog.Write($"gestures.json: {w}");
            Loaded?.Invoke(config);
            if (config.Warnings.Count > 0)
                Problem?.Invoke($"{config.Warnings.Count} aviso(s) en gestures.json (reglas o widgets ignorados): {config.Warnings[0]}");

            // Un gestures.json de la v1 no se toca: se avisa una vez al arrancar de que existe el panel.
            if (_firstLoad && !config.HasWidgetsSection)
                Problem?.Invoke("MyMouse 2 tiene panel de widgets. Para activarlo añade una lista \"widgets\" a gestures.json (ejemplo en docs/probar.md); con \"widgets\": [] no se vuelve a avisar.");
            _firstLoad = false;
        }
        catch (FormatException ex)
        {
            FileLog.Write(ex.Message);
            Problem?.Invoke($"gestures.json tiene un error y se mantiene la versión anterior: {ex.Message}");
        }
    }

    public void Dispose()
    {
        _watcher.Dispose();
        _debounce.Dispose();
    }
}
