package dev.mymouse.core.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierStateTest {
    private val shift = KeyUsages.SHIFT
    private val ctrl = KeyUsages.CTRL
    private val m = ModifierState()

    private fun down(u: Int) = KeyEvent(u, true)
    private fun up(u: Int) = KeyEvent(u, false)

    @Test
    fun aTapGoesDownImmediatelyAndWaitsForTheNextKey() {
        assertEquals(listOf(down(shift)), m.onModifierDown(shift, 0))
        assertEquals(ModifierState.Mode.HELD, m.mode(shift))
        assertEquals(emptyList(), m.onModifierUp(shift, 80))
        assertEquals(ModifierState.Mode.PENDING, m.mode(shift))
        assertTrue(m.isDown(shift))
        // La siguiente tecla normal: al soltarla se suelta el modificador.
        m.onNormalKeyDown()
        assertEquals(ModifierState.Mode.PENDING, m.mode(shift))
        assertEquals(listOf(up(shift)), m.onNormalKeyUp())
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
        assertFalse(m.anyDown)
    }

    @Test
    fun aSecondTapWithinTheWindowLocksIt() {
        m.onModifierDown(shift, 0)
        m.onModifierUp(shift, 60)
        assertEquals(emptyList(), m.onModifierDown(shift, 200))
        assertEquals(ModifierState.Mode.LOCKED, m.mode(shift))
        assertEquals(emptyList(), m.onModifierUp(shift, 260))
        assertEquals(ModifierState.Mode.LOCKED, m.mode(shift))
        // Una tecla normal no lo suelta.
        m.onNormalKeyDown()
        assertEquals(emptyList(), m.onNormalKeyUp())
        assertEquals(ModifierState.Mode.LOCKED, m.mode(shift))
    }

    @Test
    fun anotherTouchUnlocks() {
        m.onModifierDown(shift, 0); m.onModifierUp(shift, 50)
        m.onModifierDown(shift, 100); m.onModifierUp(shift, 150)
        assertEquals(listOf(up(shift)), m.onModifierDown(shift, 5_000))
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
        // Levantar el dedo de ese toque no vuelve a dejarlo en espera.
        assertEquals(emptyList(), m.onModifierUp(shift, 5_050))
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
    }

    @Test
    fun aLateSecondTapCancelsTheWait() {
        m.onModifierDown(shift, 0); m.onModifierUp(shift, 50)
        assertEquals(listOf(up(shift)), m.onModifierDown(shift, 50 + ModifierState.DOUBLE_TAP_MS + 1))
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
        assertEquals(emptyList(), m.onModifierUp(shift, 900))
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
    }

    @Test
    fun holdingWhileTypingIsLikeARealKeyboard() {
        m.onModifierDown(ctrl, 0)
        m.onNormalKeyDown()
        assertEquals(emptyList(), m.onNormalKeyUp()) // la tecla se suelta pero el dedo sigue en Ctrl
        assertEquals(ModifierState.Mode.HELD, m.mode(ctrl))
        m.onNormalKeyDown()
        assertEquals(listOf(up(ctrl)), m.onModifierUp(ctrl, 300))
        assertEquals(ModifierState.Mode.OFF, m.mode(ctrl))
    }

    @Test
    fun aLongHoldWithoutKeysDoesNotWait() {
        m.onModifierDown(shift, 0)
        assertEquals(listOf(up(shift)), m.onModifierUp(shift, ModifierState.LONG_HOLD_MS + 1))
        assertEquals(ModifierState.Mode.OFF, m.mode(shift))
    }

    @Test
    fun severalModifiersCombine() {
        m.onModifierDown(ctrl, 0); m.onModifierUp(ctrl, 40)
        m.onModifierDown(shift, 100); m.onModifierUp(shift, 140)
        assertEquals(listOf(KeyUsages.CTRL, KeyUsages.SHIFT), m.downUsages())
        m.onNormalKeyDown()
        assertEquals(listOf(up(ctrl), up(shift)), m.onNormalKeyUp())
        assertTrue(m.downUsages().isEmpty())
    }

    @Test
    fun aPointerClickReleasesPendingModifiersButNotLockedOnes() {
        m.onModifierDown(ctrl, 0); m.onModifierUp(ctrl, 40)
        m.onModifierDown(shift, 100); m.onModifierUp(shift, 140)
        m.onModifierDown(shift, 200); m.onModifierUp(shift, 240) // Shift fijado
        assertEquals(listOf(up(ctrl)), m.onNormalKeyUp())
        assertEquals(listOf(KeyUsages.SHIFT), m.downUsages())
    }

    @Test
    fun releaseAllDropsEverythingDown() {
        m.onModifierDown(ctrl, 0); m.onModifierUp(ctrl, 40)
        m.onModifierDown(shift, 100)
        assertEquals(listOf(up(ctrl), up(shift)), m.releaseAll())
        assertFalse(m.anyDown)
        assertEquals(emptyList(), m.releaseAll())
        // Y vuelve a funcionar desde cero.
        assertEquals(listOf(down(shift)), m.onModifierDown(shift, 1_000))
    }

    @Test
    fun aSecondFingerOnAHeldModifierIsIgnored() {
        m.onModifierDown(shift, 0)
        assertEquals(emptyList(), m.onModifierDown(shift, 50))
        assertEquals(ModifierState.Mode.HELD, m.mode(shift))
    }
}

class KeyRepeatTest {
    @Test
    fun repeatsAfterTheDelayAndThenEveryInterval() {
        val r = KeyRepeat()
        assertEquals(null, r.nextDeadlineMs)
        r.start(0x04, 1_000)
        assertEquals(1_400L, r.nextDeadlineMs)
        assertEquals(emptyList(), r.onTick(1_399))
        assertEquals(listOf(0x04), r.onTick(1_400))
        assertEquals(1_440L, r.nextDeadlineMs)
        assertEquals(emptyList(), r.onTick(1_420))
        assertEquals(listOf(0x04), r.onTick(1_440))
        assertEquals(listOf(0x04), r.onTick(1_480))
    }

    @Test
    fun aLateTickCatchesUpButNotMoreThanAFew() {
        val r = KeyRepeat()
        r.start(0x2A, 0)
        assertEquals(3, r.onTick(400 + 40 * 2).size)
        r.start(0x2A, 10_000)
        assertEquals(KeyRepeat.MAX_CATCH_UP, r.onTick(10_400 + 5_000).size)
        // Después de tanto retraso no se queda en deuda: la siguiente es dentro de un intervalo.
        assertTrue(r.nextDeadlineMs!! > 15_400)
    }

    @Test
    fun onlyTheLastKeyRepeatsAndStopsWhenReleased() {
        val r = KeyRepeat()
        r.start(0x04, 0)
        r.start(0x05, 100)
        r.stop(0x04) // soltar la primera no para la segunda
        assertEquals(0x05, r.active)
        assertEquals(listOf(0x05), r.onTick(500))
        r.stop(0x05)
        assertEquals(null, r.nextDeadlineMs)
        assertEquals(emptyList(), r.onTick(10_000))
        r.start(0x06, 0)
        r.stopAll()
        assertEquals(null, r.active)
    }
}
