package dev.mymouse.core.debug

import dev.mymouse.core.gesture.OutputAction.Button
import dev.mymouse.core.gesture.OutputAction.PointerMove
import dev.mymouse.core.gesture.OutputAction.Scroll
import dev.mymouse.core.gesture.OutputAction.Zoom
import dev.mymouse.core.protocol.MouseButton.LEFT
import dev.mymouse.core.protocol.MouseButton.RIGHT
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
    fun rightClickScrollAndZoom() {
        assertEquals("Clic derecho", d.describe(Button(RIGHT, true), 0)?.text)
        assertNull(d.describe(Button(RIGHT, false), 10))
        d.describe(Scroll(-120, 0), 20)
        assertEquals("Scroll ↓ 1.5", d.describe(Scroll(-60, 0), 30)?.text?.replace(',', '.'))
        assertEquals("Zoom + 1", d.describe(Zoom(120), 40)?.text)
        assertEquals("Zoom − 1", d.describe(Zoom(-240), 50)?.text)
    }
}
