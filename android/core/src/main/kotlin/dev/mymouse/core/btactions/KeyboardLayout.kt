package dev.mymouse.core.btactions

import dev.mymouse.core.hid.HidKeys

/** Una pulsación: modificadores (Shift, AltGr…) y uso HID de la tecla. */
data class KeyPress(val modifiers: Int, val usage: Int)

/**
 * Distribución del teclado **del PC** (docs/spec-v5-bluetooth-completo.md §2.1). Por Bluetooth el móvil envía posiciones de tecla
 * (usos HID) y es Windows quien las convierte en caracteres según su distribución: para escribir "@" hay que saber en qué tecla está.
 * [strokes] da, para cada carácter, la secuencia de pulsaciones que lo produce (una, o dos con teclas muertas: `á` = ´ + a).
 */
class KeyboardLayout(val id: String, private val table: Map<Char, List<KeyPress>>) {

    /** Pulsaciones que escriben [c], o null si esta distribución no lo tiene (o no se sabe teclear). */
    fun strokes(c: Char): List<KeyPress>? = table[c]

    fun supports(c: Char): Boolean = c in table

    /**
     * Caracteres de [text] que no se pueden escribir, sin repetir y en orden de aparición (un emoji cuenta como uno).
     * `\r` no cuenta: se ignora.
     */
    fun unsupported(text: String): List<String> {
        val out = LinkedHashSet<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            if (cp != '\r'.code && !(n == 1 && supports(text[i]))) out += text.substring(i, i + n)
            i += n
        }
        return out.toList()
    }

    /** Pulsaciones de todo el texto, saltando lo que no se puede escribir. */
    fun typeStrokes(text: String): List<KeyPress> = text.flatMap { strokes(it).orEmpty() }
}

/** Las distribuciones disponibles: Español (España), por defecto, e Inglés (EE. UU.). */
object KeyboardLayouts {
    private const val S = HidKeys.MOD_SHIFT
    private const val G = HidKeys.MOD_ALTGR

    private const val ENTER = 0x28
    private const val TAB = 0x2B
    private const val SPACE = 0x2C

    /** Letras y dígitos, iguales en las dos distribuciones. */
    private fun MutableMap<Char, List<KeyPress>>.letters() {
        for (i in 0 until 26) {
            put('a' + i, listOf(KeyPress(0, 0x04 + i)))
            put('A' + i, listOf(KeyPress(S, 0x04 + i)))
        }
        for (d in 1..9) put('0' + d, listOf(KeyPress(0, 0x1E + d - 1)))
        put('0', listOf(KeyPress(0, 0x27)))
        put(' ', listOf(KeyPress(0, SPACE)))
        put('\n', listOf(KeyPress(0, ENTER)))
        put('\t', listOf(KeyPress(0, TAB)))
    }

    private fun MutableMap<Char, List<KeyPress>>.key(c: Char, modifiers: Int, usage: Int) {
        put(c, listOf(KeyPress(modifiers, usage)))
    }

    /** Vocal con acento: tecla muerta (o su versión con Shift) y la vocal. */
    private fun MutableMap<Char, List<KeyPress>>.accent(dead: KeyPress, base: String, accented: String) {
        for (i in base.indices) {
            val b = base[i]
            val vowel = KeyPress(if (b.isUpperCase()) S else 0, 0x04 + (b.lowercaseChar() - 'a'))
            put(accented[i], listOf(dead, vowel))
        }
    }

