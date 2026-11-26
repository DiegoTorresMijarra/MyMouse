package dev.mymouse.core.hid

import kotlin.math.roundToInt

/**
 * Estadística del último segundo de envíos por Bluetooth (docs/spec-v4.5-estilo.md §4.2).
 *
 * Por HID no hay confirmaciones, así que no existe un ping real: lo que se puede medir con honradez es cuántos informes salen
 * por segundo, el **intervalo real entre informes** y lo que tarda cada llamada a `sendReport`. Se escribe desde el hilo emisor y
 * se lee desde el de la interfaz.
 */
class HidSendStats(private val windowMs: Long = 1_000L) {

    data class Snapshot(
        val reportsPerSecond: Int,
        /** Media del tiempo entre informes consecutivos; null con menos de dos. */
        val avgIntervalMs: Double?,
        /** Media de lo que tardó `sendReport`; null sin informes. */
        val avgSendMs: Double?,
        /** Llamadas a `sendReport` que devolvieron false en la ventana. */
        val failures: Int,
    ) {
        /** Texto corto para el estado: "120 inf/s · 8 ms" (o "en reposo"). */
        fun label(): String = when {
            reportsPerSecond == 0 -> "en reposo"
            avgIntervalMs == null -> "$reportsPerSecond inf/s"
            else -> "$reportsPerSecond inf/s · ${avgIntervalMs.roundToInt()} ms"
        }

        /** Texto largo para el diagnóstico y el informe copiado. */
        fun detail(): String =
            if (reportsPerSecond == 0) "sin informes en el último segundo"
            else "$reportsPerSecond informes/s, intervalo real entre informes " +
                (avgIntervalMs?.let { "${format(it)} ms" } ?: "—") +
                ", sendReport ${avgSendMs?.let { "${format(it)} ms" } ?: "—"} de media" +
                if (failures > 0) ", $failures rechazados" else ""
    }

    private class Sample(val timeMs: Long, val sendMicros: Long, val ok: Boolean)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun record(nowMs: Long, sendMicros: Long, ok: Boolean = true) {
        samples.addLast(Sample(nowMs, sendMicros, ok))
        prune(nowMs)
    }

    @Synchronized
    fun snapshot(nowMs: Long): Snapshot {
        prune(nowMs)
        if (samples.isEmpty()) return Snapshot(0, null, null, 0)
        val interval = if (samples.size >= 2) (samples.last().timeMs - samples.first().timeMs).toDouble() / (samples.size - 1) else null
        return Snapshot(samples.size, interval, samples.sumOf { it.sendMicros } / 1000.0 / samples.size, samples.count { !it.ok })
    }

    @Synchronized
    fun reset() = samples.clear()

    private fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().timeMs >= windowMs) samples.removeFirst()
    }

    private companion object {
        fun format(value: Double): String = ((value * 10).roundToInt() / 10.0).toString().replace('.', ',')
    }
}
