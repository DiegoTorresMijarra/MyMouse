package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.mymouse.core.shapes.P

/** Miniatura de una forma (para las listas de ajustes). */
@SuppressLint("ViewConstructor")
class ShapePreviewView(context: Context, private val points: List<P>?) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.TEXT
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val start = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.ACCENT }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = points ?: return
        val pad = 8 * density
        rect.set(pad, pad, width - pad, height - pad)
        GestureSheet.drawShape(canvas, p, rect, paint, start, density)
    }
}
