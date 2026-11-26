package dev.mymouse.core.widgets

import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetPanelControllerTest {
    private val m = WidgetLayout.Metrics(
        buttonHeight = 80f, mediaHeight = 120f, mediaButtonsHeight = 50f,
        volumeHeight = 60f, volumeIconWidth = 60f, volumeValueWidth = 50f, gap = 10f,
    )

    // Panel de 450 px de ancho: media (fila 10..130), volumen (140..200), botones de 100 px de ancho desde y = 210.
    private val entries = listOf(
        WidgetEntry(0, WidgetKind.MEDIA, "", ""),
        WidgetEntry(1, WidgetKind.VOLUME, "", ""),
    ) + (2..9).map { WidgetEntry(it, WidgetKind.BUTTON, "B$it", "") }

    private fun controller(viewHeight: Float = 1000f): WidgetPanelController {
        val c = WidgetPanelController(slopPx = 10f)
        c.setLayout(WidgetLayout.compute(entries, 4, 450f, m), viewHeight)
        return c
    }

    private val result = WidgetLayout.compute(entries, 4, 450f, m)

    @Test
    fun buttonFiresOnReleaseInside() {
        val c = controller()
        val b = result.cells[2].box
        assertTrue(c.onDown(b.centerX, b.centerY, 0).isEmpty())
        assertEquals(WidgetTarget.Button(2), c.pressed)

        val events = c.onUp(b.centerX, b.centerY, 100)
        assertEquals(1, events.size)
        assertEquals(WidgetEvent.Press(WidgetTarget.Button(2), id = 2), events[0])
        assertNull(c.pressed)
    }

    @Test
    fun draggingOutsideTheButtonCancels() {
        val c = controller()
        val b = result.cells[2].box
        c.onDown(b.centerX, b.centerY, 0)
        c.onMove(b.right + 40f, b.centerY, 20)
        assertNull(c.pressed)
        assertTrue(c.onUp(b.right + 40f, b.centerY, 40).isEmpty())
    }

    @Test
    fun releasingOnAnotherButtonDoesNotFire() {
        val c = controller()
        val a = result.cells[2].box
        val b = result.cells[3].box
        c.onDown(a.centerX, a.centerY, 0)
        assertTrue(c.onUp(b.centerX, b.centerY, 50).isEmpty())
    }

    @Test
    fun touchingEmptySpaceDoesNothing() {
        val c = controller()
        assertTrue(c.onDown(2f, 2f, 0).isEmpty())
        assertNull(c.pressed)
        assertTrue(c.onUp(2f, 2f, 10).isEmpty())
    }

    @Test
    fun mediaButtonsSendTheirCommand() {
        val c = controller()
        val parts = result.cells[0].media!!
        for ((box, command) in listOf(
            parts.previous to MediaCommand.PREVIOUS,
            parts.playPause to MediaCommand.PLAY_PAUSE,
            parts.next to MediaCommand.NEXT,
        )) {
            c.onDown(box.centerX, box.centerY, 0)
            val e = c.onUp(box.centerX, box.centerY, 50).single()
            assertEquals(command, assertIs<WidgetEvent.Media>(e).command)
        }
        // La zona del título no hace nada.
        val t = parts.text
        c.onDown(t.centerX, t.centerY, 0)
        assertTrue(c.onUp(t.centerX, t.centerY, 50).isEmpty())
    }

    @Test
    fun muteIconTogglesOnRelease() {
        val c = controller()
        val icon = result.cells[1].volume!!.icon
        assertTrue(c.onDown(icon.centerX, icon.centerY, 0).isEmpty())
        assertIs<WidgetEvent.ToggleMute>(c.onUp(icon.centerX, icon.centerY, 50).single())
    }

    @Test
    fun volumeBarSendsAtMostEvery50msAndAlwaysOnRelease() {
        val c = controller()
        val bar = result.cells[1].volume!!.bar
        val y = bar.centerY
        fun at(level: Int) = bar.x + bar.width * level / 100f

        val first = c.onDown(at(10), y, 0).single()
        assertEquals(10, assertIs<WidgetEvent.SetVolume>(first).level)
        assertTrue(c.draggingVolume)

        assertTrue(c.onMove(at(20), y, 20).isEmpty()) // dentro de los 50 ms
        assertEquals(20, c.dragLevel)
        assertEquals(30, assertIs<WidgetEvent.SetVolume>(c.onMove(at(30), y, 60).single()).level)
        assertTrue(c.onMove(at(40), y, 80).isEmpty())

        val last = assertIs<WidgetEvent.SetVolume>(c.onUp(at(45), y, 90).single())
        assertEquals(45, last.level)
        assertTrue(last.final)
        assertFalse(c.draggingVolume)
    }

    @Test
    fun volumeDragIgnoresVerticalMovementAndNeverScrolls() {
        val c = controller(viewHeight = 100f) // el contenido no cabe
        val bar = result.cells[1].volume!!.bar
        c.onDown(bar.centerX, bar.centerY, 0)
        c.onMove(bar.centerX, bar.centerY + 300f, 100)
        assertEquals(0f, c.scrollY)
        assertTrue(c.draggingVolume)
    }

    @Test
    fun verticalDragScrollsAndDoesNotPressTheButton() {
        val c = controller(viewHeight = 300f)
        val b = result.cells[2].box
        c.onDown(b.centerX, b.centerY, 0)
        c.onMove(b.centerX, b.centerY - 40f, 20)

        assertTrue(c.scrolling)
        assertNull(c.pressed)
        assertEquals(40f, c.scrollY)
        assertTrue(c.onUp(b.centerX, b.centerY - 40f, 40).isEmpty())
    }

    @Test
    fun scrollIsLimitedToTheContent() {
        val c = controller(viewHeight = 300f)
        val max = WidgetLayout.maxScroll(result.contentHeight, 300f)
        assertTrue(max > 0f)
        c.onDown(200f, 250f, 0)
        c.onMove(200f, 250f - 5000f, 20)
        assertEquals(max, c.scrollY)
        c.onUp(200f, 0f, 40)

        // Hacia abajo no pasa de 0.
        c.onDown(2f, 100f, 100) // hueco entre widgets
        c.onMove(2f, 100f + 5000f, 120)
        assertEquals(0f, c.scrollY)
    }

    @Test
    fun smallMovementsAreStillATap() {
        val c = controller(viewHeight = 300f)
        val b = result.cells[2].box
        c.onDown(b.centerX, b.centerY, 0)
        c.onMove(b.centerX, b.centerY + 5f, 10) // por debajo del umbral
        assertFalse(c.scrolling)
        assertIs<WidgetEvent.Press>(c.onUp(b.centerX, b.centerY + 5f, 30).single())
    }

    @Test
    fun scrolledContentIsHitWithTheOffset() {
        val c = controller(viewHeight = 200f)
        // Se desplaza 100 px y se toca el botón 2, que ahora está 100 px más arriba.
        c.onDown(200f, 250f, 0)
        c.onMove(200f, 150f, 10)
        c.onUp(200f, 150f, 20)
        assertEquals(100f, c.scrollY)

        val b = result.cells[2].box
        c.onDown(b.centerX, b.centerY - 100f, 100)
        assertEquals(WidgetEvent.Press(WidgetTarget.Button(2), id = 2), c.onUp(b.centerX, b.centerY - 100f, 150).single())
    }

    @Test
    fun cancelFlushesTheLastVolumeAndNothingElse() {
        val c = controller()
        val bar = result.cells[1].volume!!.bar
        c.onDown(bar.x + bar.width * 0.7f, bar.centerY, 0)
        val e = assertIs<WidgetEvent.SetVolume>(c.onCancel(10).single())
        assertEquals(70, e.level)
        assertTrue(e.final)

        val b = result.cells[2].box
        c.onDown(b.centerX, b.centerY, 100)
        assertTrue(c.onCancel(110).isEmpty())
        assertNull(c.pressed)
    }

    @Test
    fun throttleLimitsByInterval() {
        val t = SendThrottle(50)
        assertTrue(t.tryAcquire(0))
        assertFalse(t.tryAcquire(49))
        assertTrue(t.tryAcquire(50))
        t.reset()
        assertTrue(t.tryAcquire(51))
    }
}
