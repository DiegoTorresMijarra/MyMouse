package dev.mymouse.core.universe

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Una estrella del fondo "universo" (docs/spec-v4.7-universo.md §2). La posición va en coordenadas 0–1, para que no "salte" al
 * redimensionar. [radius] en dp, [opacity] de 0 a 1. Si [twinkle], la opacidad oscila con periodo [periodSeconds] y fase [phase].
 */
data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val opacity: Float,
    val halo: Boolean,
    val twinkle: Boolean,
    val periodSeconds: Float,
    val phase: Float,
)

/**
 * Generador de estrellas con semilla. El mismo algoritmo (mulberry32) está en `MyMouse.HostCore.Universe.StarField`, así que el
 * universo se ve igual en el PC y en el móvil, y los tests de las dos plataformas comparten los mismos valores de referencia.
 */
object StarField {
    const val DEFAULT_SEED = 0x4D794D73

    /** Estrellas de referencia en un área de 400 × 600 dp. */
    const val REFERENCE_COUNT = 90
    const val REFERENCE_AREA = 400f * 600f

    const val MIN_COUNT = 12
    const val MAX_COUNT = 220

    /** Una de cada 7 estrellas parpadea (unas 12–13 de cada 90). */
    const val TWINKLE_EVERY = 7

    const val MIN_RADIUS = 0.5f
    const val MAX_RADIUS = 1.6f
    const val MIN_OPACITY = 0.35f
    const val MAX_OPACITY = 1f
    const val HALO_CHANCE = 0.10
    const val MIN_PERIOD = 2f
    const val MAX_PERIOD = 5f

    /** Cuánto baja, como mínimo, la opacidad de una estrella que parpadea (fracción de la suya). */
    const val TWINKLE_FLOOR = 0.35f

    /** Cuántas estrellas caben en un área de [widthDp] × [heightDp]: unas 90 por cada 400 × 600. */
    fun countFor(widthDp: Float, heightDp: Float): Int {
        if (widthDp <= 0f || heightDp <= 0f) return MIN_COUNT
        return (REFERENCE_COUNT * (widthDp * heightDp) / REFERENCE_AREA).roundToInt().coerceIn(MIN_COUNT, MAX_COUNT)
    }

    /**
     * Las primeras [count] estrellas de la secuencia de [seed]. Determinista: con la misma semilla, `generate(n)` es un prefijo de
     * `generate(m)` para n ≤ m.
     */
    fun generate(count: Int, seed: Int = DEFAULT_SEED): List<Star> {
        val rng = Mulberry32(seed)
        return List(count.coerceAtLeast(0)) { i ->
            // Se consumen siempre los mismos números por estrella, para que la secuencia no dependa de los resultados.
            val x = rng.next()
            val y = rng.next()
            val radius = MIN_RADIUS + rng.next() * (MAX_RADIUS - MIN_RADIUS)
            val opacity = MIN_OPACITY + rng.next() * (MAX_OPACITY - MIN_OPACITY)
            val halo = rng.next() < HALO_CHANCE
            val period = MIN_PERIOD + rng.next() * (MAX_PERIOD - MIN_PERIOD)
            val phase = rng.next() * (2 * PI)
            Star(
                x = x.toFloat(), y = y.toFloat(), radius = radius.toFloat(), opacity = opacity.toFloat(), halo = halo,
                twinkle = i % TWINKLE_EVERY == 3, periodSeconds = period.toFloat(), phase = phase.toFloat(),
            )
        }
    }

    /**
     * Opacidad de [star] en el instante [seconds]. Sin parpadeo es la suya; con parpadeo oscila entre [TWINKLE_FLOOR] × su opacidad
     * y su opacidad, con una senoide de periodo [Star.periodSeconds] y fase [Star.phase].
     */
    fun opacityAt(star: Star, seconds: Double): Float {
        if (!star.twinkle) return star.opacity
        val wave = 0.5 + 0.5 * sin(2 * PI * seconds / star.periodSeconds + star.phase)
        return (star.opacity * (TWINKLE_FLOOR + (1 - TWINKLE_FLOOR) * wave)).toFloat()
    }

    /** mulberry32: generador de 32 bits pequeño y con buena distribución; devuelve números en [0, 1). */
    private class Mulberry32(seed: Int) {
        private var a = seed

        fun next(): Double {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }
}
