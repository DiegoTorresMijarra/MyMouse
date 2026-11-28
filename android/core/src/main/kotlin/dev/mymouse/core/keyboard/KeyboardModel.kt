package dev.mymouse.core.keyboard

/** Bloques del teclado del modo teclado (docs/spec-v6-teclado.md §4.1). */
enum class KeyboardBlock { FN, ARROWS, NAV, NUMPAD, NUMPAD_ONLY }

/** Qué clase de tecla es: decide cómo se envía y cómo se dibuja. */
enum class KeyKind {
    /** Tecla normal: se envía su uso HID. */
    NORMAL,

    /** Shift, Ctrl, Alt, AltGr o Win: modificador inteligente ([ModifierState]). */
    MODIFIER,

    /** Bloq Mayús / Bloq Num / Bloq Despl: tecla normal con luz. */
    LOCK,

    /** `⌨` Modo: abre y cierra la barra de chips. No envía nada. */
    MODE,

    /** `🖱` Mouse: vuelve al trackpad. No envía nada. */
    MOUSE,

    /** Hueco para que todas las filas de un bloque sumen lo mismo. No se dibuja ni se toca. */
    SPACER,
}

/** De qué parte del teclado es una tecla (la parte fija con `⌨` y `🖱` va en la base o en su columna del solo numpad). */
enum class KeyZone { FN, BASE, ARROWS, NAV, NUMPAD, NUMPAD_ONLY, FIXED }

/**
 * Una tecla con su rectángulo en unidades (1 u = ancho de una tecla normal; el alto de una fila también es 1 u).
 * [usage] es el uso HID (página 0x07), o 0 si no envía nada ([KeyKind.MODE], [KeyKind.MOUSE], [KeyKind.SPACER]).
 * [label] es la etiqueta grande; [shiftLabel] y [altGrLabel] las pequeñas de las esquinas (arriba y abajo a la derecha).
 * En las letras [letter] es true: [label] es la mayúscula y no hay [shiftLabel].
 */
data class KeyDef(
    val usage: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val shiftLabel: String? = null,
    val altGrLabel: String? = null,
    val kind: KeyKind = KeyKind.NORMAL,
    val zone: KeyZone = KeyZone.BASE,
    val letter: Boolean = false,
) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val repeatable: Boolean get() = kind == KeyKind.NORMAL && KeyUsages.isRepeatable(usage)
}

/** El teclado resuelto: las teclas y su tamaño total en unidades. */
data class KeyboardGeometry(val keys: List<KeyDef>, val widthUnits: Float, val heightUnits: Float) {
    /** La tecla que contiene el punto (en unidades), o null. Los huecos no cuentan. */
    fun keyAt(x: Float, y: Float): KeyDef? = keys.firstOrNull { it.kind != KeyKind.SPACER && x >= it.x && x < it.right && y >= it.y && y < it.bottom }
}

/** Usos HID que necesita el teclado, y sus propiedades. */
object KeyUsages {
    const val ENTER = 0x28
    const val KEYPAD_ENTER = 0x58
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val CAPS_LOCK = 0x39
    const val PRINT_SCREEN = 0x46
    const val SCROLL_LOCK = 0x47
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val NUM_LOCK = 0x53
    const val MENU = 0x65
    const val ISO_BACKSLASH = 0x64
    const val CTRL = 0xE0
    const val SHIFT = 0xE1
    const val ALT = 0xE2
    const val WIN = 0xE3
    const val SHIFT_RIGHT = 0xE5
    const val ALT_GR = 0xE6

    fun isModifier(usage: Int) = usage in 0xE0..0xE7

    /** Letras, números (también los del numpad), Retroceso, Supr, flechas, espacio e Intro (docs/spec-v6-teclado.md §5.2). */
    fun isRepeatable(usage: Int): Boolean =
        usage in 0x04..0x27 || usage == ENTER || usage == BACKSPACE || usage == SPACE || usage == DELETE ||
            usage in RIGHT..UP || usage in 0x58..0x63
}

/**
 * Qué bloques están encendidos. Las reglas (docs/spec-v6-teclado.md §4.1):
 * - [numpadOnly] es exclusivo: apaga todo lo demás (también Fn).
 * - Navegación ya lleva las flechas: encender una apaga la otra.
 * - En vertical solo cabe un bloque lateral (flechas, navegación o numpad): encender uno apaga los otros.
 */
