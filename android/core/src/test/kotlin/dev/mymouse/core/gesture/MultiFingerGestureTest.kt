package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.FourFingerSwipe
import dev.mymouse.core.gesture.OutputAction.GestureMode
import dev.mymouse.core.gesture.OutputAction.SystemGesture
import dev.mymouse.core.gesture.OutputAction.WidgetPanel
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import dev.mymouse.core.protocol.SystemGestureCommand.APP_SWITCH_END
import dev.mymouse.core.protocol.SystemGestureCommand.APP_SWITCH_NEXT
import dev.mymouse.core.protocol.SystemGestureCommand.APP_SWITCH_PREVIOUS
import dev.mymouse.core.protocol.SystemGestureCommand.APP_SWITCH_START
import dev.mymouse.core.protocol.SystemGestureCommand.SHOW_DESKTOP
import dev.mymouse.core.protocol.SystemGestureCommand.TASK_VIEW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gestos de 3 y 4 dedos (docs/spec-v3.1-ajustes.md §1). */
class MultiFingerGestureTest {
    private val config = TrackpadConfig(acceleration = 0f)
    private val g = GestureInterpreter(config)

    private fun feed(vararg events: TouchInput) = events.flatMap { g.onTouch(it) }

    /** Dedos en una fila (x = 100, 200, 300, 400; y = 300), llegando cada 30 ms: el último a t = 30·(n−1). */
    private fun row(n: Int) = Array(n) { Down(it, 100f + it * 100f, 300f, it * 30L) }

    /** Los n dedos de [row] desplazados (dx, dy) y con la fila entera en una sola muestra. */
    private fun moved(n: Int, dx: Float, dy: Float, t: Long) = Array(n) {
        Move(it, 100f + it * 100f + dx, 300f + dy, t, endOfSample = it == n - 1)
    }

    /** Cuatro dedos en cruz alrededor de (300, 300) con [r] píxeles de radio. */
    private fun cross(r: Float, t: Long, fingersDown: Boolean = false): Array<TouchInput> {
        val p = listOf(300f - r to 300f, 300f + r to 300f, 300f to 300f - r, 300f to 300f + r)
        return Array(4) { i ->
            if (fingersDown) Down(i, p[i].first, p[i].second, i * 30L)
            else Move(i, p[i].first, p[i].second, t, endOfSample = i == 3)
        }
    }

    private fun lift(n: Int, t: Long) = Array(n) { Up(it, 100f + it * 100f, 300f, t) }

    // ---- 3 dedos ----

