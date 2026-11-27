package dev.mymouse.core.hid

import dev.mymouse.core.keyboard.KeyEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HidKeyboardStateTest {
    private val s = HidKeyboardState()

    private fun bytes(vararg b: Int) = HidReport(HidMapper.KEYBOARD_ID, ByteArray(8) { b.getOrElse(it) { 0 }.toByte() })

    @Test
    fun keysAndModifiersAccumulateInTheReport() {
        assertEquals(bytes(0x02), s.apply(KeyEvent(0xE1, true))) // Shift izq.
        assertEquals(bytes(0x02, 0, 0x04), s.apply(KeyEvent(0x04, true)))
        assertEquals(bytes(0x02, 0, 0x04, 0x05), s.apply(KeyEvent(0x05, true)))
        assertEquals(bytes(0x02, 0, 0x05), s.apply(KeyEvent(0x04, false)))
        assertEquals(bytes(0, 0, 0x05), s.apply(KeyEvent(0xE1, false)))
        assertEquals(bytes(), s.apply(KeyEvent(0x05, false)))
        assertTrue(s.isEmpty)
    }

    @Test
    fun modifierUsagesMapToTheirBitsAndAltGrIsTheRightAlt() {
        for (i in 0..7) {
            val r = s.apply(KeyEvent(0xE0 + i, true))!!
            assertEquals(1 shl i, r.data[0].toInt() and 0xFF)
            s.apply(KeyEvent(0xE0 + i, false))
        }
        assertEquals(HidKeys.MOD_ALTGR, s.apply(KeyEvent(0xE6, true))!!.data[0].toInt())
        assertEquals(HidKeys.MOD_GUI, 1 shl (0xE3 - 0xE0))
    }

    @Test
    fun repeatedPressAndUnknownReleaseChangeNothing() {
        s.apply(KeyEvent(0x04, true))
        assertNull(s.apply(KeyEvent(0x04, true)))
        assertNull(s.apply(KeyEvent(0x09, false)))
        assertNull(s.apply(KeyEvent(0xE1, false)))
    }

    @Test
    fun aSeventhNormalKeyIsIgnored() {
        for (u in 0x04..0x09) assertEquals(true, s.apply(KeyEvent(u, true)) != null)
        assertNull(s.apply(KeyEvent(0x0A, true)))
        // Los modificadores no cuentan para las seis.
        assertEquals(0x01, s.apply(KeyEvent(0xE0, true))!!.data[0].toInt())
        // Al soltar la séptima (que nunca entró) no cambia nada, y al soltar otra hay hueco otra vez.
        assertNull(s.apply(KeyEvent(0x0A, false)))
        s.apply(KeyEvent(0x04, false))
        assertEquals(6, s.apply(KeyEvent(0x0A, true))!!.data.drop(2).count { it.toInt() != 0 })
    }

    @Test
    fun releaseAllGivesTheEmptyReport() {
        s.apply(KeyEvent(0xE1, true)); s.apply(KeyEvent(0x04, true))
        assertEquals(bytes(), s.releaseAll())
        assertTrue(s.isEmpty)
        // Aunque no hubiera nada, siempre devuelve el informe vacío (se envía al salir).
        assertEquals(bytes(), s.releaseAll())
    }

    @Test
    fun ledOutputReportConvertsToProtocolBits() {
        // HID: bit 0 = Num, 1 = Mayús, 2 = Despl. Protocolo: 1 = Mayús, 2 = Num, 4 = Despl.
        assertEquals(0, HidLeds.toProtocolBits(byteArrayOf(0)))
        assertEquals(2, HidLeds.toProtocolBits(byteArrayOf(1)))
        assertEquals(1, HidLeds.toProtocolBits(byteArrayOf(2)))
        assertEquals(3, HidLeds.toProtocolBits(byteArrayOf(3)))
        assertEquals(4, HidLeds.toProtocolBits(byteArrayOf(4)))
        // Con el id del teclado delante (según la versión de Android).
        assertEquals(1, HidLeds.toProtocolBits(byteArrayOf(HidMapper.KEYBOARD_ID.toByte(), 2)))
        // Bits de Compose y Kana se ignoran.
        assertEquals(1, HidLeds.toProtocolBits(byteArrayOf(0x1A)))
        assertNull(HidLeds.toProtocolBits(byteArrayOf()))
        assertNull(HidLeds.toProtocolBits(byteArrayOf(1, 1, 1)))
        assertNull(HidLeds.toProtocolBits(byteArrayOf(5, 1)))
        assertContentEquals(byteArrayOf(1), byteArrayOf(HidDescriptor.LED_REPORT_SIZE.toByte()))
    }
}
