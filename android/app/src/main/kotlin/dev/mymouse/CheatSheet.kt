package dev.mymouse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import dev.mymouse.core.layout.Area
import dev.mymouse.core.layout.SheetLayout

/**
 * Chuleta de gestos básicos en el centro del trackpad (docs/spec-v0.md §2.1.2).
 * Solo dibuja: no intercepta toques.
 */
class CheatSheet(private val density: Float) {

    enum class Icon { MOVE, TAP, TWO_TAP, SCROLL, PINCH, DRAG, THREE_UP, THREE_DOWN, THREE_SIDE, FOUR_HOLD, FOUR_PINCH, VOLUME }

    data class Row(val icon: Icon, val text: String)

    var rows: List<Row> = DEFAULT_ROWS

    /** Desenfoque de lo que hay detrás (Android 12+); sin él, la tarjeta usa el color translúcido sin desenfoque. */
    var backdrop: GlassTheme.BackdropBlur? = null

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.BORDER
        strokeWidth = density
    }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.ELEVATED }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT_SECONDARY
        textSize = GlassTheme.CARD_TITLE_SP * density
        letterSpacing = 0.06f
        isFakeBoldText = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT
        textSize = 14 * density
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.TEXT }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT
        textSize = 10 * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val box = RectF()
    private val path = Path()

    /**
     * Dibuja la chuleta en el hueco libre del trackpad. [debug] es lo que ocupa el modo depuración (null si está apagado): la chuleta
     * se coloca a su derecha en horizontal y debajo en vertical, y nunca se solapa con él (docs/spec-v4.7-universo.md §4). Si la
     * lista no cabe (móvil en horizontal, o con la depuración al lado), se dibuja encogida para que quepa entera.
     */
    fun draw(canvas: Canvas, width: Int, height: Int, debug: Area? = null) {
        if (rows.isEmpty()) return
        val pad = 14 * density
        val titleH = 26 * density
        val rowH = 40 * density
        val tileW = 52 * density
        val tileH = 32 * density
        // En horizontal (más ancho que alto) la lista se reparte en dos columnas.
        val cols = if (width > height && rows.size > 4) 2 else 1
        val perCol = (rows.size + cols - 1) / cols
        val columns = rows.chunked(perCol)
        val colGap = 20 * density
        val colW = columns.map { col -> tileW + 12 * density + col.maxOf { textPaint.measureText(it.text) } }
        val w = pad * 2 + colW.sum() + colGap * (columns.size - 1)
        val h = pad * 2 + titleH + rowH * perCol
        // Sitio libre y escala (la lógica pura está en `core`, con tests). Se dibuja a tamaño natural en (left, top) y el lienzo se escala.
        val placement = SheetLayout.placeSheet(width.toFloat(), height.toFloat(), w, h, margin = 16 * density, debug = debug)
        val scale = placement.scale
        val left = placement.left
        val top = placement.top
        val radius = GlassTheme.RADIUS_CARD * density

        // Tarjeta de cristal: desenfoque de lo de detrás si se puede y, si no, color translúcido; después, borde y brillo superior.
        val card = RectF(left, top, left + w * scale, top + h * scale)
        val blurred = backdrop?.draw(canvas, card, radius * scale, 0f, 0f, width, height) == true
        boxPaint.color = if (blurred) GlassTheme.CARD_FILL else GlassTheme.PANEL_SOLID
        canvas.drawRoundRect(card, radius * scale, radius * scale, boxPaint)
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        box.set(0f, 0f, w, h)
        // Brillo que se desvanece de forma continua en el 35 % superior (sin corte brusco).
        shinePaint.shader = LinearGradient(0f, 0f, 0f, h * 0.35f, GlassTheme.SHINE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, radius, radius, shinePaint)
        canvas.drawRoundRect(box, radius, radius, borderPaint)

        canvas.drawText("GESTOS BÁSICOS", pad, pad + titlePaint.textSize, titlePaint)
        val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2
        var colLeft = box.left + pad
        columns.forEachIndexed { c, col ->
            col.forEachIndexed { i, row ->
                val cy = box.top + pad + titleH + rowH * i + rowH / 2
                val tileLeft = colLeft
                // Icono en baldosa, como en el PC.
                canvas.drawRoundRect(tileLeft, cy - tileH / 2, tileLeft + tileW, cy + tileH / 2, GlassTheme.RADIUS_ROW * density, GlassTheme.RADIUS_ROW * density, tilePaint)
                drawIcon(canvas, row.icon, tileLeft + tileW / 2, cy)
                canvas.drawText(row.text, tileLeft + tileW + 12 * density, cy + baselineOffset, textPaint)
            }
            colLeft += colW[c] + colGap
        }
        canvas.restore()
    }

    private fun drawIcon(c: Canvas, icon: Icon, cx: Float, cy: Float) {
        val d = density
        val dot = 3.5f * d
        val ring = 7f * d
        when (icon) {
            Icon.MOVE -> {
                c.drawCircle(cx - 10 * d, cy, dot, iconFill)
                arrow(c, cx - 3 * d, cy, cx + 14 * d, cy)
            }
            Icon.TAP -> {
                c.drawCircle(cx, cy, dot, iconFill)
                c.drawCircle(cx, cy, ring, iconStroke)
            }
            Icon.TWO_TAP -> for (dx in listOf(-8 * d, 8 * d)) {
                c.drawCircle(cx + dx, cy, dot, iconFill)
                c.drawCircle(cx + dx, cy, ring, iconStroke)
            }
            Icon.SCROLL -> {
                c.drawCircle(cx - 9 * d, cy, dot, iconFill)
                c.drawCircle(cx - 1 * d, cy, dot, iconFill)
                arrow(c, cx + 11 * d, cy, cx + 11 * d, cy - 10 * d)
                arrow(c, cx + 11 * d, cy, cx + 11 * d, cy + 10 * d)
            }
            Icon.PINCH -> {
                c.drawCircle(cx - 4 * d, cy + 4 * d, dot, iconFill)
                c.drawCircle(cx + 4 * d, cy - 4 * d, dot, iconFill)
                arrow(c, cx - 7 * d, cy + 7 * d, cx - 15 * d, cy + 12 * d)
                arrow(c, cx + 7 * d, cy - 7 * d, cx + 15 * d, cy - 12 * d)
            }
            Icon.DRAG -> {
                c.drawCircle(cx - 10 * d, cy, dot, iconFill)
                c.drawCircle(cx - 10 * d, cy, ring, iconStroke)
                c.drawCircle(cx - 10 * d, cy, ring + 3 * d, iconStroke)
                arrow(c, cx + 1 * d, cy, cx + 16 * d, cy)
            }
            Icon.THREE_UP -> {
                threeDots(c, cx, cy)
                arrow(c, cx + 14 * d, cy + 5 * d, cx + 14 * d, cy - 9 * d)
            }
            Icon.THREE_DOWN -> {
                threeDots(c, cx, cy)
                arrow(c, cx + 14 * d, cy - 5 * d, cx + 14 * d, cy + 9 * d)
            }
            Icon.THREE_SIDE -> {
                threeDots(c, cx - 3 * d, cy)
                arrow(c, cx + 11 * d, cy, cx + 20 * d, cy)
                arrow(c, cx + 11 * d, cy, cx + 2 * d, cy)
            }
            Icon.FOUR_HOLD -> {
                for (dx in listOf(-15 * d, -5 * d, 5 * d, 15 * d)) {
                    c.drawCircle(cx + dx, cy, dot - 0.5f * d, iconFill)
                    c.drawCircle(cx + dx, cy, ring - 1.5f * d, iconStroke)
                }
            }
            Icon.FOUR_PINCH -> {
                // Cuatro dedos en cruz y dos flechas que se juntan.
                val fx = cx - 10 * d
                for ((dx, dy) in listOf(-6 * d to 0f, 6 * d to 0f, 0f to -6 * d, 0f to 6 * d)) {
                    c.drawCircle(fx + dx, cy + dy, 2.6f * d, iconFill)
                }
                arrow(c, cx + 12 * d, cy - 11 * d, cx + 12 * d, cy - 3 * d)
                arrow(c, cx + 12 * d, cy + 11 * d, cx + 12 * d, cy + 3 * d)
            }
            Icon.VOLUME -> {
                val r = RectF(cx - 17 * d, cy - 8 * d, cx + 17 * d, cy + 8 * d)
                c.drawRoundRect(r, 4 * d, 4 * d, iconStroke)
                c.drawText("VOL−×2", cx, cy - (iconText.ascent() + iconText.descent()) / 2, iconText)
            }
        }
    }

    private fun threeDots(c: Canvas, cx: Float, cy: Float) {
        for (dx in listOf(-12 * density, -4 * density, 4 * density)) c.drawCircle(cx + dx, cy, 3f * density, iconFill)
    }

    /** Flecha de (x0,y0) a (x1,y1) con punta en (x1,y1). */
    private fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        c.drawLine(x0, y0, x1, y1, iconStroke)
        val angle = Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
        val head = 4.5f * density
        path.reset()
        for (sign in listOf(-1, 1)) {
            val a = angle + Math.PI + sign * Math.PI / 6
            path.moveTo(x1, y1)
            path.lineTo(x1 + head * Math.cos(a).toFloat(), y1 + head * Math.sin(a).toFloat())
        }
        c.drawPath(path, iconStroke)
    }

    companion object {
        /** Gestos básicos, sin los de 3 y 4 dedos (ver [rowsFor]). */
        private val BASIC_ROWS = listOf(
            Row(Icon.MOVE, "Mover"),
            Row(Icon.TAP, "Clic"),
            Row(Icon.TWO_TAP, "Clic derecho"),
            Row(Icon.SCROLL, "Scroll"),
            Row(Icon.PINCH, "Zoom"),
            Row(Icon.DRAG, "Doble toque y mover: arrastrar"),
        )

        val DEFAULT_ROWS = rowsFor(windowsGestures = true, gestureMode = true)

        /** Filas de la chuleta: los gestos de 3 dedos solo si están activados, el modo gestos solo si está disponible. */
        fun rowsFor(windowsGestures: Boolean, gestureMode: Boolean): List<Row> = buildList {
            addAll(BASIC_ROWS)
            if (windowsGestures) {
                add(Row(Icon.THREE_UP, "3 dedos ↑: Vista de tareas"))
                add(Row(Icon.THREE_DOWN, "3 dedos ↓: Mostrar el escritorio"))
                add(Row(Icon.THREE_SIDE, "3 dedos ← →: Cambiar de app"))
            }
            if (gestureMode) add(Row(Icon.FOUR_HOLD, "4 dedos quietos: modo gestos"))
            add(Row(Icon.FOUR_PINCH, "4 dedos juntar / separar: widgets"))
        }
    }
}
