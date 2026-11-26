using System.Text.Json;

namespace MyMouse.HostCore.Gestures;

/// <summary>Qué hace una forma. Ver docs/spec-v1-gestos.md §3.</summary>
public abstract record GestureAction
{
    /// <summary>Texto de ayuda por defecto si la regla no trae <c>help</c>.</summary>
    public abstract string DefaultHelp { get; }
}

public sealed record KeysAction(IReadOnlyList<KeyStroke> Strokes) : GestureAction
{
    public override string DefaultHelp => string.Join(", ", Strokes);
}

public sealed record LaunchAction(string Target, string? Arguments, bool FocusIfRunning) : GestureAction
{
    public override string DefaultHelp
    {
        get
        {
            var name = Target.Contains("://", StringComparison.Ordinal) || Target.EndsWith(':')
                ? Target
                : FileNameWithoutExtension(Target.TrimEnd('\\', '/'));
            return $"Abrir {(string.IsNullOrEmpty(name) ? Target : name)}";
        }
    }

    /// <summary>Como Path.GetFileNameWithoutExtension, pero con '\' y '/' en cualquier sistema (tests en Linux).</summary>
    private static string FileNameWithoutExtension(string path)
    {
        var name = path[(path.LastIndexOfAny(['\\', '/']) + 1)..];
        var dot = name.LastIndexOf('.');
        return dot > 0 ? name[..dot] : name;
    }
}

public sealed record TextAction(string Text) : GestureAction
{
    public override string DefaultHelp => $"Escribir \"{(Text.Length > 20 ? Text[..20] + "…" : Text).Replace('\n', ' ')}\"";
}

public sealed record MediaAction(string Key, KeyStroke Stroke) : GestureAction
{
    public override string DefaultHelp => KeyChord.MediaLabels.TryGetValue(Key, out var l) ? l : Key;
}

public enum MyMouseCommand { Pause, Lock }

public sealed record MyMouseAction(MyMouseCommand Command) : GestureAction
{
    public override string DefaultHelp => Command switch
    {
        MyMouseCommand.Pause => "Pausar / reanudar MyMouse",
        MyMouseCommand.Lock => "Bloquear el PC",
        _ => Command.ToString(),
    };
}

/// <summary>Regla: forma (y opcionalmente app activa) → acción.</summary>
public sealed record GestureBinding(string Gesture, GestureAction Action, string? Help, bool Pinned, string? App)
{
    public string HelpText => string.IsNullOrWhiteSpace(Help) ? Action.DefaultHelp : Help!;
}

/// <summary>Contenido de <c>gestures.json</c>. Las reglas con errores se descartan y se informan en <see cref="Warnings"/>.</summary>
public sealed class GestureConfig
{
    public IReadOnlyList<GestureBinding> Bindings { get; }
    public IReadOnlyList<string> Warnings { get; }

    public GestureConfig(IReadOnlyList<GestureBinding> bindings, IReadOnlyList<string>? warnings = null)
    {
        Bindings = bindings;
        Warnings = warnings ?? [];
    }

    public static GestureConfig Empty { get; } = new([]);

    public bool HasAppRules => Bindings.Any(b => b.App is not null);

    /// <summary>Regla que se aplica a <paramref name="gesture"/> con la app <paramref name="foregroundApp"/> activa: primero la de esa app, si no, la general.</summary>
    public GestureBinding? Resolve(string gesture, string? foregroundApp)
    {
        GestureBinding? general = null;
        foreach (var b in Bindings)
        {
            if (!string.Equals(b.Gesture, gesture, StringComparison.Ordinal)) continue;
            if (b.App is null) general ??= b;
            else if (foregroundApp is not null && AppMatches(b.App, foregroundApp)) return b;
        }
        return general;
    }

    /// <summary>¿Hay alguna regla específica para esta app?</summary>
    public bool HasRulesFor(string? foregroundApp) =>
        foregroundApp is not null && Bindings.Any(b => b.App is not null && AppMatches(b.App, foregroundApp));

    /// <summary>Compara sin mayúsculas y con o sin ".exe".</summary>
    public static bool AppMatches(string rule, string app) =>
        string.Equals(StripExe(rule), StripExe(app), StringComparison.OrdinalIgnoreCase);

    private static string StripExe(string s) =>
        s.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? s[..^4] : s;

    private static readonly JsonDocumentOptions Options = new()
    {
        CommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true,
    };

    /// <summary>Lanza <see cref="FormatException"/> si el JSON no es válido; las reglas sueltas mal escritas solo generan avisos.</summary>
    public static GestureConfig Parse(string json)
    {
        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(json, Options);
        }
        catch (JsonException ex)
        {
            throw new FormatException($"gestures.json no es JSON válido (línea {ex.LineNumber + 1}): {ex.Message}");
        }

