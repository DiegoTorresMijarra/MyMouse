package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.SystemGestureCommand
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Máquina de estados que convierte toques en acciones de ratón. Ver docs/spec-v0.md §2.2.
 *
 * Con 3 o 4 dedos clasifica el gesto (deslizar o pellizcar) según docs/spec-v3.1-ajustes.md §1; con 2, decide entre
 * scroll y zoom (§2).
 *
 * No depende de Android ni del tiempo real: el llamador pasa los eventos con su marca de tiempo
 * y llama a [onTick] cuando se alcanza [nextDeadlineMs] (clic diferido, activación del modo gestos e inercia).
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

        /**
         * Scroll con 2 dedos. [startSpread] es la distancia entre ellos al empezar (para pasar a zoom si cambia mucho);
         * [velocityV] y [velocityH] (unidades de rueda por ms) alimentan la inercia.
         */
        class Scrolling(
            var lastCentroid: Point,
            val startSpread: Float,
            var sampleCentroid: Point,
            var sampleTime: Long,
        ) : State {
            var velocityV = 0f
            var velocityH = 0f
        }

        /** Pellizco de 2 dedos: [anchor] es la distancia al inicio del tramo; el cambio se acumula hasta el siguiente envío. */
        class Zooming(var anchor: Float, var lastEmitTime: Long) : State

        /**
         * 3 o 4 dedos apoyados dentro de la ventana de [TrackpadConfig.gestureWindowMs] y sin clasificar.
         * [base] son las posiciones al llegar el último dedo. Con 4 dedos quietos hasta [deadline] se activa el modo gestos
         * ([deadline] es null si no está armado: 3 dedos, modo gestos desactivado o los dedos se han movido).
         */
        class Multi(val startTime: Long, var base: Map<Int, Point>, var count: Int, var deadline: Long?) : State

        /** Cambio de app con 3 dedos (Alt pulsado): [accumulated] es el recorrido horizontal aún sin convertir en pasos. */
        class AppSwitching(var lastCentroid: Point, var accumulated: Float = 0f) : State

        /** Se ignora todo hasta que se levanten todos los dedos. */
        data object Ignoring : State
    }

    private val pointers = LinkedHashMap<Int, Point>()
    private var state: State = State.Idle
    private val accelerator = Accelerator(config)
    private val scrollV = FractionalAccumulator()
    private val scrollH = FractionalAccumulator()
    private val zoomAcc = FractionalAccumulator()

    /** Instante en que expira el clic diferido; el botón izquierdo está pulsado hasta entonces. */
    private var pendingReleaseAt: Long? = null

    /** Scroll que continúa tras levantar los dedos (docs/spec-v3.1-ajustes.md §2). */
    private class Inertia(var velocityV: Float, var velocityH: Float, var lastTime: Long) {
        var nextAt = lastTime
    }

    private var inertia: Inertia? = null

    /** Próximo instante (misma base que [TouchInput.timeMs]) en que debe llamarse a [onTick]. */
    val nextDeadlineMs: Long?
        get() = listOfNotNull(pendingReleaseAt, (state as? State.Multi)?.deadline, inertia?.nextAt).minOrNull()

    fun onTick(nowMs: Long): List<OutputAction> {
        val out = mutableListOf<OutputAction>()
        val deadline = pendingReleaseAt
        if (deadline != null && nowMs >= deadline) {
            pendingReleaseAt = null
            out += leftUp()
        }
        val s = state
        val activation = (s as? State.Multi)?.deadline
        if (activation != null && nowMs >= activation) {
            state = State.Ignoring // los dedos siguen apoyados: se esperan a que se levanten
            out += OutputAction.GestureMode(true)
        }
        inertia?.let { if (nowMs >= it.nextAt) stepInertia(it, nowMs, out) }
        return out
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
        inertia = null // cualquier toque nuevo corta la inercia
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
                state = startScrolling(e.timeMs, spread(pointers.values))
            }
            is State.Dragging -> Unit // dedos extra durante el arrastre: se ignoran
            is State.TwoDown -> state =
                if (e.timeMs - s.startTime <= config.gestureWindowMs) State.Multi(s.startTime, pointers.toMap(), 3, null)
                else State.Ignoring
            is State.Multi -> state =
                if (s.count == 3 && e.timeMs - s.startTime <= config.gestureWindowMs) {
                    // Cuarto dedo a tiempo: ya no se puede decidir un gesto de 3 dedos.
                    State.Multi(s.startTime, pointers.toMap(), 4, if (config.gestureFingers == 4) e.timeMs + config.gestureHoldMs else null)
                } else {
                    State.Ignoring
                }
            // Un dedo más con el gesto ya decidido no lo cambia; solo se evita un salto del centro.
            is State.AppSwitching -> s.lastCentroid = centroid()
            is State.Scrolling, is State.Zooming -> state = State.Ignoring
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
                // Scroll: los dedos se desplazan juntos (se mueve el centro). Zoom: cambia el cociente de distancias.
                // Un pellizco con un dedo quieto también mueve el centro, pero siempre menos que la distancia.
                val startSpread = spread(s.starts.values)
                val spreadChange = abs(spread(pointers.values) - startSpread)
                val ratioChange = if (startSpread > 0f) spreadChange / startSpread else 0f
                val centroidMove = distance(centroid(s.starts.values), centroid())
                if (ratioChange >= config.pinchRatio && spreadChange >= config.touchSlopPx && spreadChange > centroidMove) {
                    state = startZooming(e.timeMs)
                } else if (centroidMove > config.touchSlopPx && centroidMove > spreadChange) {
                    state = startScrolling(e.timeMs, startSpread)
                }
            }
            is State.Zooming -> if (e.endOfSample) {
                val now = spread(pointers.values)
                if (now > 0f && s.anchor > 0f && e.timeMs - s.lastEmitTime >= config.zoomIntervalMs) {
                    // Proporcional al logaritmo del cociente: igual en cualquier tamaño de pantalla y aditivo entre tramos.
                    val units = ln(now / s.anchor) * TrackpadConfig.ZOOM_UNITS_PER_LN * config.zoomSpeed
                    s.anchor = now
                    s.lastEmitTime = e.timeMs
                    val n = zoomAcc.add(units)
                    if (n != 0) out += OutputAction.Zoom(n.clampI16())
                }
            }
            is State.Scrolling -> {
                val c = centroid()
                val sign = if (config.naturalScroll) 1f else -1f
                val v = scrollV.add((c.y - s.lastCentroid.y) * config.scrollSpeed * sign)
                val h = scrollH.add(-(c.x - s.lastCentroid.x) * config.scrollSpeed * sign)
                s.lastCentroid = c
                if (v != 0 || h != 0) out += OutputAction.Scroll(v.clampI16(), h.clampI16())
                if (e.endOfSample) {
                    trackScrollVelocity(s, c, e.timeMs, sign)
                    // Si los dedos se separan o se juntan mucho, en realidad era un pellizco.
                    val now = spread(pointers.values)
                    if (s.startSpread > 0f && abs(now / s.startSpread - 1f) > config.scrollToZoomRatio) {
                        state = startZooming(e.timeMs)
                    }
                }
            }
            is State.Multi -> if (e.endOfSample) classifyMulti(s, e.timeMs, out)
            is State.AppSwitching -> if (e.endOfSample) {
                val c = centroid()
                s.accumulated += c.x - s.lastCentroid.x
                s.lastCentroid = c
                while (s.accumulated >= config.appSwitchStepPx) {
                    s.accumulated -= config.appSwitchStepPx
                    out += OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_NEXT)
                }
                while (s.accumulated <= -config.appSwitchStepPx) {
                    s.accumulated += config.appSwitchStepPx
                    out += OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_PREVIOUS)
                }
            }
            State.Idle, State.Ignoring -> Unit
        }
    }

    // ---- 3 y 4 dedos ----

    private fun classifyMulti(s: State.Multi, timeMs: Long, out: MutableList<OutputAction>) {
        val ids = s.base.keys.filter { it in pointers }
        if (ids.isEmpty()) return
        val before = ids.map { s.base.getValue(it) }
        val after = ids.map { pointers.getValue(it) }

        // El modo gestos solo se activa con los dedos quietos: un movimiento mayor que el umbral lo desarma.
        if (s.deadline != null && ids.indices.any { distance(before[it], after[it]) > config.touchSlopPx }) s.deadline = null

        // Un gesto de 3 dedos no se decide mientras pueda llegar el cuarto (la ventana cuenta desde el primer dedo).
        if (s.count < 4 && timeMs - s.startTime <= config.gestureWindowMs) return

        val c0 = centroid(before)
        val c1 = centroid(after)
        val r0 = meanDistance(before, c0)
        val r1 = meanDistance(after, c1)
        val dx = c1.x - c0.x
        val dy = c1.y - c0.y
        val move = hypot(dx, dy)
        val spreadChange = abs(r1 - r0)
        val ratioChange = if (r0 > 0f) spreadChange / r0 else 0f

        val pinch = ratioChange >= config.multiPinchRatio && spreadChange >= config.touchSlopPx && spreadChange > move
        val swipe = !pinch && move >= config.multiSwipePx && move >= spreadChange
        if (!pinch && !swipe) return

        // Un gesto decidido no cambia de tipo hasta levantar todos los dedos.
        state = State.Ignoring
        val vertical = abs(dy) > abs(dx)
        if (s.count == 4) {
            if (pinch) out += OutputAction.WidgetPanel(show = r1 < r0)
            else if (vertical) out += OutputAction.FourFingerSwipe(up = dy < 0)
        } else if (swipe && config.windowsGestures) {
            when {
                vertical -> out += OutputAction.SystemGesture(
                    if (dy < 0) SystemGestureCommand.TASK_VIEW else SystemGestureCommand.SHOW_DESKTOP,
                )
                else -> {
                    state = State.AppSwitching(c1)
                    out += OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_START)
                    out += OutputAction.SystemGesture(
                        if (dx > 0) SystemGestureCommand.APP_SWITCH_NEXT else SystemGestureCommand.APP_SWITCH_PREVIOUS,
                    )
                }
            }
        }
    }

    // ---- Scroll ----

    private fun trackScrollVelocity(s: State.Scrolling, c: Point, timeMs: Long, sign: Float) {
        val dt = timeMs - s.sampleTime
        if (dt < MIN_VELOCITY_DT_MS) return
        val v = (c.y - s.sampleCentroid.y) * config.scrollSpeed * sign / dt
        val h = -(c.x - s.sampleCentroid.x) * config.scrollSpeed * sign / dt
        s.velocityV = 0.5f * s.velocityV + 0.5f * v
        s.velocityH = 0.5f * s.velocityH + 0.5f * h
        s.sampleCentroid = c
        s.sampleTime = timeMs
    }

    private fun stepInertia(i: Inertia, nowMs: Long, out: MutableList<OutputAction>) {
        val dt = (nowMs - i.lastTime).coerceAtLeast(0)
        // Fricción exponencial: v(t) = v0 · 2^(−t / vidaMedia). El recorrido del tramo es la integral exacta.
        val tau = config.inertiaHalfLifeMs / LN2
        val decay = exp(-dt / tau).toFloat()
        val factor = (tau * (1 - decay)).toFloat()
        val v = scrollV.add(i.velocityV * factor)
        val h = scrollH.add(i.velocityH * factor)
        i.velocityV *= decay
        i.velocityH *= decay
        i.lastTime = nowMs
        i.nextAt = nowMs + config.inertiaTickMs
        if (v != 0 || h != 0) out += OutputAction.Scroll(v.clampI16(), h.clampI16())
        if (hypot(i.velocityV, i.velocityH) < config.inertiaMinVelocity / INERTIA_STOP_DIVISOR) inertia = null
    }

    private fun maybeStartInertia(s: State.Scrolling, timeMs: Long) {
        if (!config.scrollInertia) return
        // Con los dedos parados antes de levantarlos no hay inercia.
        if (timeMs - s.sampleTime > INERTIA_STALE_MS) return
        if (hypot(s.velocityV, s.velocityH) < config.inertiaMinVelocity) return
        scrollV.reset()
        scrollH.reset()
        inertia = Inertia(s.velocityV, s.velocityH, timeMs).also { it.nextAt = timeMs + config.inertiaTickMs }
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
            is State.Scrolling -> {
                maybeStartInertia(s, e.timeMs)
                state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            }
            is State.AppSwitching -> {
                // Levantar los dedos termina el cambio de app: el host suelta Alt.
                out += OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_END)
                state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            }
            is State.Zooming, is State.Multi, State.Ignoring ->
                state = if (pointers.isEmpty()) State.Idle else State.Ignoring
            State.Idle -> Unit
        }
    }

    private fun onCancel(out: MutableList<OutputAction>) {
        val s = state
        if (s is State.Dragging || (s is State.OneDown && s.dragCandidate) || pendingReleaseAt != null) {
            out += leftUp()
        }
        if (s is State.AppSwitching) out += OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_END)
        pendingReleaseAt = null
        inertia = null
        pointers.clear()
        state = State.Idle
    }

    private fun emitMove(from: Point, to: Point, dtMs: Long, out: MutableList<OutputAction>) {
        val (dx, dy) = accelerator.apply(to.x - from.x, to.y - from.y, dtMs)
        if (dx != 0 || dy != 0) out += OutputAction.PointerMove(dx.clampI16(), dy.clampI16())
    }

    private fun startScrolling(timeMs: Long, startSpread: Float): State.Scrolling {
        scrollV.reset()
        scrollH.reset()
        val c = centroid()
        return State.Scrolling(c, startSpread, c, timeMs)
    }

    private fun startZooming(timeMs: Long): State.Zooming {
        zoomAcc.reset()
        return State.Zooming(spread(pointers.values), timeMs)
    }

    private fun centroid(points: Collection<Point> = pointers.values): Point = Point(
        points.map { it.x }.average().toFloat(),
        points.map { it.y }.average().toFloat(),
    )

    /** Distancia media de los puntos a [center]. */
    private fun meanDistance(points: Collection<Point>, center: Point): Float =
        points.map { distance(it, center) }.average().toFloat()

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
        val LN2 = ln(2.0)
        const val MIN_VELOCITY_DT_MS = 4L

        /** Si el último movimiento de los dedos fue hace más de esto, estaban parados: sin inercia. */
        const val INERTIA_STALE_MS = 100L

        /** La inercia termina cuando la velocidad cae por debajo de la mínima dividida entre esto. */
        const val INERTIA_STOP_DIVISOR = 10f
    }
}