    /** Inglés (EE. UU.). */
    val US: KeyboardLayout = KeyboardLayout("us", buildMap<Char, List<KeyPress>> {
        letters()
        // Fila de números con Shift.
        val shifted = "!@#$%^&*("
        for (d in 1..9) key(shifted[d - 1], S, 0x1E + d - 1)
        key(')', S, 0x27)
        key('-', 0, 0x2D); key('_', S, 0x2D)
        key('=', 0, 0x2E); key('+', S, 0x2E)
        key('[', 0, 0x2F); key('{', S, 0x2F)
        key(']', 0, 0x30); key('}', S, 0x30)
        key('\\', 0, 0x31); key('|', S, 0x31)
        key(';', 0, 0x33); key(':', S, 0x33)
        key('\'', 0, 0x34); key('"', S, 0x34)
        key('`', 0, 0x35); key('~', S, 0x35)
        key(',', 0, 0x36); key('<', S, 0x36)
        key('.', 0, 0x37); key('>', S, 0x37)
        key('/', 0, 0x38); key('?', S, 0x38)
    })

    private val DEAD_ACUTE = KeyPress(0, 0x34)
    private val DEAD_DIAERESIS = KeyPress(S, 0x34)
    private val DEAD_GRAVE = KeyPress(0, 0x2F)
    private val DEAD_CIRCUMFLEX = KeyPress(S, 0x2F)

    /**
     * Español (España), la distribución ISO de Windows: `ñ` y `ç` tienen tecla propia, `@ # ~ € \ | [ ] { }` van con AltGr
     * y los acentos son teclas muertas (`´` `¨` `` ` `` `^`) que se combinan con la vocal siguiente.
     */
    val ES: KeyboardLayout = KeyboardLayout("es", buildMap<Char, List<KeyPress>> {
        letters()
        // Fila de números: normal, Shift y AltGr.
        key('º', 0, 0x35); key('ª', S, 0x35); key('\\', G, 0x35)
        key('!', S, 0x1E); key('|', G, 0x1E)
        key('"', S, 0x1F); key('@', G, 0x1F)
        key('·', S, 0x20); key('#', G, 0x20)
        key('$', S, 0x21); key('~', G, 0x21)
        key('%', S, 0x22)
        key('&', S, 0x23); key('¬', G, 0x23)
        key('/', S, 0x24)
        key('(', S, 0x25)
        key(')', S, 0x26)
        key('=', S, 0x27)
        key('\'', 0, 0x2D); key('?', S, 0x2D)
        key('¡', 0, 0x2E); key('¿', S, 0x2E)
        key('[', G, 0x2F)
        key('+', 0, 0x30); key('*', S, 0x30); key(']', G, 0x30)
        key('ñ', 0, 0x33); key('Ñ', S, 0x33)
        key('{', G, 0x34)
        key('ç', 0, 0x32); key('Ç', S, 0x32); key('}', G, 0x32)
        key(',', 0, 0x36); key(';', S, 0x36)
        key('.', 0, 0x37); key(':', S, 0x37)
        key('-', 0, 0x38); key('_', S, 0x38)
        key('<', 0, 0x64); key('>', S, 0x64)
        key('€', G, 0x08) // AltGr+E, además de AltGr+5
        // Acentos: tecla muerta y vocal. Sueltos, la tecla muerta y Espacio.
        accent(DEAD_ACUTE, "aeiouAEIOU", "áéíóúÁÉÍÓÚ")
        accent(DEAD_GRAVE, "aeiouAEIOU", "àèìòùÀÈÌÒÙ")
        accent(DEAD_CIRCUMFLEX, "aeiouAEIOU", "âêîôûÂÊÎÔÛ")
        accent(DEAD_DIAERESIS, "aeiouAEIOU", "äëïöüÄËÏÖÜ")
        put('´', listOf(DEAD_ACUTE, KeyPress(0, SPACE)))
        put('`', listOf(DEAD_GRAVE, KeyPress(0, SPACE)))
        put('^', listOf(DEAD_CIRCUMFLEX, KeyPress(0, SPACE)))
        put('¨', listOf(DEAD_DIAERESIS, KeyPress(0, SPACE)))
    })

    val ALL: List<KeyboardLayout> = listOf(ES, US)

    /** La distribución con ese id (`es`, `us`); España si no se conoce. */
    fun byId(id: String?): KeyboardLayout = ALL.firstOrNull { it.id == id } ?: ES
}
