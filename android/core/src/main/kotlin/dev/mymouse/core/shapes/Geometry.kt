package dev.mymouse.core.shapes

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class P(val x: Float, val y: Float)

internal object Geometry {
    fun pathLength(points: List<P>): Float =
        points.zipWithNext { a, b -> dist(a, b) }.sum()

    fun dist(a: P, b: P): Float = hypot(a.x - b.x, a.y - b.y)

    /** Remuestrea el trazo a [n] puntos equidistantes a lo largo del recorrido. */
    fun resample(points: List<P>, n: Int): List<P> {
        require(points.isNotEmpty())
        if (points.size == 1) return List(n) { points[0] }
        val interval = pathLength(points) / (n - 1)
        if (interval == 0f) return List(n) { points[0] }
        val src = points.toMutableList()
        val out = mutableListOf(src[0])
        var acc = 0f
        var i = 1
        while (i < src.size) {
            val a = src[i - 1]
            val b = src[i]
            val d = dist(a, b)
            if (acc + d >= interval && d > 0f) {
                val t = (interval - acc) / d
                val q = P(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                out += q
                src.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        while (out.size < n) out += src.last()
        return out.take(n)
    }

    fun centroid(points: List<P>) = P(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())

    fun translateToOrigin(points: List<P>): List<P> {
        val c = centroid(points)
        return points.map { P(it.x - c.x, it.y - c.y) }
    }

    fun rotate(points: List<P>, radians: Double): List<P> {
        val c = centroid(points)
        val cs = cos(radians).toFloat()
        val sn = sin(radians).toFloat()
        return points.map {
            val dx = it.x - c.x
            val dy = it.y - c.y
            P(c.x + dx * cs - dy * sn, c.y + dx * sn + dy * cs)
        }
    }

    /** Vector normalizado (Protractor): puntos centrados, concatenados y con norma 1. */
    fun vectorize(points: List<P>): FloatArray {
        val centered = translateToOrigin(points)
        val v = FloatArray(centered.size * 2)
        var sum = 0.0
        centered.forEachIndexed { i, p ->
            v[2 * i] = p.x
            v[2 * i + 1] = p.y
            sum += p.x * p.x + p.y * p.y
        }
        val norm = Math.sqrt(sum).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val width get() = maxX - minX
        val height get() = maxY - minY
    }

    fun bounds(points: List<P>) = Bounds(
        points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
    )
}
