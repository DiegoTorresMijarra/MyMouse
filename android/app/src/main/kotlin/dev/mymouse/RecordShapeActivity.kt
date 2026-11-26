package dev.mymouse

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.mymouse.core.shapes.P

/**
 * Grabar una forma: se dibuja [SAMPLES] veces y se comprueba que no se confunda con las activas
 * (docs/spec-v1-gestos.md §5). Sirve para formas propias y para "tu versión" de una incluida.
 */
class RecordShapeActivity : Activity() {

    private lateinit var name: String
    private lateinit var store: ShapeStore
    private lateinit var info: TextView
    private lateinit var saveButton: Button
    private lateinit var canvasView: DrawView
    private val samples = mutableListOf<List<P>>()
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        name = intent.getStringExtra(EXTRA_NAME) ?: run { finish(); return }
        store = ShapeStore(this)

        info = TextView(this).apply {
            textSize = 15f
            setTextColor(GlassTheme.TEXT)
        }
        saveButton = GlassTheme.button(this, "Guardar", accent = true) {
            store.put(name, samples.toList())
            finish()
        }
        val again = GlassTheme.button(this, "Repetir") { reset() }
        val cancel = GlassTheme.button(this, "Cancelar") { finish() }
        canvasView = DrawView(this) { stroke -> onStroke(stroke) }

        val screen = GlassTheme.Screen(this, "Grabar \"${ShapeStore.label(name)}\"", scrollable = false)
        val card = screen.card(null)
        card.addView(info)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(saveButton)
            addView(again)
            addView(cancel)
        })
        screen.content.addView(canvasView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = (12 * density).toInt()
        })
        screen.setContentView()
        reset()
    }

    private fun reset() {
        samples.clear()
        canvasView.clearSamples()
        setSaveEnabled(false)
        info.text = "Dibújala con un dedo, de un solo trazo (1 de $SAMPLES)."
    }

    private fun setSaveEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
        saveButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun onStroke(stroke: List<P>) {
        val xs = stroke.map { it.x }
        val ys = stroke.map { it.y }
        if ((xs.max() - xs.min()) < 40 * density && (ys.max() - ys.min()) < 40 * density) {
            info.text = "Demasiado pequeña: dibújala más grande (${samples.size + 1} de $SAMPLES)."
            return
        }
        samples += stroke
        canvasView.addSample(stroke)
        if (samples.size < SAMPLES) {
            info.text = "Otra vez (${samples.size + 1} de $SAMPLES)."
            return
        }
        info.text = check()
        setSaveEnabled(true)
    }

    /** ¿Se confunde con alguna forma activa? (La lógica está en `core`, compartida con las formas que llegan del PC.) */
    private fun check(): String {
        val clashes = store.clashes(name, samples, minSizePx = 40 * density)
        return if (clashes.isEmpty()) {
            "Lista. No se parece a ninguna otra forma activa."
        } else {
            "Ojo: se confunde con ${clashes.joinToString { "\"${ShapeStore.label(it)}\"" }}. " +
                "Puedes guardarla igualmente, repetirla de otra manera o desactivar esa forma."
        }
    }

    /** Lienzo: dibuja las muestras grabadas (atenuadas) y el trazo en curso. */
    @SuppressLint("ViewConstructor")
    private class DrawView(context: Context, private val onStroke: (List<P>) -> Unit) : View(context) {
        private val density = resources.displayMetrics.density
        private val current = mutableListOf<P>()
        private val samples = mutableListOf<List<P>>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5 * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            // El lienzo es un panel de cristal (radio 16).
            background = GlassTheme.rounded(context, GlassTheme.PANEL_FILL, GlassTheme.RADIUS_PANEL, GlassTheme.BORDER)
        }

        fun clearSamples() {
            samples.clear()
            invalidate()
        }

        fun addSample(s: List<P>) {
            samples += s
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    current.clear()
                    current += P(e.x, e.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    for (h in 0 until e.historySize) current += P(e.getHistoricalX(0, h), e.getHistoricalY(0, h))
                    current += P(e.x, e.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> current.clear() // solo un dedo
                MotionEvent.ACTION_UP -> {
                    current += P(e.x, e.y)
                    if (current.size >= 2) onStroke(current.toList())
                    current.clear()
                }
                MotionEvent.ACTION_CANCEL -> current.clear()
            }
            invalidate()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = Color.argb(110, 0x35, 0x74, 0xF0)
            samples.forEach { draw(canvas, it) }
            paint.color = GlassTheme.TEXT
            draw(canvas, current)
        }

        private fun draw(canvas: Canvas, pts: List<P>) {
            if (pts.size < 2) return
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (k in 1 until pts.size) path.lineTo(pts[k].x, pts[k].y)
            canvas.drawPath(path, paint)
        }
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val SAMPLES = 3
    }
}