data class KeyboardCombo(
    val fn: Boolean = false,
    val arrows: Boolean = false,
    val nav: Boolean = false,
    val numpad: Boolean = false,
    val numpadOnly: Boolean = false,
) {
    fun isOn(block: KeyboardBlock): Boolean = when (block) {
        KeyboardBlock.FN -> fn
        KeyboardBlock.ARROWS -> arrows
        KeyboardBlock.NAV -> nav
        KeyboardBlock.NUMPAD -> numpad
        KeyboardBlock.NUMPAD_ONLY -> numpadOnly
    }

    /** Enciende o apaga [block] aplicando las reglas de combinación de la orientación. */
    fun toggled(block: KeyboardBlock, landscape: Boolean): KeyboardCombo {
        val on = !isOn(block)
        val next = when (block) {
            KeyboardBlock.FN -> copy(fn = on, numpadOnly = if (on) false else numpadOnly)
            KeyboardBlock.NUMPAD_ONLY -> if (on) KeyboardCombo(numpadOnly = true) else KeyboardCombo()
            KeyboardBlock.ARROWS -> copy(arrows = on, nav = if (on) false else nav, numpadOnly = false)
            KeyboardBlock.NAV -> copy(nav = on, arrows = if (on) false else arrows, numpadOnly = false)
            KeyboardBlock.NUMPAD -> copy(numpad = on, numpadOnly = false)
        }
        if (landscape || !on || block == KeyboardBlock.FN || block == KeyboardBlock.NUMPAD_ONLY) return next.normalized(landscape)
        // En vertical, el bloque lateral que se enciende deja fuera a los otros dos.
        return when (block) {
            KeyboardBlock.ARROWS -> next.copy(nav = false, numpad = false)
            KeyboardBlock.NAV -> next.copy(arrows = false, numpad = false)
            else -> next.copy(arrows = false, nav = false)
        }
    }

    /** La combinación válida para la orientación (por si viene de un ajuste guardado a mano o de otra orientación). */
    fun normalized(landscape: Boolean): KeyboardCombo {
        if (numpadOnly) return KeyboardCombo(numpadOnly = true)
        var c = if (nav) copy(arrows = false) else this
        if (!landscape) {
            c = when {
                c.numpad -> c.copy(arrows = false, nav = false)
                c.nav -> c.copy(arrows = false)
                else -> c
            }
        }
        return c
    }

    /** Texto para guardar en los ajustes: `fn,nav,num`; vacío si no hay ningún bloque. */
    fun encode(): String = listOfNotNull(
        "fn".takeIf { fn }, "arrows".takeIf { arrows }, "nav".takeIf { nav }, "num".takeIf { numpad }, "numonly".takeIf { numpadOnly },
    ).joinToString(",")

    /** Ids de los bloques encendidos, en orden (barra de estado; la app pone el texto). */
    fun blocks(): List<String> = listOfNotNull(
        "fn".takeIf { fn }, "arrows".takeIf { arrows }, "nav".takeIf { nav }, "num".takeIf { numpad }, "numonly".takeIf { numpadOnly },
    )

    companion object {
        /** En vertical: la base sola. En horizontal: la base con las flechas. */
        fun default(landscape: Boolean) = if (landscape) KeyboardCombo(arrows = true) else KeyboardCombo()

        fun decode(text: String?, landscape: Boolean): KeyboardCombo {
            if (text == null) return default(landscape)
            val parts = text.split(',').map { it.trim() }
            return KeyboardCombo(
                fn = "fn" in parts, arrows = "arrows" in parts, nav = "nav" in parts, numpad = "num" in parts, numpadOnly = "numonly" in parts,
            ).normalized(landscape)
        }
    }
}

/**
 * Las dos últimas combinaciones usadas, para alternar entre ellas deslizando sobre `⌨`
 * (docs/spec-v6-teclado.md §4.2): como Alt+Tab entre las dos últimas.
 */
class ComboHistory(var current: KeyboardCombo, var previous: KeyboardCombo? = null) {
    /** Pasa a [next] guardando la actual como anterior (si no cambia nada, no se toca el historial). */
    fun change(next: KeyboardCombo) {
        if (next == current) return
        previous = current
        current = next
    }

    /** Intercambia la actual con la anterior. Devuelve false si todavía no hay anterior. */
    fun swap(): Boolean {
        val p = previous ?: return false
        previous = current
        current = p
        return true
    }
}

