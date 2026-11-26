namespace MyMouse.HostCore.Gestures;

[Flags]
public enum Modifiers
{
    None = 0,
    Ctrl = 1,
    Shift = 2,
    Alt = 4,
    Win = 8,
}

/// <summary>Una combinación: modificadores + una tecla (código virtual de Windows).</summary>
public sealed record KeyStroke(Modifiers Modifiers, ushort VirtualKey, string KeyName)
{
    public override string ToString()
    {
        var parts = new List<string>();
        if (Modifiers.HasFlag(Modifiers.Ctrl)) parts.Add("Ctrl");
        if (Modifiers.HasFlag(Modifiers.Shift)) parts.Add("Shift");
        if (Modifiers.HasFlag(Modifiers.Alt)) parts.Add("Alt");
        if (Modifiers.HasFlag(Modifiers.Win)) parts.Add("Win");
        parts.Add(KeyName);
        return string.Join("+", parts);
    }
}

/// <summary>
/// Sintaxis de atajos (docs/spec-v1-gestos.md §3.3): <c>Ctrl+Shift+T</c>; secuencias separadas por comas: <c>Ctrl+K, Ctrl+C</c>.
/// Se envían como teclas virtuales, así que no dependen de la distribución del teclado.
/// </summary>
public static class KeyChord
{
    public static IReadOnlyList<KeyStroke> Parse(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
            throw new FormatException("Atajo vacío");

        var strokes = new List<KeyStroke>();
        foreach (var part in text.Split(',', StringSplitOptions.TrimEntries))
        {
            if (part.Length == 0) throw new FormatException($"Atajo con una coma de más: '{text}'");
            var tokens = part.Split('+', StringSplitOptions.TrimEntries);
            // "Ctrl++" → la tecla es "+".
            if (part.EndsWith("++", StringComparison.Ordinal)) tokens = [.. tokens.Where(t => t.Length > 0), "Plus"];

            var modifiers = Modifiers.None;
            ushort? vk = null;
            string? keyName = null;
            foreach (var token in tokens)
            {
                if (token.Length == 0) throw new FormatException($"Atajo mal escrito: '{part}'");
                if (ModifierNames.TryGetValue(token, out var m))
                {
                    if (vk is not null) throw new FormatException($"El modificador '{token}' va antes de la tecla en '{part}'");
                    modifiers |= m;
                    continue;
                }
                if (vk is not null) throw new FormatException($"Más de una tecla en '{part}' (¿falta una coma?)");
                if (!TryKey(token, out var code, out var canonical))
                    throw new FormatException($"Tecla desconocida: '{token}'");
                vk = code;
                keyName = canonical;
            }

            if (vk is null)
            {
                // Solo modificadores (p. ej. "Win"): se pulsa el último como tecla.
                var last = tokens[^1];
                vk = ModifierKeys[ModifierNames[last]];
                keyName = Canonical(last);
                modifiers &= ~ModifierNames[last];
            }
            strokes.Add(new KeyStroke(modifiers, vk.Value, keyName!));
        }
        return strokes;
    }

    public static ushort ModifierKey(Modifiers m) => ModifierKeys[m];

    /// <summary>Teclas que Windows marca como "extendidas" (hace falta el flag para que lleguen bien).</summary>
    public static bool IsExtended(ushort vk) => vk is
        >= 0x21 and <= 0x28 // PageUp, PageDown, End, Home, flechas
        or 0x2C or 0x2D or 0x2E // PrintScreen, Insert, Delete
        or 0x5B or 0x5C or 0x5D // Win, Apps
        or 0x6F // Divide
        or >= 0xA6 and <= 0xB7; // navegador, volumen, multimedia

    /// <summary>Nombre con el que se escribe una tecla en un atajo (<c>T</c>, <c>F5</c>, <c>PageUp</c>…), o null si no tiene (Parse la entendería con ese nombre).</summary>
    public static string? NameFor(ushort vk)
    {
        if (vk is >= 0x41 and <= 0x5A or >= 0x30 and <= 0x39) return ((char)vk).ToString();
        if (vk is >= 0x70 and <= 0x87) return $"F{vk - 0x70 + 1}";
        return NamesByKey.GetValueOrDefault(vk);
    }

    /// <summary>¿Es una tecla modificadora (Ctrl, Mayús, Alt o Win, izquierda o derecha)? Devuelve cuál.</summary>
    public static bool TryModifier(ushort vk, out Modifiers modifier)
    {
        modifier = vk switch
        {
            0x10 or 0xA0 or 0xA1 => Modifiers.Shift,
            0x11 or 0xA2 or 0xA3 => Modifiers.Ctrl,
            0x12 or 0xA4 or 0xA5 => Modifiers.Alt,
            0x5B or 0x5C => Modifiers.Win,
            _ => Modifiers.None,
        };
        return modifier != Modifiers.None;
    }

