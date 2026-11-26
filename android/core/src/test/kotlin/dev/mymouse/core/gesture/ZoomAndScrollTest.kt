package dev.mymouse.core.gesture

import dev.mymouse.core.gesture.OutputAction.Scroll
import dev.mymouse.core.gesture.OutputAction.Zoom
import dev.mymouse.core.gesture.TouchInput.Down
import dev.mymouse.core.gesture.TouchInput.Move
import dev.mymouse.core.gesture.TouchInput.Up
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Zoom fino por cociente, bloqueo scroll/zoom e inercia (docs/spec-v3.1-ajustes.md §2). */
class ZoomAndScrollTest {
    private val config = TrackpadConfig(acceleration = 0f, scrollSpeed = 1f)
    private val g = GestureInterpreter(config)

    private fun feed(vararg events: TouchInput) = events.flatMap { g.onTouch(it) }

    /** Dos dedos centrados en (cx, 300) separados [spread] px. */
    private fun two(spread: Float, t: Long, cx: Float = 300f, cy: Float = 300f) = arrayOf(
        Move(0, cx - spread / 2, cy, t, endOfSample = false),
        Move(1, cx + spread / 2, cy, t, endOfSample = true),
    )

    private fun start(spread: Float, scale: Float = 1f, g: GestureInterpreter = this.g): List<OutputAction> = listOf(
        Down(0, (300f - spread / 2) * scale, 300f * scale, 0),
        Down(1, (300f + spread / 2) * scale, 300f * scale, 10),
    ).flatMap { g.onTouch(it) }

    private fun zoomTotal(out: List<OutputAction>) = out.filterIsInstance<Zoom>().sumOf { it.delta }

    // ---- Zoom por cociente ----

    @Test
    fun zoomIsProportionalToTheLogarithmOfTheRatio() {
        start(100f)
        feed(*two(120f, 30)) // decide el zoom (+20 %): la referencia empieza aquí
        val out = feed(*two(240f, 60))
        val expected = ln(240.0 / 120.0) * TrackpadConfig.ZOOM_UNITS_PER_LN * config.zoomSpeed
        assertEquals(expected.toInt(), zoomTotal(out))
    }

    @Test
    fun sameGestureOnABiggerScreenGivesTheSameZoom() {
        fun total(scale: Float): Int {
            val g = GestureInterpreter(config)
            start(100f, scale, g)
            val moves = listOf(120f to 30L, 150f to 50L, 200f to 70L, 260f to 90L)
            return moves.flatMap { (s, t) ->
                two(s * scale, t, cx = 300f * scale, cy = 300f * scale).flatMap { g.onTouch(it) }
            }.filterIsInstance<Zoom>().sumOf { it.delta }
        }
        assertEquals(total(1f), total(2.5f))
    }

    @Test
    fun zoomingInAndBackOutCancelsOut() {
        start(100f)
        feed(*two(120f, 30))
        val total = zoomTotal(feed(*two(200f, 50), *two(300f, 70), *two(120f, 90)))
        assertTrue(abs(total) <= 1, "total = $total")
    }

    @Test
    fun zoomSpeedScalesTheDelta() {
        fun total(speed: Float): Int {
            val g = GestureInterpreter(config.copy(zoomSpeed = speed))
            start(100f, g = g)
            return (two(120f, 30).toList() + two(220f, 50).toList()).flatMap { g.onTouch(it) }.filterIsInstance<Zoom>().sumOf { it.delta }
        }
        val slow = total(1f)
        val fast = total(2f)
        assertTrue(abs(fast - 2 * slow) <= 1, "slow=$slow fast=$fast")
        assertTrue(abs(total(4f)) > abs(total(0.5f)))
    }

    @Test
    fun zoomIsSentAtMostEvery16MsAndTheRestIsAccumulated() {
        start(100f)
        feed(*two(120f, 30))
        val early = feed(*two(130f, 38), *two(140f, 44)) // < 16 ms desde el último envío: nada
        assertEquals(emptyList(), early)
        val out = feed(*two(150f, 50)) // 20 ms: se envía todo lo acumulado
        val expected = ln(150.0 / 120.0) * TrackpadConfig.ZOOM_UNITS_PER_LN * config.zoomSpeed
        assertEquals(listOf(Zoom(expected.toInt())), out)
    }

    @Test
    fun pinchIsDecidedWithAnEightPercentChange() {
        start(200f)
        // 8 % de 200 = 16 px: con 20 px de cambio y el centro quieto es zoom.
        assertEquals(emptyList(), feed(*two(220f, 30)))
        assertTrue(feed(*two(260f, 50)).any { it is Zoom })
    }

    @Test
    fun pinchBecomesScrollNeverWhenAlreadyZooming() {
        start(100f)
        feed(*two(130f, 30))
        // Una vez en zoom, mover los dedos juntos no cambia a scroll.
        val out = feed(*two(130f, 50, cy = 340f), *two(130f, 70, cy = 380f))
        assertTrue(out.none { it is Scroll })
    }

    @Test
    fun twoFingerMoveWithSmallSpreadDriftStaysScroll() {
        start(100f)
        val out = feed(*two(104f, 30, cy = 330f), *two(106f, 50, cy = 360f), *two(108f, 70, cy = 390f))
        assertTrue(out.any { it is Scroll })
        assertTrue(out.none { it is Zoom })
    }

