package dev.mymouse.core.debug

import dev.mymouse.core.gesture.OutputAction.Button
import dev.mymouse.core.gesture.OutputAction.FourFingerSwipe
import dev.mymouse.core.gesture.OutputAction.SystemGesture
import dev.mymouse.core.gesture.OutputAction.WidgetPanel
import dev.mymouse.core.gesture.OutputAction.PointerMove
import dev.mymouse.core.gesture.OutputAction.Scroll
import dev.mymouse.core.gesture.OutputAction.Zoom
import dev.mymouse.core.protocol.MouseButton.LEFT
import dev.mymouse.core.protocol.MouseButton.RIGHT
import dev.mymouse.core.protocol.SystemGestureCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionDescriberTest {
    private val d = ActionDescriber()

    @Test
    fun clickThenDoubleClick() {
        assertEquals("Clic", d.describe(Button(LEFT, true), 0)?.text)
        assertNull(d.describe(Button(LEFT, false), 250))
        assertEquals("Doble clic", d.describe(Button(LEFT, true), 300)?.text)
        assertNull(d.describe(Button(LEFT, false), 400))
        assertEquals("Clic", d.describe(Button(LEFT, true), 2000)?.text)
    }

    @Test
    fun movesAreMergedIntoOneEntry() {
        val a = d.describe(PointerMove(3, 4), 0)!!
        val b = d.describe(PointerMove(3, 4), 10)!!
        assertEquals(a.key, b.key)
        assertEquals("Mover (10 px)", b.text)
    }

    @Test
    fun dragAndRelease() {
        d.describe(Button(LEFT, true), 0)
        assertEquals("Arrastrar (5 px)", d.describe(PointerMove(3, 4), 100)?.text)
        assertEquals("Soltar", d.describe(Button(LEFT, false), 500)?.text)
        // Tras arrastrar, el siguiente clic no cuenta como doble clic.
        assertEquals("Clic", d.describe(Button(LEFT, true), 600)?.text)
    }

    @Test
    fun gestureEvents() {
        assertEquals("Modo gestos: dibuja una forma", d.describe(dev.mymouse.core.gesture.OutputAction.GestureMode(true), 0)?.text)
        assertEquals("Forma: circle (93 %)", d.describe(dev.mymouse.core.gesture.OutputAction.Gesture("circle", 0.93f), 10)?.text)
        assertEquals("Forma: No reconocido", d.describe(dev.mymouse.core.gesture.OutputAction.GestureRejected("No reconocido"), 20)?.text)
    }

    @Test
    fun rightClickScrollAndZoom() {
        assertEquals("Clic derecho", d.describe(Button(RIGHT, true), 0)?.text)
        assertNull(d.describe(Button(RIGHT, false), 10))
        d.describe(Scroll(-120, 0), 20)
        assertEquals("Scroll ↓ 1.5", d.describe(Scroll(-60, 0), 30)?.text?.replace(',', '.'))
        assertEquals("Zoom + 1", d.describe(Zoom(120), 40)?.text)
        assertEquals("Zoom − 1", d.describe(Zoom(-240), 50)?.text)
    }

    @Test
    fun windowsAndPanelGestures() {
        assertEquals("3 dedos ↑: Vista de tareas", d.describe(SystemGesture(SystemGestureCommand.TASK_VIEW), 0)?.text)
        assertEquals("3 dedos ↓: Escritorio", d.describe(SystemGesture(SystemGestureCommand.SHOW_DESKTOP), 10)?.text)
        assertEquals("Siguiente app", d.describe(SystemGesture(SystemGestureCommand.APP_SWITCH_NEXT), 20)?.text)
        assertEquals("4 dedos juntar: mostrar widgets", d.describe(WidgetPanel(true), 30)?.text)
        assertEquals("4 dedos separar: ocultar widgets", d.describe(WidgetPanel(false), 40)?.text)
        assertEquals("4 dedos ↑ (reservado)", d.describe(FourFingerSwipe(true), 50)?.text)
    }

    @Test
    fun fineZoomShowsFractionalSteps() {
        assertEquals("Zoom + 0.5", d.describe(Zoom(60), 0)?.text?.replace(',', '.'))
    }
}
