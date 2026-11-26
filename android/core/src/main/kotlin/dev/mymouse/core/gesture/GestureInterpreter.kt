package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.MouseButton
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Máquina de estados que convierte toques en acciones de ratón. Ver docs/spec-v0.md §2.2.
 *
 * No depende de Android ni del tiempo real: el llamador pasa los eventos con su marca de tiempo
 * y llama a [onTick] cuando se alcanza [nextDeadlineMs] (para soltar el clic diferido).
 */
class GestureInterpreter(private val config: TrackpadConfig = TrackpadConfig()) {

    private data class Point(val x: Float, val y: Float)

    private sealed interface State {
        data object Idle : State

        /** Un dedo tocando, aún sin superar el umbral de movimiento. */
        data class OneDown(val id: Int, val start: Point, val startTime: Long, val dragCandidate: Boolean) : State

        data class Moving(val id: Int, var last: Point, var lastTime: Long) : State
        data class Dragging(val id: Int, var last: Point, var lastTime: Long) : State

        /** Dos dedos tocando, sin superar el umbral. */
        data class TwoDown(val startTime: Long, val starts: Map<Int, Point>) : State

        data class Scrolling(var lastCentroid: Point) : State

        /** Pellizco: [accumulated] guarda el cambio de distancia aún no convertido en pasos de zoom. */
        data class Zooming(var lastSpread: Float, var accumulated: Float = 0f) : State

        /** Se ignora todo hasta que se levanten todos los dedos. */
        data object Ignoring : State
    }

    private val pointers = LinkedHashMap<Int, Point>()
    private var state: State = State.Idle
    private val accelerator = Accelerator(config)
    private val scrollV = FractionalAccumulator()
    private val scrollH = FractionalAccumulator()

    /** Instante en que expira el clic diferido; el botón izquierdo está pulsado hasta entonces. */
    private var pendingReleaseAt: Long? = null

    /** Próximo instante (misma base que [TouchInput.timeMs]) en que debe llamarse a [onTick]. */
    val nextDeadlineMs: Long? get() = pendingReleaseAt

    fun onTick(nowMs: Long): List<OutputAction> {
        val deadline = pendingReleaseAt ?: return emptyList()
        if (nowMs < deadline) return emptyList()
        pendingReleaseAt = null
        return listOf(leftUp())
    }

    fun onTouch(event: TouchInput): List<OutputAction> {
        val out = mutableListOf<OutputAction>()
        when (event) {
            is TouchInput.Down -> onDown(event, out)
            is TouchInput.Move -> onMove(event, out)
            is TouchInput.Up -> onUp(event, out)
            is TouchInput.Cancel -> onCancel(out)
        }
        return out
    }

    private fun onDown(e: TouchInput.Down, out: MutableList<OutputAction>) {
        pointers[e.id] = Point(e.x, e.y)
        when (val s = state) {
            State.Idle -> {
                val pending = pendingReleaseAt
                pendingReleaseAt = null
                val dragCandidate = pending != null && e.timeMs < pending
                if (pending != null && !dragCandidate) out += leftUp() // el tick llegó tarde
                state = State.OneDown(e.id, Point(e.x, e.y), e.timeMs, dragCandidate)
            }
            is State.OneDown -> {
                if (s.dragCandidate) out += leftUp()
                state = State.TwoDown(s.startTime, pointers.toMap())
            }
            is State.Moving -> {
                accelerator.reset()
                state = startScrolling()
            }
            is State.Dragging -> Unit // dedos extra durante el arrastre: se ignoran
            is State.TwoDown, is State.Scrolling, is State.Zooming -> state = State.Ignoring // 3+ dedos: reservado para v1
            State.Ignoring -> Unit
        }
    }

