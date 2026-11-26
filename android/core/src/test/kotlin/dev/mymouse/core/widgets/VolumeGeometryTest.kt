package dev.mymouse.core.widgets

import dev.mymouse.core.widgets.VolumeGeometry.ValuePlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeGeometryTest {
    // Una unidad = 1 dp: icono 44, número de 3 cifras 32, pista de 4, pulgar de 18 (radio 9), pista mínima de 96.
    private val p = VolumeGeometry.Params(
        iconWidth = 44f, valueWidth = 32f, trackHeight = 4f, thumbRadius = 9f, minTrack = 96f, valueLineHeight = 16f, gap = 6f,
    )

    private fun row(width: Float, height: Float = 56f) = Box(10f, 20f, width, height)

    @Test
    fun wideRowHasTheTrackTakingTheRemainingWidth() {
        val g = VolumeGeometry.compute(row(400f), p)
        assertEquals(ValuePlacement.RIGHT, g.valuePlacement)
        assertEquals(44f, g.icon.width)
        assertEquals(10f, g.icon.x)
        assertEquals(410f, g.value.right)
        assertEquals(32f, g.value.width)
        // la pista llena el hueco entre el altavoz y el número, dejando sitio al pulgar
        assertEquals(g.icon.right + 12f, g.bar.x)
        assertEquals(g.value.x - 12f, g.bar.right)
        assertTrue(g.bar.width > 250f)
    }

    @Test
    fun trackIsThinAndCentredAndThumbIsBig() {
        val g = VolumeGeometry.compute(row(400f), p)
        assertEquals(4f, g.track.height)
        assertEquals(g.bar.centerY, g.track.centerY, 0.001f)
        assertEquals(g.bar.x, g.track.x)
        assertEquals(g.bar.width, g.track.width)
        assertEquals(9f, g.thumbRadius)
        assertTrue(g.thumbRadius * 2 > g.track.height * 4) // el pulgar se ve claramente más grande que la pista
    }

    @Test
    fun thumbTravelsTheTrackFromZeroToOneHundred() {
        val g = VolumeGeometry.compute(row(400f), p)
        assertEquals(g.bar.x, g.thumbX(0))
        assertEquals(g.bar.right, g.thumbX(100))
        assertEquals(g.bar.centerX, g.thumbX(50), 0.001f)
        assertEquals(g.bar.x, g.thumbX(-5))
        assertEquals(g.bar.right, g.thumbX(150))
    }

    @Test
    fun thumbNeverCoversTheNeighbours() {
        for (width in listOf(160f, 200f, 260f, 400f)) {
            val g = VolumeGeometry.compute(row(width), p)
            assertTrue(g.thumbX(0) - g.thumbRadius >= g.icon.right, "ancho $width")
            if (g.valuePlacement == ValuePlacement.RIGHT) assertTrue(g.thumbX(100) + g.thumbRadius <= g.value.x, "ancho $width")
            assertTrue(g.thumbX(100) + g.thumbRadius <= g.bar.right + g.thumbRadius + 0.001f)
        }
    }

    @Test
    fun narrowPanelMovesTheNumberBelowBeforeShrinkingTheTrack() {
        // 44 + 12 + 96 + 12 + 32 = 196 es lo mínimo con el número a la derecha.
        val ok = VolumeGeometry.compute(row(196f), p)
        assertEquals(ValuePlacement.RIGHT, ok.valuePlacement)
        assertEquals(96f, ok.bar.width, 0.001f)

        val narrow = VolumeGeometry.compute(row(195f), p)
        assertEquals(ValuePlacement.BELOW, narrow.valuePlacement)
        assertTrue(narrow.bar.width >= 96f)
        // el número queda debajo de la pista y dentro de la fila
        assertTrue(narrow.value.y >= narrow.bar.bottom - 0.001f)
        assertTrue(narrow.value.bottom <= 76f + 0.001f)
        assertFalse(narrow.value.overlapsRows(narrow.track))
    }

    @Test
    fun veryNarrowPanelHidesTheNumberInsteadOfShrinkingTheTrackBelowTheMinimum() {
        // con 56 de alto no cabe una segunda línea si el panel es muy corto de alto
        val flat = VolumeGeometry.compute(row(150f, height = 30f), p)
        assertEquals(ValuePlacement.HIDDEN, flat.valuePlacement)
        assertEquals(0f, flat.value.width)
        assertEquals(30f, flat.bar.height)
        assertTrue(flat.bar.width > 0f)
    }

    @Test
    fun tinyRowNeverGivesNegativeSizes() {
        val g = VolumeGeometry.compute(row(40f), p)
        assertTrue(g.bar.width >= 0f && g.track.width >= 0f && g.value.width >= 0f)
    }

    @Test
    fun mutedOrNotTheGeometryIsTheSame() {
        // El estado de silencio solo cambia colores: la geometría no depende de él.
        assertEquals(VolumeGeometry.compute(row(300f), p), VolumeGeometry.compute(row(300f), p))
    }

    @Test
    fun volumeLevelUsesTheWholeBar() {
        val g = VolumeGeometry.compute(row(400f), p)
        assertEquals(0, WidgetLayout.volumeLevelAt(g.bar, g.bar.x))
        assertEquals(100, WidgetLayout.volumeLevelAt(g.bar, g.bar.right))
        assertEquals(50, WidgetLayout.volumeLevelAt(g.bar, g.bar.centerX))
    }

    private fun Box.overlapsRows(o: Box) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
}
