package dev.mymouse.core.gesture

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Convierte valores fraccionarios en enteros sin perder el resto (movimientos lentos no se pierden). */
class FractionalAccumulator {
    private var remainder = 0f

    fun add(value: Float): Int {
        val total = value + remainder
        val whole = total.toInt() // trunca hacia cero
        remainder = total - whole
        return whole
    }

    fun reset() {
        remainder = 0f
    }
}

/**
 * Curva de aceleración del puntero:
 * `salida = entrada × sensitivity × min(maxGain, 1 + accel × max(0, v − v0) / v0)`.
 */
class Accelerator(private val config: TrackpadConfig) {
    private val x = FractionalAccumulator()
    private val y = FractionalAccumulator()
    private var lastSpeed = 0f

    fun apply(dx: Float, dy: Float, dtMs: Long): Pair<Int, Int> {
        val speed = if (dtMs > 0) hypot(dx, dy) / dtMs else lastSpeed
        lastSpeed = speed
        val v0 = config.accelThresholdPxPerMs
        val gain = min(config.maxGain, 1f + config.acceleration * max(0f, speed - v0) / v0) * config.sensitivity
        return x.add(dx * gain) to y.add(dy * gain)
    }

    fun reset() {
        x.reset()
        y.reset()
        lastSpeed = 0f
    }
}
