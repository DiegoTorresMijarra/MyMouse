package dev.mymouse.core.hid

/**
 * Teclas del teclado HID (página de uso 0x07) y bits de modificadores del informe de teclado
 * (docs/spec-v5-bluetooth-completo.md §2). Los nombres son los mismos que en `gestures.json` del PC (`Ctrl+Shift+T`).
 */
object HidKeys {
    /** Bits del byte de modificadores del informe de teclado. */
    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08

    /** Alt derecho: en las distribuciones con AltGr (Español) es AltGr. */
    const val MOD_ALTGR = 0x40

    /** Una tecla del selector del editor: nombre canónico (el que se guarda), etiqueta para mostrar y uso HID. */
    data class Key(val name: String, val label: String, val usage: Int)

    /** Letras A–Z, números, F1–F12, flechas y teclas de edición: lo que ofrece el editor. */
    val PICKER: List<Key> = buildList {
        for (i in 0 until 26) add(Key(('A' + i).toString(), ('A' + i).toString(), 0x04 + i))
        for (d in 1..9) add(Key(d.toString(), d.toString(), 0x1E + d - 1))
        add(Key("0", "0", 0x27))
        for (f in 1..12) add(Key("F$f", "F$f", 0x3A + f - 1))
        add(Key("Left", "←", 0x50))
        add(Key("Right", "→", 0x4F))
        add(Key("Up", "↑", 0x52))
        add(Key("Down", "↓", 0x51))
        add(Key("Home", "Inicio", 0x4A))
        add(Key("End", "Fin", 0x4D))
        add(Key("PageUp", "RePág", 0x4B))
        add(Key("PageDown", "AvPág", 0x4E))
        add(Key("Delete", "Supr", 0x4C))
        add(Key("Backspace", "Retroceso", 0x2A))
        add(Key("Enter", "Intro", 0x28))
        add(Key("Esc", "Esc", 0x29))
        add(Key("Tab", "Tab", 0x2B))
        add(Key("Space", "Espacio", 0x2C))
        add(Key("PrintScreen", "Impr Pant", 0x46))
    }

    private val byName: Map<String, Key> = PICKER.associateBy { it.name.lowercase() }

    private val byUsage: Map<Int, Key> = PICKER.associateBy { it.usage }

    /** Otros nombres que se aceptan al leer un atajo (los del PC, en inglés y en español). */
    private val aliases: Map<String, String> = mapOf(
        "return" to "enter", "intro" to "enter", "escape" to "esc", "espacio" to "space", "retroceso" to "backspace",
        "del" to "delete", "supr" to "delete", "suprimir" to "delete", "inicio" to "home", "fin" to "end",
        "pgup" to "pageup", "repág" to "pageup", "repag" to "pageup", "pgdn" to "pagedown", "avpág" to "pagedown",
        "avpag" to "pagedown", "izquierda" to "left", "arriba" to "up", "derecha" to "right", "abajo" to "down",
        "prtsc" to "printscreen", "imprpant" to "printscreen",
    )

    /** La tecla con ese nombre (sin distinguir mayúsculas; admite los alias del PC), o null. */
    fun byName(name: String): Key? {
        val k = name.trim().lowercase()
        return byName[aliases[k] ?: k]
    }

    /** Nombre canónico del uso HID, o null si no está en el selector. */
    fun nameOf(usage: Int): String? = byUsage[usage]?.name

    /** Etiqueta para mostrar de un uso HID (o su nombre canónico). */
    fun labelOf(usage: Int): String? = byUsage[usage]?.label
}
