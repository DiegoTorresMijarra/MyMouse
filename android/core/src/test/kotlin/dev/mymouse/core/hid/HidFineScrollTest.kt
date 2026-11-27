package dev.mymouse.core.hid

import dev.mymouse.core.bluetooth.BluetoothDiagnostics
import dev.mymouse.core.bluetooth.BluetoothFacts
import dev.mymouse.core.gesture.OutputAction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HidDescriptorTest {
    private fun ints(bytes: ByteArray) = bytes.map { it.toInt() and 0xFF }

    /** Los bytes del descriptor de la v4 (sin scroll fino): el ajuste "Scroll fino por Bluetooth" apagado debe registrar exactamente este. */
    private val v4Hex = "05010902A10185010901A1000509190129051500250195057501810295017503810105010930093109381581257F750895038106050C0A38021581257F750895018106C0C005010906A1018502050719E029E71500250175019508810295017508810195067508150025650507190029658100C0050C0901A1018503150026FF0319002AFF03751095018100C0"

    /** El informe de salida de LEDs que la v6 añade al teclado (justo antes de cerrar su colección). */
    private val ledHex = "050819012905950575019102950175039101"

    @Test
    fun legacyDescriptorIsTheOneFromV4PlusTheKeyboardLedReport() {
        val keysEnd = "0507190029658100"
        assertTrue(v4Hex.contains(keysEnd + "C0"))
        val expected = v4Hex.replace(keysEnd + "C0", keysEnd + ledHex + "C0").chunked(2).map { it.toInt(16) }
        assertEquals(expected, ints(HidDescriptor.build(fineScroll = false)))
        assertContentEquals(HidDescriptor.build(false), HidMapper.DESCRIPTOR)
    }

    /** Recorre el descriptor por elementos (prefijo + datos) y cuenta aperturas y cierres de colección. */
    private class Walk(bytes: List<Int>) {
        var opens = 0
        var closes = 0
        val items = mutableListOf<Pair<Int, List<Int>>>() // (prefijo sin tamaño, datos)

        init {
            var i = 0
            while (i < bytes.size) {
                val prefix = bytes[i]
                val size = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
                val tag = prefix and 0xFC
                val data = bytes.subList(i + 1, i + 1 + size)
                check(i + 1 + size <= bytes.size) { "elemento cortado en $i" }
                if (tag == 0xA0) opens++
                if (tag == 0xC0) closes++
                items += tag to data
                i += 1 + size
            }
        }
    }

    @Test
    fun bothDescriptorsAreWellFormedAndBalanced() {
        for (fine in listOf(false, true)) {
            val w = Walk(ints(HidDescriptor.build(fine)))
            assertEquals(w.opens, w.closes, "colecciones (fine=$fine)")
        }
        // Antiguo: ratón (aplicación + física), teclado y consumo. Con multiplicador: dos colecciones lógicas más.
        assertEquals(4, Walk(ints(HidDescriptor.build(false))).opens)
        assertEquals(6, Walk(ints(HidDescriptor.build(true))).opens)
    }

    @Test
    fun fineDescriptorDeclaresTwoResolutionMultiplierFeatures() {
        val w = Walk(ints(HidDescriptor.build(true)))
        val features = w.items.filter { it.first == 0xB0 }
        // Dos multiplicadores de 2 bits (datos 0x02 = datos, variable, absoluto) y el relleno constante de 4 bits (0x03).
        assertEquals(listOf(0x02, 0x02, 0x03), features.map { it.second.single() })
        // Uso 0x48 (Resolution Multiplier) dos veces, físico 1–120.
        assertEquals(2, w.items.count { it.first == 0x08 && it.second == listOf(0x48) })
        assertEquals(2, w.items.count { it.first == 0x44 && it.second == listOf(120) })
        assertEquals(2, w.items.count { it.first == 0x34 && it.second == listOf(1) })
        // Los 2 + 2 + 4 bits del feature suman un byte.
        val sizes = w.items.withIndex().filter { it.value.first == 0x74 }.map { it.value.second.single() }
        assertTrue(sizes.contains(2))
        assertTrue(sizes.contains(4))
    }

    @Test
    fun keyboardDeclaresTheLedOutputReportInOneByte() {
        for (fine in listOf(false, true)) {
            val w = Walk(ints(HidDescriptor.build(fine)))
            // Los elementos del teclado: entre su id (2) y el id del consumo (3).
            val from = w.items.indexOfFirst { it.first == 0x84 && it.second == listOf(HidMapper.KEYBOARD_ID) }
            val to = w.items.indexOfFirst { it.first == 0x84 && it.second == listOf(HidMapper.CONSUMER_ID) }
            val kb = w.items.subList(from, to)
            // Página de LEDs, usos 1–5, cinco campos de 1 bit de salida (0x91 → etiqueta 0x90, datos 0x02) y 3 bits de relleno constante.
            assertTrue(kb.contains(0x04 to listOf(0x08)), "página de LEDs (fine=$fine)")
            val outputs = kb.filter { it.first == 0x90 }
            assertEquals(listOf(0x02, 0x01), outputs.map { it.second.single() })
            val bits = kb.withIndex().filter { it.value.first == 0x74 }.map { it.value.second.single() }
            // Entrada: 8×1 (modificadores), 1×8 (reservado), 6×8 (teclas); salida: 5×1 y 1×3.
            assertEquals(listOf(1, 8, 8, 1, 3), bits)
            val counts = kb.filter { it.first == 0x94 }.map { it.second.single() }
            assertEquals(listOf(8, 1, 6, 5, 1), counts)
            // 5 + 3 bits = un byte (el informe de salida sin el id).
            assertEquals(HidDescriptor.LED_REPORT_SIZE * 8, 5 * 1 + 1 * 3)
        }
    }

    @Test
    fun fineDescriptorKeepsTheFiveByteInputReport() {
        // Botones (5 + 3 de relleno) + X + Y + rueda + pan = 5 bytes, igual que el antiguo: HidMapper no cambia el formato.
        val w = Walk(ints(HidDescriptor.build(true)))
        val inputs = w.items.takeWhile { !(it.first == 0x84 && it.second == listOf(HidMapper.KEYBOARD_ID)) }.filter { it.first == 0x80 }
        // botones, relleno, X/Y (2 campos), rueda, pan
        assertEquals(5, inputs.size)
        assertEquals(listOf(0x02, 0x01, 0x06, 0x06, 0x06), inputs.map { it.second.single() })
        // Cada eje es de 8 bits con signo, de −127 a 127.
        assertEquals(3, w.items.count { it.first == 0x14 && it.second == listOf(0x81) })
        // El informe feature lleva el id del ratón: es el único id antes del teclado.
        assertEquals(listOf(HidMapper.MOUSE_ID), w.items.filter { it.first == 0x84 }.map { it.second.single() }.takeWhile { it != HidMapper.KEYBOARD_ID })
    }
}

