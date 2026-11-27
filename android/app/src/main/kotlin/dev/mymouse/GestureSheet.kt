package dev.mymouse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.mymouse.core.layout.Area
import dev.mymouse.core.layout.SheetLayout
import dev.mymouse.core.protocol.GestureListEntry
import dev.mymouse.core.shapes.P

/**
 * Chuleta del modo gestos (docs/spec-v1-gestos.md §6): miniatura de cada forma + texto de ayuda del PC.
 * Primero "Tus formas" (las fijadas en el PC), después el resto.
 */
class GestureSheet(private val density: Float) {

    data class Item(val name: String, val label: String, val help: String, val pinned: Boolean, val preview: List<P>?)

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.panelSolid }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.BORDER
        strokeWidth = density
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.ELEVATED }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT_SECONDARY
        textSize = GlassTheme.CARD_TITLE_SP * density
        letterSpacing = 0.06f
        isFakeBoldText = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT
        textSize = 13 * density
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT_SECONDARY
        textSize = 11 * density
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.TEXT
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.accent }
    private val box = RectF()

    /**
     * [items]: formas a mostrar. [context]: app activa en el PC si tiene reglas propias. [note]: aviso opcional (p. ej. sin conexión).
     * [debug]: lo que ocupa el modo depuración, para no solaparse con ello.
     *
     * En dos columnas (docs/spec-v4.7-universo.md §4): siempre en horizontal; en vertical solo si cada columna tiene al menos 150 dp y,
     * si no, en una. Si no cabe a lo alto, se encoge entera como la chuleta (lógica pura en `core`: [SheetLayout]).
     */
    fun draw(
        canvas: Canvas, width: Int, height: Int, items: List<Item>, context: String, note: String?, highlight: String?,
        debug: Area? = null,
    ) {
        val pad = 12 * density
        val cellH = 40 * density
        val icon = 30 * density
        val w = (width - 32 * density).coerceAtMost(560 * density)
        val columns = SheetLayout.gestureSheetColumns(w - 2 * pad, landscape = width > height, minColumnWidth = 150 * density, columnGap = COLUMN_GAP_DP * density)
        val cellW = (w - 2 * pad - (columns - 1) * COLUMN_GAP_DP * density) / columns

        val pinned = items.filter { it.pinned }
        val others = items.filterNot { it.pinned }
        val sections = listOfNotNull(
            if (pinned.isNotEmpty()) "Tus formas" to pinned else null,
            if (others.isNotEmpty()) (if (pinned.isEmpty()) "Formas" else "Otras") to others else null,
        )
        val headerH = 22 * density
        val rows = sections.sumOf { (_, list) -> (list.size + columns - 1) / columns }
        val titleH = 24 * density
        val noteH = if (note != null) 18 * density else 0f
        val emptyH = if (items.isEmpty()) 22 * density else 0f
        // Tamaño natural; el lienzo se escala después si no cabe.
        val h = pad * 2 + titleH + noteH + emptyH + sections.size * headerH + rows * cellH
        val placement = SheetLayout.placeSheet(width.toFloat(), height.toFloat(), w, h, margin = 16 * density, debug = debug)

        canvas.save()
        canvas.translate(placement.left, placement.top)
        canvas.scale(placement.scale, placement.scale)
        box.set(0f, 0f, w, h)
        boxPaint.color = GlassTheme.panelSolid
        canvas.drawRoundRect(box, GlassTheme.RADIUS_CARD * density, GlassTheme.RADIUS_CARD * density, boxPaint)
        canvas.drawRoundRect(box, GlassTheme.RADIUS_CARD * density, GlassTheme.RADIUS_CARD * density, borderPaint)
        canvas.clipRect(box)

        var y = box.top + pad + 14 * density
        val title = if (context.isNotEmpty()) "Modo gestos · $context" else "Modo gestos: dibuja una forma"
        canvas.drawText(ellipsize(title, w - 2 * pad, textPaint), box.left + pad, y, textPaint)
        y += titleH - 14 * density
        if (note != null) {
            canvas.drawText(ellipsize(note, w - 2 * pad, dimPaint), box.left + pad, y + 12 * density, dimPaint)
            y += noteH
        }
        if (items.isEmpty()) {
            canvas.drawText(ellipsize("No hay formas activas. Actívalas en Ajustes → Formas.", w - 2 * pad, dimPaint), box.left + pad, y + 16 * density, dimPaint)
        }
        for ((header, list) in sections) {
            canvas.drawText(header.uppercase(), box.left + pad, y + 15 * density, headerPaint)
            y += headerH
            list.forEachIndexed { i, item ->
                val col = i % columns
                if (i > 0 && col == 0) y += cellH
                val x = box.left + pad + col * (cellW + COLUMN_GAP_DP * density)
                val iconRect = RectF(x, y + (cellH - icon) / 2, x + icon, y + (cellH + icon) / 2)
                val tile = RectF(iconRect).apply { inset(-3 * density, -3 * density) }
                canvas.drawRoundRect(tile, GlassTheme.RADIUS_ROW * density, GlassTheme.RADIUS_ROW * density, tilePaint)
                item.preview?.let { drawShape(canvas, it, RectF(iconRect).apply { inset(3 * density, 3 * density) }, item.name == highlight) }
                val text = item.help.ifEmpty { item.label }
                canvas.drawText(ellipsize(text, cellW - icon - 14 * density, textPaint), x + icon + 8 * density, y + cellH / 2 - 1 * density, textPaint)
                canvas.drawText(ellipsize(item.label, cellW - icon - 14 * density, dimPaint), x + icon + 8 * density, y + cellH / 2 + 12 * density, dimPaint)
            }
            y += cellH
        }
        canvas.restore()
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun drawShape(canvas: Canvas, points: List<P>, rect: RectF, highlighted: Boolean) {
        shapePaint.color = if (highlighted) GlassTheme.GREEN else GlassTheme.TEXT
        startPaint.color = GlassTheme.accent
        drawShape(canvas, points, rect, shapePaint, startPaint, density)
    }

    companion object {
        /** Hueco entre las dos columnas, en dp. */
        private const val COLUMN_GAP_DP = 16f

        /** Dibuja un trazo escalado para caber en [rect], manteniendo la proporción, con un punto donde empieza. */
        fun drawShape(canvas: Canvas, points: List<P>, rect: RectF, paint: Paint, startPaint: Paint?, density: Float) {
            if (points.size < 2) return
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val span = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1e-3f)
            val scale = minOf(rect.width(), rect.height()) / span
            val ox = rect.centerX() - (minX + maxX) / 2 * scale
            val oy = rect.centerY() - (minY + maxY) / 2 * scale
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = ox + p.x * scale
                val y = oy + p.y * scale
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
            // Punta de flecha al final para indicar el sentido.
            val a = points[points.size - 2]
            val b = points.last()
            val angle = Math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val head = 5 * density
            val ex = ox + b.x * scale
            val ey = oy + b.y * scale
            for (sign in listOf(-1, 1)) {
                val t = angle + Math.PI + sign * Math.PI / 6
                canvas.drawLine(ex, ey, ex + head * Math.cos(t).toFloat(), ey + head * Math.sin(t).toFloat(), paint)
            }
            startPaint?.let { canvas.drawCircle(ox + points[0].x * scale, oy + points[0].y * scale, 2.5f * density, it) }
        }

        fun items(store: ShapeStore, hostList: List<GestureListEntry>?): List<Item> {
            val enabled = store.enabled
            return if (hostList != null) {
                hostList.filter { it.name in enabled }.map {
                    Item(it.name, ShapeStore.label(it.name), it.help, it.pinned, store.preview(it.name))
                }
            } else {
                enabled.sortedBy { dev.mymouse.core.shapes.ShapeCatalog.IDS.indexOf(it).let { i -> if (i < 0) 999 else i } }
                    .map { Item(it, ShapeStore.label(it), "", false, store.preview(it)) }
            }
        }
    }
}
