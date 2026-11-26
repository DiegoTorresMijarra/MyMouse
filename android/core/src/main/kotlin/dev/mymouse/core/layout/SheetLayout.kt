package dev.mymouse.core.layout

import kotlin.math.max
import kotlin.math.min

/** Rectángulo por bordes, en píxeles. */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** ¿Se solapan de verdad? Dos áreas que solo se tocan en un borde no se solapan. */
    fun overlaps(o: Area): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/** Dónde se dibuja una tarjeta: esquina superior izquierda y escala (1 = tamaño natural). */
data class Placement(val left: Float, val top: Float, val scale: Float, val width: Float, val height: Float) {
    val area get() = Area(left, top, left + width, top + height)
}

/**
 * Disposición de las hojas que se pintan sobre el trackpad (docs/spec-v4.7-universo.md §4): ninguna se solapa con otra y, si no
 * caben a tamaño natural, se encogen enteras (sin recortar texto), como la chuleta desde la v4.6. Lógica pura con tests.
 */
object SheetLayout {
    /**
     * Escala para que un contenido de [contentWidth] × [contentHeight] quepa en [availWidth] × [availHeight]: 1 si cabe, menos si no.
     * Con un hueco sin espacio (≤ 0), no se encoge por ese lado (no hay nada que respetar).
     */
    fun fitScale(contentWidth: Float, contentHeight: Float, availWidth: Float, availHeight: Float): Float {
        var scale = 1f
        if (contentWidth > 0f && availWidth > 0f) scale = min(scale, availWidth / contentWidth)
        if (contentHeight > 0f && availHeight > 0f) scale = min(scale, availHeight / contentHeight)
        return scale
    }

    /**
     * Columnas de la hoja de gestos: siempre dos en horizontal; en vertical, dos solo si cada columna tiene al menos
     * [minColumnWidth] de ancho, y si no, una. [contentWidth] es el ancho disponible dentro de los márgenes de la hoja.
     */
    fun gestureSheetColumns(contentWidth: Float, landscape: Boolean, minColumnWidth: Float, columnGap: Float): Int {
        if (landscape) return 2
        val perColumn = (contentWidth - columnGap) / 2
        return if (perColumn >= minColumnWidth) 2 else 1
    }

    /**
     * Sitio de la chuleta básica de tamaño natural [sheetWidth] × [sheetHeight] en un trackpad de [width] × [height].
     * - Sin depuración: centrada (o pegada abajo si [atBottom]).
     * - Con la depuración ([debug] es todo lo que ocupa: minipantalla y lista de acciones): en horizontal, a la derecha de ella; en
     *   vertical, debajo. Se centra en el hueco libre y se encoge si no cabe. Nunca se solapa con [debug].
     * [margin] es el margen con los bordes del trackpad y con la depuración.
     */
    fun placeCheatSheet(
        width: Float, height: Float, sheetWidth: Float, sheetHeight: Float, margin: Float, debug: Area?, atBottom: Boolean = false,
    ): Placement {
        val free = when {
            debug == null -> Area(margin, margin, width - margin, height - margin)
            width > height -> Area(debug.right + margin, margin, width - margin, height - margin) // horizontal: a la derecha
            else -> Area(margin, debug.bottom + margin, width - margin, height - margin) // vertical: debajo
        }
        // Un hueco sin sitio (pantalla diminuta): se usa un mínimo para no dividir por cero ni dar una escala negativa.
        val availW = max(free.width, 1f)
        val availH = max(free.height, 1f)
        val scale = fitScale(sheetWidth, sheetHeight, availW, availH)
        val w = sheetWidth * scale
        val h = sheetHeight * scale
        val left = free.left + (availW - w) / 2
        val top = if (atBottom && debug == null) free.bottom - h else free.top + (availH - h) / 2
        return Placement(left, top, scale, w, h)
    }
}