    @Test
    fun scrollSwitchesToZoomWhenTheDistanceChangesMoreThan25Percent() {
        start(100f)
        assertTrue(feed(*two(100f, 30, cy = 330f)).none { it is Zoom }) // scroll
        feed(*two(110f, 50, cy = 330f)) // +10 %: sigue en scroll
        assertTrue(feed(*two(115f, 70, cy = 330f)).none { it is Zoom })
        // Ahora se abren más de un 25 %: pasa a zoom y desde ahí envía el zoom.
        feed(*two(140f, 90, cy = 330f))
        val out = feed(*two(200f, 110, cy = 330f))
        assertTrue(out.any { it is Zoom && it.delta > 0 })
        assertTrue(out.none { it is Scroll })
    }

    // ---- Scroll ----

    @Test
    fun scrollSendsFineDeltasNotMultiplesOf120() {
        start(100f)
        val out = feed(*two(100f, 30, cy = 320f), *two(100f, 40, cy = 323f))
        // 3 px de centroide = 3 unidades (con la velocidad de scroll ×1): nada de pasos de 120.
        assertEquals(3, out.filterIsInstance<Scroll>().sumOf { it.vertical })
    }

    // ---- Inercia ----

    private val inertial = config.copy(scrollInertia = true)

    /** Scroll con 2 dedos a 1 px/ms hacia abajo (rueda +1 unidad/ms) y se levantan sin pausa. */
    private fun flick(g: GestureInterpreter, liftDelayMs: Long = 0): Long {
        start(100f, g = g)
        var t = 20L
        var y = 300f
        repeat(12) {
            t += 10
            y += 10f
            two(100f, t, cy = y).forEach { g.onTouch(it) }
        }
        t += liftDelayMs
        g.onTouch(Up(0, 250f, y, t))
        g.onTouch(Up(1, 350f, y, t))
        return t
    }

    private fun drain(g: GestureInterpreter, from: Long): List<OutputAction> {
        val out = mutableListOf<OutputAction>()
        var guard = 0
        while (g.nextDeadlineMs != null && guard++ < 10_000) out += g.onTick(g.nextDeadlineMs!!.coerceAtLeast(from))
        return out
    }

    @Test
    fun inertiaContinuesScrollingWithExponentialFriction() {
        val g = GestureInterpreter(inertial)
        val up = flick(g)
        assertEquals(up + 16, g.nextDeadlineMs)
        val out = drain(g, up)
        val scrolls = out.filterIsInstance<Scroll>()
        assertTrue(scrolls.isNotEmpty() && scrolls.all { it.vertical >= 0 && it.horizontal == 0 })
        // Recorrido total ≈ v0 · vidaMedia / ln 2 = 1 · 300 / 0,693 ≈ 433 unidades.
        val total = scrolls.sumOf { it.vertical }
        assertTrue(total in 380..470, "total = $total")
        // La velocidad baja: el primer tramo envía más que el último.
        assertTrue(scrolls.first().vertical > scrolls.last().vertical)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun halfLifeHalvesTheVelocity() {
        val g = GestureInterpreter(inertial)
        val up = flick(g)
        val sumUntil = { until: Long ->
            var s = 0
            while (g.nextDeadlineMs != null && g.nextDeadlineMs!! <= until) {
                s += g.onTick(g.nextDeadlineMs!!).filterIsInstance<Scroll>().sumOf { it.vertical }
            }
            s
        }
        val first = sumUntil(up + 300) // de 0 a 300 ms: 1 − 1/2 = 50 % del recorrido total
        val second = sumUntil(up + 600) // de 300 a 600 ms: 25 %
        assertTrue(abs(first - 2 * second) <= 0.15 * first, "first=$first second=$second")
    }

    @Test
    fun inertiaIsOffByDefault() {
        val g = GestureInterpreter(config)
        flick(g)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun anyNewTouchStopsInertia() {
        val g = GestureInterpreter(inertial)
        val up = flick(g)
        assertTrue(g.onTick(up + 16).isNotEmpty())
        g.onTouch(Down(5, 50f, 50f, up + 40))
        assertNull(g.nextDeadlineMs)
        assertEquals(emptyList(), g.onTick(up + 60))
    }

    @Test
    fun cancelStopsInertia() {
        val g = GestureInterpreter(inertial)
        flick(g)
        g.onTouch(TouchInput.Cancel(10_000))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun noInertiaIfTheFingersStoppedBeforeLifting() {
        val g = GestureInterpreter(inertial)
        flick(g, liftDelayMs = 300)
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun noInertiaForASlowScroll() {
        val g = GestureInterpreter(inertial)
        start(100f, g = g)
        var t = 20L
        var y = 300f
        repeat(6) {
            t += 20
            y += 1f // 0,05 px/ms
            two(100f, t, cy = y).forEach { g.onTouch(it) }
        }
        g.onTouch(Up(0, 250f, y, t + 5))
        assertNull(g.nextDeadlineMs)
    }

    @Test
    fun inertiaFollowsInvertedScrollDirection() {
        val g = GestureInterpreter(inertial.copy(naturalScroll = false))
        val up = flick(g)
        val scrolls = drain(g, up).filterIsInstance<Scroll>()
        assertTrue(scrolls.isNotEmpty() && scrolls.all { it.vertical <= 0 })
    }
}
