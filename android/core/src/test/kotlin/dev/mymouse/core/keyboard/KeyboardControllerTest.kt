package dev.mymouse.core.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardControllerTest {
    private val geo = KeyboardModel.build("es", KeyboardCombo(fn = true, numpad = true), true)
    private fun key(usage: Int) = geo.keys.first { it.usage == usage }
    private fun kind(kind: KeyKind) = geo.keys.first { it.kind == kind }
    private fun down(u: Int) = KeyEvent(u, true)
    private fun up(u: Int) = KeyEvent(u, false)

    private val a = 0x04
    private val shift = KeyUsages.SHIFT
    private val k = KeyboardController()

    @Test
    fun aNormalKeySendsPressAndRelease() {
        assertEquals(listOf(down(a)), k.press(1, key(a), 0))
        assertEquals(listOf(up(a)), k.release(1, 50))
        assertEquals(emptyList(), k.release(1, 60)) // ya soltada
    }

    @Test
    fun shiftThenLetterReleasesShiftAfterTheLetter() {
        assertEquals(listOf(down(shift)), k.press(1, key(shift), 0))
        assertEquals(emptyList(), k.release(1, 50)) // un toque: en espera
        assertEquals(listOf(down(a)), k.press(2, key(a), 200))
        assertEquals(listOf(up(a), up(shift)), k.release(2, 260))
    }

    @Test
    fun heldShiftWithSeveralLetters() {
        k.press(1, key(shift), 0)
        assertEquals(listOf(down(a)), k.press(2, key(a), 100))
        assertEquals(listOf(up(a)), k.release(2, 150))
        assertEquals(listOf(down(0x05)), k.press(2, key(0x05), 200))
        assertEquals(listOf(up(0x05)), k.release(2, 250))
        assertEquals(listOf(up(shift)), k.release(1, 400))
    }

    @Test
    fun releasingAndModeAndMouseKeysSendNothing() {
        assertEquals(emptyList(), k.press(1, kind(KeyKind.MODE), 0))
        assertEquals(emptyList(), k.press(2, kind(KeyKind.MOUSE), 0))
        assertEquals(emptyList(), k.release(1, 10))
        assertEquals(emptyList(), k.releaseAll())
    }

    @Test
    fun repeatStartsOnlyForRepeatableKeysAndOnlyWhenEnabled() {
        k.press(1, key(a), 0)
        assertEquals(400L, k.nextDeadlineMs)
        assertEquals(listOf(down(a)), k.tick(400))
        assertEquals(emptyList(), k.tick(410))
        assertEquals(listOf(down(a)), k.tick(440))
        k.release(1, 500)
        assertNull(k.nextDeadlineMs)
        assertEquals(emptyList(), k.tick(2_000))

        k.press(1, key(shift), 0)
        assertNull(k.nextDeadlineMs)
        k.press(2, key(KeyUsages.CAPS_LOCK), 0)
        assertNull(k.nextDeadlineMs)
        k.releaseAll()

        val bt = KeyboardController(repeatEnabled = false)
        bt.press(1, key(a), 0)
        assertNull(bt.nextDeadlineMs)
    }

    @Test
    fun releaseAllDropsKeysAndModifiers() {
        k.press(1, key(shift), 0); k.release(1, 40) // en espera
        k.press(2, key(KeyUsages.CTRL), 100) // con el dedo encima
        k.press(3, key(a), 150)
        k.press(4, key(0x05), 160)
        val out = k.releaseAll()
        assertEquals(setOf(up(a), up(0x05), up(KeyUsages.CTRL), up(shift)), out.toSet())
        assertEquals(out.size, out.toSet().size)
        assertTrue(k.pressedKeys.isEmpty())
        assertNull(k.nextDeadlineMs)
        assertFalse(k.modifiers.anyDown)
        // Y levantar los dedos después no envía nada.
        assertEquals(emptyList(), k.release(2, 300))
        assertEquals(emptyList(), k.release(3, 300))
    }

    @Test
    fun aSecondTouchOnTheSamePointerReleasesTheFirst() {
        k.press(1, key(a), 0)
        assertEquals(listOf(up(a), down(0x05)), k.press(1, key(0x05), 10))
    }

    @Test
    fun trackpadClickMakesHeldModifiersCountAndReleasesPendingOnes() {
        k.press(1, key(KeyUsages.CTRL), 0); k.release(1, 40) // Ctrl en espera
        assertTrue(k.modifiers.anyDown)
        k.onPointerClickStart()
        assertEquals(listOf(up(KeyUsages.CTRL)), k.afterPointerClick())
    }

    @Test
    fun ledsAreAssumedUntilTheHostReportsThem() {
        assertFalse(k.leds.known)
        k.press(1, key(KeyUsages.CAPS_LOCK), 0)
        k.release(1, 20)
        assertTrue(k.leds.caps)
        k.press(1, key(KeyUsages.CAPS_LOCK), 100); k.release(1, 120)
        assertFalse(k.leds.caps)
        k.press(1, key(KeyUsages.NUM_LOCK), 200); k.release(1, 220)
        assertTrue(k.leds.num)
        assertFalse(k.leds.known)
        // El PC manda las luces reales: a partir de ahí se hace caso al PC y no se conmuta.
        assertTrue(k.leds.onHostLeds(KeyboardLeds.LED_CAPS))
        assertTrue(k.leds.known && k.leds.caps && !k.leds.num)
        k.press(1, key(KeyUsages.CAPS_LOCK), 300); k.release(1, 320)
        assertTrue(k.leds.caps)
        assertFalse(k.leds.onHostLeds(KeyboardLeds.LED_CAPS))
        assertTrue(k.leds.onHostLeds(KeyboardLeds.LED_CAPS or KeyboardLeds.LED_NUM or KeyboardLeds.LED_SCROLL))
        assertEquals(7, k.leds.bits)
        k.leds.reset()
        assertFalse(k.leds.known || k.leds.caps)
    }

    @Test
    fun lockKeysAreSentLikeOrdinaryKeys() {
        assertEquals(listOf(down(KeyUsages.NUM_LOCK)), k.press(1, key(KeyUsages.NUM_LOCK), 0))
        assertEquals(listOf(up(KeyUsages.NUM_LOCK)), k.release(1, 50))
    }
}

