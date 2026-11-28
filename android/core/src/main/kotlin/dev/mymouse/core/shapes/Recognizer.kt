package dev.mymouse.core.shapes

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/** Por qué no se reconoció (o se canceló) un trazo: un código, no un texto; la app lo escribe en el idioma del móvil. */
enum class RejectReason { EMPTY_STROKE, TOO_SMALL, NO_ACTIVE_SHAPES, NOT_RECOGNIZED, AMBIGUOUS, TIMEOUT, CANCELLED, NO_RECOGNIZER }

/**
 * Reconocedor de trazos de un solo dedo (docs/spec-v1-gestos.md §4):
 * 1. Descarta trazos diminutos (toques).
 * 2. Líneas casi rectas: por geometría, con su sentido.
 * 3. El resto: Protractor sensible a la rotación (solo tolera ±[maxRotationDeg]), contra las plantillas activas.
 * 4. Rechaza si la mejor no llega a [minScore] o no saca [minMargin] a la segunda.
 */
class Recognizer(
    templates: List<NamedTemplate>,
    private val enabled: Set<String>,
    private val config: Config = Config(),
) {
    data class Config(
        /** Por debajo de este tamaño (ancho y alto) el trazo es un toque. */
        val minSizePx: Float = 40f,
        /** Recorrido / distancia entre extremos por debajo de la cual el trazo es una línea. */
        val lineStraightness: Float = 1.15f,
        val minScore: Float = 0.85f,
        val minMargin: Float = 0.02f,
        val maxRotationDeg: Double = 30.0,
    )

    /** Una plantilla: un trazo de referencia con nombre. Varias plantillas pueden compartir nombre. */
    data class NamedTemplate(val name: String, val points: List<P>)

    sealed interface Result {
        data class Match(val name: String, val score: Float) : Result
        data class NoMatch(val reason: RejectReason, val best: String? = null, val score: Float = 0f, val second: String? = null) : Result
    }

    private class Prepared(val name: String, val vector: FloatArray)

    private val prepared: List<Prepared> = templates
        .filter { it.name in enabled && it.name !in ShapeCatalog.LINES && it.points.size >= 2 }
        .map { Prepared(it.name, Geometry.vectorize(Geometry.resample(it.points, N))) }

    fun recognize(stroke: List<P>): Result {
        if (stroke.size < 2) return Result.NoMatch(RejectReason.EMPTY_STROKE)
        val b = Geometry.bounds(stroke)
        if (b.width < config.minSizePx && b.height < config.minSizePx) return Result.NoMatch(RejectReason.TOO_SMALL)

        line(stroke)?.let { return it }

        if (prepared.isEmpty()) return Result.NoMatch(RejectReason.NO_ACTIVE_SHAPES)
        val v = Geometry.vectorize(Geometry.resample(stroke, N))
        val bestByName = HashMap<String, Float>()
        for (t in prepared) {
            val s = similarity(t.vector, v)
            if (s > (bestByName[t.name] ?: -1f)) bestByName[t.name] = s
        }
        val ranked = bestByName.entries.sortedByDescending { it.value }
        val best = ranked[0]

        // Círculo, triángulo y cuadrado se parecen mucho para Protractor: se decide por el número de esquinas.
        if (best.key in CLOSED_BASIC && isClosed(stroke, b)) {
            val byCorners = when (corners(stroke)) {
                0, 1 -> "circle"
                3 -> "triangle"
                4, 5 -> "square"
                else -> null
            }
            val score = byCorners?.let { bestByName[it] }
            if (byCorners != null && score != null && score >= config.minScore) return Result.Match(byCorners, score)
        }

        val second = ranked.getOrNull(1)?.value ?: -1f
        return when {
            best.value < config.minScore -> Result.NoMatch(RejectReason.NOT_RECOGNIZED, best.key, best.value)
            best.value - second < config.minMargin -> Result.NoMatch(RejectReason.AMBIGUOUS, best.key, best.value, ranked[1].key)
            else -> Result.Match(best.key, best.value)
        }
    }

    private fun isClosed(stroke: List<P>, b: Geometry.Bounds): Boolean =
        Geometry.dist(stroke.first(), stroke.last()) < 0.3f * maxOf(b.width, b.height)

    /** Esquinas de un trazo cerrado: grupos de puntos donde la dirección gira más de [CORNER_DEG]. */
    private fun corners(stroke: List<P>): Int {
        val pts = Geometry.resample(stroke, CORNER_POINTS).dropLast(1) // cerrado: el último ≈ el primero
        val n = pts.size
        val sharp = BooleanArray(n) { i ->
            val a = pts[(i - CORNER_SPAN + n) % n]
            val p = pts[i]
            val c = pts[(i + CORNER_SPAN) % n]
            val turn = Math.toDegrees(
                Math.atan2((c.y - p.y).toDouble(), (c.x - p.x).toDouble()) -
                    Math.atan2((p.y - a.y).toDouble(), (p.x - a.x).toDouble()),
            )
            val normalized = ((turn % 360) + 540) % 360 - 180 // a (-180, 180]
            abs(normalized) > CORNER_DEG
        }
        if (sharp.all { it }) return 0 // todo "esquina": sin estructura reconocible
        // Contar grupos de puntos seguidos (de forma circular).
        val start = sharp.indexOfFirst { !it }
        var groups = 0
        var inGroup = false
        for (k in 1..n) {
            val s = sharp[(start + k) % n]
            if (s && !inGroup) groups++
            inGroup = s
        }
        return groups
    }

    private fun line(stroke: List<P>): Result? {
        val ends = Geometry.dist(stroke.first(), stroke.last())
        // Remuestrear a pocos puntos suaviza el temblor del dedo, que alarga el recorrido sin cambiar la forma.
        val smooth = Geometry.resample(stroke, LINE_POINTS)
        val b = Geometry.bounds(stroke)
        val thin = minOf(b.width, b.height) < LINE_THINNESS * maxOf(b.width, b.height) // horizontal o vertical con temblor
        val straight = Geometry.pathLength(smooth) / ends <= config.lineStraightness
        if (ends == 0f || ends < 0.8f * maxOf(b.width, b.height) || !(straight || thin)) return null
        val dx = stroke.last().x - stroke.first().x
        val dy = stroke.last().y - stroke.first().y
        val name = if (abs(dx) >= abs(dy)) {
            if (dx > 0) ShapeCatalog.LINE_RIGHT else ShapeCatalog.LINE_LEFT
        } else {
            if (dy > 0) ShapeCatalog.LINE_DOWN else ShapeCatalog.LINE_UP
        }
        // Una línea con la dirección desactivada se intenta con las plantillas (p. ej. una "l" muy recta).
        return if (name in enabled) Result.Match(name, 1f) else null
    }

    /** Protractor: coseno con la rotación óptima, limitada a ±maxRotationDeg. */
    private fun similarity(t: FloatArray, v: FloatArray): Float {
        var a = 0.0
        var b = 0.0
        var i = 0
        while (i < t.size) {
            a += t[i] * v[i] + t[i + 1] * v[i + 1]
            b += t[i] * v[i + 1] - t[i + 1] * v[i]
            i += 2
        }
        val limit = config.maxRotationDeg * PI / 180
        val angle = if (a == 0.0) PI / 2 else atan(b / a)
        val clamped = angle.coerceIn(-limit, limit)
        return (a * cos(clamped) + b * sin(clamped)).toFloat()
    }

    companion object {
        const val N = 64
        private const val LINE_POINTS = 12
        private const val LINE_THINNESS = 0.2f
        private const val CORNER_POINTS = 41
        private const val CORNER_SPAN = 2
        private const val CORNER_DEG = 60.0
        private val CLOSED_BASIC = setOf("circle", "triangle", "square")

        /** Plantillas del catálogo, con variantes: sentido inverso y, en las cerradas, varios puntos de inicio. */
        fun catalogTemplates(): List<NamedTemplate> = ShapeCatalog.ALL
            .filter { it.id !in ShapeCatalog.LINES }
            .flatMap { variants(it) }

        fun variants(shape: ShapeCatalog.Shape): List<NamedTemplate> {
            val base = Geometry.resample(shape.points, N)
            val out = mutableListOf(base, base.reversed())
            if (shape.closed) {
                val ring = base.dropLast(1) // el último punto repite el primero
                for (k in 1 until CLOSED_STARTS) {
                    val shift = ring.size * k / CLOSED_STARTS
                    val rotated = ring.drop(shift) + ring.take(shift)
                    val closedLoop = rotated + rotated.first()
                    out += closedLoop
                    out += closedLoop.reversed()
                }
            }
            return out.map { NamedTemplate(shape.id, it) }
        }

        private const val CLOSED_STARTS = 8
    }
}
