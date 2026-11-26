package dev.mymouse.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetLayoutTest {
    @Test
    fun fitScaleOnlyShrinks() {
        assertEquals(1f, SheetLayout.fitScale(100f, 100f, 200f, 200f))
        assertEquals(0.5f, SheetLayout.fitScale(100f, 100f, 200f, 50f))
        assertEquals(0.25f, SheetLayout.fitScale(400f, 100f, 100f, 500f))
        assertEquals(1f, SheetLayout.fitScale(100f, 100f, 0f, 0f))
    }

    @Test
    fun gestureSheetHasTwoColumnsInLandscapeAndOnlyWhenTheyAreWideEnoughInPortrait() {
        // dp: columnas de al menos 150 con 16 de hueco
        assertEquals(2, SheetLayout.gestureSheetColumns(200f, landscape = true, minColumnWidth = 150f, columnGap = 16f))
        assertEquals(2, SheetLayout.gestureSheetColumns(10f, landscape = true, minColumnWidth = 150f, columnGap = 16f))
        assertEquals(1, SheetLayout.gestureSheetColumns(315f, landscape = false, minColumnWidth = 150f, columnGap = 16f)) // 149,5 por columna
        assertEquals(2, SheetLayout.gestureSheetColumns(316f, landscape = false, minColumnWidth = 150f, columnGap = 16f)) // justo 150
        assertEquals(2, SheetLayout.gestureSheetColumns(500f, landscape = false, minColumnWidth = 150f, columnGap = 16f))
        assertEquals(1, SheetLayout.gestureSheetColumns(240f, landscape = false, minColumnWidth = 150f, columnGap = 16f))
    }

    @Test
    fun withoutDebugTheSheetIsCentred() {
        val p = SheetLayout.placeSheet(800f, 400f, 300f, 200f, margin = 16f, debug = null)
        assertEquals(1f, p.scale)
        assertEquals(250f, p.left)
        assertEquals(100f, p.top)
    }

    @Test
    fun withoutDebugItCanSitAtTheBottom() {
        val p = SheetLayout.placeSheet(800f, 400f, 300f, 200f, margin = 16f, debug = null, atBottom = true)
        assertEquals(400f - 16f, p.top + p.height)
    }

    @Test
    fun landscapeWithDebugPutsTheSheetRightOfIt() {
        val debug = Area(0f, 0f, 300f, 330f)
        val p = SheetLayout.placeSheet(1000f, 400f, 400f, 250f, margin = 16f, debug = debug)
        assertEquals(1f, p.scale)
        assertTrue(p.left >= debug.right + 16f)
        assertFalse(p.area.overlaps(debug))
        assertTrue(p.area.right <= 1000f - 16f && p.area.bottom <= 400f - 16f && p.area.top >= 16f)
        // centrada en el hueco libre
        assertEquals((debug.right + 16f + 1000f - 16f) / 2, p.area.left + p.width / 2, 0.01f)
    }

    @Test
    fun landscapeShrinksWhenItDoesNotFitNextToTheDebug() {
        val debug = Area(0f, 0f, 400f, 330f)
        val p = SheetLayout.placeSheet(800f, 360f, 500f, 300f, margin = 16f, debug = debug)
        assertTrue(p.scale < 1f)
        assertFalse(p.area.overlaps(debug))
        assertTrue(p.area.right <= 800f - 16f + 0.01f)
        assertTrue(p.area.bottom <= 360f - 16f + 0.01f)
        assertEquals(p.width, 500f * p.scale, 0.01f)
    }

    @Test
    fun portraitWithDebugPutsTheSheetBelowIt() {
        val debug = Area(0f, 0f, 200f, 300f)
        val p = SheetLayout.placeSheet(400f, 900f, 300f, 400f, margin = 16f, debug = debug)
        assertEquals(1f, p.scale)
        assertTrue(p.top >= debug.bottom + 16f)
        assertFalse(p.area.overlaps(debug))
        assertTrue(p.area.bottom <= 900f - 16f)
    }

    @Test
    fun portraitShrinksWhenTheFreeSpaceBelowIsSmall() {
        val debug = Area(0f, 0f, 200f, 500f)
        val p = SheetLayout.placeSheet(400f, 700f, 330f, 500f, margin = 16f, debug = debug)
        assertTrue(p.scale < 0.5f)
        assertFalse(p.area.overlaps(debug))
        assertTrue(p.area.top >= debug.bottom + 16f - 0.01f)
        assertTrue(p.area.bottom <= 700f - 16f + 0.01f)
        assertTrue(p.area.left >= 16f - 0.01f && p.area.right <= 400f - 16f + 0.01f)
    }

    @Test
    fun noOverlapForAnySizeOrOrientation() {
        for ((w, h) in listOf(360f to 640f, 640f to 360f, 412f to 915f, 915f to 412f, 800f to 1280f)) {
            val debug = Area(0f, 0f, w * 0.35f + 24f, w * 0.35f * h / w + 24f + 150f)
            for ((sw, sh) in listOf(260f to 460f, 480f to 250f, 700f to 700f)) {
                val p = SheetLayout.placeSheet(w, h, sw, sh, 12f, debug)
                if (p.scale > 0f && debug.right + 12f < w && debug.bottom + 12f < h) assertFalse(p.area.overlaps(debug), "$w x $h, $sw x $sh")
                assertTrue(p.scale > 0f)
            }
        }
    }

    @Test
    fun areasThatOnlyTouchDoNotOverlap() {
        assertFalse(Area(0f, 0f, 10f, 10f).overlaps(Area(10f, 0f, 20f, 10f)))
        assertTrue(Area(0f, 0f, 10f, 10f).overlaps(Area(9f, 9f, 20f, 20f)))
    }
}