    private fun onMove(e: TouchInput.Move, out: MutableList<OutputAction>) {
        if (e.id !in pointers) return
        val p = Point(e.x, e.y)
        pointers[e.id] = p

        when (val s = state) {
            is State.OneDown -> if (e.id == s.id && distance(s.start, p) > config.touchSlopPx) {
                // La referencia empieza en el punto de cruce del umbral: sin salto inicial.
                accelerator.reset()
                state = if (s.dragCandidate) State.Dragging(s.id, p, e.timeMs) else State.Moving(s.id, p, e.timeMs)
            }
            is State.Moving -> if (e.id == s.id) {
                emitMove(s.last, p, e.timeMs - s.lastTime, out)
                s.last = p
                s.lastTime = e.timeMs
            }
            is State.Dragging -> if (e.id == s.id) {
                emitMove(s.last, p, e.timeMs - s.lastTime, out)
                s.last = p
                s.lastTime = e.timeMs
            }
            is State.TwoDown -> if (e.endOfSample) {
                // Scroll: los dedos se desplazan juntos (se mueve el centro). Zoom: cambia la distancia entre ellos.
                // Un pellizco con un dedo quieto también mueve el centro, pero siempre menos que la distancia.
                val startSpread = spread(s.starts.values)
                val spreadChange = abs(spread(pointers.values) - startSpread)
                val centroidMove = distance(centroid(s.starts.values), centroid())
                if (spreadChange >= config.pinchSlopPx && spreadChange > centroidMove) {
                    state = State.Zooming(spread(pointers.values))
                } else if (centroidMove > config.touchSlopPx && centroidMove > spreadChange) {
                    state = startScrolling()
                }
            }
            is State.Zooming -> {
                val now = spread(pointers.values)
                s.accumulated += now - s.lastSpread
                s.lastSpread = now
                val steps = (s.accumulated / config.zoomStepPx).toInt() // trunca hacia 0
                if (steps != 0) {
                    s.accumulated -= steps * config.zoomStepPx
                    out += OutputAction.Zoom((steps * WHEEL_STEP).clampI16())
                }
            }
            is State.Scrolling -> {
                val c = centroid()
                val sign = if (config.naturalScroll) 1f else -1f
                val v = scrollV.add((c.y - s.lastCentroid.y) * config.scrollSpeed * sign)
                val h = scrollH.add(-(c.x - s.lastCentroid.x) * config.scrollSpeed * sign)
                s.lastCentroid = c
                if (v != 0 || h != 0) out += OutputAction.Scroll(v.clampI16(), h.clampI16())
            }
            State.Idle, State.Ignoring -> Unit
        }
    }

    private fun onUp(e: TouchInput.Up, out: MutableList<OutputAction>) {
        pointers.remove(e.id) ?: return

        when (val s = state) {
            is State.OneDown -> {
                val isTap = e.timeMs - s.startTime <= config.tapTimeoutMs
                if (s.dragCandidate) out += leftUp() // completa el primer clic
                if (isTap) {
                    out += OutputAction.Button(MouseButton.LEFT, true)
                    if (config.tapToDrag) pendingReleaseAt = e.timeMs + config.doubleTapTimeoutMs
                    else out += leftUp()
                }
                state = State.Idle
            }
            is State.Moving -> state = State.Idle
            is State.Dragging -> if (e.id == s.id) {
                out += leftUp()
                state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            }
            is State.TwoDown -> {
                if (e.timeMs - s.startTime <= config.tapTimeoutMs) {
                    out += OutputAction.Button(MouseButton.RIGHT, true)
                    out += OutputAction.Button(MouseButton.RIGHT, false)
                }
                state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            }
            is State.Scrolling, is State.Zooming -> state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            State.Ignoring -> if (pointers.isEmpty()) state = State.Idle
            State.Idle -> Unit
        }
    }

    private fun onCancel(out: MutableList<OutputAction>) {
        val s = state
        if (s is State.Dragging || (s is State.OneDown && s.dragCandidate) || pendingReleaseAt != null) {
            out += leftUp()
        }
        pendingReleaseAt = null
        pointers.clear()
        state = State.Idle
    }

    private fun emitMove(from: Point, to: Point, dtMs: Long, out: MutableList<OutputAction>) {
        val (dx, dy) = accelerator.apply(to.x - from.x, to.y - from.y, dtMs)
        if (dx != 0 || dy != 0) out += OutputAction.PointerMove(dx.clampI16(), dy.clampI16())
    }

    private fun startScrolling(): State.Scrolling {
        scrollV.reset()
        scrollH.reset()
        return State.Scrolling(centroid())
    }

    private fun centroid(points: Collection<Point> = pointers.values): Point = Point(
        points.map { it.x }.average().toFloat(),
        points.map { it.y }.average().toFloat(),
    )

    /** Distancia entre los dos primeros dedos (0 si hay menos de dos). */
    private fun spread(points: Collection<Point>): Float {
        if (points.size < 2) return 0f
        val (a, b) = points.take(2)
        return distance(a, b)
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun leftUp() = OutputAction.Button(MouseButton.LEFT, false)

    private fun Int.clampI16() = coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    private companion object {
        const val WHEEL_STEP = 120
    }
}