class KeyboardSettingsTest {
    @Test
    fun automaticLayoutFollowsThePcOverCableAndTheTextSettingOverBluetooth() {
        val auto = KeyboardLayoutSetting.AUTO
        assertEquals("es", KeyboardSettings.resolveLayout(auto, bluetooth = false, hostLayoutId = "es", textLayoutId = "us"))
        assertEquals("us", KeyboardSettings.resolveLayout(auto, bluetooth = false, hostLayoutId = "us", textLayoutId = "es"))
        // Sin dato del PC todavía: el ajuste de texto.
        assertEquals("us", KeyboardSettings.resolveLayout(auto, bluetooth = false, hostLayoutId = null, textLayoutId = "us"))
        // Por Bluetooth el PC no dice nada, aunque hubiera un dato viejo.
        assertEquals("es", KeyboardSettings.resolveLayout(auto, bluetooth = true, hostLayoutId = "us", textLayoutId = "es"))
        // Cualquier otra distribución del PC se dibuja en inglés.
        assertEquals("us", KeyboardSettings.resolveLayout(auto, bluetooth = false, hostLayoutId = "fr", textLayoutId = "es"))
    }

    @Test
    fun explicitLayoutAlwaysWins() {
        assertEquals("es", KeyboardSettings.resolveLayout(KeyboardLayoutSetting.ES, false, "us", "us"))
        assertEquals("us", KeyboardSettings.resolveLayout(KeyboardLayoutSetting.US, true, "es", "es"))
    }

    @Test
    fun settingsParseWithDefaults() {
        assertEquals(KeyboardLayoutSetting.AUTO, KeyboardLayoutSetting.fromId(null))
        assertEquals(KeyboardLayoutSetting.US, KeyboardLayoutSetting.fromId("us"))
        assertEquals(KeySize.MEDIUM, KeySize.fromId("???"))
        assertEquals(KeySize.LARGE, KeySize.fromId("large"))
        assertTrue(KeySize.SMALL.heightFactor < KeySize.MEDIUM.heightFactor && KeySize.MEDIUM.heightFactor < KeySize.LARGE.heightFactor)
    }
}
