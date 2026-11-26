package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.Protocol
import dev.mymouse.core.protocol.StrokePoint
import kotlin.math.roundToInt

/**
 * Agrupa los puntos de un trazo en lotes de como mucho [intervalMs] para enviarlos al PC (`STROKE_POINTS`) y los
 * normaliza a 0–65535. Se normaliza con la misma escala en los dos ejes (el lado largo del área ocupa todo el
 * rango) para que el trazo conserve sus proporciones en el PC.
 * El primer punto sale enseguida; los demás, cuando han pasado [intervalMs] desde el último envío.
 */
class StrokeBatcher(widthPx: Float, heightPx: Float, private val intervalMs: Long = 16) {
    private val scale = 65535f / maxOf(widthPx, heightPx, 1f)
    private val pending = ArrayList<StrokePoint>()
    private var lastFlushMs: Long? = null

    /** Cuándo toca enviar lo pendiente, o null si no hay nada pendiente. */
    val nextFlushMs: Long? get() = if (pending.isEmpty()) null else (lastFlushMs ?: 0L) + intervalMs

    /** Añade un punto (píxeles del área del trackpad). Devuelve los lotes que ya toca enviar. */
    fun add(x: Float, y: Float, timeMs: Long): List<OutputAction.StrokePoints> {
        pending += StrokePoint(normalize(x), normalize(y))
        val last = lastFlushMs
        return if (last == null || timeMs - last >= intervalMs) flush(timeMs) else emptyList()
    }

    /** Envía ya lo pendiente (en lotes de como mucho 255 puntos, el límite del protocolo). */
    fun flush(timeMs: Long): List<OutputAction.StrokePoints> {
        if (pending.isEmpty()) return emptyList()
        val batches = pending.chunked(Protocol.MAX_POINTS_PER_BATCH).map { OutputAction.StrokePoints(it) }
        pending.clear()
        lastFlushMs = timeMs
        return batches
    }

    /** Envía lo pendiente si ya ha pasado el intervalo. */
    fun tick(nowMs: Long): List<OutputAction.StrokePoints> {
        val due = nextFlushMs ?: return emptyList()
        return if (nowMs >= due) flush(nowMs) else emptyList()
    }

    private fun normalize(v: Float): Int = (v * scale).roundToInt().coerceIn(0, 65535)
}
