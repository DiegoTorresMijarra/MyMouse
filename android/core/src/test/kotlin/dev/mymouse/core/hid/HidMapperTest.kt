package dev.mymouse.core.hid

import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.MouseButton
import kotlin.test.Test
import kotlin.test.assertEquals

class HidMapperTest {
    private val m = HidMapper()

    private fun mouse(b: Int, x: Int, y: Int, w: Int = 0, p: Int = 0) =
        HidReport(1, byteArrayOf(b.toByte(), x.toByte(), y.toByte(), w.toByte(), p.toByte()))

    @Test
    fun smallMoveIsOneReport() {
        assertEquals(listOf(mouse(0, 5, -3)), m.map(OutputAction.PointerMove(5, -3)))
    }

    @Test
    fun bigMoveIsSplit() {
        assertEquals(
            listOf(mouse(0, 127, -10), mouse(0, 127, 0), mouse(0, 46, 0)),
            m.map(OutputAction.PointerMove(300, -10)),
        )
    }

    @Test
    fun buttonsKeepTheirStateWhileMoving() {
        assertEquals(listOf(mouse(1, 0, 0)), m.map(OutputAction.Button(MouseButton.LEFT, true)))
        assertEquals(listOf(mouse(1, 4, 4)), m.map(OutputAction.PointerMove(4, 4)))
        assertEquals(listOf(mouse(3, 0, 0)), m.map(OutputAction.Button(MouseButton.RIGHT, true)))
        assertEquals(listOf(mouse(2, 0, 0)), m.map(OutputAction.Button(MouseButton.LEFT, false)))
    }

    @Test
    fun scrollAccumulatesWheelSteps() {
        assertEquals(emptyList(), m.map(OutputAction.Scroll(60, 0)))
        assertEquals(listOf(mouse(0, 0, 0, 1)), m.map(OutputAction.Scroll(60, 0)))
        assertEquals(listOf(mouse(0, 0, 0, -2, 1)), m.map(OutputAction.Scroll(-240, 120)))
    }

    @Test
    fun zoomIsCtrlPlusWheel() {
        val ctrl = HidReport(2, byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0))
        val none = HidReport(2, ByteArray(8))
        assertEquals(listOf(ctrl, mouse(0, 0, 0, -1), none), m.map(OutputAction.Zoom(-120)))
    }

    @Test
    fun consumerKeyIsPressAndRelease() {
        assertEquals(
            listOf(HidReport(3, byteArrayOf(0xE9.toByte(), 0)), HidReport(3, byteArrayOf(0, 0))),
            m.consumer(ConsumerKey.VOLUME_UP),
        )
    }

    @Test
    fun gestureActionsProduceNothing() {
        assertEquals(emptyList(), m.map(OutputAction.Gesture("circle", 1f)))
    }

    @Test
    fun descriptorIsBalanced() {
        // Cada colección (0xA1) se cierra (0xC0): 4 aperturas, 4 cierres.
        val bytes = HidMapper.DESCRIPTOR.map { it.toInt() and 0xFF }
        assertEquals(4, bytes.windowed(2).count { it[0] == 0xA1 })
        assertEquals(4, bytes.count { it == 0xC0 })
    }
}