class ScrollResolutionTest {
    @Test
    fun startsWithWholeSteps() {
        val r = ScrollResolution()
        assertEquals(1, r.wheelMultiplier)
        assertEquals(1, r.panMultiplier)
        assertFalse(r.fine)
        assertContentEquals(byteArrayOf(0), r.featureData())
    }

    @Test
    fun hostEnablesBothMultipliers() {
        val r = ScrollResolution()
        assertTrue(r.applyFeature(byteArrayOf(0b0101)))
        assertEquals(120, r.wheelMultiplier)
        assertEquals(120, r.panMultiplier)
        assertTrue(r.fine)
        assertContentEquals(byteArrayOf(0b0101), r.featureData())
        // Lo mismo otra vez no es un cambio.
        assertFalse(r.applyFeature(byteArrayOf(0b0101)))
    }

    @Test
    fun axesAreIndependent() {
        val r = ScrollResolution()
        r.applyFeature(byteArrayOf(0b0001))
        assertEquals(120, r.wheelMultiplier)
        assertEquals(1, r.panMultiplier)
        r.applyFeature(byteArrayOf(0b0100))
        assertEquals(1, r.wheelMultiplier)
        assertEquals(120, r.panMultiplier)
        assertFalse(r.fine)
    }

    @Test
    fun acceptsTheReportIdInFrontAndRejectsGarbage() {
        val r = ScrollResolution()
        assertTrue(r.applyFeature(byteArrayOf(HidMapper.MOUSE_ID.toByte(), 0b0101)))
        assertTrue(r.fine)
        assertFalse(r.applyFeature(byteArrayOf()))
        assertFalse(r.applyFeature(byteArrayOf(2, 0)))
        assertFalse(r.applyFeature(byteArrayOf(1, 2, 3)))
        assertTrue(r.fine) // nada de eso lo ha cambiado
        // Valores no definidos (lógico 2 o 3) se tratan como "sin multiplicador".
        assertTrue(r.applyFeature(byteArrayOf(0b1010)))
        assertFalse(r.fine)
    }

