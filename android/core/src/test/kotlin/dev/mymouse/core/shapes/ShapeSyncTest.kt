package dev.mymouse.core.shapes

import dev.mymouse.core.protocol.Message
import dev.mymouse.core.protocol.Protocol
import dev.mymouse.core.protocol.ShapeStatus
import dev.mymouse.core.protocol.StrokePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ShapeSyncTest {
    private fun wire(points: List<P>): List<StrokePoint> = ShapeSync.toWire(points)

    /** Una espiral: no se parece a nada del catálogo. */
    private val w = List(60) { i ->
        val t = i / 59.0 * 3.5 * Math.PI
        val r = 0.1 + 0.4 * i / 59.0
        P((0.5 + r * Math.cos(t)).toFloat(), (0.5 + r * Math.sin(t)).toFloat())
    }
    private val wSamples = List(3) { i -> wire(Geometry.resample(w, 40).map { P(it.x * (300 + i * 10), it.y * 300) }) }

    @Test
    fun clashLogicFindsLookalikesAndIgnoresItself() {
        val lib = ShapeLibrary()
        // Una "Z" grabada con otro nombre se confunde con la "z" del catálogo.
        val z = ShapeCatalog.BY_ID.getValue("z").points.map { P(it.x * 300, it.y * 300) }
        assertEquals(listOf("z"), lib.clashes("custom:zeta", List(3) { z }, minSizePx = 40f))
        // Una forma que ya existe no se confunde consigo misma.
        val withW = lib.withRecorded("custom:w", List(3) { w.map { p -> P(p.x * 300, p.y * 300) } })
        assertEquals(emptyList(), withW.clashes("custom:w", withW.recorded[0].samples, 40f))
        // Pero una copia con otro nombre sí.
        assertEquals(listOf("custom:w"), withW.clashes("custom:otra", withW.recorded[0].samples, 40f))
    }

    @Test
    fun listHasOneInfoPerCustomShapeAndTheCount() {
        val lib = ShapeLibrary()
            .withRecorded("circle", listOf(listOf(P(0f, 0f), P(10f, 10f)))) // versión propia del catálogo: no se envía
            .withRecorded("custom:a", listOf(listOf(P(0f, 0f), P(100f, 50f), P(200f, 0f))))
            .withRecorded("custom:b", listOf(listOf(P(0f, 0f), P(100f, 100f))))
        val msgs = ShapeSync.listMessages(lib)
        assertEquals(3, msgs.size)
        assertEquals(listOf("custom:a", "custom:b"), msgs.filterIsInstance<Message.ShapeInfo>().map { it.name })
        assertEquals(Message.ShapeListEnd(2), msgs.last())
        assertEquals(listOf(Message.ShapeListEnd(0)), ShapeSync.listMessages(ShapeLibrary()))
    }

    @Test
    fun infoResamplesTo32PointsAndKeepsProportions() {
        val info = assertNotNull(ShapeSync.info(RecordedShape("custom:x", listOf(listOf(P(10f, 10f), P(210f, 10f), P(210f, 110f))))))
        val s = info.samples.single()
        assertEquals(Protocol.SHAPE_SAMPLE_POINTS, s.size)
        assertEquals(StrokePoint(0, 0), s.first())
        // Ancho 200 y alto 100: el lado largo ocupa todo el rango y el corto, la mitad.
        assertEquals(65535, s.maxOf { it.x })
        assertTrue(s.maxOf { it.y } in 32700..32800)
    }

    @Test
    fun shapesWithoutUsableSamplesAreNotSent() {
        assertEquals(null, ShapeSync.info(RecordedShape("custom:x", listOf(listOf(P(1f, 1f))))))
    }

    @Test
    fun addSavesANewShape() {
        val out = ShapeSync.applyAdd(ShapeLibrary(), Message.ShapeAdd("custom:w", wSamples, force = false))
        assertEquals(Message.ShapeResult("custom:w", ShapeStatus.SAVED, ""), out.result)
        assertTrue(out.changed)
        assertEquals(listOf("custom:w"), out.library.custom.map { it.name })
        assertTrue("custom:w" in out.library.enabled)
        assertEquals(3, out.library.custom[0].samples.size)
    }

    @Test
    fun addReportsClashUnlessForced() {
        val zWire = List(3) { wire(ShapeCatalog.BY_ID.getValue("z").points.map { p -> P(p.x * 300, p.y * 300) }) }
        val lib = ShapeLibrary()
        val clash = ShapeSync.applyAdd(lib, Message.ShapeAdd("custom:zeta", zWire, force = false))
        assertEquals(Message.ShapeResult("custom:zeta", ShapeStatus.CLASHES, "z"), clash.result)
        assertFalse(clash.changed)
        assertEquals(lib, clash.library)

        val forced = ShapeSync.applyAdd(lib, Message.ShapeAdd("custom:zeta", zWire, force = true))
        assertEquals(ShapeStatus.SAVED, forced.result.status)
        assertEquals(listOf("custom:zeta"), forced.library.custom.map { it.name })
    }

    @Test
    fun addRejectsDuplicatesAndInvalidShapes() {
        val lib = ShapeSync.applyAdd(ShapeLibrary(), Message.ShapeAdd("custom:w", wSamples, false)).library
        assertEquals(ShapeStatus.DUPLICATE_NAME, ShapeSync.applyAdd(lib, Message.ShapeAdd("custom:w", wSamples, true)).result.status)
        for (bad in listOf(
            Message.ShapeAdd("w", wSamples, false), // sin prefijo
            Message.ShapeAdd("custom:", wSamples, false), // sin nombre
            Message.ShapeAdd("custom:" + "x".repeat(60), wSamples, false), // más de 64 bytes
            Message.ShapeAdd("custom:v", emptyList(), false), // sin muestras
            Message.ShapeAdd("custom:v", listOf(listOf(StrokePoint(1, 1))), false), // un solo punto
            Message.ShapeAdd("custom:v", listOf(listOf(StrokePoint(5, 5), StrokePoint(9, 9))), false), // diminuta
        )) {
            assertEquals(ShapeStatus.INVALID, ShapeSync.applyAdd(lib, bad).result.status, bad.name)
        }
    }

    @Test
    fun deleteRemovesOnlyCustomShapes() {
        val lib = ShapeSync.applyAdd(ShapeLibrary(), Message.ShapeAdd("custom:w", wSamples, false)).library
        val deleted = ShapeSync.applyDelete(lib, Message.ShapeDelete("custom:w"))
        assertEquals(Message.ShapeResult("custom:w", ShapeStatus.DELETED, ""), deleted.result)
        assertEquals(emptyList(), deleted.library.custom)
        assertEquals(ShapeStatus.NOT_FOUND, ShapeSync.applyDelete(lib, Message.ShapeDelete("custom:nada")).result.status)
        assertEquals(ShapeStatus.NOT_FOUND, ShapeSync.applyDelete(lib, Message.ShapeDelete("circle")).result.status)
    }

    @Test
    fun savedShapeIsRecognizedFromAHandDrawnStroke() {
        val lib = ShapeSync.applyAdd(ShapeLibrary(), Message.ShapeAdd("custom:w", wSamples, false)).library
        val r = lib.recognizer(minSizePx = 40f)
        val stroke = Geometry.resample(w, 50).map { P(100 + it.x * 280, 100 + it.y * 280) }
        val result = r.recognize(stroke)
        assertEquals("custom:w", (result as? Recognizer.Result.Match)?.name, result.toString())
    }
}
