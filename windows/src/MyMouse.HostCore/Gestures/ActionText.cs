namespace MyMouse.HostCore.Gestures;

/// <summary>Tipos de acción del editor (docs/spec-v4-toolbox.md §4.1), en el orden del selector.</summary>
public enum ActionKind
{
    None,
    Keys,
    Launch,
    Text,
    Media,
    MyMouse,
}

/// <summary>Textos de las acciones para la interfaz: el tipo, un resumen corto ("launch chrome.exe") y el nombre en el selector.</summary>
public static class ActionText
{
    public static ActionKind KindOf(GestureAction? action) => action switch
    {
        KeysAction => ActionKind.Keys,
        LaunchAction => ActionKind.Launch,
        TextAction => ActionKind.Text,
        MediaAction => ActionKind.Media,
        MyMouseAction => ActionKind.MyMouse,
        _ => ActionKind.None,
    };

    public static string KindLabel(ActionKind kind) => kind switch
    {
        ActionKind.None => "Ninguna",
        ActionKind.Keys => "Atajo de teclado",
        ActionKind.Launch => "Abrir app, archivo o web",
        ActionKind.Text => "Escribir texto",
        ActionKind.Media => "Multimedia",
        ActionKind.MyMouse => "MyMouse",
        _ => kind.ToString(),
    };

    /// <summary>Resumen técnico y corto: <c>keys Ctrl+Z</c>, <c>launch chrome.exe</c>, <c>text "hola"</c>…</summary>
    public static string Summary(GestureAction? action) => action switch
    {
        KeysAction k => "keys " + string.Join(", ", k.Strokes),
        LaunchAction l => "launch " + l.Target + (string.IsNullOrWhiteSpace(l.Arguments) ? "" : " " + l.Arguments),
        TextAction t => "text \"" + (t.Text.Length > 30 ? t.Text[..30] + "…" : t.Text).Replace('\n', ' ') + "\"",
        MediaAction m => "media " + m.Key,
        MyMouseAction { Command: MyMouseCommand.Pause } => "mymouse pause",
        MyMouseAction { Command: MyMouseCommand.Lock } => "mymouse lock",
        _ => "sin acción",
    };

    /// <summary>Opciones del desplegable Multimedia: (clave, etiqueta).</summary>
    public static IEnumerable<KeyValuePair<string, string>> MediaChoices { get; } =
        [.. KeyChord.MediaLabels.Where(kv => kv.Key is "PlayPause" or "Next" or "Previous" or "VolumeUp" or "VolumeDown" or "Mute")];
}
