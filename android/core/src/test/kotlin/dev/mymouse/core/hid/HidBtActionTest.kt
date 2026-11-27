package dev.mymouse.core.hid

import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.btactions.KeyboardLayouts
import kotlin.test.Test
import kotlin.test.assertEquals

class HidBtActionTest {
    private val m = HidMapper()
    private val es = KeyboardLayouts.ES
    private val us = KeyboardLayouts.US

    private fun key(modifiers: Int, code: Int = 0) =
        HidReport(2, byteArrayOf(modifiers.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0))

    @Test
    fun shortcutIsPressAndRelease() {
        assertEquals(listOf(key(0x01, 0x1D), key(0)), m.action(BtActions.parseKeys("Ctrl+Z"), es))
        assertEquals(listOf(key(0x0A, 0x16), key(0)), m.action(BtActions.parseKeys("Win+Shift+S"), es))
        assertEquals(listOf(key(0x08, 0x0F), key(0)), m.action(BtActions.parseKeys("Win+L"), es))
        // Solo un modificador: se pulsa y se suelta.
        assertEquals(listOf(key(0x08), key(0)), m.action(BtActions.parseKeys("Win"), es))
    }

    @Test
    fun shortcutsDoNotDependOnTheLayout() {
        assertEquals(m.action(BtActions.parseKeys("Ctrl+Z"), es), m.action(BtActions.parseKeys("Ctrl+Z"), us))
    }

    @Test
    fun mediaIsAConsumerPress() {
        assertEquals(m.consumer(ConsumerKey.PLAY_PAUSE), m.action(BtAction.Media(ConsumerKey.PLAY_PAUSE), es))
    }

    @Test
    fun textIsPressAndReleasePerKey() {
        assertEquals(
            listOf(key(0x02, 0x0B), key(0), key(0, 0x08), key(0), key(0, 0x0F), key(0), key(0, 0x0F), key(0), key(0, 0x12), key(0)),
            m.action(BtAction.Text("Hello"), us),
        )
    }

    @Test
    fun spanishTextUsesAltGrAndDeadKeys() {
        // "a@ñ": a, AltGr+2 (modificador 0x40), ñ.
        assertEquals(
            listOf(key(0, 0x04), key(0), key(0x40, 0x1F), key(0), key(0, 0x33), key(0)),
            m.action(BtAction.Text("a@ñ"), es),
        )
        // "é": tecla muerta ´ y e.
        assertEquals(listOf(key(0, 0x34), key(0), key(0, 0x08), key(0)), m.action(BtAction.Text("é"), es))
        // Salto de línea: Intro.
        assertEquals(listOf(key(0, 0x28), key(0)), m.action(BtAction.Text("\n"), es))
    }

    @Test
    fun unsupportedCharactersAreSkipped() {
        assertEquals(listOf(key(0, 0x04), key(0)), m.action(BtAction.Text("ñañ"), us))
        assertEquals(emptyList(), m.action(BtAction.Text("😀"), us))
    }

    @Test
    fun repeatedKeysAreSeparatedByARelease() {
        val reports = m.action(BtAction.Text("ll"), us)
        assertEquals(listOf(key(0, 0x0F), key(0), key(0, 0x0F), key(0)), reports)
    }
}