    @Test
    fun threeFingersSwipeUpOpensTaskView() {
        val out = feed(*row(3), *moved(3, 0f, -60f, 200), *moved(3, 0f, -120f, 220), *lift(3, 400))
        assertEquals(listOf(SystemGesture(TASK_VIEW)), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun threeFingersSwipeDownShowsDesktop() {
        val out = feed(*row(3), *moved(3, 0f, 60f, 200), *lift(3, 400))
        assertEquals(listOf(SystemGesture(SHOW_DESKTOP)), out)
    }

    @Test
    fun threeFingersSwipeRightStartsAppSwitchAndLiftEndsIt() {
        val out = feed(*row(3), *moved(3, 60f, 0f, 200))
        assertEquals(
            listOf(SystemGesture(APP_SWITCH_START), SystemGesture(APP_SWITCH_NEXT)),
            out,
        )
        // Al levantar los dedos se suelta Alt (una sola vez).
        assertEquals(listOf(SystemGesture(APP_SWITCH_END)), feed(*lift(3, 400)))
    }

    @Test
    fun threeFingersSwipeLeftStartsWithPrevious() {
        val out = feed(*row(3), *moved(3, -60f, 0f, 200))
        assertEquals(listOf(SystemGesture(APP_SWITCH_START), SystemGesture(APP_SWITCH_PREVIOUS)), out)
    }

    @Test
    fun appSwitchStepsEvery120PxInBothDirections() {
        feed(*row(3), *moved(3, 60f, 0f, 200))
        // 60 → 170 (+110): aún no hay otro paso. → 185 (+15): paso (125 ≥ 120).
        assertEquals(emptyList(), feed(*moved(3, 170f, 0f, 220)))
        assertEquals(listOf(SystemGesture(APP_SWITCH_NEXT)), feed(*moved(3, 185f, 0f, 240)))
        // Vuelta de 185 a −60 (−245 con 5 sobrantes): dos pasos atrás.
        assertEquals(
            listOf(SystemGesture(APP_SWITCH_PREVIOUS), SystemGesture(APP_SWITCH_PREVIOUS)),
            feed(*moved(3, -60f, 0f, 260)),
        )
    }

    @Test
    fun cancelDuringAppSwitchReleasesAlt() {
        feed(*row(3), *moved(3, 60f, 0f, 200))
        assertEquals(listOf(SystemGesture(APP_SWITCH_END)), g.onTouch(TouchInput.Cancel(300)))
    }

    @Test
    fun windowsGesturesCanBeDisabled() {
        val g = GestureInterpreter(config.copy(windowsGestures = false))
        val out = listOf(*row(3), *moved(3, 0f, -60f, 200), *moved(3, 60f, 0f, 220)).flatMap { g.onTouch(it) }
        assertEquals(emptyList(), out)
    }

    @Test
    fun threeFingerGestureIsNotDecidedWhileAFourthCouldArrive() {
        // Los 60 px ya están hechos a los 100 ms, pero la ventana de 150 ms desde el primer dedo sigue abierta.
        assertEquals(emptyList(), feed(*row(3), *moved(3, 0f, -60f, 100)))
        // En cuanto se cierra, se decide con lo que haya recorrido.
        assertEquals(listOf(SystemGesture(TASK_VIEW)), feed(*moved(3, 0f, -70f, 160)))
    }

    @Test
    fun fourthFingerWithinWindowMakesItAFourFingerGesture() {
        // El cuarto dedo llega a los 90 ms: no se decide nada como gesto de 3 dedos.
        val out = feed(*row(3), *moved(3, 0f, -10f, 70), Down(3, 400f, 290f, 90), *moved(4, 0f, -10f, 100))
        assertEquals(emptyList(), out)
        assertEquals(listOf(FourFingerSwipe(up = true)), feed(*moved(4, 0f, -70f, 120)))
    }

    @Test
    fun lateFourthFingerCancelsAnUndecidedGesture() {
        val out = feed(*row(3), Down(3, 400f, 300f, 400), *moved(4, 0f, -80f, 450))
        assertEquals(emptyList(), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun lateFourthFingerDoesNotChangeADecidedGesture() {
        feed(*row(3), *moved(3, 60f, 0f, 200))
        assertEquals(emptyList(), feed(Down(3, 400f, 300f, 500)))
        assertEquals(listOf(SystemGesture(APP_SWITCH_END)), feed(Up(0, 160f, 300f, 600)))
        assertEquals(emptyList(), feed(Up(1, 260f, 300f, 610), Up(2, 360f, 300f, 610), Up(3, 400f, 300f, 610)))
    }

    @Test
    fun threeFingerPinchDoesNothing() {
        val g = GestureInterpreter(config)
        val p = listOf(200f to 300f, 400f to 300f, 300f to 200f)
        val down = p.mapIndexed { i, (x, y) -> Down(i, x, y, i * 30L) }
        // Se juntan hacia el centro (300, 266).
        val pinch = p.mapIndexed { i, (x, y) -> Move(i, 300f + (x - 300f) * 0.5f, 266f + (y - 266f) * 0.5f, 200, i == 2) }
        assertEquals(emptyList(), (down + pinch).flatMap { g.onTouch(it) })
    }

    // ---- 4 dedos ----

    @Test
    fun fourFingersHeldStillActivateGestureMode() {
        feed(*row(4))
        assertEquals(90L + config.gestureHoldMs, g.nextDeadlineMs)
        assertEquals(emptyList(), g.onTick(300))
        assertEquals(listOf(GestureMode(true)), g.onTick(90L + config.gestureHoldMs))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun smallJitterKeepsActivationButMovingBeyondSlopCancelsIt() {
        feed(*row(4), *moved(4, 4f, 3f, 150))
        assertEquals(90L + config.gestureHoldMs, g.nextDeadlineMs)
        feed(*moved(4, 20f, 0f, 200))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun fourFingersCanBeDisabled() {
        val g = GestureInterpreter(config.copy(gestureFingers = 0))
        listOf(*row(4)).forEach { g.onTouch(it) }
        assertNull(g.nextDeadlineMs)
        assertEquals(emptyList(), g.onTick(1000))
    }

    @Test
    fun fourFingersPinchInShowsWidgetPanel() {
        val out = feed(*cross(100f, 0, fingersDown = true), *cross(60f, 120))
        assertEquals(listOf(WidgetPanel(show = true)), out)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun fourFingersPinchOutHidesWidgetPanel() {
        val out = feed(*cross(100f, 0, fingersDown = true), *cross(150f, 120))
        assertEquals(listOf(WidgetPanel(show = false)), out)
    }

    @Test
    fun fourFingersSwipeUpAndDownAreOnlyReported() {
        assertEquals(listOf(FourFingerSwipe(up = true)), feed(*row(4), *moved(4, 0f, -60f, 120)))
        feed(*lift(4, 300))
        assertEquals(listOf(FourFingerSwipe(up = false)), feed(*row(4).map { it.copy(timeMs = it.timeMs + 1000) }.toTypedArray(), *moved(4, 0f, 60f, 1120)))
    }

    @Test
    fun fourFingersSidewaysDoNothing() {
        assertEquals(emptyList(), feed(*row(4), *moved(4, 80f, 0f, 120)))
    }

    @Test
    fun aDecidedGestureNeverChangesType() {
        val out = feed(*row(4), *moved(4, 0f, -60f, 120))
        assertEquals(listOf(FourFingerSwipe(up = true)), out)
        // Ahora se juntan: sigue siendo el mismo gesto, no se decide otro.
        assertEquals(emptyList(), feed(*cross(40f, 200)))
        assertEquals(emptyList(), feed(*lift(4, 500)))
    }

    @Test
    fun smallMovementsAreNotClassified() {
        assertEquals(emptyList(), feed(*row(4), *moved(4, 20f, -20f, 120), *moved(4, 0f, -30f, 140)))
        assertTrue(g.nextDeadlineMs == null) // además, se ha pasado el umbral de quietud
    }

    private fun Down.copy(timeMs: Long) = Down(id, x, y, timeMs)
    private fun TouchInput.copy(timeMs: Long): TouchInput = when (this) {
        is Down -> Down(id, x, y, timeMs)
        is Move -> Move(id, x, y, timeMs, endOfSample)
        is Up -> Up(id, x, y, timeMs)
        else -> this
    }
}
