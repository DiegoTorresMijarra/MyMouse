package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.FourFingerDoubleTap
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Doble toque con 4 dedos: abre el teclado (docs/spec-v6-teclado.md §3). */
class FourFingerDoubleTapTest {
    private val config = TrackpadConfig(acceleration = 0f)
    private val g = GestureInterpreter(config)

    private fun feed(vararg events: TouchInput) = events.flatMap { g.onTouch(it) }

    /** Cuatro dedos que bajan cada 20 ms desde [t0]. */
    private fun down4(t0: Long) = Array(4) { Down(it, 100f + it * 100f, 300f, t0 + it * 20L) }

    private fun up4(t: Long) = Array(4) { Up(it, 100f + it * 100f, 300f, t + it * 5L) }

    /** Un toque de 4 dedos: bajan en t0, t0+20, t0+40, t0+60 y se levantan en [upAt]. */
    private fun tap(t0: Long, upAt: Long) = arrayOf(*down4(t0), *up4(upAt))

    @Test
    fun twoQuickTapsOpenTheKeyboard() {
        assertEquals(emptyList(), feed(*tap(0, 150))) // el primero no hace nada
        assertNull(g.nextDeadlineMs)
        // El segundo empieza 200 ms después de levantar el primero.
        val out = feed(*tap(370, 500))
        assertEquals(listOf(FourFingerDoubleTap), out)
    }

    @Test
    fun theDoubleTapEmitsOnLiftOfTheLastFinger() {
        feed(*tap(0, 150))
        val events = arrayOf(*down4(300), *up4(420))
        val early = events.dropLast(1).flatMap { g.onTouch(it) }
        assertEquals(emptyList(), early)
        assertEquals(listOf(FourFingerDoubleTap), g.onTouch(events.last()))
    }

    @Test
    fun aSlowSecondTapIsJustAFirstTap() {
        feed(*tap(0, 150))
        // Empieza a los 400 ms de levantar el anterior (> 350): no es doble toque...
        assertEquals(emptyList(), feed(*tap(570, 700)))
        // ...pero sí es el primero de uno nuevo.
        assertEquals(listOf(FourFingerDoubleTap), feed(*tap(900, 1_000)))
    }

    @Test
    fun theLimitIsMeasuredFromTheLiftToTheStartOfTheSecondTap() {
        feed(*tap(0, 150)) // se levanta del todo en 165
        assertEquals(listOf(FourFingerDoubleTap), feed(*tap(165 + config.fourFingerDoubleTapMs, 600)))
        feed(*tap(2_000, 2_100))
        assertEquals(emptyList(), feed(*tap(2_115 + config.fourFingerDoubleTapMs + 1, 2_700)))
    }

    @Test
    fun aTapThatTakesTooLongIsNotATap() {
        // Los dedos siguen más de gestureHoldMs: entra el modo gestos como siempre.
        val out1 = feed(*down4(0))
        assertEquals(emptyList(), out1)
        val tick = g.onTick(60 + config.gestureHoldMs)
        assertEquals(listOf(OutputAction.GestureMode(true)), tick)
        feed(*up4(700))
        // Y no cuenta como primer toque de nada.
        assertEquals(emptyList(), feed(*tap(900, 1_000)))
    }

    @Test
    fun withoutGestureModeALongTapStillIsNotATap() {
        val g2 = GestureInterpreter(TrackpadConfig(gestureFingers = 0))
        val all = arrayOf(*down4(0), *up4(500), *down4(700), *up4(800))
        assertEquals(emptyList(), all.flatMap { g2.onTouch(it) })
    }

    @Test
    fun movementCancelsTheTap() {
        // El primer toque se desplaza más que el umbral: no cuenta.
        val moved = Array(4) { Move(it, 100f + it * 100f + 30f, 300f, 100, endOfSample = it == 3) }
        feed(*down4(0), *moved, *up4(200))
        assertEquals(emptyList(), feed(*tap(400, 500)))
        // Un temblor pequeño no lo cancela.
        val jitter = Array(4) { Move(it, 100f + it * 100f + 3f, 300f, 100, endOfSample = it == 3) }
        feed(*down4(2_000), *jitter, *up4(2_100))
        assertEquals(listOf(FourFingerDoubleTap), feed(*tap(2_300, 2_400)))
    }

    @Test
    fun aSingleTapDoesNothing() {
        assertEquals(emptyList(), feed(*tap(0, 100)))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun anotherGestureBetweenTheTapsBreaksTheSequence() {
        feed(*tap(0, 100))
        // Un toque de un dedo (clic) entre los dos.
        feed(Down(9, 50f, 50f, 200), Up(9, 50f, 50f, 230))
        g.onTick(1_000)
        assertEquals(emptyList(), feed(*tap(300, 400)))
    }

    @Test
    fun threeFingersAndFiveFingersAreNotTaps() {
        fun row(n: Int, t0: Long) = Array(n) { Down(it, 100f + it * 60f, 300f, t0 + it * 20L) }
        fun lift(n: Int, t: Long) = Array(n) { Up(it, 100f + it * 60f, 300f, t) }
        feed(*row(3, 0), *lift(3, 100))
        assertEquals(emptyList(), feed(*row(3, 200), *lift(3, 300)))
        // Cinco dedos: no es un toque de 4.
        feed(*tap(1_000, 1_100))
        assertEquals(emptyList(), feed(*row(5, 1_300), *lift(5, 1_400)))
    }

    @Test
    fun aSwipeOrPinchIsNotATap() {
        feed(*tap(0, 100))
        val moved = Array(4) { Move(it, 100f + it * 100f, 300f - 80f, 400, endOfSample = it == 3) }
        val out = feed(*down4(300), *moved, *up4(500))
        assertEquals(listOf(OutputAction.FourFingerSwipe(up = true)), out)
        assertEquals(emptyList(), feed(*tap(700, 800)))
    }

    @Test
    fun threeTapsGiveOneOpeningAndTheThirdStartsOver() {
        feed(*tap(0, 100))
        assertEquals(listOf(FourFingerDoubleTap), feed(*tap(300, 400)))
        assertEquals(emptyList(), feed(*tap(600, 700)))
        assertEquals(listOf(FourFingerDoubleTap), feed(*tap(900, 1_000)))
    }

    @Test
    fun cancelForgetsTheFirstTap() {
        feed(*tap(0, 100))
        g.onTouch(TouchInput.Cancel(200))
        assertEquals(emptyList(), feed(*tap(300, 400)))
    }

    @Test
    fun holdToGestureModeKeepsWorkingWithTheEngine() {
        // Un dedo suelto antes del doble toque tampoco rompe el modo gestos normal.
        val engine = TrackpadEngine(config, null)
        for (e in down4(0)) engine.onTouch(e)
        val out = engine.onTick(60 + config.gestureHoldMs)
        assertTrue(out.none { it is OutputAction.GestureMode }) // sin reconocedor se queda en el trackpad
    }
}
