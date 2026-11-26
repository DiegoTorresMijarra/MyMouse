package dev.mymouse.core.widgets

import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Animación del panel (docs/spec-v3.1-ajustes.md §1.1). */
class PanelRevealTest {
    @Test
    fun rowsFadeInOneAfterAnotherWith30MsBetweenThem() {
        assertEquals(0f, PanelReveal.rowAlpha(0, 0))
        assertEquals(0f, PanelReveal.rowAlpha(2, 60)) // la fila 2 empieza a los 60 ms
        assertTrue(PanelReveal.rowAlpha(2, 100) in 0.1f..0.9f)
        assertEquals(1f, PanelReveal.rowAlpha(0, PanelReveal.FADE_MS))
        assertEquals(1f, PanelReveal.rowAlpha(2, 60 + PanelReveal.FADE_MS))
        assertTrue(PanelReveal.rowAlpha(0, 50) > PanelReveal.rowAlpha(1, 50))
    }

    @Test
    fun alphaStaysInRange() {
        assertEquals(0f, PanelReveal.rowAlpha(3, -50))
        assertEquals(1f, PanelReveal.rowAlpha(0, 10_000))
    }

    @Test
    fun slideLastsAboutHalfASecondAtMostAndCoversTheStagger() {
        assertEquals(280L, PanelReveal.SLIDE_IN_MS)
        assertEquals(PanelReveal.SLIDE_IN_MS, PanelReveal.totalMs(1))
        assertEquals(PanelReveal.SLIDE_IN_MS, PanelReveal.totalMs(0))
        assertEquals(9 * PanelReveal.STAGGER_MS + PanelReveal.FADE_MS, PanelReveal.totalMs(10))
    }

    @Test
    fun cellsOnTheSameYShareARow() {
        val metrics = WidgetLayout.Metrics(60f, 100f, 40f, 50f, 40f, 40f, 6f)
        fun button(i: Int) = WidgetEntry(i, WidgetKind.BUTTON, "b$i", "")
        val entries = listOf(button(0), button(1), button(2), WidgetEntry(3, WidgetKind.VOLUME, "", ""), button(4))
        val result = WidgetLayout.compute(entries, columns = 2, width = 300f, metrics = metrics)
        assertEquals(listOf(0, 0, 1, 2, 3), result.rowOfCell)
        assertEquals(emptyList(), WidgetLayout.Result.EMPTY.rowOfCell)
    }
}
