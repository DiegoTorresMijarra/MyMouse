package dev.mymouse.core.btactions

import dev.mymouse.core.hid.HidKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardLayoutTest {
    private val es = KeyboardLayouts.ES
    private val us = KeyboardLayouts.US
    private val shift = HidKeys.MOD_SHIFT
    private val altGr = HidKeys.MOD_ALTGR

    private fun KeyboardLayout.one(c: Char): KeyPress {
        val strokes = strokes(c)!!
        assertEquals(1, strokes.size, "'$c' debería ser una sola pulsación")
        return strokes[0]
    }

    @Test
    fun lettersAndDigitsAreTheSameInBothLayouts() {
        for (layout in KeyboardLayouts.ALL) {
            assertEquals(KeyPress(0, 0x04), layout.one('a'))
            assertEquals(KeyPress(shift, 0x04), layout.one('A'))
            assertEquals(KeyPress(0, 0x1D), layout.one('z'))
            assertEquals(KeyPress(0, 0x1E), layout.one('1'))
            assertEquals(KeyPress(0, 0x27), layout.one('0'))
            assertEquals(KeyPress(0, 0x2C), layout.one(' '))
            assertEquals(KeyPress(0, 0x28), layout.one('\n'))
            assertEquals(KeyPress(0, 0x2B), layout.one('\t'))
        }
    }

    @Test
    fun spanishSymbolsNeedAltGrAndOwnKeys() {
        assertEquals(KeyPress(altGr, 0x1F), es.one('@')) // AltGr+2
        assertEquals(KeyPress(altGr, 0x20), es.one('#')) // AltGr+3
        assertEquals(KeyPress(altGr, 0x21), es.one('~')) // AltGr+4
        assertEquals(KeyPress(altGr, 0x1E), es.one('|')) // AltGr+1
        assertEquals(KeyPress(altGr, 0x35), es.one('\\'))
        assertEquals(KeyPress(altGr, 0x2F), es.one('['))
        assertEquals(KeyPress(altGr, 0x30), es.one(']'))
        assertEquals(KeyPress(altGr, 0x34), es.one('{'))
        assertEquals(KeyPress(altGr, 0x32), es.one('}'))
        assertEquals(KeyPress(altGr, 0x08), es.one('€'))
        assertEquals(KeyPress(0, 0x33), es.one('ñ'))
        assertEquals(KeyPress(shift, 0x33), es.one('Ñ'))
        assertEquals(KeyPress(0, 0x32), es.one('ç'))
        assertEquals(KeyPress(0, 0x2E), es.one('¡'))
        assertEquals(KeyPress(shift, 0x2E), es.one('¿'))
        assertEquals(KeyPress(shift, 0x1F), es.one('"'))
        assertEquals(KeyPress(0, 0x2D), es.one('\''))
        assertEquals(KeyPress(shift, 0x24), es.one('/'))
        assertEquals(KeyPress(shift, 0x27), es.one('='))
        assertEquals(KeyPress(0, 0x38), es.one('-'))
        assertEquals(KeyPress(shift, 0x38), es.one('_'))
        assertEquals(KeyPress(0, 0x64), es.one('<'))
        assertEquals(KeyPress(shift, 0x64), es.one('>'))
        assertEquals(KeyPress(0, 0x30), es.one('+'))
    }

    @Test
    fun accentsAreDeadKeyPlusVowel() {
        // á = ´ (tecla del apóstrofo) y a; Á = ´ y Mayús+a.
        assertEquals(listOf(KeyPress(0, 0x34), KeyPress(0, 0x04)), es.strokes('á'))
        assertEquals(listOf(KeyPress(0, 0x34), KeyPress(shift, 0x04)), es.strokes('Á'))
        assertEquals(listOf(KeyPress(0, 0x34), KeyPress(0, 0x18)), es.strokes('ú'))
        assertEquals(listOf(KeyPress(shift, 0x34), KeyPress(0, 0x18)), es.strokes('ü')) // diéresis
        assertEquals(listOf(KeyPress(0, 0x2F), KeyPress(0, 0x08)), es.strokes('è'))
        assertEquals(listOf(KeyPress(shift, 0x2F), KeyPress(0, 0x12)), es.strokes('ô'))
        // El acento suelto: tecla muerta y Espacio.
        assertEquals(listOf(KeyPress(0, 0x34), KeyPress(0, 0x2C)), es.strokes('´'))
    }

    @Test
    fun everyAccentedVowelExistsInSpanish() {
        for (c in "áéíóúÁÉÍÓÚàèìòùÀÈÌÒÙâêîôûÂÊÎÔÛäëïöüÄËÏÖÜ") {
            val s = es.strokes(c)
            assertTrue(s != null && s.size == 2, "falta $c")
        }
    }

    @Test
    fun englishHasNoAccentsButHasItsSymbols() {
        assertNull(us.strokes('ñ'))
        assertNull(us.strokes('á'))
        assertEquals(KeyPress(shift, 0x1F), us.one('@'))
        assertEquals(KeyPress(shift, 0x20), us.one('#'))
        assertEquals(KeyPress(0, 0x34), us.one('\''))
        assertEquals(KeyPress(shift, 0x34), us.one('"'))
        assertEquals(KeyPress(0, 0x31), us.one('\\'))
        assertEquals(KeyPress(shift, 0x31), us.one('|'))
        assertEquals(KeyPress(0, 0x35), us.one('`'))
        assertEquals(KeyPress(shift, 0x35), us.one('~'))
        assertEquals(KeyPress(shift, 0x26), us.one('('))
        assertEquals(KeyPress(shift, 0x27), us.one(')'))
        assertEquals(KeyPress(0, 0x2E), us.one('='))
        assertEquals(KeyPress(shift, 0x38), us.one('?'))
    }

    @Test
    fun everyAsciiPrintableCharacterIsTypeableInEnglish() {
        for (c in ' '..'~') assertTrue(us.supports(c), "falta '$c' en US")
    }

    @Test
    fun everyAsciiPrintableCharacterIsTypeableInSpanish() {
        // Salvo los acentos sueltos del teclado muerto, ASCII completo: `` ` `` y ^ salen con tecla muerta + Espacio.
        for (c in ' '..'~') assertTrue(es.supports(c), "falta '$c' en ES")
    }

    @Test
    fun allUsagesAreWithinTheKeyboardDescriptor() {
        for (layout in KeyboardLayouts.ALL) {
            for (c in (' '..'~').toList() + "áéíóúñÑçÇ¿¡€ºª·¬\n\t".toList()) {
                for (k in layout.strokes(c).orEmpty()) assertTrue(k.usage in 0x04..0x65, "${layout.id} '$c' ${k.usage}")
            }
        }
    }

    @Test
    fun reportsUnsupportedCharactersOnce() {
        assertEquals(emptyList(), es.unsupported("Hola, ¿qué tal? ñandú @ \n\r"))
        assertEquals(listOf("ã"), es.unsupported("pão ã"))
        assertEquals(listOf("ñ", "á"), us.unsupported("ñññáá"))
        assertEquals(listOf("😀", "€"), us.unsupported("a😀b😀€"))
        assertFalse(us.supports('€'))
    }

    @Test
    fun typeStrokesSkipsWhatItCannot() {
        assertEquals(listOf(KeyPress(0, 0x04), KeyPress(0, 0x05)), us.typeStrokes("añb\r😀"))
        assertEquals(emptyList(), us.typeStrokes("ñ"))
    }

    @Test
    fun layoutLookupFallsBackToSpanish() {
        assertEquals("es", KeyboardLayouts.byId(null).id)
        assertEquals("es", KeyboardLayouts.byId("xx").id)
        assertEquals("us", KeyboardLayouts.byId("us").id)
    }
}
