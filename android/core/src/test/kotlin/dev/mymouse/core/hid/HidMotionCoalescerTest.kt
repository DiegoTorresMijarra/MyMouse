package dev.mymouse.core.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HidMotionCoalescerTest {
    private fun mouse(buttons: Int, x: Int, y: Int, wheel: Int = 0, pan: Int = 0) =
        HidReport(HidMapper.MOUSE_ID, byteArrayOf(buttons.toByte(), x.toByte(), y.toByte(), wheel.toByte(), pan.toByte()))

    private fun HidReport.dx() = data[1].toInt()
    private fun HidReport.dy() = data[2].toInt()

    @Test
    fun motionIsSummedIntoOneReportPerTick() {
        val c = HidMotionCoalescer()
        for (i in 1..10) assertTrue(c.offer(mouse(0, 3, -2)).isEmpty())
        val r = c.tick()!!
        assertEquals(30, r.dx())
        assertEquals(-20, r.dy())
        assertNull(c.tick())
    }

    @Test
    fun oppositeMovesCancelOut() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 5, 0))
        c.offer(mouse(0, -5, 0))
        assertFalse(c.hasPending)
        assertNull(c.tick())
    }

    @Test
    fun bigMotionIsChunkedAcrossTicks() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 127, 127))
        c.offer(mouse(0, 127, -127))
        c.offer(mouse(0, 46, 0))
        assertEquals(listOf(127, 127, 46), generateSequence { c.tick() }.map { it.dx() }.toList())
        assertFalse(c.hasPending)
    }

    @Test
    fun negativeChunks() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, -127, 0))
        c.offer(mouse(0, -100, 0))
        val ticks = generateSequence { c.tick() }.map { it.dx() }.toList()
        assertEquals(listOf(-127, -100), ticks)
    }

    @Test
    fun wheelAndPanAccumulate() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 0, 0, wheel = 1))
        c.offer(mouse(0, 0, 0, wheel = 2, pan = -1))
        val r = c.tick()!!
        assertEquals(3, r.data[3].toInt())
        assertEquals(-1, r.data[4].toInt())
    }

    @Test
    fun buttonChangeFlushesPendingMotionFirstWithTheOldButtons() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 10, 4))
        val out = c.offer(mouse(1, 0, 0)) // pulsar el botón izquierdo
        assertEquals(2, out.size)
        assertEquals(mouse(0, 10, 4), out[0]) // el movimiento llega antes y sin el botón
        assertEquals(mouse(1, 0, 0), out[1])
        assertFalse(c.hasPending)
    }

    @Test
    fun dragKeepsTheButtonInFollowingMotion() {
        val c = HidMotionCoalescer()
        c.offer(mouse(1, 0, 0))
        c.offer(mouse(1, 6, 0))
        val r = c.tick()!!
        assertEquals(1, r.data[0].toInt())
        assertEquals(6, r.dx())
        // Soltar: el movimiento sale con el botón pulsado y después la suelta.
        c.offer(mouse(1, 2, 0))
        val out = c.offer(mouse(0, 0, 0))
        assertEquals(listOf(mouse(1, 2, 0), mouse(0, 0, 0)), out)
    }

    @Test
    fun keyboardAndConsumerFlushMotionAndKeepOrder() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 7, 7))
        val key = HidReport(HidMapper.KEYBOARD_ID, ByteArray(8))
        assertEquals(listOf(mouse(0, 7, 7), key), c.offer(key))
        val media = HidReport(HidMapper.CONSUMER_ID, byteArrayOf(0xCD.toByte(), 0))
        assertEquals(listOf(media), c.offer(media)) // sin movimiento pendiente, solo él
    }

    @Test
    fun restoreKeepsTheDeltaOfAFailedSend() {
        val c = HidMotionCoalescer()
        c.offer(mouse(0, 20, -5))
        val first = c.tick()!!
        c.restore(first) // sendReport devolvió false
        c.offer(mouse(0, 1, 1)) // sigue llegando movimiento
        val r = c.tick()!!
        assertEquals(21, r.dx())
        assertEquals(-4, r.dy())
    }

    @Test
    fun resetDropsEverything() {
        val c = HidMotionCoalescer()
        c.offer(mouse(1, 9, 9))
        c.reset()
        assertFalse(c.hasPending)
        assertEquals(0, c.buttonState)
        assertNull(c.tick())
    }

    @Test
    fun worksWithTheRealMapper() {
        val mapper = HidMapper()
        val c = HidMotionCoalescer()
        val sent = mutableListOf<HidReport>()
        for (i in 1..50) mapper.map(dev.mymouse.core.gesture.OutputAction.PointerMove(2, 1)).forEach { sent += c.offer(it) }
        assertTrue(sent.isEmpty())
        val r = c.tick()!!
        assertEquals(100, r.dx()) // 100 cabe en un solo informe
        assertEquals(50, r.dy())
    }
}
