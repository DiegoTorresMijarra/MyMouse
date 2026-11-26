package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.Gesture
import dev.mymouse.core.gesture.OutputAction.GestureMode
import dev.mymouse.core.gesture.OutputAction.GestureRejected
import dev.mymouse.core.gesture.OutputAction.PointerMove
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import dev.mymouse.core.shapes.Recognizer
import dev.mymouse.core.shapes.ShapeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackpadEngineTest {
    private val config = TrackpadConfig(acceleration = 0f)
    private val engine = TrackpadEngine(config, Recognizer(Recognizer.catalogTemplates(), ShapeCatalog.IDS.toSet()))

    private fun feed(vararg events: TouchInput) = events.flatMap { engine.onTouch(it) }

    /** Cuatro dedos casi a la vez y quietos: activa en el tick de t+90+300. */
    private fun activate(t: Long = 0): List<OutputAction> {
        val out = feed(
            Down(0, 100f, 100f, t), Down(1, 200f, 100f, t + 30), Down(2, 300f, 100f, t + 60), Down(3, 400f, 100f, t + 90),
        ).toMutableList()
        assertEquals(t + 90 + config.gestureHoldMs, engine.nextDeadlineMs)
        out += engine.onTick(t + 90 + config.gestureHoldMs)
        out += feed(Up(0, 100f, 100f, t + 500), Up(1, 200f, 100f, t + 500), Up(2, 300f, 100f, t + 510), Up(3, 400f, 100f, t + 510))
        return out
    }

    @Test
    fun fourFingersHeldActivateGestureMode() {
        assertEquals(listOf(GestureMode(true)), activate())
        assertTrue(engine.gestureModeActive)
    }

    @Test
    fun drawnLineIsRecognizedAndModeEnds() {
        activate()
        val out = feed(
            Down(5, 100f, 400f, 600),
            *Array(10) { Move(5, 100f + it * 30f, 400f, 610L + it * 10) },
            Up(5, 400f, 400f, 800),
        )
        assertEquals(listOf(Gesture("line-right", 1f), GestureMode(false)), out)
        assertFalse(engine.gestureModeActive)
        // Vuelve el trackpad: un dedo mueve el cursor.
        val move = feed(Down(6, 0f, 0f, 1000), Move(6, 50f, 0f, 1010), Move(6, 60f, 0f, 1020))
        assertTrue(move.any { it is PointerMove })
    }

    @Test
    fun movingFingersDoNotActivate() {
        val out = feed(
            Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 90),
            Move(0, 100f, 160f, 100, false), Move(1, 200f, 160f, 100, false), Move(2, 300f, 160f, 100, false), Move(3, 400f, 160f, 100),
        ).toMutableList()
        out += engine.onTick(1000)
        assertTrue(out.none { it is GestureMode })
    }

    @Test
    fun threeFingersHeldDoNotActivate() {
        feed(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60))
        assertEquals(null, engine.nextDeadlineMs)
        assertEquals(emptyList(), engine.onTick(2000))
    }

    @Test
    fun slowFourthFingerDoesNotActivate() {
        feed(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 400))
        assertEquals(emptyList(), engine.onTick(2000))
    }

    @Test
    fun noStrokeTimesOut() {
        activate()
        val deadline = engine.nextDeadlineMs!!
        assertEquals(listOf(GestureRejected("Tiempo agotado"), GestureMode(false)), engine.onTick(deadline))
    }

    @Test
    fun secondFingerCancels() {
        activate()
        val out = feed(Down(5, 100f, 400f, 600), Down(6, 200f, 400f, 650))
        assertEquals(listOf(GestureRejected("Cancelado"), GestureMode(false)), out)
        // Hasta que no se levantan todos, no se mueve nada.
        assertEquals(emptyList(), feed(Move(5, 300f, 400f, 700), Up(5, 300f, 400f, 710), Up(6, 200f, 400f, 720)))
    }

    @Test
    fun tinyStrokeIsRejected() {
        activate()
        val out = feed(Down(5, 100f, 400f, 600), Up(5, 102f, 401f, 650))
        assertEquals(GestureMode(false), out.last())
        assertTrue(out.first() is GestureRejected)
    }

    @Test
    fun withoutRecognizerFourFingersDoNothing() {
        val e = TrackpadEngine(config, recognizer = null)
        val out = listOf(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 90))
            .flatMap { e.onTouch(it) } + e.onTick(1000)
        assertEquals(emptyList(), out)
    }
}
