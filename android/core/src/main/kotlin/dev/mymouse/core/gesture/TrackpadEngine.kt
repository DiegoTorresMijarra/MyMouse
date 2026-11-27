package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.StrokeResult
import dev.mymouse.core.shapes.P
import dev.mymouse.core.shapes.Recognizer

/**
 * Une el trackpad ([GestureInterpreter]) y el modo gestos (docs/spec-v1-gestos.md §1):
 * 1. 4 dedos quietos (docs/spec-v3.1-ajustes.md §1) → el intérprete emite [OutputAction.GestureMode] (true).
 * 2. Se espera a que se levanten esos dedos y, durante [TrackpadConfig.gestureStrokeTimeoutMs], a que empiece un trazo.
 * 3. Un dedo dibuja; al levantarlo se reconoce la forma → [OutputAction.Gesture] o [OutputAction.GestureRejected].
 * 4. Dos o más dedos durante el modo gestos lo cancelan (incluido volver a apoyar 4 dedos).
 *
 * Además (docs/spec-v4-toolbox.md §5) emite lo que necesita el PC para mostrar el gesto en vivo:
 * [OutputAction.StrokePoints] (lotes de como mucho 16 ms, si se ha dado el tamaño del área con [setArea]) y
 * [OutputAction.StrokeEnd] al terminar el trazo, antes del resultado.
 */
