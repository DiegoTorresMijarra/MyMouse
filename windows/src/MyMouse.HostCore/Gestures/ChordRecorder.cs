namespace MyMouse.HostCore.Gestures;

/// <summary>
/// Graba una combinación de teclas pulsada por el usuario (docs/spec-v4-toolbox.md §4.1). Recibe los eventos de teclado
/// (códigos virtuales de Windows; en la interfaz, de un hook de bajo nivel activo solo mientras el campo tiene el foco)
/// y da la combinación en el formato del fichero: <c>Ctrl+Shift+T</c>. Termina al pulsar una tecla que no es modificadora;
/// si solo se pulsan modificadores, al soltarlos todos (<c>Win</c>, <c>Ctrl+Shift</c>).
/// </summary>
public sealed class ChordRecorder
{
    private readonly List<(Modifiers Modifier, ushort Vk)> _pressedOrder = [];
    private readonly HashSet<ushort> _held = [];
    private KeyStroke? _result;

    /// <summary>La combinación grabada, cuando <see cref="IsComplete"/>.</summary>
    public KeyStroke? Result => _result;

    public bool IsComplete => _result is not null;

    /// <summary>Lo que se muestra: la combinación grabada o, mientras se pulsa, los modificadores y "…".</summary>
    public string Text
    {
        get
        {
            if (_result is not null) return _result.ToString();
            if (_pressedOrder.Count == 0) return "";
            return Format(_pressedOrder.Select(p => p.Modifier).Aggregate(Modifiers.None, (a, b) => a | b)) + "+…";
        }
    }

    /// <summary>Vuelve a empezar (otra grabación).</summary>
    public void Reset()
    {
        _pressedOrder.Clear();
        _held.Clear();
        _result = null;
    }

    public void KeyDown(ushort vk)
    {
        if (_result is not null) return;
        if (KeyChord.TryModifier(vk, out var modifier))
        {
            if (_held.Add(vk) && _pressedOrder.All(p => p.Modifier != modifier)) _pressedOrder.Add((modifier, vk));
            return;
        }
        var name = KeyChord.NameFor(vk);
        if (name is null) return; // tecla que los atajos no admiten: se ignora
        var mods = _pressedOrder.Where(p => _held.Contains(p.Vk) || IsStillHeld(p.Modifier)).Select(p => p.Modifier)
            .Aggregate(Modifiers.None, (a, b) => a | b);
        _result = new KeyStroke(mods, vk, name);
    }

    public void KeyUp(ushort vk)
    {
        if (_result is not null) return;
        _held.Remove(vk);
        if (_pressedOrder.Count > 0 && _held.Count == 0) FinishWithModifiersOnly();
    }

    private bool IsStillHeld(Modifiers modifier) => _held.Any(vk => KeyChord.TryModifier(vk, out var m) && m == modifier);

    /// <summary>Solo modificadores: el último pulsado hace de tecla, como en <see cref="KeyChord.Parse"/> (<c>Win</c>, <c>Ctrl+Shift</c>).</summary>
    private void FinishWithModifiersOnly()
    {
        var last = _pressedOrder[^1];
        var others = _pressedOrder.Take(_pressedOrder.Count - 1).Select(p => p.Modifier).Aggregate(Modifiers.None, (a, b) => a | b);
        _result = new KeyStroke(others, KeyChord.ModifierKey(last.Modifier), last.Modifier.ToString());
    }

    private static string Format(Modifiers m)
    {
        var parts = new List<string>();
        if (m.HasFlag(Modifiers.Ctrl)) parts.Add("Ctrl");
        if (m.HasFlag(Modifiers.Shift)) parts.Add("Shift");
        if (m.HasFlag(Modifiers.Alt)) parts.Add("Alt");
        if (m.HasFlag(Modifiers.Win)) parts.Add("Win");
        return string.Join("+", parts);
    }
}