    @Test
    fun resetGoesBackToWholeSteps() {
        val r = ScrollResolution()
        r.applyFeature(byteArrayOf(0b0101))
        assertTrue(r.reset())
        assertFalse(r.fine)
        assertFalse(r.reset())
    }
}

class HidFineScrollMapperTest {
    private fun mouse(b: Int, x: Int, y: Int, w: Int = 0, p: Int = 0) =
        HidReport(1, byteArrayOf(b.toByte(), x.toByte(), y.toByte(), w.toByte(), p.toByte()))

    private fun key(modifiers: Int, code: Int = 0) =
        HidReport(2, byteArrayOf(modifiers.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0))

    private fun fineMapper() = HidMapper().also { assertTrue(it.applyScrollFeature(byteArrayOf(0b0101))) }

    @Test
    fun withoutTheMultiplierScrollIsWholeSteps() {
        val m = HidMapper()
        assertEquals(emptyList(), m.map(OutputAction.Scroll(50, 0)))
        assertEquals(listOf(mouse(0, 0, 0, 1)), m.map(OutputAction.Scroll(70, 0)))
    }

    @Test
    fun withTheMultiplierScrollGoesOutInFineUnits() {
        val m = fineMapper()
        assertEquals(listOf(mouse(0, 0, 0, 7, -3)), m.map(OutputAction.Scroll(7, -3)))
        assertEquals(listOf(mouse(0, 0, 0, 120, 0)), m.map(OutputAction.Scroll(120, 0)))
        // Más de 127 unidades se trocea.
        assertEquals(listOf(mouse(0, 0, 0, 127), mouse(0, 0, 0, 73)), m.map(OutputAction.Scroll(200, 0)))
        assertEquals(listOf(mouse(0, 0, 0, -127), mouse(0, 0, 0, -23)), m.map(OutputAction.Scroll(-150, 0)))
    }

    @Test
    fun onlyTheEnabledAxisIsFine() {
        val m = HidMapper()
        m.applyScrollFeature(byteArrayOf(0b0001)) // solo la rueda vertical
        // La horizontal sigue en pasos enteros: 60 + 60 = 1 paso.
        assertEquals(listOf(mouse(0, 0, 0, 5)), m.map(OutputAction.Scroll(5, 60)))
        assertEquals(listOf(mouse(0, 0, 0, 5, 1)), m.map(OutputAction.Scroll(5, 60)))
    }

    @Test
    fun changingTheMultiplierDropsTheOldRemainder() {
        val m = HidMapper()
        assertEquals(emptyList(), m.map(OutputAction.Scroll(100, 0))) // 100/120 de paso pendientes
        m.applyScrollFeature(byteArrayOf(0b0101))
        assertEquals(listOf(mouse(0, 0, 0, 20)), m.map(OutputAction.Scroll(20, 0))) // sin el resto de antes
        m.resetScrollResolution()
        assertEquals(emptyList(), m.map(OutputAction.Scroll(100, 0)))
        assertEquals(listOf(mouse(0, 0, 0, 1)), m.map(OutputAction.Scroll(20, 0)))
    }

