package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.GestureMode
import dev.mymouse.core.gesture.OutputAction.StrokeEnd
import dev.mymouse.core.gesture.OutputAction.StrokePoints
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import dev.mymouse.core.protocol.StrokePoint
import dev.mymouse.core.protocol.StrokeResult
import dev.mymouse.core.shapes.Recognizer
import dev.mymouse.core.shapes.ShapeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lo que el motor emite para el gesto en vivo del PC (protocolo v5). */
class StrokeStreamingTest {
    private val config = TrackpadConfig(acceleration = 0f)
    private val engine = TrackpadEngine(config, Recognizer(Recognizer.catalogTemplates(), ShapeCatalog.IDS.toSet())).also {
        it.setArea(1000f, 500f)
    }

    private fun feed(vararg events: TouchInput) = events.flatMap { engine.onTouch(it) }

    private fun activate() {
        feed(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 90))
        engine.onTick(90L + config.gestureHoldMs)
        feed(Up(0, 100f, 100f, 500), Up(1, 200f, 100f, 500), Up(2, 300f, 100f, 510), Up(3, 400f, 100f, 510))
    }

    @Test
    fun activationCarriesTheStartTimeout() {
        feed(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 90))
        assertEquals(listOf(GestureMode(true, 3000)), engine.onTick(90L + config.gestureHoldMs))
    }

    @Test
    fun firstPointIsSentRightAwayAndNormalizedWithTheSameScale() {
        activate()
        val out = feed(Down(5, 500f, 250f, 600))
        // Área 1000×500: el lado largo (1000 px) ocupa 0–65535 en los dos ejes.
        assertEquals(listOf(StrokePoints(listOf(StrokePoint(32768, 16384)))), out)
    }

    @Test
    fun pointsAreBatchedEvery16ms() {
        activate()
        feed(Down(5, 0f, 0f, 600))
        assertEquals(emptyList(), feed(Move(5, 10f, 0f, 605)))
        assertEquals(emptyList(), feed(Move(5, 20f, 0f, 610)))
        assertEquals(11L + 600 + 5 - 5 + 5 + 0, 616L) // el lote toca a los 616 ms
        assertEquals(616L, engine.nextDeadlineMs)
        val out = feed(Move(5, 30f, 0f, 617))
        assertEquals(1, out.size)
        assertEquals(3, (out[0] as StrokePoints).points.size)
        assertNull(engine.nextDeadlineMs)
    }

    @Test
    fun pendingPointsAreFlushedByTick() {
        activate()
        feed(Down(5, 0f, 0f, 600))
        feed(Move(5, 10f, 0f, 605))
        assertEquals(emptyList(), engine.onTick(610))
        val out = engine.onTick(616)
        assertEquals(listOf(StrokePoints(listOf(StrokePoint(655, 0)))), out)
    }

    @Test
    fun recognizedStrokeEndsBeforeTheGesture() {
        activate()
        val out = feed(
            Down(5, 100f, 400f, 600),
            *Array(10) { Move(5, 100f + it * 30f, 400f, 610L + it * 10) },
            Up(5, 400f, 400f, 800),
        )
        val tail = out.takeLast(3)
        assertEquals(StrokeEnd(StrokeResult.RECOGNIZED), tail[0])
        assertTrue(tail[1] is OutputAction.Gesture)
        assertEquals(GestureMode(false), tail[2])
        // Todos los puntos llegaron al PC.
        assertEquals(12, out.filterIsInstance<StrokePoints>().sumOf { it.points.size })
    }

    @Test
    fun unrecognizedCancelledAndTimedOutHaveTheirResult() {
        activate()
        val tiny = feed(Down(5, 100f, 400f, 600), Up(5, 102f, 401f, 650))
        assertTrue(StrokeEnd(StrokeResult.NOT_RECOGNIZED) in tiny)

        activate2(1000)
        val cancelled = feed(Down(5, 100f, 400f, 1600), Down(6, 200f, 400f, 1650))
        assertTrue(StrokeEnd(StrokeResult.CANCELLED) in cancelled)
        feed(Up(5, 100f, 400f, 1700), Up(6, 200f, 400f, 1710))

        activate2(3000)
        val timeout = engine.onTick(engine.nextDeadlineMs!!)
        assertEquals(StrokeEnd(StrokeResult.TIMED_OUT), timeout.first())
    }

    private fun activate2(t: Long) {
        feed(Down(0, 100f, 100f, t), Down(1, 200f, 100f, t + 30), Down(2, 300f, 100f, t + 60), Down(3, 400f, 100f, t + 90))
        engine.onTick(t + 90 + config.gestureHoldMs)
        feed(Up(0, 100f, 100f, t + 500), Up(1, 200f, 100f, t + 500), Up(2, 300f, 100f, t + 510), Up(3, 400f, 100f, t + 510))
    }

    @Test
    fun withoutAreaNoPointsAreEmitted() {
        val e = TrackpadEngine(config, Recognizer(Recognizer.catalogTemplates(), ShapeCatalog.IDS.toSet()))
        listOf(Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 30), Down(2, 300f, 100f, 60), Down(3, 400f, 100f, 90)).forEach { e.onTouch(it) }
        e.onTick(90L + config.gestureHoldMs)
        listOf(Up(0, 100f, 100f, 500), Up(1, 200f, 100f, 500), Up(2, 300f, 100f, 510), Up(3, 400f, 100f, 510)).forEach { e.onTouch(it) }
        assertEquals(emptyList(), e.onTouch(Down(5, 100f, 400f, 600)))
        assertEquals(emptyList(), e.onTouch(Move(5, 130f, 400f, 650)))
    }
}