/**
 * Construye las teclas del teclado según la distribución, los bloques y la orientación (docs/spec-v6-teclado.md §4).
 * Las distribuciones son tablas: añadir otra es añadir un [KeyLabels] y una fila en [LAYOUTS].
 */
object KeyboardModel {
    /** Ancho de la base (con Esc) y espacio entre bloques, en unidades. */
    const val BASE_WIDTH = 16f
    const val GAP = 0.5f
    const val ARROWS_WIDTH = 3f
    const val NAV_WIDTH = 3f
    const val NUMPAD_WIDTH = 4f
    const val FIXED_COLUMN_WIDTH = 1.25f

    /** Una tecla de la base de una distribución: su uso, ancho y etiquetas. */
    class Cap(val usage: Int, val w: Float, val label: String, val shift: String? = null, val altGr: String? = null, val h: Float = 1f)

    /** Textos y teclas de una distribución. [rows] son las cuatro filas de arriba de la base; la de abajo es común. */
    class KeyLabels(
        val id: String,
        val name: String,
        val rows: List<List<Cap>>,
        val altGrLabel: String,
        val strings: Map<Int, String>,
    )

    private fun letterRow(text: String): List<Cap> = text.map { Cap(0x04 + (it - 'A'), 1f, it.toString()) }

    // Textos de las teclas especiales: en la distribución española en español y en la de EE. UU. como en su teclado.
    private val STRINGS_ES = mapOf(
        KeyUsages.BACKSPACE to "Retroceso", KeyUsages.TAB to "Tab", KeyUsages.CAPS_LOCK to "Bloq Mayús", KeyUsages.ENTER to "Intro",
        KeyUsages.ESC to "Esc", KeyUsages.SPACE to "", KeyUsages.MENU to "Menú", KeyUsages.CTRL to "Ctrl", KeyUsages.SHIFT to "⇧",
        KeyUsages.SHIFT_RIGHT to "⇧", KeyUsages.ALT to "Alt", KeyUsages.WIN to "Win", KeyUsages.PRINT_SCREEN to "Impr Pant",
        KeyUsages.INSERT to "Insert", KeyUsages.DELETE to "Supr", KeyUsages.HOME to "Inicio", KeyUsages.END to "Fin",
        KeyUsages.PAGE_UP to "RePág", KeyUsages.PAGE_DOWN to "AvPág", KeyUsages.NUM_LOCK to "Bloq Num",
    )

    private val STRINGS_US = mapOf(
        KeyUsages.BACKSPACE to "Backspace", KeyUsages.TAB to "Tab", KeyUsages.CAPS_LOCK to "Caps Lock", KeyUsages.ENTER to "Enter",
        KeyUsages.ESC to "Esc", KeyUsages.SPACE to "", KeyUsages.MENU to "Menu", KeyUsages.CTRL to "Ctrl", KeyUsages.SHIFT to "⇧",
        KeyUsages.SHIFT_RIGHT to "⇧", KeyUsages.ALT to "Alt", KeyUsages.WIN to "Win", KeyUsages.PRINT_SCREEN to "PrtSc",
        KeyUsages.INSERT to "Ins", KeyUsages.DELETE to "Del", KeyUsages.HOME to "Home", KeyUsages.END to "End",
        KeyUsages.PAGE_UP to "PgUp", KeyUsages.PAGE_DOWN to "PgDn", KeyUsages.NUM_LOCK to "Num Lock",
    )

