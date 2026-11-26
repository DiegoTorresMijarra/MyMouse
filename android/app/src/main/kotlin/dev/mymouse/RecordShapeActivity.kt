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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
            textSize = 16f
            setTextColor(Color.rgb(230, 230, 230))
        }
        saveButton = Button(this).apply {
            text = "Guardar"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                store.put(name, samples.toList())
                finish()
            }
        }
        val again = Button(this).apply {
            text = "Repetir"
            isAllCaps = false
            setOnClickListener { reset() }
        }
        val cancel = Button(this).apply {
            text = "Cancelar"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        canvasView = DrawView(this) { stroke -> onStroke(stroke) }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * density).toInt()
            setPadding(p, p, p, p)
            addView(TextView(this@RecordShapeActivity).apply {
                text = "Grabar \"${ShapeStore.label(name)}\""
                textSize = 22f
                setTextColor(Color.WHITE)
            })
            addView(info)
            addView(LinearLayout(this@RecordShapeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(saveButton)
                addView(again)
                addView(cancel)
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(top)
            addView(canvasView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        reset()
    }

    private fun reset() {
        samples.clear()
        canvasView.clearSamples()
        saveButton.isEnabled = false
        info.text = "Dibújala con un dedo, de un solo trazo (1 de $SAMPLES)."
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
        saveButton.isEnabled = true
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
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(60, 110, 160)
            strokeWidth = 2 * density
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
            val m = border.strokeWidth
            canvas.drawRoundRect(m, m, width - m, height - m, 12 * density, 12 * density, border)
            paint.color = Color.argb(110, 120, 200, 255)
            samples.forEach { draw(canvas, it) }
            paint.color = Color.rgb(120, 200, 255)
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
