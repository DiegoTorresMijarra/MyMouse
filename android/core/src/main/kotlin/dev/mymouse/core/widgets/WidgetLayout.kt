package dev.mymouse.core.widgets

import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.math.max
import kotlin.math.roundToInt

/** Tamaño del panel de widgets: fracción del lado correspondiente (alto en vertical, ancho en horizontal). */
enum class WidgetPanelSize(val fraction: Float) {
    SMALL(0.22f), MEDIUM(0.30f), LARGE(0.40f);

    /** Píxeles que ocupa el panel en una pantalla con [total] píxeles en ese lado. */
    fun extent(total: Int): Int = (total * fraction).roundToInt()
}

/** Rectángulo en las coordenadas del contenido del panel (antes de aplicar el scroll). */
data class Box(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2

    fun contains(px: Float, py: Float) = px >= x && px < right && py >= y && py < bottom
}

/**
 * Rejilla del panel de widgets (docs/spec-v2-widgets.md §2): los botones ocupan una celda; `media` y `volume`,
 * una fila entera cada uno. Lógica pura: la vista solo dibuja lo que sale de aquí.
 */
object WidgetLayout {
    const val COLUMNS_PORTRAIT = 4
    const val COLUMNS_LANDSCAPE = 2

    /** El panel es estrecho en horizontal (va a la derecha), así que allí hay menos columnas. */
    fun columnsFor(landscape: Boolean) = if (landscape) COLUMNS_LANDSCAPE else COLUMNS_PORTRAIT

    /** Alturas y huecos, en píxeles. */
    data class Metrics(
        val buttonHeight: Float,
        val mediaHeight: Float,
        /** Alto de la fila ⏮ ⏯ ⏭ dentro del widget multimedia (abajo). */
        val mediaButtonsHeight: Float,
        val volumeHeight: Float,
        val volumeIconWidth: Float,
        val volumeValueWidth: Float,
        val gap: Float,
    )

    /** [text]: título y artista; los tres botones de debajo. */
    data class MediaParts(val text: Box, val previous: Box, val playPause: Box, val next: Box)

    /** Zonas táctiles del volumen: silencio, barra (con toda la altura de la fila, para apuntar mejor) y número. */
    data class VolumeParts(val icon: Box, val bar: Box, val value: Box)

    data class Cell(
        val entry: WidgetEntry,
        val box: Box,
        val media: MediaParts? = null,
        val volume: VolumeParts? = null,
    )

    data class Result(val cells: List<Cell>, val contentHeight: Float) {
        companion object {
            val EMPTY = Result(emptyList(), 0f)
        }
    }

    fun cellWidth(width: Float, columns: Int, gap: Float): Float = (width - gap * (columns + 1)) / columns

    /** Coloca [entries] en orden dentro de [width] píxeles. Un `media` o `volume` cierra la fila de botones en curso. */
    fun compute(entries: List<WidgetEntry>, columns: Int, width: Float, metrics: Metrics): Result {
        require(columns >= 1) { "columns debe ser >= 1" }
        val gap = metrics.gap
        val cellW = cellWidth(width, columns, gap)
        val cells = ArrayList<Cell>(entries.size)
        var y = gap
        var column = 0

        fun endButtonRow() {
            if (column > 0) {
                column = 0
                y += metrics.buttonHeight + gap
            }
        }

        for (entry in entries) {
            when (entry.kind) {
                WidgetKind.BUTTON -> {
                    val box = Box(gap + column * (cellW + gap), y, cellW, metrics.buttonHeight)
                    cells += Cell(entry, box)
                    column++
                    if (column == columns) endButtonRow()
                }
                WidgetKind.MEDIA -> {
                    endButtonRow()
                    val box = Box(gap, y, width - 2 * gap, metrics.mediaHeight)
                    cells += Cell(entry, box, media = mediaParts(box, metrics))
                    y += metrics.mediaHeight + gap
                }
                WidgetKind.VOLUME -> {
                    endButtonRow()
                    val box = Box(gap, y, width - 2 * gap, metrics.volumeHeight)
                    cells += Cell(entry, box, volume = volumeParts(box, metrics))
                    y += metrics.volumeHeight + gap
                }
            }
        }
        endButtonRow()
        return Result(cells, y)
    }

    private fun mediaParts(box: Box, m: Metrics): MediaParts {
        val g = m.gap
        val buttonsTop = box.bottom - g - m.mediaButtonsHeight
        val bw = (box.width - 4 * g) / 3
        fun button(i: Int) = Box(box.x + g + i * (bw + g), buttonsTop, bw, m.mediaButtonsHeight)
        return MediaParts(
            text = Box(box.x + g, box.y + g, box.width - 2 * g, max(0f, buttonsTop - g - (box.y + g))),
            previous = button(0),
            playPause = button(1),
            next = button(2),
        )
    }

    private fun volumeParts(box: Box, m: Metrics) = VolumeParts(
        icon = Box(box.x, box.y, m.volumeIconWidth, box.height),
        bar = Box(box.x + m.volumeIconWidth, box.y, max(0f, box.width - m.volumeIconWidth - m.volumeValueWidth), box.height),
        value = Box(box.right - m.volumeValueWidth, box.y, m.volumeValueWidth, box.height),
    )

    /** Cuánto se puede desplazar el contenido hacia arriba (0 si cabe entero). */
    fun maxScroll(contentHeight: Float, viewHeight: Float): Float = max(0f, contentHeight - viewHeight)

    /** Nivel 0–100 que corresponde a la posición horizontal [x] sobre [bar] (se recorta en los extremos). */
    fun volumeLevelAt(bar: Box, x: Float): Int {
        if (bar.width <= 0f) return 0
        return (((x - bar.x) / bar.width) * 100f).roundToInt().coerceIn(0, 100)
    }
}