        using (doc)
        {
            if (doc.RootElement.ValueKind != JsonValueKind.Object ||
                !doc.RootElement.TryGetProperty("bindings", out var list) || list.ValueKind != JsonValueKind.Array)
                throw new FormatException("gestures.json debe tener la forma { \"bindings\": [ ... ] }");

            var bindings = new List<GestureBinding>();
            var warnings = new List<string>();
            var index = 0;
            foreach (var item in list.EnumerateArray())
            {
                index++;
                try
                {
                    bindings.Add(ParseBinding(item));
                }
                catch (Exception ex) when (ex is FormatException or InvalidOperationException or KeyNotFoundException)
                {
                    warnings.Add($"Regla {index}: {ex.Message}");
                }
            }
            return new GestureConfig(bindings, warnings);
        }
    }

    private static GestureBinding ParseBinding(JsonElement e)
    {
        var gesture = RequiredString(e, "gesture");
        if (!e.TryGetProperty("action", out var action) || action.ValueKind != JsonValueKind.Object)
            throw new FormatException($"'{gesture}' no tiene \"action\"");
        return new GestureBinding(
            gesture,
            ParseAction(action),
            OptionalString(e, "help"),
            e.TryGetProperty("pin", out var pin) && pin.ValueKind == JsonValueKind.True,
            OptionalString(e, "app"));
    }

    private static GestureAction ParseAction(JsonElement a)
    {
        var type = RequiredString(a, "type");
        switch (type.ToLowerInvariant())
        {
            case "keys":
                return new KeysAction(KeyChord.Parse(RequiredString(a, "keys")));
            case "launch":
                return new LaunchAction(
                    RequiredString(a, "target"),
                    OptionalString(a, "args"),
                    a.TryGetProperty("focusIfRunning", out var f) && f.ValueKind == JsonValueKind.True);
            case "text":
                return new TextAction(RequiredString(a, "text"));
            case "media":
            {
                var key = RequiredString(a, "key");
                if (!KeyChord.MediaLabels.ContainsKey(key))
                    throw new FormatException($"Tecla multimedia desconocida: '{key}' (válidas: {string.Join(", ", KeyChord.MediaLabels.Keys)})");
                return new MediaAction(key, KeyChord.Parse(key)[0]);
            }
            case "mymouse":
            {
                var command = RequiredString(a, "command");
                return command.ToLowerInvariant() switch
                {
                    "pause" => new MyMouseAction(MyMouseCommand.Pause),
                    "lock" => new MyMouseAction(MyMouseCommand.Lock),
                    _ => throw new FormatException($"Comando de MyMouse desconocido: '{command}' (válidos: pause, lock)"),
                };
            }
            default:
                throw new FormatException($"Tipo de acción desconocido: '{type}' (válidos: keys, launch, text, media, mymouse)");
        }
    }

    private static string RequiredString(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String && !string.IsNullOrWhiteSpace(v.GetString())
            ? v.GetString()!
            : throw new FormatException($"Falta \"{name}\" (texto)");

    private static string? OptionalString(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    /// <summary>Configuración de ejemplo que se crea la primera vez.</summary>
    public const string DefaultJson = """
        {
          // Formas del móvil → acciones en el PC. Ver docs/spec-v1-gestos.md.
          // Tipos: keys, launch, text, media, mymouse. Campos opcionales: help (texto de la chuleta),
          // pin (destacar en la chuleta), app (solo cuando esa aplicación está activa, p. ej. "chrome.exe").
          // Formas: line-right, line-left, line-up, line-down, v, caret, lt, gt, circle, triangle, square,
          //         check, z, s, l, y las propias como "custom:<nombre>".
          // Al guardar este fichero, MyMouse lo recarga solo.
          "bindings": [
            { "gesture": "circle",     "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true }, "help": "Abrir Chrome", "pin": true },
            { "gesture": "z",          "action": { "type": "keys", "keys": "Ctrl+Z" }, "help": "Deshacer", "pin": true },
            { "gesture": "line-left",  "action": { "type": "keys", "keys": "Alt+Left" }, "help": "Atrás" },
            { "gesture": "line-right", "action": { "type": "keys", "keys": "Alt+Right" }, "help": "Adelante" },
            { "gesture": "line-down",  "action": { "type": "keys", "keys": "Win+D" }, "help": "Escritorio" },
            { "gesture": "line-up",    "action": { "type": "keys", "keys": "Win+Tab" }, "help": "Vista de tareas" },
            { "gesture": "v",          "action": { "type": "media", "key": "PlayPause" }, "pin": true },
            { "gesture": "gt",         "action": { "type": "media", "key": "Next" } },
            { "gesture": "lt",         "action": { "type": "media", "key": "Previous" } },
            { "gesture": "check",      "action": { "type": "keys", "keys": "Ctrl+S" }, "help": "Guardar" },
            { "gesture": "s",          "action": { "type": "launch", "target": "ms-settings:" }, "help": "Configuración de Windows" },
            { "gesture": "check",      "action": { "type": "keys", "keys": "F5" }, "help": "Recargar", "app": "chrome.exe" }
          ]
        }
        """;
}
