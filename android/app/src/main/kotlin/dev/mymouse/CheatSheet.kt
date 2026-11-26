package dev.mymouse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Chuleta de gestos básicos en el centro del trackpad (docs/spec-v0.md §2.1.2).
 * Solo dibuja: no intercepta toques.
 */
class CheatSheet(private val density: Float) {

    enum class Icon { MOVE, TAP, TWO_TAP, SCROLL, PINCH, DRAG, VOLUME }

    data class Row(val icon: Icon, val text: String)

    var rows: List<Row> = DEFAULT_ROWS

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 24, 24) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 140, 140)
        textSize = 14 * density
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 120, 120) }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 120, 120)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 120, 120)
        textSize = 10 * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val box = RectF()
    private val path = Path()

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (rows.isEmpty()) return
        val pad = 14 * density
        val rowH = 30 * density
        val iconW = 44 * density
        val textW = rows.maxOf { textPaint.measureText(it.text) }
        val w = (pad * 2 + iconW + 10 * density + textW).coerceAtMost(width - 32 * density)
        val h = pad * 2 + rowH * rows.size
        box.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawRoundRect(box, 14 * density, 14 * density, boxPaint)

        val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2
        rows.forEachIndexed { i, row ->
            val cy = box.top + pad + rowH * i + rowH / 2
            drawIcon(canvas, row.icon, box.left + pad + iconW / 2, cy)
            canvas.drawText(row.text, box.left + pad + iconW + 10 * density, cy + baselineOffset, textPaint)
        }
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
            Icon.VOLUME -> {
                val r = RectF(cx - 17 * d, cy - 8 * d, cx + 17 * d, cy + 8 * d)
                c.drawRoundRect(r, 4 * d, 4 * d, iconStroke)
                c.drawText("VOL−×2", cx, cy - (iconText.ascent() + iconText.descent()) / 2, iconText)
            }
        }
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
        val DEFAULT_ROWS = listOf(
            Row(Icon.MOVE, "Mover"),
            Row(Icon.TAP, "Clic"),
            Row(Icon.TWO_TAP, "Clic derecho"),
            Row(Icon.SCROLL, "Scroll"),
            Row(Icon.PINCH, "Zoom"),
            Row(Icon.DRAG, "Doble toque y mover: arrastrar"),
        )
    }
}