    private static bool TryKey(string token, out ushort vk, out string canonical)
    {
        canonical = Canonical(token);
        if (token.Length == 1 && char.IsAsciiLetter(token[0]))
        {
            var upper = char.ToUpperInvariant(token[0]);
            vk = upper;
            canonical = upper.ToString();
            return true;
        }
        if (token.Length == 1 && char.IsAsciiDigit(token[0]))
        {
            vk = token[0];
            return true;
        }
        if (token.Length is 2 or 3 && (token[0] is 'F' or 'f') && int.TryParse(token.AsSpan(1), out var f) && f is >= 1 and <= 24)
        {
            vk = (ushort)(0x70 + f - 1);
            canonical = $"F{f}";
            return true;
        }
        if (NamedKeys.TryGetValue(token, out var named))
        {
            vk = named.Vk;
            canonical = named.Name;
            return true;
        }
        vk = 0;
        return false;
    }

    private static string Canonical(string token) =>
        NamedKeys.TryGetValue(token, out var k) ? k.Name :
        ModifierNames.TryGetValue(token, out var m) ? m.ToString() : token;

    private static readonly Dictionary<string, Modifiers> ModifierNames = new(StringComparer.OrdinalIgnoreCase)
    {
        ["Ctrl"] = Modifiers.Ctrl, ["Control"] = Modifiers.Ctrl, ["Ctl"] = Modifiers.Ctrl,
        ["Shift"] = Modifiers.Shift, ["Mayús"] = Modifiers.Shift, ["Mayus"] = Modifiers.Shift,
        ["Alt"] = Modifiers.Alt,
        ["Win"] = Modifiers.Win, ["Windows"] = Modifiers.Win, ["Meta"] = Modifiers.Win,
    };

    private static readonly Dictionary<Modifiers, ushort> ModifierKeys = new()
    {
        [Modifiers.Ctrl] = 0x11, [Modifiers.Shift] = 0x10, [Modifiers.Alt] = 0x12, [Modifiers.Win] = 0x5B,
    };

    private static readonly Dictionary<string, (ushort Vk, string Name)> NamedKeys = BuildNamedKeys();

    // Debe ir después de NamedKeys: los inicializadores estáticos se ejecutan en orden.
    private static readonly Dictionary<ushort, string> NamesByKey =
        NamedKeys.Values.GroupBy(v => v.Vk).ToDictionary(g => g.Key, g => g.First().Name);

    private static Dictionary<string, (ushort, string)> BuildNamedKeys()
    {
        var d = new Dictionary<string, (ushort, string)>(StringComparer.OrdinalIgnoreCase);
        void Add(ushort vk, params string[] names)
        {
            foreach (var n in names) d[n] = (vk, names[0]);
        }
        Add(0x0D, "Enter", "Return", "Intro");
        Add(0x09, "Tab");
        Add(0x1B, "Esc", "Escape");
        Add(0x20, "Space", "Espacio");
        Add(0x08, "Backspace", "Retroceso");
        Add(0x2E, "Delete", "Del", "Supr", "Suprimir");
        Add(0x2D, "Insert", "Ins");
        Add(0x24, "Home", "Inicio");
        Add(0x23, "End", "Fin");
        Add(0x21, "PageUp", "PgUp", "RePág");
        Add(0x22, "PageDown", "PgDn", "AvPág");
        Add(0x25, "Left", "Izquierda");
        Add(0x26, "Up", "Arriba");
        Add(0x27, "Right", "Derecha");
        Add(0x28, "Down", "Abajo");
        Add(0x2C, "PrintScreen", "PrtSc", "ImprPant");
        Add(0x5D, "Apps", "Menu");
        Add(0x14, "CapsLock");
        Add(0xBB, "Plus", "=");
        Add(0xBD, "Minus", "-");
        Add(0xBC, "Comma");
        Add(0xBE, "Period", ".");
        Add(0xAF, "VolumeUp");
        Add(0xAE, "VolumeDown");
        Add(0xAD, "Mute", "VolumeMute");
        Add(0xB3, "PlayPause", "MediaPlayPause");
        Add(0xB0, "Next", "MediaNext", "NextTrack");
        Add(0xB1, "Previous", "Prev", "MediaPrevious", "PreviousTrack");
        Add(0xB2, "Stop", "MediaStop");
        Add(0xA6, "BrowserBack");
        Add(0xA7, "BrowserForward");
        Add(0xA8, "BrowserRefresh");
        Add(0xBA, "Semicolon");
        Add(0xBF, "Slash");
        Add(0xC0, "Backtick", "Grave");
        Add(0xDB, "OpenBracket");
        Add(0xDC, "Backslash");
        Add(0xDD, "CloseBracket");
        Add(0xDE, "Quote");
        return d;
    }

    /// <summary>Teclas válidas para acciones <c>media</c>.</summary>
    public static readonly IReadOnlyDictionary<string, string> MediaLabels = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
    {
        ["PlayPause"] = "Reproducir / pausar",
        ["Next"] = "Siguiente canción",
        ["Previous"] = "Canción anterior",
        ["Stop"] = "Detener",
        ["VolumeUp"] = "Subir volumen",
        ["VolumeDown"] = "Bajar volumen",
        ["Mute"] = "Silenciar",
    };
}