class TrackpadEngine(
    private val config: TrackpadConfig,
    private val recognizer: Recognizer?,
) {
    private val interpreter = GestureInterpreter(config)

    private sealed interface Mode {
        data object Trackpad : Mode
        /** Activado: esperando a que se levanten los dedos de la activación. */
        data object ReleasingActivation : Mode
        data class WaitingStroke(val deadline: Long) : Mode
        data class Drawing(val id: Int, val points: MutableList<P>) : Mode
        /** Cancelado o terminado: se ignora todo hasta que no quede ningún dedo. */
        data object Draining : Mode
    }

    private var mode: Mode = Mode.Trackpad
    private val down = HashSet<Int>()

    private var areaWidthPx = 0f
    private var areaHeightPx = 0f
    private var batcher: StrokeBatcher? = null

    /** Tamaño del área del trackpad en píxeles: sin él no se emiten [OutputAction.StrokePoints]. */
    fun setArea(widthPx: Float, heightPx: Float) {
        areaWidthPx = widthPx
        areaHeightPx = heightPx
    }

    /** true mientras el modo gestos está activo (para el feedback en pantalla). */
    val gestureModeActive: Boolean get() = mode != Mode.Trackpad && mode != Mode.Draining

    /** Trazo en curso del modo gestos, en coordenadas de pantalla. */
    val currentStroke: List<P> get() = (mode as? Mode.Drawing)?.points ?: emptyList()

    val nextDeadlineMs: Long?
        get() = when (val m = mode) {
            Mode.Trackpad -> interpreter.nextDeadlineMs
            is Mode.WaitingStroke -> m.deadline
            is Mode.Drawing -> batcher?.nextFlushMs
            else -> null
        }

    fun onTick(nowMs: Long): List<OutputAction> {
        val m = mode
        if (m is Mode.WaitingStroke) {
            if (nowMs < m.deadline) return emptyList()
            mode = if (down.isEmpty()) Mode.Trackpad else Mode.Draining
            return listOf(
                OutputAction.StrokeEnd(StrokeResult.TIMED_OUT),
                OutputAction.GestureRejected("Tiempo agotado"),
                OutputAction.GestureMode(false),
            )
        }
        if (m is Mode.Drawing) return batcher?.tick(nowMs) ?: emptyList()
        if (m != Mode.Trackpad) return emptyList()
        return afterInterpreter(interpreter.onTick(nowMs), nowMs)
    }

    fun onTouch(event: TouchInput): List<OutputAction> {
        when (event) {
            is TouchInput.Down -> down += event.id
            is TouchInput.Up -> down -= event.id
            is TouchInput.Cancel -> down.clear()
            is TouchInput.Move -> Unit
        }

        return when (val m = mode) {
            Mode.Trackpad -> afterInterpreter(interpreter.onTouch(event), event.timeMs)
            Mode.ReleasingActivation -> {
                forwardRelease(event)
                if (event is TouchInput.Cancel) return exit("Cancelado")
                if (down.isEmpty()) mode = Mode.WaitingStroke(event.timeMs + config.gestureStrokeTimeoutMs)
                emptyList()
            }
            is Mode.WaitingStroke -> when (event) {
                is TouchInput.Down -> {
                    mode = Mode.Drawing(event.id, mutableListOf(P(event.x, event.y)))
                    batcher = if (areaWidthPx > 0f && areaHeightPx > 0f) StrokeBatcher(areaWidthPx, areaHeightPx) else null
                    batcher?.add(event.x, event.y, event.timeMs) ?: emptyList() // el primer punto sale enseguida
                }
                is TouchInput.Cancel -> exit("Cancelado")
                else -> emptyList()
            }
            is Mode.Drawing -> when (event) {
                is TouchInput.Down -> exit("Cancelado") // un segundo dedo cancela
                is TouchInput.Move -> {
                    if (event.id != m.id) return emptyList()
                    m.points += P(event.x, event.y)
                    batcher?.add(event.x, event.y, event.timeMs) ?: emptyList()
                }
                is TouchInput.Up -> {
                    if (event.id != m.id) return emptyList()
                    m.points += P(event.x, event.y)
                    val last = batcher?.let { it.add(event.x, event.y, event.timeMs) + it.flush(event.timeMs) } ?: emptyList()
                    last + finish(m.points)
                }
                is TouchInput.Cancel -> exit("Cancelado")
            }
            Mode.Draining -> {
                forwardRelease(event)
                if (down.isEmpty()) mode = Mode.Trackpad
                emptyList()
            }
        }
    }

    private fun afterInterpreter(out: List<OutputAction>, nowMs: Long): List<OutputAction> {
        if (out.any { it is OutputAction.GestureMode && it.active }) {
            mode = if (recognizer == null) Mode.Draining else Mode.ReleasingActivation
            if (recognizer == null) return out.filterNot { it is OutputAction.GestureMode }
            if (down.isEmpty()) mode = Mode.WaitingStroke(nowMs + config.gestureStrokeTimeoutMs)
            // El PC necesita saber cuánto tiempo hay para empezar a dibujar.
            return out.map {
                if (it is OutputAction.GestureMode && it.active) it.copy(startTimeoutMs = config.gestureStrokeTimeoutMs.toInt().coerceIn(0, 0xFFFF)) else it
            }
        }
        return out
    }

    /** Los dedos que se levantan en modo gestos también se le pasan al intérprete, para que acabe en reposo. */
    private fun forwardRelease(event: TouchInput) {
        if (event is TouchInput.Up || event is TouchInput.Cancel) interpreter.onTouch(event)
    }

    private fun finish(points: List<P>): List<OutputAction> {
        batcher = null
        mode = if (down.isEmpty()) Mode.Trackpad else Mode.Draining
        val result = recognizer?.recognize(points) ?: Recognizer.Result.NoMatch("Sin reconocedor")
        val (end, outcome) = when (result) {
            is Recognizer.Result.Match -> StrokeResult.RECOGNIZED to OutputAction.Gesture(result.name, result.score)
            is Recognizer.Result.NoMatch -> StrokeResult.NOT_RECOGNIZED to OutputAction.GestureRejected(result.reason)
        }
        return listOf(OutputAction.StrokeEnd(end), outcome, OutputAction.GestureMode(false))
    }

    private fun exit(reason: String): List<OutputAction> {
        batcher = null
        mode = if (down.isEmpty()) Mode.Trackpad else Mode.Draining
        return listOf(OutputAction.StrokeEnd(StrokeResult.CANCELLED), OutputAction.GestureRejected(reason), OutputAction.GestureMode(false))
    }
}