    /** Español (España), ISO: `º ª \`, `ñ`, `ç`, la tecla `<>` y AltGr (`@ # ~ € \ | [ ] { }`). */
    val ES = KeyLabels(
        id = "es",
        name = "Español (España)",
        rows = listOf(
            // Fila de números (Esc se añade delante).
            listOf(
                Cap(0x35, 1f, "º", "ª", "\\"), Cap(0x1E, 1f, "1", "!", "|"), Cap(0x1F, 1f, "2", "\"", "@"), Cap(0x20, 1f, "3", "·", "#"),
                Cap(0x21, 1f, "4", "$", "~"), Cap(0x22, 1f, "5", "%", "€"), Cap(0x23, 1f, "6", "&", "¬"), Cap(0x24, 1f, "7", "/"),
                Cap(0x25, 1f, "8", "("), Cap(0x26, 1f, "9", ")"), Cap(0x27, 1f, "0", "="), Cap(0x2D, 1f, "'", "?"), Cap(0x2E, 1f, "¡", "¿"),
                Cap(KeyUsages.BACKSPACE, 2f, "Retroceso"),
            ),
            listOf(Cap(KeyUsages.TAB, 2f, "Tab")) + letterRow("QWERTYUIOP") +
                listOf(Cap(0x2F, 1f, "`", "^", "["), Cap(0x30, 1f, "+", "*", "]"), Cap(KeyUsages.ENTER, 2f, "Intro", h = 2f)),
            listOf(Cap(KeyUsages.CAPS_LOCK, 2f, "Bloq Mayús")) + letterRow("ASDFGHJKL") +
                listOf(Cap(0x33, 1f, "Ñ"), Cap(0x34, 1f, "´", "¨", "{"), Cap(0x32, 1f, "Ç", null, "}")),
            listOf(Cap(KeyUsages.SHIFT, 1.5f, "⇧"), Cap(KeyUsages.ISO_BACKSLASH, 1f, "<", ">")) + letterRow("ZXCVBNM") +
                listOf(Cap(0x36, 1f, ",", ";"), Cap(0x37, 1f, ".", ":"), Cap(0x38, 1f, "-", "_"), Cap(KeyUsages.SHIFT_RIGHT, 3.5f, "⇧")),
        ),
        altGrLabel = "Alt Gr",
        strings = STRINGS_ES,
    )

    /** Inglés (EE. UU.), ANSI. */
    val US = KeyLabels(
        id = "us",
        name = "Inglés (EE. UU.)",
        rows = listOf(
            listOf(
                Cap(0x35, 1f, "`", "~"), Cap(0x1E, 1f, "1", "!"), Cap(0x1F, 1f, "2", "@"), Cap(0x20, 1f, "3", "#"), Cap(0x21, 1f, "4", "$"),
                Cap(0x22, 1f, "5", "%"), Cap(0x23, 1f, "6", "^"), Cap(0x24, 1f, "7", "&"), Cap(0x25, 1f, "8", "*"), Cap(0x26, 1f, "9", "("),
                Cap(0x27, 1f, "0", ")"), Cap(0x2D, 1f, "-", "_"), Cap(0x2E, 1f, "=", "+"), Cap(KeyUsages.BACKSPACE, 2f, "Backspace"),
            ),
            listOf(Cap(KeyUsages.TAB, 2f, "Tab")) + letterRow("QWERTYUIOP") +
                listOf(Cap(0x2F, 1f, "[", "{"), Cap(0x30, 1f, "]", "}"), Cap(0x31, 2f, "\\", "|")),
            listOf(Cap(KeyUsages.CAPS_LOCK, 2.25f, "Caps Lock")) + letterRow("ASDFGHJKL") +
                listOf(Cap(0x33, 1f, ";", ":"), Cap(0x34, 1f, "'", "\""), Cap(KeyUsages.ENTER, 2.75f, "Enter")),
            listOf(Cap(KeyUsages.SHIFT, 2.5f, "⇧")) + letterRow("ZXCVBNM") +
                listOf(Cap(0x36, 1f, ",", "<"), Cap(0x37, 1f, ".", ">"), Cap(0x38, 1f, "/", "?"), Cap(KeyUsages.SHIFT_RIGHT, 3.5f, "⇧")),
        ),
        altGrLabel = "Alt",
        strings = STRINGS_US,
    )

    /** Distribuciones disponibles, por id. */
    val LAYOUTS: List<KeyLabels> = listOf(ES, US)

    fun layout(id: String?): KeyLabels = LAYOUTS.firstOrNull { it.id == id } ?: ES

