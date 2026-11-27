namespace MyMouse.HostCore.Keyboard;

/// <summary>Un scancode del conjunto 1 de PC/AT. <see cref="Extended"/> = prefijo E0 (<c>KEYEVENTF_EXTENDEDKEY</c> en SendInput).</summary>
public readonly record struct ScanCode(ushort Code, bool Extended);

/// <summary>
/// Tabla de usos HID (página 0x07 de teclado) a scancodes del conjunto 1, para inyectar con <c>SendInput</c> +
/// <c>KEYEVENTF_SCANCODE</c> (docs/spec-v6-teclado.md §2). Con scancodes, Windows convierte la tecla en carácter según su propia
/// distribución: dead keys, AltGr y atajos funcionan como en un teclado de verdad.
/// Los usos que no están aquí (Pausa, medios…) se ignoran.
/// </summary>
public static class HidScancodes
{
    private static readonly Dictionary<byte, ScanCode> Table = Build();

    /// <summary>Número de usos conocidos.</summary>
    public static int Count => Table.Count;

    /// <summary>Todos los usos conocidos.</summary>
    public static IEnumerable<byte> Usages => Table.Keys;

    public static bool TryGet(byte usage, out ScanCode code) => Table.TryGetValue(usage, out code);

    /// <summary>¿Es uno de los modificadores (<c>0xE0</c>–<c>0xE7</c>)?</summary>
    public static bool IsModifier(byte usage) => usage is >= 0xE0 and <= 0xE7;

    private static Dictionary<byte, ScanCode> Build()
    {
        var t = new Dictionary<byte, ScanCode>();
        void Add(byte usage, ushort code, bool extended = false) => t[usage] = new ScanCode(code, extended);

        // Letras A–Z (usos 0x04–0x1D), en el orden de los usos HID.
        ushort[] letters =
        [
            0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32, // A–M
            0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C, // N–Z
        ];
        for (var i = 0; i < letters.Length; i++) Add((byte)(0x04 + i), letters[i]);

        // Números 1–9 y 0 (0x1E–0x27) = scancodes 0x02–0x0B.
        for (var i = 0; i < 10; i++) Add((byte)(0x1E + i), (ushort)(0x02 + i));

        Add(0x28, 0x1C); // Intro
        Add(0x29, 0x01); // Esc
        Add(0x2A, 0x0E); // Retroceso
        Add(0x2B, 0x0F); // Tab
        Add(0x2C, 0x39); // Espacio
        Add(0x2D, 0x0C); // - (ES: ')
        Add(0x2E, 0x0D); // = (ES: ¡)
        Add(0x2F, 0x1A); // [ (ES: `)
        Add(0x30, 0x1B); // ] (ES: +)
        Add(0x31, 0x2B); // \ (ANSI)
        Add(0x32, 0x2B); // # europea (ISO, ES: Ç): el mismo scancode que la \ de ANSI
        Add(0x33, 0x27); // ; (ES: Ñ)
        Add(0x34, 0x28); // ' (ES: ´)
        Add(0x35, 0x29); // ` (ES: º)
        Add(0x36, 0x33); // ,
        Add(0x37, 0x34); // .
        Add(0x38, 0x35); // / (ES: -)
        Add(0x39, 0x3A); // Bloq Mayús

        // F1–F10 (0x3A–0x43) = 0x3B–0x44; F11 y F12 (0x44, 0x45) = 0x57, 0x58.
        for (var i = 0; i < 10; i++) Add((byte)(0x3A + i), (ushort)(0x3B + i));
        Add(0x44, 0x57);
        Add(0x45, 0x58);

        Add(0x46, 0x37, extended: true); // Impr Pant (E0 37)
        Add(0x47, 0x46);                 // Bloq Despl
        // 0x48 Pausa: su secuencia (E1 1D 45) no se puede inyectar con un solo scancode.
        Add(0x49, 0x52, extended: true); // Insert
        Add(0x4A, 0x47, extended: true); // Inicio
        Add(0x4B, 0x49, extended: true); // RePág
        Add(0x4C, 0x53, extended: true); // Supr
        Add(0x4D, 0x4F, extended: true); // Fin
        Add(0x4E, 0x51, extended: true); // AvPág
        Add(0x4F, 0x4D, extended: true); // →
        Add(0x50, 0x4B, extended: true); // ←
        Add(0x51, 0x50, extended: true); // ↓
        Add(0x52, 0x48, extended: true); // ↑

        // Numpad.
        Add(0x53, 0x45);                 // Bloq Num
        Add(0x54, 0x35, extended: true); // /
        Add(0x55, 0x37);                 // *
        Add(0x56, 0x4A);                 // -
        Add(0x57, 0x4E);                 // +
        Add(0x58, 0x1C, extended: true); // Intro del numpad
        ushort[] keypad = [0x4F, 0x50, 0x51, 0x4B, 0x4C, 0x4D, 0x47, 0x48, 0x49]; // 1–9
        for (var i = 0; i < keypad.Length; i++) Add((byte)(0x59 + i), keypad[i]);
        Add(0x62, 0x52); // 0
        Add(0x63, 0x53); // .

        Add(0x64, 0x56);                 // < > de la ISO (entre Shift y Z)
        Add(0x65, 0x5D, extended: true); // Menú contextual

        // Modificadores.
        Add(0xE0, 0x1D);                 // Ctrl izq.
        Add(0xE1, 0x2A);                 // Shift izq.
        Add(0xE2, 0x38);                 // Alt
        Add(0xE3, 0x5B, extended: true); // Win izq.
        Add(0xE4, 0x1D, extended: true); // Ctrl dcha.
        Add(0xE5, 0x36);                 // Shift dcha.
        Add(0xE6, 0x38, extended: true); // Alt dcha. = AltGr
        Add(0xE7, 0x5C, extended: true); // Win dcha.
        return t;
    }
}

/// <summary>Distribución del teclado de Windows a las dos que dibuja el móvil (docs/spec-v6-teclado.md §6).</summary>
public static class KeyboardLayoutIds
{
    /// <summary>
    /// <c>es</c> si el identificador de idioma de la distribución (la palabra baja del HKL) es español de España
    /// (<c>0x040A</c>, tradicional, o <c>0x0C0A</c>, moderno); <c>us</c> con cualquier otra.
    /// </summary>
    public static string FromLangId(ushort langId) => langId is 0x040A or 0x0C0A ? "es" : "us";

    /// <summary>Lo mismo con el nombre de la cultura (<c>es-ES</c>, <c>es-ES_tradnl</c>…).</summary>
    public static string FromCultureName(string name) =>
        name.Equals("es-ES", StringComparison.OrdinalIgnoreCase) || name.Equals("es-ES_tradnl", StringComparison.OrdinalIgnoreCase) ? "es" : "us";
}
