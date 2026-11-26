package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.mymouse.core.debug.WidgetDescriber
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import dev.mymouse.core.widgets.Box
import dev.mymouse.core.widgets.WidgetEvent
import dev.mymouse.core.widgets.WidgetLayout
import dev.mymouse.core.widgets.WidgetPanelController
import dev.mymouse.core.widgets.WidgetTarget

/**
 * Panel de widgets junto al trackpad (docs/spec-v2-widgets.md §2): botones, control multimedia y volumen.
 * Solo dibuja y traduce los toques: la rejilla y la máquina de toques están en `core`
 * ([WidgetLayout], [WidgetPanelController]) y tienen tests.
 *
 * [send] envía una orden al PC; [debug] anota la acción en el modo depuración del trackpad.
 */
@SuppressLint("ViewConstructor")
class WidgetPanelView(
    context: Context,
    private val send: (Message) -> Unit,
    private val debug: (String) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val controller = WidgetPanelController(slopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private var layout = WidgetLayout.Result.EMPTY

    /** Panel a la derecha (pantalla horizontal): menos columnas y la línea de separación a la izquierda. */
    var landscape = false
        set(value) {
            if (field == value) return
            field = value
            relayout()
        }

    /** Widgets del último `WIDGET_LAYOUT`. Vacío = no hay panel. */
    var entries: List<WidgetEntry> = emptyList()
        private set

    val hasWidgets get() = entries.isNotEmpty()

    private var volumeState: Message.VolumeState? = null
    private var mediaState: Message.MediaState? = null
    private var flashTarget: WidgetTarget? = null
    private var flashUntil = 0L
    private var pointerId = -1

    /** Un layout nuevo invalida los ids anteriores: se vacían también los estados. */
    fun setEntries(list: List<WidgetEntry>) {
        entries = list
        volumeState = null
        mediaState = null
        relayout()
    }

    /** El host dejó de estar disponible (o es anterior a la v2): sin widgets. */
    fun clear() = setEntries(emptyList())

    /** Cambio de volumen en el PC. Se ignora mientras el usuario arrastra la barra. */
    fun onVolumeState(state: Message.VolumeState) {
        if (controller.draggingVolume) return
        volumeState = state
        invalidate()
    }

    fun onMediaState(state: Message.MediaState) {
        mediaState = state
        invalidate()
    }

    // ---- Disposición ----

    private val divider get() = dp(1f)
    private val contentLeft get() = if (landscape) divider else 0f
    private val contentTop get() = if (landscape) 0f else divider

    private fun metrics(contentWidth: Float): WidgetLayout.Metrics {
        val gap = dp(6f)
        val cellWidth = WidgetLayout.cellWidth(contentWidth, WidgetLayout.columnsFor(landscape), gap)
        return WidgetLayout.Metrics(
            buttonHeight = (cellWidth * 0.9f).coerceIn(dp(56f), dp(96f)),
            mediaHeight = dp(116f),
            mediaButtonsHeight = dp(44f),
            volumeHeight = dp(56f),
            volumeIconWidth = dp(52f),
            volumeValueWidth = dp(44f),
            gap = gap,
        )
    }

    private fun relayout() {
        val w = width - contentLeft
        val h = height - contentTop
        layout = if (w > 0f && h > 0f && entries.isNotEmpty()) {
            WidgetLayout.compute(entries, WidgetLayout.columnsFor(landscape), w, metrics(w))
        } else {
            WidgetLayout.Result.EMPTY
        }
        controller.setLayout(layout, maxOf(h, 0f))
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    // ---- Toques ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val events: List<WidgetEvent> = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                controller.onDown(event.x - contentLeft, event.y - contentTop, event.eventTime)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(pointerId)
                if (i < 0) emptyList() else controller.onMove(event.getX(i) - contentLeft, event.getY(i) - contentTop, event.eventTime)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Solo cuenta el primer dedo; los demás se ignoran.
                val i = event.actionIndex
                if (event.getPointerId(i) != pointerId) {
                    emptyList()
                } else {
                    pointerId = -1
                    controller.onUp(event.getX(i) - contentLeft, event.getY(i) - contentTop, event.eventTime)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerId = -1
                controller.onCancel(event.eventTime)
            }
            else -> emptyList()
        }
        events.forEach(::handle)
        invalidate()
        return true
    }

    private fun handle(event: WidgetEvent) {
        when (event) {
            is WidgetEvent.Press -> {
                send(Message.WidgetPress(event.id))
                debug(WidgetDescriber.press(entries.firstOrNull { it.id == event.id }?.label.orEmpty()))
                feedback(event.target)
            }
            is WidgetEvent.Media -> {
                send(Message.MediaControl(event.command))
                debug(WidgetDescriber.media(event.command))
                feedback(event.target)
            }
            is WidgetEvent.SetVolume -> {
                send(Message.VolumeSet(event.level))
                // La barra sigue al dedo sin esperar al eco del PC.
                volumeState = Message.VolumeState(event.level, volumeState?.muted ?: false)
                if (event.final) debug(WidgetDescriber.volume(event.level))
            }
            is WidgetEvent.ToggleMute -> {
                val muted = !(volumeState?.muted ?: false)
                send(Message.VolumeMute(muted))
                volumeState = Message.VolumeState(volumeState?.level ?: 0, muted)
                debug(WidgetDescriber.mute(muted))
                feedback(event.target)
            }
        }
    }

    /** Spec §2.1: ilumina 150 ms y vibra 20 ms. */
    private fun feedback(target: WidgetTarget) {
        flashTarget = target
        flashUntil = SystemClock.uptimeMillis() + FLASH_MS
        postInvalidateDelayed(FLASH_MS + 10)
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: SecurityException) {
            // Sin permiso de vibración: no es imprescindible.
        }
    }

    private fun isFlashing(target: WidgetTarget) = flashTarget == target && SystemClock.uptimeMillis() < flashUntil

    // ---- Dibujo ----

    private val bgPaint = Paint().apply { color = Color.rgb(14, 14, 14) }
    private val dividerPaint = Paint().apply { color = Color.rgb(70, 70, 70) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val iconText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.rgb(215, 215, 215)
        textSize = dp(11f)
    }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 235, 235)
        textSize = dp(15f)
        isFakeBoldText = true
    }
    private val artistText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(150, 150, 150)
        textSize = dp(12f)
    }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.rgb(220, 220, 220)
        textSize = dp(14f)
    }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // Línea fina que separa el panel del trackpad.
        if (landscape) canvas.drawRect(0f, 0f, divider, height.toFloat(), dividerPaint)
        else canvas.drawRect(0f, 0f, width.toFloat(), divider, dividerPaint)

        canvas.save()
        canvas.clipRect(contentLeft, contentTop, width.toFloat(), height.toFloat())
        canvas.translate(contentLeft, contentTop - controller.scrollY)
        layout.cells.forEachIndexed { i, cell ->
            when (cell.entry.kind) {
                WidgetKind.BUTTON -> drawButton(canvas, i, cell)
                WidgetKind.MEDIA -> drawMedia(canvas, i, cell)
                WidgetKind.VOLUME -> drawVolume(canvas, i, cell)
            }
        }
        canvas.restore()

        // Indicador de scroll discreto cuando no cabe todo.
        val max = WidgetLayout.maxScroll(layout.contentHeight, height - contentTop)
        if (max > 0f) {
            val viewH = height - contentTop
            val barH = viewH * viewH / layout.contentHeight
            val top = contentTop + (viewH - barH) * (controller.scrollY / max)
            fill.color = Color.argb(110, 200, 200, 200)
            rect.set(width - dp(3f), top, width - dp(1f), top + barH)
            canvas.drawRoundRect(rect, dp(1f), dp(1f), fill)
        }
    }

    private fun card(canvas: Canvas, box: Box, color: Int) {
        fill.color = color
        rect.set(box.x, box.y, box.right, box.bottom)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
    }

    private fun buttonColor(active: Boolean, flashing: Boolean) = when {
        flashing -> Color.rgb(70, 130, 200)
        active -> Color.rgb(85, 85, 85)
        else -> Color.rgb(44, 44, 44)
    }

    private fun drawButton(canvas: Canvas, index: Int, cell: WidgetLayout.Cell) {
        val target = WidgetTarget.Button(index)
        val box = cell.box
        card(canvas, box, buttonColor(controller.pressed == target, isFlashing(target)))

        // Icono grande arriba; sin icono, las 2 primeras letras de la etiqueta.
        val icon = cell.entry.icon.ifEmpty { firstLetters(cell.entry.label, 2) }
        iconText.color = Color.rgb(235, 235, 235)
        iconText.textSize = box.height * 0.36f
        canvas.drawText(icon, box.centerX, box.y + box.height * 0.5f, iconText)
        canvas.drawText(ellipsize(cell.entry.label, box.width - dp(8f), labelText), box.centerX, box.bottom - dp(9f), labelText)
    }

    private fun drawMedia(canvas: Canvas, index: Int, cell: WidgetLayout.Cell) {
        val parts = cell.media ?: return
        card(canvas, cell.box, Color.rgb(34, 34, 34))

        val state = mediaState
        val text = parts.text
        if (state == null || (state.title.isEmpty() && state.artist.isEmpty())) {
            canvas.drawText("Nada en reproducción", text.x, text.y + dp(16f), artistText)
        } else {
            canvas.drawText(ellipsize(state.title, text.width, titleText), text.x, text.y + dp(16f), titleText)
            canvas.drawText(ellipsize(state.artist, text.width, artistText), text.x, text.y + dp(34f), artistText)
        }

        val playing = state?.playing == true
        mediaButton(canvas, parts.previous, WidgetTarget.MediaButton(index, MediaCommand.PREVIOUS)) { b -> drawSkip(canvas, b, forward = false) }
        mediaButton(canvas, parts.playPause, WidgetTarget.MediaButton(index, MediaCommand.PLAY_PAUSE)) { b ->
            // El icono refleja el estado: ▶ en pausa, ⏸ reproduciendo.
            if (playing) drawPause(canvas, b) else drawPlay(canvas, b)
        }
        mediaButton(canvas, parts.next, WidgetTarget.MediaButton(index, MediaCommand.NEXT)) { b -> drawSkip(canvas, b, forward = true) }
    }

    private inline fun mediaButton(canvas: Canvas, box: Box, target: WidgetTarget, drawIcon: (Box) -> Unit) {
        card(canvas, box, buttonColor(controller.pressed == target, isFlashing(target)))
        drawIcon(box)
    }

    // Iconos dibujados con trazados: no dependen de que la fuente del móvil tenga ⏮ ⏯ ⏭.
    private fun iconSize(box: Box) = minOf(box.height * 0.2f, dp(9f))

    private fun drawPlay(canvas: Canvas, box: Box) {
        val s = iconSize(box)
        fill.color = Color.rgb(235, 235, 235)
        path.reset()
        path.moveTo(box.centerX - s * 0.7f, box.centerY - s)
        path.lineTo(box.centerX + s * 0.9f, box.centerY)
        path.lineTo(box.centerX - s * 0.7f, box.centerY + s)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawPause(canvas: Canvas, box: Box) {
        val s = iconSize(box)
        fill.color = Color.rgb(235, 235, 235)
        canvas.drawRect(box.centerX - s * 0.8f, box.centerY - s, box.centerX - s * 0.2f, box.centerY + s, fill)
        canvas.drawRect(box.centerX + s * 0.2f, box.centerY - s, box.centerX + s * 0.8f, box.centerY + s, fill)
    }

    /** ⏭ (triángulo y barra a su derecha) o, con [forward] = false, ⏮ (su imagen en el espejo). */
    private fun drawSkip(canvas: Canvas, box: Box, forward: Boolean) {
        val s = iconSize(box)
        val d = if (forward) 1f else -1f
        fill.color = Color.rgb(235, 235, 235)
        path.reset()
        path.moveTo(box.centerX - d * s * 0.9f, box.centerY - s)
        path.lineTo(box.centerX + d * s * 0.5f, box.centerY)
        path.lineTo(box.centerX - d * s * 0.9f, box.centerY + s)
        path.close()
        canvas.drawPath(path, fill)
        val barLeft = minOf(box.centerX + d * s * 0.6f, box.centerX + d * s * 1.0f)
        val barRight = maxOf(box.centerX + d * s * 0.6f, box.centerX + d * s * 1.0f)
        canvas.drawRect(barLeft, box.centerY - s, barRight, box.centerY + s, fill)
    }

    private fun drawVolume(canvas: Canvas, index: Int, cell: WidgetLayout.Cell) {
        val parts = cell.volume ?: return
        card(canvas, cell.box, Color.rgb(34, 34, 34))

        val state = volumeState
        val muted = state?.muted == true
        val level = if (controller.draggingVolume) controller.dragLevel else state?.level ?: 0

        // Icono de altavoz (pulsar = silenciar / quitar el silencio).
        val iconTarget = WidgetTarget.MuteIcon(index)
        if (controller.pressed == iconTarget || isFlashing(iconTarget)) {
            card(canvas, Box(parts.icon.x + dp(4f), parts.icon.y + dp(4f), parts.icon.width - dp(8f), parts.icon.height - dp(8f)),
                buttonColor(controller.pressed == iconTarget, isFlashing(iconTarget)))
        }
        drawSpeaker(canvas, parts.icon, muted)

        // Pista, relleno y pulgar.
        val bar = parts.bar
        val trackH = dp(6f)
        val cy = bar.centerY
        fill.color = Color.rgb(70, 70, 70)
        rect.set(bar.x, cy - trackH / 2, bar.right, cy + trackH / 2)
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, fill)
        val x = bar.x + bar.width * level / 100f
        fill.color = if (muted) Color.rgb(120, 120, 120) else Color.rgb(80, 170, 255)
        rect.set(bar.x, cy - trackH / 2, x, cy + trackH / 2)
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, fill)
        fill.color = Color.rgb(240, 240, 240)
        canvas.drawCircle(x, cy, if (controller.draggingVolume) dp(11f) else dp(9f), fill)

        valueText.color = if (muted) Color.rgb(130, 130, 130) else Color.rgb(220, 220, 220)
        canvas.drawText(level.toString(), parts.value.centerX, parts.value.centerY + dp(5f), valueText)
    }

    private fun drawSpeaker(canvas: Canvas, box: Box, muted: Boolean) {
        val u = dp(1f)
        val cx = box.centerX - 3 * u
        val cy = box.centerY
        fill.color = Color.rgb(235, 235, 235)
        path.reset()
        path.moveTo(cx - 9 * u, cy - 4 * u)
        path.lineTo(cx - 4 * u, cy - 4 * u)
        path.lineTo(cx + 2 * u, cy - 9 * u)
        path.lineTo(cx + 2 * u, cy + 9 * u)
        path.lineTo(cx - 4 * u, cy + 4 * u)
        path.lineTo(cx - 9 * u, cy + 4 * u)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = if (muted) Color.rgb(255, 120, 100) else Color.rgb(235, 235, 235)
        if (muted) {
            canvas.drawLine(cx + 6 * u, cy - 5 * u, cx + 14 * u, cy + 5 * u, stroke)
            canvas.drawLine(cx + 14 * u, cy - 5 * u, cx + 6 * u, cy + 5 * u, stroke)
        } else {
            rect.set(cx - 2 * u, cy - 6 * u, cx + 10 * u, cy + 6 * u)
            canvas.drawArc(rect, -45f, 90f, false, stroke)
            rect.set(cx - 4 * u, cy - 10 * u, cx + 14 * u, cy + 10 * u)
            canvas.drawArc(rect, -45f, 90f, false, stroke)
        }
    }

    /** Las primeras [count] letras (puntos de código, para no partir un emoji) en mayúsculas. */
    private fun firstLetters(text: String, count: Int): String {
        val end = if (text.codePointCount(0, text.length) <= count) text.length else text.offsetByCodePoints(0, count)
        return text.substring(0, end).uppercase()
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private companion object {
        const val FLASH_MS = 150L
    }
}