    @Test
    fun fineZoomIsCtrlHeldPlusFineWheelUnits() {
        val m = fineMapper()
        // Primer zoom: Ctrl pulsado y la rueda. Los siguientes solo la rueda.
        assertEquals(listOf(key(1), mouse(0, 0, 0, 9)), m.map(OutputAction.Zoom(9)))
        assertEquals(listOf(mouse(0, 0, 0, -4)), m.map(OutputAction.Zoom(-4)))
        assertTrue(m.zoomHeld)
        assertEquals(listOf(key(0)), m.endZoom())
        assertFalse(m.zoomHeld)
        assertEquals(emptyList(), m.endZoom())
        // Otra vez: se vuelve a pulsar Ctrl.
        assertEquals(listOf(key(1), mouse(0, 0, 0, 3)), m.map(OutputAction.Zoom(3)))
    }

    @Test
    fun anythingElseReleasesTheHeldCtrlFirst() {
        val m = fineMapper()
        m.map(OutputAction.Zoom(30))
        assertEquals(listOf(key(0), mouse(0, 0, 0, 6)), m.map(OutputAction.Scroll(6, 0)))
        m.map(OutputAction.Zoom(30))
        assertEquals(listOf(key(0), mouse(0, 5, 5)), m.map(OutputAction.PointerMove(5, 5)))
        m.map(OutputAction.Zoom(30))
        assertEquals(listOf(key(0)) + m.consumer(ConsumerKey.MUTE), m.action(dev.mymouse.core.btactions.BtAction.Media(ConsumerKey.MUTE), dev.mymouse.core.btactions.KeyboardLayouts.ES))
    }

    @Test
    fun releaseAllForgetsTheHeldCtrl() {
        val m = fineMapper()
        m.map(OutputAction.Zoom(30))
        m.releaseAll()
        assertFalse(m.zoomHeld)
        assertEquals(emptyList(), m.endZoom())
    }

    @Test
    fun coalescerSumsFineUnitsAndSplitsAt127() {
        val c = HidMotionCoalescer()
        val m = fineMapper()
        for (i in 1..30) m.map(OutputAction.Scroll(10, 0)).forEach { c.offer(it) }
        // 300 unidades finas = 2,5 pasos: salen en tres informes de ≤127.
        val ticks = generateSequence { c.tick() }.map { it.data[3].toInt() }.toList()
        assertEquals(listOf(127, 127, 46), ticks)
    }

    @Test
    fun coalescerDropsPendingScrollButKeepsMotion() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 5, 3, 40, 10))
        c.dropScroll()
        val r = c.tick()!!
        assertEquals(5, r.data[1].toInt())
        assertEquals(3, r.data[2].toInt())
        assertEquals(0, r.data[3].toInt())
        assertEquals(0, r.data[4].toInt())
    }

    @Test
    fun diagnosticsLineSaysIfThePcEnabledFineScroll() {
        assertEquals("Scroll fino: activado por el PC", BluetoothDiagnostics.fineScrollLine(BluetoothFacts(fineScrollActive = true)))
        assertEquals(
            "Scroll fino: no (el PC no lo ha activado; se usan pasos enteros)",
            BluetoothDiagnostics.fineScrollLine(BluetoothFacts(fineScrollActive = false)),
        )
        assertTrue(BluetoothDiagnostics.fineScrollLine(BluetoothFacts(fineScrollEnabled = false, fineScrollActive = true)).startsWith("Scroll fino: no"))
        assertTrue(BluetoothDiagnostics.report(BluetoothFacts(fineScrollActive = true), emptyList()).contains("Scroll fino: activado por el PC"))
    }
}
