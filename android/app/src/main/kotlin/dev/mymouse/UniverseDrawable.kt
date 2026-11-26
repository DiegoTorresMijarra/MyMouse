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
import dev.mymouse.core.universe.Star
import dev.mymouse.core.universe.StarField

/**
 * El fondo "universo" (docs/spec-v4.7-universo.md §2): degradado diagonal, tres nebulosas y estrellas con semilla fija. Las estrellas
 * que parpadean solo se animan si [animated] (todas las pantallas menos el trackpad, que es estático para no gastar batería ni
 * distraer); estático, se dibuja el instante 0. Sin desenfoque. La generación de las estrellas y el parpadeo están en `core`
 * ([StarField]) y tienen tests; el PC usa el mismo algoritmo.
 */
class UniverseDrawable(private val density: Float, private val animated: Boolean) : Drawable() {
    private val basePaint = Paint()
    private val violet = Paint()
    private val blue = Paint()
    private val magenta = Paint()
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

    private fun nebula(cx: Float, cy: Float, radius: Float, rgb: Int, opacity: Float): RadialGradient {
        fun at(k: Float) = Color.argb((255 * opacity * k).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        return RadialGradient(cx, cy, radius, intArrayOf(at(1f), at(0.45f), at(0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    /** Los degradados dependen del tamaño: se rehacen solo cuando cambia. */
    private fun ensureShaders(w: Int, h: Int) {
        if (w == shadersW && h == shadersH) return
        shadersW = w
        shadersH = h
        val fw = w.toFloat()
        val fh = h.toFloat()
        basePaint.shader = LinearGradient(
            0f, 0f, fw, fh, intArrayOf(GlassTheme.BASE_START, GlassTheme.BASE_MID, GlassTheme.BASE_END), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        violet.shader = nebula(0.10f * fw, 0.06f * fh, 0.70f * fw, Color.parseColor("#7C3AED"), 0.35f) // arriba a la izquierda
        blue.shader = nebula(0.90f * fw, 0.95f * fh, 0.60f * fw, Color.parseColor("#2563EB"), 0.28f) // abajo a la derecha
        magenta.shader = nebula(0.85f * fw, 0.50f * fh, 0.40f * fw, Color.parseColor("#DB2777"), 0.18f) // centro-derecha
        val count = StarField.countFor(fw / density, fh / density)
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
        canvas.drawRect(0f, 0f, fw, fh, violet)
        canvas.drawRect(0f, 0f, fw, fh, blue)
        canvas.drawRect(0f, 0f, fw, fh, magenta)

        val seconds = if (animated) (SystemClock.uptimeMillis() - startMs) / 1000.0 else 0.0
        for (star in stars) {
            val alpha = (StarField.opacityAt(star, seconds) * 255).toInt().coerceIn(0, 255)
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
        if (animated && isVisible && stars.any { it.twinkle }) scheduleSelf(frame, SystemClock.uptimeMillis() + FRAME_MS)
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
