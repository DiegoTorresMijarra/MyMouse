using MyMouse.HostCore.Gestures;

namespace MyMouse.HostCore.Toolbox;

/// <summary>Texto del chip de una acción y, si hace falta, el tooltip con el detalle completo.</summary>
public readonly record struct ActionChipInfo(string Text, string? Tooltip);

/// <summary>
/// Formato del "chip" de la tarjeta Gestos (docs/spec-v4.5-estilo.md §2.4): la acción técnica en una frase corta.
/// Para <c>launch</c> solo se muestra el nombre del fichero, nunca la ruta; la ruta completa va en el tooltip.
/// </summary>
public static class ActionChip
{
    public const int MaxLength = 28;

    public static ActionChipInfo Format(GestureAction? action) => action switch
    {
        KeysAction k => Plain(string.Join(", ", k.Strokes)),
        LaunchAction l => FormatLaunch(l),
        TextAction t => Plain("\"" + Shorten(t.Text.Replace('\n', ' ').Replace('\r', ' '), 20) + "\""),
        MediaAction m => Plain(m.Key),
        MyMouseAction { Command: MyMouseCommand.Pause } => Plain("Pause"),
        MyMouseAction { Command: MyMouseCommand.Lock } => Plain("Lock"),
        _ => Plain("Sin acción"),
    };

    /// <summary>Nombre del fichero de una ruta (con <c>\</c> o <c>/</c> en cualquier sistema) o el host de una URL.</summary>
    public static string TargetName(string target)
    {
        var t = target.Trim();
        var scheme = t.IndexOf("://", StringComparison.Ordinal);
        if (scheme > 0)
        {
            var rest = t[(scheme + 3)..];
            var end = rest.IndexOfAny(['/', '?', '#']);
            var host = end >= 0 ? rest[..end] : rest;
            host = host[(host.LastIndexOf('@') + 1)..];
            return host.StartsWith("www.", StringComparison.OrdinalIgnoreCase) ? host[4..] : host;
        }
        var trimmed = t.TrimEnd('\\', '/');
        var name = trimmed[(trimmed.LastIndexOfAny(['\\', '/']) + 1)..];
        // "C:" o "ms-settings:" no son rutas con carpetas: se dejan tal cual.
        return name.Length == 0 ? t : name;
    }

    private static ActionChipInfo FormatLaunch(LaunchAction l)
    {
        var name = TargetName(l.Target);
        var full = string.IsNullOrWhiteSpace(l.Arguments) ? l.Target : l.Target + " " + l.Arguments;
        return new ActionChipInfo(Shorten(name, MaxLength), full);
    }

    private static ActionChipInfo Plain(string text) => new(Shorten(text, MaxLength), text.Length > MaxLength ? text : null);

    private static string Shorten(string text, int max) => text.Length > max ? text[..(max - 1)] + "…" : text;
}
