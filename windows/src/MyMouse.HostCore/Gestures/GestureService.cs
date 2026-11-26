using MyMouse.Protocol;

namespace MyMouse.HostCore.Gestures;

/// <summary>Ejecuta acciones en el sistema. En Windows: SendInput, Process.Start, LockWorkStation…</summary>
public interface IActionExecutor
{
    /// <summary>Lanza una excepción si no se puede ejecutar (se informa al móvil como error).</summary>
    void Execute(GestureAction action);
}

/// <summary>Qué pasó al ejecutar una forma recibida: para el gesto en vivo del PC. <paramref name="Binding"/> es null si no tiene acción.</summary>
public sealed record GestureOutcome(string Name, GestureStatus Status, GestureBinding? Binding, Exception? Error);

/// <summary>
/// Implementación de <see cref="IGestureService"/>: busca la regla según la ventana activa y la ejecuta.
/// La configuración se cambia en caliente con <see cref="SetConfig"/>; la ventana activa se informa con
/// <see cref="SetForegroundApp"/>.
/// </summary>
public sealed class GestureService(IActionExecutor executor) : IGestureService
{
    private readonly object _lock = new();
    private GestureConfig _config = GestureConfig.Empty;
    private string? _foregroundApp;

    public event Action? ListChanged;

    /// <summary>Forma recibida sin regla. Útil para avisar al usuario.</summary>
    public event Action<string>? Unassigned;

    /// <summary>Resultado de cada forma ejecutada (con acción, sin ella o con error).</summary>
    public event Action<GestureOutcome>? Outcome;

    /// <summary>Error al ejecutar una acción.</summary>
    public event Action<string, Exception>? Failed;

    public GestureConfig Config
    {
        get { lock (_lock) return _config; }
    }

    public void SetConfig(GestureConfig config)
    {
        lock (_lock) _config = config;
        ListChanged?.Invoke();
    }

    /// <summary>Nombre del proceso de la ventana activa (p. ej. "chrome"). Solo avisa si cambia la lista.</summary>
    public void SetForegroundApp(string? app)
    {
        bool changed;
        lock (_lock)
        {
            if (string.Equals(_foregroundApp, app, StringComparison.OrdinalIgnoreCase)) return;
            var before = _config.HasRulesFor(_foregroundApp) ? _foregroundApp : null;
            _foregroundApp = app;
            var after = _config.HasRulesFor(app) ? app : null;
            changed = !string.Equals(before, after, StringComparison.OrdinalIgnoreCase);
        }
        if (changed) ListChanged?.Invoke();
    }

    public GestureStatus Execute(string name)
    {
        GestureBinding? binding;
        lock (_lock) binding = _config.Resolve(name, _foregroundApp);
        if (binding is null)
        {
            Unassigned?.Invoke(name);
            Outcome?.Invoke(new GestureOutcome(name, GestureStatus.Unassigned, null, null));
            return GestureStatus.Unassigned;
        }
        try
        {
            executor.Execute(binding.Action);
            Outcome?.Invoke(new GestureOutcome(name, GestureStatus.Executed, binding, null));
            return GestureStatus.Executed;
        }
        catch (Exception ex)
        {
            Failed?.Invoke(name, ex);
            Outcome?.Invoke(new GestureOutcome(name, GestureStatus.Error, binding, ex));
            return GestureStatus.Error;
        }
    }

    public GestureListMessage CurrentList()
    {
        lock (_lock)
        {
            var app = _foregroundApp;
            var context = _config.HasRulesFor(app) ? app! : "";
            var entries = _config.Bindings
                .Select(b => b.Gesture)
                .Distinct(StringComparer.Ordinal)
                .Select(g => _config.Resolve(g, app))
                .OfType<GestureBinding>()
                .OrderByDescending(b => b.Pinned)
                .Take(ProtocolInfo.MaxGestureListEntries)
                .Select(b => new GestureListEntry(b.Gesture, b.HelpText, b.Pinned))
                .ToList();
            return new GestureListMessage(context, entries);
        }
    }
}