    /** Fila inferior de la base (común): las dos teclas fijas y las de control. Suma [BASE_WIDTH]. */
    private fun bottomRow(l: KeyLabels, y: Float): List<KeyDef> {
        var x = 0f
        fun add(w: Float, usage: Int, label: String, kind: KeyKind, zone: KeyZone = KeyZone.BASE): KeyDef {
            val k = KeyDef(usage, x, y, w, 1f, label, kind = kind, zone = zone)
            x += w
            return k
        }
        return listOf(
            add(1.25f, 0, "⌨", KeyKind.MODE, KeyZone.FIXED),
            add(1.25f, 0, "🖱", KeyKind.MOUSE, KeyZone.FIXED),
            add(1.25f, KeyUsages.CTRL, "Ctrl", KeyKind.MODIFIER),
            add(1.25f, KeyUsages.WIN, "Win", KeyKind.MODIFIER),
            add(1.25f, KeyUsages.ALT, "Alt", KeyKind.MODIFIER),
            add(7.25f, KeyUsages.SPACE, "", KeyKind.NORMAL),
            add(1.25f, KeyUsages.ALT_GR, l.altGrLabel, KeyKind.MODIFIER),
            add(1.25f, KeyUsages.MENU, l.strings.getValue(KeyUsages.MENU), KeyKind.NORMAL),
        )
    }

    private fun kindOf(usage: Int) = when {
        KeyUsages.isModifier(usage) -> KeyKind.MODIFIER
        usage == KeyUsages.CAPS_LOCK || usage == KeyUsages.NUM_LOCK || usage == KeyUsages.SCROLL_LOCK -> KeyKind.LOCK
        else -> KeyKind.NORMAL
    }

    private fun base(l: KeyLabels, y0: Float): List<KeyDef> {
        val out = mutableListOf<KeyDef>()
        // Fila de números: Esc y el resto. Los usos de Esc y de la fila no se repiten con los del bloque Fn (Esc solo está aquí).
        val numberRow = listOf(Cap(KeyUsages.ESC, 1f, "Esc")) + l.rows[0]
        val rows = listOf(numberRow) + l.rows.drop(1)
        rows.forEachIndexed { r, caps ->
            var x = 0f
            // La Intro ISO (alta) ocupa la fila 1 y la 2: la fila 2 termina donde empieza.
            for (c in caps) {
                out += KeyDef(
                    c.usage, x, y0 + r, c.w, c.h, c.label, c.shift, c.altGr, kindOf(c.usage), KeyZone.BASE,
                    letter = c.usage in 0x04..0x1D,
                )
                x += c.w
            }
        }
        out += bottomRow(l, y0 + 4)
        return out
    }

    /** Fila Fn: F1–F12 e Impr Pant, a lo ancho de la base (Esc está en la base). */
    private fun fnRow(l: KeyLabels, y: Float): List<KeyDef> {
        val out = mutableListOf<KeyDef>()
        var x = 0f
        for (f in 1..12) {
            out += KeyDef(0x3A + f - 1, x, y, 1.25f, 1f, "F$f", zone = KeyZone.FN)
            x += 1.25f
        }
        out += KeyDef(KeyUsages.PRINT_SCREEN, x, y, 1f, 1f, l.strings.getValue(KeyUsages.PRINT_SCREEN), zone = KeyZone.FN)
        return out
    }

    private fun arrowsAt(x0: Float, y0: Float, zone: KeyZone): List<KeyDef> = listOf(
        KeyDef(0, x0, y0, 1f, 1f, "", kind = KeyKind.SPACER, zone = zone),
        KeyDef(KeyUsages.UP, x0 + 1, y0, 1f, 1f, "▲", zone = zone),
        KeyDef(0, x0 + 2, y0, 1f, 1f, "", kind = KeyKind.SPACER, zone = zone),
        KeyDef(KeyUsages.LEFT, x0, y0 + 1, 1f, 1f, "◀", zone = zone),
        KeyDef(KeyUsages.DOWN, x0 + 1, y0 + 1, 1f, 1f, "▼", zone = zone),
        KeyDef(KeyUsages.RIGHT, x0 + 2, y0 + 1, 1f, 1f, "▶", zone = zone),
    )

    private fun spacers(x0: Float, y: Float, n: Int, zone: KeyZone) =
        List(n) { KeyDef(0, x0 + it, y, 1f, 1f, "", kind = KeyKind.SPACER, zone = zone) }

    /** Navegación: Insert, Inicio, RePág / Supr, Fin, AvPág y las flechas en T invertida (filas 0–4 de la base). */
    private fun nav(l: KeyLabels, x0: Float, y0: Float): List<KeyDef> {
        val s = l.strings
        fun k(u: Int, x: Float, y: Float) = KeyDef(u, x0 + x, y0 + y, 1f, 1f, s.getValue(u), zone = KeyZone.NAV)
        return listOf(
            k(KeyUsages.INSERT, 0f, 0f), k(KeyUsages.HOME, 1f, 0f), k(KeyUsages.PAGE_UP, 2f, 0f),
            k(KeyUsages.DELETE, 0f, 1f), k(KeyUsages.END, 1f, 1f), k(KeyUsages.PAGE_DOWN, 2f, 1f),
        ) + spacers(x0, y0 + 2, 3, KeyZone.NAV) + arrowsAt(x0, y0 + 3, KeyZone.NAV)
    }

