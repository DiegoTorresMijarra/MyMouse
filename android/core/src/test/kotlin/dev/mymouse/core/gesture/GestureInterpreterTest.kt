package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.Button
import dev.mymouse.core.gesture.OutputAction.PointerMove
import dev.mymouse.core.gesture.OutputAction.Scroll
import dev.mymouse.core.gesture.OutputAction.Zoom
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import dev.mymouse.core.protocol.MouseButton.LEFT
import dev.mymouse.core.protocol.MouseButton.RIGHT
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureInterpreterTest {
    // Sin aceleración para que los deltas sean predecibles.
    private val config = TrackpadConfig(acceleration = 0f, scrollSpeed = 1f)
    private val g = GestureInterpreter(config)

    private fun feed(vararg events: TouchInput) = events.flatMap { g.onTouch(it) }

    private val leftDown = Button(LEFT, true)
    private val leftUp = Button(LEFT, false)

    @Test
    fun tapClicksAndReleasesAfterDoubleTapTimeout() {
        val out = feed(Down(0, 100f, 100f, 0), Up(0, 100f, 100f, 100))
        assertEquals(listOf(leftDown), out)
        assertEquals(350L, g.nextDeadlineMs)

        assertEquals(emptyList(), g.onTick(349))
        assertEquals(listOf(leftUp), g.onTick(350))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun tapWithoutTapToDragReleasesImmediately() {
        val g = GestureInterpreter(config.copy(tapToDrag = false))
        val out = listOf(Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 50)).flatMap { g.onTouch(it) }
        assertEquals(listOf(leftDown, leftUp), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun slowPressIsNotATap() {
        assertEquals(emptyList(), feed(Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 500)))
    }

    @Test
    fun oneFingerMovesPointerFromSlopCrossing() {
        val out = feed(
            Down(0, 0f, 0f, 0),
            Move(0, 5f, 0f, 10),   // dentro del umbral: nada
            Move(0, 10f, 0f, 20),  // cruza el umbral: referencia aquí, sin salto
            Move(0, 13f, 4f, 30),
            Move(0, 20f, 4f, 40),
            Up(0, 20f, 4f, 50),
        )
        assertEquals(listOf(PointerMove(3, 4), PointerMove(7, 0)), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun slowMovementIsNotLost() {
        val g = GestureInterpreter(config.copy(sensitivity = 0.5f))
        val out = buildList {
            addAll(g.onTouch(Down(0, 0f, 0f, 0)))
            addAll(g.onTouch(Move(0, 10f, 0f, 10)))
            for (i in 1..4) addAll(g.onTouch(Move(0, 10f + i, 0f, 10L + i * 10)))
        }
        // 4 px × 0.5 = 2 px en total, aunque cada paso individual sea 0.5 px.
        assertEquals(2, out.filterIsInstance<PointerMove>().sumOf { it.dx })
    }

    @Test
    fun doubleTapIsTwoClicks() {
        val out = feed(
            Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 80),
            Down(0, 0f, 0f, 150), Up(0, 0f, 0f, 220),
        )
        assertEquals(listOf(leftDown, leftUp, leftDown), out)
        assertEquals(listOf(leftUp), g.onTick(g.nextDeadlineMs!!))
    }

    @Test
    fun tapThenTouchAndMoveDrags() {
        val out = feed(
            Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 80),
            Down(0, 0f, 0f, 150),
            Move(0, 20f, 0f, 170),
            Move(0, 30f, 0f, 180),
            Up(0, 30f, 0f, 400),
        )
        assertEquals(listOf(leftDown, PointerMove(10, 0), leftUp), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun secondTouchAfterTimeoutIsNotADrag() {
        val out = feed(Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 80)) +
            g.onTick(330) +
            feed(Down(0, 0f, 0f, 400), Move(0, 20f, 0f, 420), Move(0, 30f, 0f, 430))
        assertEquals(listOf(leftDown, leftUp, PointerMove(10, 0)), out)
    }

    @Test
    fun lateTickIsResolvedOnNextDown() {
        val out = feed(Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 80), Down(0, 0f, 0f, 1000))
        assertEquals(listOf(leftDown, leftUp), out)
    }

    @Test
    fun twoFingerTapIsRightClick() {
        val out = feed(
            Down(0, 0f, 0f, 0), Down(1, 50f, 0f, 20),
            Up(0, 0f, 0f, 100), Up(1, 50f, 0f, 110),
        )
        assertEquals(listOf(Button(RIGHT, true), Button(RIGHT, false)), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun twoFingerDragScrolls() {
        val out = feed(
            Down(0, 0f, 0f, 0), Down(1, 50f, 0f, 10),
            Move(0, 0f, 20f, 30, false), Move(1, 50f, 20f, 30),   // cruza el umbral → empieza scroll
            Move(0, 0f, 30f, 40, false), Move(1, 50f, 30f, 40),   // centroide baja 10 px
            Up(0, 0f, 30f, 500), Up(1, 50f, 30f, 500),
        )
        // El scroll cuenta desde la muestra que cruza el umbral (centroide en y=20) hasta y=30.
        // Scroll natural: dedos hacia abajo = rueda positiva (contenido baja).
        assertEquals(10, out.filterIsInstance<Scroll>().sumOf { it.vertical })
        assertTrue(out.none { it is Button })
    }

    @Test
    fun invertedScrollFlipsSign() {
        val g = GestureInterpreter(config.copy(naturalScroll = false))
        val out = listOf(
            Down(0, 0f, 0f, 0), Down(1, 50f, 0f, 10),
            Move(0, 20f, 0f, 30, false), Move(1, 70f, 0f, 30),
            Move(0, 30f, 0f, 40, false), Move(1, 80f, 0f, 40),
        ).flatMap { g.onTouch(it) }
        // Dedos a la derecha (centroide de x=45 a x=55), scroll invertido: rueda horizontal positiva.
        assertEquals(10, out.filterIsInstance<Scroll>().sumOf { it.horizontal })
    }

    @Test
    fun movingThenSecondFingerSwitchesToScrollAndNeverBackToMove() {
        val out = feed(
            Down(0, 0f, 0f, 0),
            Move(0, 20f, 0f, 10),
            Down(1, 70f, 0f, 20),
            Move(0, 20f, 10f, 30, false), Move(1, 70f, 10f, 30),
            Up(1, 70f, 10f, 40),
            Move(0, 60f, 60f, 50), // con un dedo tras el scroll: ignorado
            Up(0, 60f, 60f, 60),
        )
        assertTrue(out.any { it is Scroll })
        assertTrue(out.none { it is PointerMove })
    }

    @Test
    fun pinchOutZoomsInWithFineDelta() {
        val out = feed(
            Down(0, 100f, 100f, 0), Down(1, 200f, 100f, 10),
            Move(0, 80f, 100f, 30, false), Move(1, 220f, 100f, 30),   // distancia 100 → 140: empieza el zoom (sin salto)
            Move(0, 60f, 100f, 50, false), Move(1, 240f, 100f, 50),   // 140 → 180
            Up(0, 60f, 100f, 500), Up(1, 240f, 100f, 500),
        )
        val expected = (ln(180.0 / 140.0) * TrackpadConfig.ZOOM_UNITS_PER_LN * config.zoomSpeed).toInt()
        assertEquals(listOf(Zoom(expected)), out)
        assertTrue(expected % 120 != 0, "el zoom ya no va a saltos de 120")
    }

    @Test
    fun pinchInWithOneFingerStillZoomsOut() {
        // Pulgar quieto, índice acercándose: el centro se mueve, pero menos que la distancia.
        val out = feed(
            Down(0, 100f, 100f, 0), Down(1, 300f, 100f, 10),
            Move(1, 270f, 100f, 30),   // distancia 200 → 170: empieza el zoom
            Move(1, 230f, 100f, 50),   // 170 → 130
            Move(1, 190f, 100f, 70),   // 130 → 90
        )
        val zooms = out.filterIsInstance<Zoom>()
        assertEquals(2, zooms.size)
        assertTrue(zooms.all { it.delta < 0 })
        assertTrue(out.none { it is Scroll })
    }

    @Test
    fun threeFingersDoNotMovePointerNorClick() {
        // Con 3 dedos solo hay gestos de Windows (ver MultiFingerGestureTest); un toque corto no hace nada.
        val out = feed(
            Down(0, 0f, 0f, 0), Down(1, 50f, 0f, 5), Down(2, 100f, 0f, 10),
            Up(0, 0f, 0f, 50), Up(1, 50f, 0f, 50), Up(2, 100f, 0f, 50),
        )
        assertEquals(emptyList(), out)
    }

    @Test
    fun cancelDuringDragReleasesButton() {
        val out = feed(
            Down(0, 0f, 0f, 0), Up(0, 0f, 0f, 80),
            Down(0, 0f, 0f, 150), Move(0, 20f, 0f, 170),
            TouchInput.Cancel(200),
        )
        assertEquals(listOf(leftDown, leftUp), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun accelerationIncreasesGainWithSpeed() {
        val g = GestureInterpreter(TrackpadConfig(acceleration = 1f, accelThresholdPxPerMs = 0.5f, maxGain = 3f))
        fun total(stepPx: Float): Int {
            val out = mutableListOf<OutputAction>()
            out += g.onTouch(Down(0, 0f, 0f, 0))
            out += g.onTouch(Move(0, 10f, 0f, 10))
            out += g.onTouch(Move(0, 10f + stepPx, 0f, 20))
            out += g.onTouch(Up(0, 0f, 0f, 30))
            return out.filterIsInstance<PointerMove>().sumOf { it.dx }
        }
        assertEquals(4, total(4f))    // 0.4 px/ms < umbral → ganancia 1
        assertEquals(20, total(10f))  // 1 px/ms → ganancia 1 + 1 × (1 − 0.5) / 0.5 = 2
        assertEquals(90, total(30f))  // 3 px/ms → ganancia 6, limitada a maxGain = 3
    }
}
