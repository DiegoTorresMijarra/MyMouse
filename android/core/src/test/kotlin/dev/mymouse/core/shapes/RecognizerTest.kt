package dev.mymouse.core.shapes

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecognizerTest {
    private val all = ShapeCatalog.IDS.toSet()
    private val recognizer = Recognizer(Recognizer.catalogTemplates(), all)

    /** Trazo "a mano": la forma de referencia escalada, girada un poco, con ruido y empezando donde toque. */
    private fun handDrawn(
        shape: ShapeCatalog.Shape, random: Random, size: Float = 300f,
        rotationDeg: Double = 0.0, noise: Float = 0.03f, startShift: Int = 0, aspect: Float = 1f,
    ): List<P> {
        var pts = Geometry.resample(shape.points, 48).map { P(100 + it.x * size * aspect, 200 + it.y * size) }
        if (shape.closed && startShift > 0) {
            val ring = pts.dropLast(1)
            val s = startShift % ring.size
            pts = ring.drop(s) + ring.take(s) + ring.drop(s).first()
        }
        pts = Geometry.rotate(pts, Math.toRadians(rotationDeg))
        // Temblor del dedo: ondulación suave (amplitud [noise] × tamaño) más un poco de ruido punto a punto.
        val fx = random.nextDouble(1.0, 3.0); val px = random.nextDouble(0.0, 6.3)
        val fy = random.nextDouble(1.0, 3.0); val py = random.nextDouble(0.0, 6.3)
        return pts.mapIndexed { i, p ->
            val t = i.toDouble() / pts.size * 2 * Math.PI
            P(
                p.x + (noise * size * Math.sin(fx * t + px)).toFloat() + (random.nextFloat() - 0.5f) * 0.01f * size,
                p.y + (noise * size * Math.sin(fy * t + py)).toFloat() + (random.nextFloat() - 0.5f) * 0.01f * size,
            )
        }
    }

    @Test
    fun everyCatalogShapeIsRecognized() {
        val random = Random(42)
        val failures = mutableListOf<String>()
        for (shape in ShapeCatalog.ALL) {
            repeat(20) { i ->
                val stroke = handDrawn(
                    shape, random,
                    size = 150f + random.nextFloat() * 300f,
                    rotationDeg = random.nextDouble(-12.0, 12.0),
                    startShift = if (shape.closed) random.nextInt(48) else 0,
                    aspect = 0.8f + random.nextFloat() * 0.4f,
                )
                val result = recognizer.recognize(if (shape.closed && i % 2 == 1) stroke.reversed() else stroke)
                if ((result as? Recognizer.Result.Match)?.name != shape.id) failures += "${shape.id} → $result"
            }
        }
        // Objetivo: ≥ 90 % de aciertos. Con trazos sintéticos se exige más.
        val total = ShapeCatalog.ALL.size * 20
        val summary = failures.groupingBy { it.substringBefore(" →") + " → " + (Regex("name=(\\w[\\w-]*)|best=(\\w[\\w-]*)|reason=([^,]*)").find(it.substringAfter("→"))?.value ?: "?") }.eachCount()
        assertTrue(failures.size <= total * 0.03, "Fallos (${failures.size}/$total): $summary")
    }

    @Test
    fun tinyStrokeIsATap() {
        val result = recognizer.recognize(listOf(P(0f, 0f), P(10f, 5f), P(12f, 8f)))
        assertIs<Recognizer.Result.NoMatch>(result)
    }

    @Test
    fun linesKeepTheirDirection() {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) = List(20) { i ->
            val t = i / 19f
            P(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t + if (i % 2 == 0) 2f else -2f)
        }
        assertEquals(Recognizer.Result.Match("line-right", 1f), recognizer.recognize(line(0f, 0f, 300f, 20f)))
        assertEquals(Recognizer.Result.Match("line-left", 1f), recognizer.recognize(line(300f, 0f, 0f, -30f)))
        assertEquals(Recognizer.Result.Match("line-down", 1f), recognizer.recognize(line(0f, 0f, 10f, 300f)))
        assertEquals(Recognizer.Result.Match("line-up", 1f), recognizer.recognize(line(0f, 300f, 0f, 0f)))
    }

    @Test
    fun mirroredShapesAreDistinguished() {
        val random = Random(1)
        val lt = recognizer.recognize(handDrawn(ShapeCatalog.BY_ID.getValue("lt"), random))
        val gt = recognizer.recognize(handDrawn(ShapeCatalog.BY_ID.getValue("gt"), random))
        assertEquals("lt", (lt as Recognizer.Result.Match).name)
        assertEquals("gt", (gt as Recognizer.Result.Match).name)
    }

    @Test
    fun onlyEnabledShapesAreRecognized() {
        val onlyCircle = Recognizer(Recognizer.catalogTemplates(), setOf("circle"))
        val z = handDrawn(ShapeCatalog.BY_ID.getValue("z"), Random(3))
        val result = onlyCircle.recognize(z)
        assertIs<Recognizer.Result.NoMatch>(result)
    }

    @Test
    fun scribbleIsRejected() {
        val random = Random(7)
        var rejected = 0
        repeat(20) {
            val scribble = List(40) { P(random.nextFloat() * 300, random.nextFloat() * 300) }
            if (recognizer.recognize(scribble) is Recognizer.Result.NoMatch) rejected++
        }
        assertTrue(rejected >= 18, "Garabatos rechazados: $rejected/20")
    }
}