    /** Numpad: Bloq Num, `/ * -`, 7–9 y `+` (alta), 4–6, 1–3 e Intro (alta), 0 (ancha) y `.`. */
    private fun numpad(l: KeyLabels, x0: Float, y0: Float, zone: KeyZone, ws: Float = 1f, hs: Float = 1f): List<KeyDef> {
        fun k(usage: Int, c: Int, r: Float, label: String, w: Int = 1, h: Float = 1f, sub: String? = null): KeyDef =
            KeyDef(usage, x0 + c * ws, y0 + r * hs, w * ws, h * hs, label, shiftLabel = sub, kind = kindOf(usage), zone = zone)
        return listOf(
            k(KeyUsages.NUM_LOCK, 0, 0f, l.strings.getValue(KeyUsages.NUM_LOCK)), k(0x54, 1, 0f, "/"), k(0x55, 2, 0f, "*"), k(0x56, 3, 0f, "-"),
            k(0x5F, 0, 1f, "7", sub = "Inicio"), k(0x60, 1, 1f, "8", sub = "▲"), k(0x61, 2, 1f, "9", sub = "RePág"), k(0x57, 3, 1f, "+", h = 2f),
            k(0x5C, 0, 2f, "4", sub = "◀"), k(0x5D, 1, 2f, "5"), k(0x5E, 2, 2f, "6", sub = "▶"),
            k(0x59, 0, 3f, "1", sub = "Fin"), k(0x5A, 1, 3f, "2", sub = "▼"), k(0x5B, 2, 3f, "3", sub = "AvPág"), k(KeyUsages.KEYPAD_ENTER, 3, 3f, "Intro", h = 2f),
            k(0x62, 0, 4f, "0", w = 2, sub = "Insert"), k(0x63, 2, 4f, ".", sub = "Supr"),
        )
    }

    /**
     * Las teclas del teclado para [layoutId] (`es`, `us`), la combinación [combo] y la orientación. La combinación se normaliza
     * antes con [KeyboardCombo.normalized].
     */
    fun build(layoutId: String?, combo: KeyboardCombo, landscape: Boolean): KeyboardGeometry {
        val l = layout(layoutId)
        val c = combo.normalized(landscape)
        if (c.numpadOnly) return numpadOnly(l)

        val y0 = if (c.fn) 1f else 0f
        val keys = mutableListOf<KeyDef>()
        if (c.fn) keys += fnRow(l, 0f)
        keys += base(l, y0)
        var x = BASE_WIDTH
        if (c.nav) {
            keys += nav(l, x + GAP, y0)
            x += GAP + NAV_WIDTH
        } else if (c.arrows) {
            keys += arrowsAt(x + GAP, y0 + 3, KeyZone.ARROWS)
            x += GAP + ARROWS_WIDTH
        }
        if (c.numpad) {
            keys += numpad(l, x + GAP, y0, KeyZone.NUMPAD)
            x += GAP + NUMPAD_WIDTH
        }
        return KeyboardGeometry(keys, x, y0 + 5)
    }

    /** El numpad grande a toda la pantalla, con `⌨` y `🖱` en una columna a la izquierda. */
    private fun numpadOnly(l: KeyLabels): KeyboardGeometry {
        val keys = mutableListOf<KeyDef>()
        keys += KeyDef(0, 0f, 0f, FIXED_COLUMN_WIDTH, 2.5f, "⌨", kind = KeyKind.MODE, zone = KeyZone.FIXED)
        keys += KeyDef(0, 0f, 2.5f, FIXED_COLUMN_WIDTH, 2.5f, "🖱", kind = KeyKind.MOUSE, zone = KeyZone.FIXED)
        keys += numpad(l, FIXED_COLUMN_WIDTH, 0f, KeyZone.NUMPAD_ONLY)
        return KeyboardGeometry(keys, FIXED_COLUMN_WIDTH + NUMPAD_WIDTH, 5f)
    }
}
