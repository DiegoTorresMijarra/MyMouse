package dev.mymouse.core.widgets

import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetLayoutTest {
    private val m = WidgetLayout.Metrics(
        buttonHeight = 80f, mediaHeight = 120f, mediaButtonsHeight = 50f,
        volumeHeight = 60f, volumeIconWidth = 60f, volumeValueWidth = 50f, gap = 10f,
    )

    private fun button(id: Int) = WidgetEntry(id, WidgetKind.BUTTON, "B$id", "")
    private val media = WidgetEntry(0, WidgetKind.MEDIA, "", "")
    private val volume = WidgetEntry(1, WidgetKind.VOLUME, "", "")

    @Test
    fun columnsDependOnOrientation() {
        assertEquals(4, WidgetLayout.columnsFor(landscape = false))
        assertEquals(2, WidgetLayout.columnsFor(landscape = true))
    }

    @Test
    fun panelSizeFractions() {
        assertEquals(220, WidgetPanelSize.SMALL.extent(1000))
        assertEquals(300, WidgetPanelSize.MEDIUM.extent(1000))
        assertEquals(400, WidgetPanelSize.LARGE.extent(1000))
    }

    @Test
    fun buttonsFlowInRowsOfTheGivenColumns() {
        val r = WidgetLayout.compute((0..5).map(::button), columns = 4, width = 450f, metrics = m)

        // Ancho de celda: (450 - 5*10) / 4 = 100.
        assertEquals(6, r.cells.size)
        assertEquals(Box(10f, 10f, 100f, 80f), r.cells[0].box)
        assertEquals(10f, r.cells[3].box.y)
        assertEquals(340f, r.cells[3].box.x)
        assertEquals(100f, r.cells[4].box.y) // segunda fila
        assertEquals(10f, r.cells[4].box.x)
        // 2 filas de 80 + huecos: 10 + 80 + 10 + 80 + 10
        assertEquals(190f, r.contentHeight)
    }

    @Test
    fun mediaAndVolumeTakeAWholeRowAndCloseTheButtonRow() {
        val entries = listOf(button(0), button(1), media, button(3), volume, button(5))
        val r = WidgetLayout.compute(entries, columns = 4, width = 450f, metrics = m)

        assertEquals(10f, r.cells[0].box.y)
        assertEquals(10f, r.cells[1].box.y)
        // El widget multimedia empieza en una fila nueva y ocupa todo el ancho.
        val mediaCell = r.cells[2]
        assertEquals(100f, mediaCell.box.y)
        assertEquals(10f, mediaCell.box.x)
        assertEquals(430f, mediaCell.box.width)
        assertEquals(120f, mediaCell.box.height)
        // El botón siguiente va debajo (fila nueva), y el volumen otra fila entera.
        assertEquals(230f, r.cells[3].box.y)
        assertEquals(320f, r.cells[4].box.y)
        assertEquals(60f, r.cells[4].box.height)
        assertEquals(390f, r.cells[5].box.y)
        assertEquals(480f, r.contentHeight)
    }

    @Test
    fun cellsKeepTheOrderAndIdsOfTheLayout() {
        val entries = listOf(media, volume, button(2), button(3))
        val r = WidgetLayout.compute(entries, columns = 2, width = 330f, metrics = m)

        assertEquals(listOf(0, 1, 2, 3), r.cells.map { it.entry.id })
        assertTrue(r.cells[0].media != null && r.cells[0].volume == null)
        assertTrue(r.cells[1].volume != null && r.cells[1].media == null)
        // En horizontal, 2 columnas: los dos botones comparten fila.
        assertEquals(r.cells[2].box.y, r.cells[3].box.y)
    }

    @Test
    fun mediaPartsSitInsideTheCard() {
        val r = WidgetLayout.compute(listOf(media), columns = 4, width = 450f, metrics = m)
        val box = r.cells[0].box
        val parts = r.cells[0].media!!

        for (b in listOf(parts.previous, parts.playPause, parts.next)) {
            assertEquals(50f, b.height)
            assertEquals(box.bottom - 10f, b.bottom)
            assertTrue(b.x >= box.x && b.right <= box.right)
        }
        assertTrue(parts.previous.right < parts.playPause.x && parts.playPause.right < parts.next.x)
        assertTrue(parts.text.bottom <= parts.previous.y)
    }

    @Test
    fun volumePartsSplitTheRow() {
        val r = WidgetLayout.compute(listOf(volume), columns = 4, width = 450f, metrics = m)
        val box = r.cells[0].box
        val p = r.cells[0].volume!!

        assertEquals(box.x, p.icon.x)
        assertTrue(p.icon.right < p.bar.x && p.bar.right < p.value.x)
        assertEquals(p.icon.right + 14f, p.bar.x) // radio del pulgar (9) + medio hueco (5)
        assertEquals(p.value.x - 14f, p.bar.right)
        assertEquals(box.right, p.value.right)
        assertEquals(box.height, p.bar.height)
        assertEquals(VolumeGeometry.ValuePlacement.RIGHT, p.geometry.valuePlacement)
    }

    @Test
    fun volumeLevelFollowsTheFingerAndIsClamped() {
        val bar = Box(100f, 0f, 200f, 60f)
        assertEquals(0, WidgetLayout.volumeLevelAt(bar, 100f))
        assertEquals(50, WidgetLayout.volumeLevelAt(bar, 200f))
        assertEquals(100, WidgetLayout.volumeLevelAt(bar, 300f))
        assertEquals(0, WidgetLayout.volumeLevelAt(bar, -50f))
        assertEquals(100, WidgetLayout.volumeLevelAt(bar, 999f))
        assertEquals(25, WidgetLayout.volumeLevelAt(bar, 150f))
    }

    @Test
    fun maxScrollIsZeroWhenEverythingFits() {
        assertEquals(0f, WidgetLayout.maxScroll(300f, 400f))
        assertEquals(100f, WidgetLayout.maxScroll(500f, 400f))
    }

    @Test
    fun emptyListHasNoContent() {
        val r = WidgetLayout.compute(emptyList(), columns = 4, width = 450f, metrics = m)
        assertTrue(r.cells.isEmpty())
        assertEquals(10f, r.contentHeight)
    }
}
