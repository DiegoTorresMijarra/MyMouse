package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import dev.mymouse.core.theme.ThemeDef

/**
 * Muestra de un tema (docs/spec-v4.8-temas.md §3): un rectángulo de 72 × 96 dp que dibuja el fondo real en pequeño (el mismo
 * [UniverseDrawable] que las pantallas, con estrellas pero sin parpadeo). La elegida lleva un borde de acento de 2 dp.
 */
@SuppressLint("ViewConstructor")
class ThemeSwatchView(context: Context, private val theme: ThemeDef, private val selected: Boolean) : View(context) {
    private val d = resources.displayMetrics.density
    private val art = UniverseDrawable(d, theme, showStars = theme.stars, animated = false)
    private val clip = Path()
    private val rect = RectF()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((WIDTH_DP * d).toInt(), (HEIGHT_DP * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = GlassTheme.RADIUS_CARD * d
        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        art.setBounds(0, 0, width, height)
        art.draw(canvas)
        canvas.restore()
        // Una "tarjeta de cristal" en pequeño, para que se vea cómo queda el cristal sobre ese fondo.
        rect.set(10 * d, 14 * d, w - 10 * d, 38 * d)
        GlassTheme.rounded(context, GlassTheme.CARD_FILL, 6f, GlassTheme.BORDER).apply {
            setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            draw(canvas)
        }
        // Un punto del color de acento del tema.
        border.style = Paint.Style.FILL
        border.color = (0xFF shl 24) or theme.accent
        canvas.drawCircle(w / 2, 62 * d, 5 * d, border)
        // Borde: 2 dp de acento si es la elegida; si no, el fino de las tarjetas.
        border.style = Paint.Style.STROKE
        border.strokeWidth = if (selected) 2 * d else maxOf(1f, d)
        border.color = if (selected) GlassTheme.accent else GlassTheme.BORDER
        val half = border.strokeWidth / 2
        rect.set(half, half, w - half, h - half)
        canvas.drawRoundRect(rect, radius - half, radius - half, border)
    }

    companion object {
        const val WIDTH_DP = 72f
        const val HEIGHT_DP = 96f
    }
}

/**
 * Círculo de color del acento. Con [auto] (el acento del tema) lleva el texto "Auto" sobre el color del tema.
 * El elegido lleva un anillo del color de texto.
 */
@SuppressLint("ViewConstructor")
class AccentDotView(context: Context, private val rgb: Int, private val auto: Boolean, private val selected: Boolean) : View(context) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xFF shl 24) or rgb }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        color = GlassTheme.TEXT
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        color = GlassTheme.BORDER
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * d
        isFakeBoldText = true
        // Texto oscuro sobre colores claros y claro sobre colores oscuros.
        color = if (luminance(rgb) > 0.55) 0xFF10131A.toInt() else GlassTheme.TEXT
    }

    init {
        isClickable = true
        isFocusable = true
    }

    private fun luminance(c: Int): Double {
        val r = (c shr 16 and 0xFF) / 255.0
        val g = (c shr 8 and 0xFF) / 255.0
        val b = (c and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((SIZE_DP * d).toInt(), (SIZE_DP * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 3 * d
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, edge)
        if (selected) canvas.drawCircle(cx, cy, width / 2f - d, ring)
        if (auto) canvas.drawText("Auto", cx, cy - (label.ascent() + label.descent()) / 2, label)
    }

    companion object {
        const val SIZE_DP = 44f
    }
}
