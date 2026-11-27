package dev.mymouse.core.hid

import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.KeyboardLayouts
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.SystemGestureCommand
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
    fun fineZoomAccumulatesUntilAWholeWheelStep() {
        val ctrl = HidReport(2, byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0))
        val none = HidReport(2, ByteArray(8))
        assertEquals(emptyList(), m.map(OutputAction.Zoom(50)))
        assertEquals(emptyList(), m.map(OutputAction.Zoom(50)))
        assertEquals(listOf(ctrl, mouse(0, 0, 0, 1), none), m.map(OutputAction.Zoom(40)))
        // Quedan 20 de 120 acumulados: −250 suman −230 → un solo paso atrás (y −110 de resto).
        assertEquals(listOf(ctrl, mouse(0, 0, 0, -1), none), m.map(OutputAction.Zoom(-250)))
    }

    private fun key(modifiers: Int, code: Int = 0) =
        HidReport(2, byteArrayOf(modifiers.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0))

    @Test
    fun taskViewIsWinTab() {
        assertEquals(
            listOf(key(0x08, 0x2B), key(0)),
            m.map(OutputAction.SystemGesture(SystemGestureCommand.TASK_VIEW)),
        )
    }

    @Test
    fun showDesktopIsWinD() {
        assertEquals(
            listOf(key(0x08, 0x07), key(0)),
            m.map(OutputAction.SystemGesture(SystemGestureCommand.SHOW_DESKTOP)),
        )
    }

    @Test
    fun appSwitchHoldsAltBetweenStartAndEnd() {
        fun send(c: SystemGestureCommand) = m.map(OutputAction.SystemGesture(c))
        assertEquals(listOf(key(0x04)), send(SystemGestureCommand.APP_SWITCH_START))
        assertEquals(listOf(key(0x04, 0x2B), key(0x04)), send(SystemGestureCommand.APP_SWITCH_NEXT))
        // Hacia atrás: Alt + Shift + Tab; Alt sigue pulsado.
        assertEquals(listOf(key(0x06, 0x2B), key(0x04)), send(SystemGestureCommand.APP_SWITCH_PREVIOUS))
        assertEquals(listOf(key(0)), send(SystemGestureCommand.APP_SWITCH_END))
        // Terminado: ya no hay nada que soltar ni pasos sueltos.
        assertEquals(emptyList(), send(SystemGestureCommand.APP_SWITCH_END))
        assertEquals(emptyList(), send(SystemGestureCommand.APP_SWITCH_NEXT))
    }

    @Test
    fun releaseAllDropsAltAndLocalActionsProduceNothing() {
        m.map(OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_START))
        m.releaseAll()
        assertEquals(emptyList(), m.map(OutputAction.SystemGesture(SystemGestureCommand.APP_SWITCH_NEXT)))
        assertEquals(emptyList(), m.map(OutputAction.WidgetPanel(true)))
        assertEquals(emptyList(), m.map(OutputAction.FourFingerSwipe(true)))
        assertEquals(emptyList(), m.map(OutputAction.FourFingerDoubleTap))
    }

    @Test
    fun consumerKeyIsPressAndRelease() {
        assertEquals(
            listOf(HidReport(3, byteArrayOf(0xE9.toByte(), 0)), HidReport(3, byteArrayOf(0, 0))),
            m.consumer(ConsumerKey.VOLUME_UP),
        )
    }

    @Test
    fun keyboardActionSendsNothing_theAppOpensTheKeyboard() {
        assertEquals(emptyList(), m.action(BtAction.Keyboard, KeyboardLayouts.ES))
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
