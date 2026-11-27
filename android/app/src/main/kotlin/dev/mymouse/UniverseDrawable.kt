package dev.mymouse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import dev.mymouse.core.theme.ThemeDef
import dev.mymouse.core.theme.Themes
import dev.mymouse.core.universe.Star
import dev.mymouse.core.universe.StarField

/**
 * El fondo "universo" de un tema (docs/spec-v4.7-universo.md §2, docs/spec-v4.8-temas.md §2): degradado diagonal de 1–3 colores,
 * de 0 a 3 nebulosas y estrellas con semilla fija. [showStars]: si se dibujan las estrellas (el tema las tiene y están activadas).
 * Las que parpadean solo se animan si [animated] (en el trackpad, no por defecto, para no gastar batería ni distraer); estático, se
 * dibuja el instante 0. Sin desenfoque. La generación de las estrellas y el parpadeo están en `core` ([StarField]) y tienen tests;
 * el PC usa el mismo algoritmo.
 */
class UniverseDrawable(
    private val density: Float,
    private val theme: ThemeDef,
    private val showStars: Boolean,
    private val animated: Boolean,
) : Drawable() {
    private val basePaint = Paint()
    private val nebulaPaints = theme.nebulas.map { Paint() }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloRadius = 3.5f * density
    private val startMs = SystemClock.uptimeMillis()
    private val frame = Runnable { invalidateSelf() }

    private var stars: List<Star> = emptyList()
    private var shadersW = 0
    private var shadersH = 0

    init {
        haloPaint.shader = RadialGradient(
            0f, 0f, haloRadius, intArrayOf(Color.argb(140, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP,
        )
    }

    private fun rgb(value: Int): Int = (0xFF shl 24) or (value and 0xFFFFFF)

    private fun nebula(cx: Float, cy: Float, radius: Float, color: Int, opacity: Float): RadialGradient {
        fun at(k: Float) = Color.argb((255 * opacity * k).toInt(), Color.red(rgb(color)), Color.green(rgb(color)), Color.blue(rgb(color)))
        return RadialGradient(cx, cy, radius, intArrayOf(at(1f), at(0.45f), at(0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    /** Los degradados dependen del tamaño: se rehacen solo cuando cambia. */
    private fun ensureShaders(w: Int, h: Int) {
        if (w == shadersW && h == shadersH) return
        shadersW = w
        shadersH = h
        val fw = w.toFloat()
        val fh = h.toFloat()
        // Una base de un solo color (OLED) se pasa como dos iguales: el degradado necesita al menos dos.
        val colors = theme.base.map { rgb(it) }.let { if (it.size == 1) listOf(it[0], it[0]) else it }.toIntArray()
        basePaint.shader = LinearGradient(0f, 0f, fw, fh, colors, null, Shader.TileMode.CLAMP)
        theme.nebulas.forEachIndexed { i, n ->
            nebulaPaints[i].shader = nebula(n.x * fw, n.y * fh, n.r * fw, n.color, n.opacity)
        }
        val count = if (showStars) Themes.starCount(theme, fw / density, fh / density) else 0
        if (stars.size != count) stars = StarField.generate(count)
    }

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        val w = b.width()
        val h = b.height()
        if (w <= 0 || h <= 0) return
        ensureShaders(w, h)
        val fw = w.toFloat()
        val fh = h.toFloat()
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.drawRect(0f, 0f, fw, fh, basePaint)
        for (paint in nebulaPaints) canvas.drawRect(0f, 0f, fw, fh, paint)

        val seconds = if (animated) (SystemClock.uptimeMillis() - startMs) / 1000.0 else 0.0
        for (star in stars) {
            val alpha = (StarField.opacityAt(star, seconds, theme.twinkleDepth) * 255).toInt().coerceIn(0, 255)
            val cx = star.x * fw
            val cy = star.y * fh
            if (star.halo) {
                haloPaint.alpha = alpha
                canvas.save()
                canvas.translate(cx, cy)
                canvas.drawCircle(0f, 0f, haloRadius + star.radius * density, haloPaint)
                canvas.restore()
            }
            starPaint.alpha = alpha
            canvas.drawCircle(cx, cy, star.radius * density, starPaint)
        }
        canvas.restore()

        // Unos 30 fotogramas por segundo, solo mientras se vea y haya estrellas que parpadean.
        if (animated && isVisible && theme.twinkleDepth > 0f && stars.any { it.twinkle }) scheduleSelf(frame, SystemClock.uptimeMillis() + FRAME_MS)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) unscheduleSelf(frame) else if (animated) invalidateSelf()
        return changed
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val FRAME_MS = 33L
    }
}
