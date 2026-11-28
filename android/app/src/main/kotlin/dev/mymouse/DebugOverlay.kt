package dev.mymouse

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import dev.mymouse.core.debug.ActionDescriber
import dev.mymouse.core.debug.DebugTexts
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.gesture.TouchInput
import dev.mymouse.core.layout.Area

/**
 * Modo depuración: una minipantalla con los dedos y sus trazos en vivo y, debajo, la lista de
 * acciones que se han enviado al PC ("Clic", "Doble clic", "Scroll ↓ 2"...). Se activa en Ajustes.
 */
class DebugOverlay(private val density: Float, private val res: Resources, texts: DebugTexts) {

    private data class TrailPoint(val x: Float, val y: Float, val t: Long)
    private data class Entry(val key: Long, var text: String, var t: Long)

    private val describer = ActionDescriber(texts)
    private val trails = LinkedHashMap<Int, ArrayDeque<TrailPoint>>()
    private val down = HashSet<Int>()
    private val entries = ArrayDeque<Entry>()
    private var maxFingers = 0

    /** Estado del enlace Bluetooth (informes por segundo e intervalo real), si lo hay. */
    var linkInfo: String? = null

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.panelSolid }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.BORDER
        strokeWidth = 1.5f * density
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * density }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * density
        color = GlassTheme.TEXT_SECONDARY
    }
    private val panel = RectF()

    /** true mientras haya algo que se desvanece: el llamador debe seguir redibujando. */
    val animating: Boolean get() = trails.isNotEmpty() || entries.isNotEmpty()

    fun onTouch(input: TouchInput) {
        val now = input.timeMs
        when (input) {
            is TouchInput.Down -> {
                down += input.id
                trails[input.id] = ArrayDeque<TrailPoint>().apply { addLast(TrailPoint(input.x, input.y, now)) }
                if (down.size > maxFingers) maxFingers = down.size
            }
            is TouchInput.Move -> trails[input.id]?.addLast(TrailPoint(input.x, input.y, now))
            is TouchInput.Up -> {
                down -= input.id
                trails[input.id]?.addLast(TrailPoint(input.x, input.y, now))
            }
            is TouchInput.Cancel -> {
                down.clear()
                trails.clear()
            }
        }
        if (down.isEmpty()) maxFingers = 0
    }

    fun onAction(action: OutputAction, nowMs: Long) {
        val label = describer.describe(action, nowMs) ?: return
        log(label.key, label.text, nowMs)
    }

    /** Anota una acción que no viene del trackpad (p. ej. un widget). */
    fun note(text: String, nowMs: Long) = log(-1, text, nowMs)

    private fun log(key: Long, text: String, now: Long) {
        val first = entries.firstOrNull()
        if (first != null && first.key == key && key >= 0) {
            first.text = text
            first.t = now
        } else {
            entries.addFirst(Entry(key, text, now))
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    /**
     * Todo lo que ocupa en el trackpad: la minipantalla y la lista de acciones de debajo (con sitio para las [MAX_ENTRIES] líneas).
     * La chuleta se coloca en el espacio libre que deja ([dev.mymouse.core.layout.SheetLayout.placeSheet]), así que no cambia aunque
     * la lista crezca o se vacíe. En horizontal, los textos se recortan al ancho de la minipantalla; en vertical pueden ocupar todo el ancho.
     */
    fun occupiedArea(width: Int, height: Int): Area {
        val m = 12 * density
        val w = width * 0.35f
        val h = w * height / width.coerceAtLeast(1)
        val right = if (width > height) m + w else width.toFloat()
        // Estado (16 dp bajo la minipantalla) + una línea de 20 dp por entrada + el descenso del último texto.
        val bottom = m + h + 16 * density + 20 * density * MAX_ENTRIES + 6 * density
        return Area(0f, 0f, right, bottom)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, now: Long, connected: Boolean) {
        expire(now)

        // Minipantalla con la misma proporción que el trackpad.
        val m = 12 * density
        val w = width * 0.35f
        val h = w * height / width.coerceAtLeast(1)
        val textWidth = if (width > height) w else width - 2 * m
        panel.set(m, m, m + w, m + h)
        panelPaint.color = GlassTheme.panelSolid
        canvas.drawRoundRect(panel, 8 * density, 8 * density, panelPaint)
        canvas.drawRoundRect(panel, 8 * density, 8 * density, borderPaint)
        val sx = w / width
        val sy = h / height

        for ((id, trail) in trails) {
            val color = COLORS[Math.floorMod(id, COLORS.size)]
            var prev: TrailPoint? = null
            for (p in trail) {
                val alpha = (255 * (1 - (now - p.t).toFloat() / TRAIL_MS)).toInt().coerceIn(0, 255)
                trailPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                prev?.let { canvas.drawLine(m + it.x * sx, m + it.y * sy, m + p.x * sx, m + p.y * sy, trailPaint) }
                prev = p
            }
            if (id in down) {
                val last = trail.last()
                fingerPaint.color = color
                canvas.drawCircle(m + last.x * sx, m + last.y * sy, 6 * density, fingerPaint)
            }
        }

        var y = panel.bottom + 16 * density
        canvas.drawText(
            ellipsize(
                res.getString(if (down.size == 1) R.string.debug_finger_one else R.string.debug_finger_many, down.size) + " · " +
                    res.getString(if (connected) R.string.debug_sending else R.string.debug_not_connected) + (linkInfo?.let { " · $it" } ?: ""),
                textWidth, smallPaint,
            ),
            m, y, smallPaint,
        )
        for (e in entries) {
            y += 20 * density
            val age = now - e.t
            val alpha = if (age < FADE_START_MS) 255 else (255 * (1 - (age - FADE_START_MS).toFloat() / FADE_MS)).toInt()
            textPaint.color = Color.argb(alpha.coerceIn(0, 255), 0xF4, 0xF5, 0xFA)
            canvas.drawText(ellipsize(e.text, textWidth, textPaint), m, y, textPaint)
        }
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun expire(now: Long) {
        val it = trails.entries.iterator()
        while (it.hasNext()) {
            val (id, trail) = it.next()
            while (trail.size > 1 && now - trail.first().t > TRAIL_MS) trail.removeFirst()
            if (id !in down && now - trail.last().t > TRAIL_MS) it.remove()
        }
        while (entries.isNotEmpty() && now - entries.last().t > FADE_START_MS + FADE_MS) entries.removeLast()
    }

    private companion object {
        const val TRAIL_MS = 1_000L
        const val FADE_START_MS = 4_000L
        const val FADE_MS = 2_000L
        const val MAX_ENTRIES = 6
        val COLORS = intArrayOf(
            Color.rgb(80, 170, 255), Color.rgb(255, 150, 60), Color.rgb(110, 220, 110),
            Color.rgb(230, 90, 200), Color.rgb(240, 220, 80),
        )
    }
}
