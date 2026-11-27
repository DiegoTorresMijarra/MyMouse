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
import dev.mymouse.core.widgets.PanelReveal
import dev.mymouse.core.widgets.VolumeGeometry
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

    /** Durante la animación de mostrar u ocultar el panel no se aceptan toques (se consumen sin hacer nada). */
    var inputBlocked = false

    private var revealStartedAt = 0L

    /** Empieza el fundido escalonado de los widgets (al mostrar el panel). */
    fun playReveal() {
        revealStartedAt = SystemClock.uptimeMillis()
        invalidate()
    }

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

    /** Margen interior del panel de cristal: las tarjetas no tocan el borde. */
    private val inset get() = dp(8f)
    private val contentLeft get() = inset
    private val contentTop get() = inset

    /** Desenfoque de lo que hay detrás (Android 12+); sin él, el panel usa el color translúcido sin desenfoque. */
    var backdrop: GlassTheme.BackdropBlur? = null

    /** Modo ventana: panel de cristal con radio 16. En modo bloqueado, a pantalla completa y sin radio. */
    var framed = true
        set(value) {
            field = value
            invalidate()
        }

    private fun metrics(contentWidth: Float): WidgetLayout.Metrics {
        val gap = dp(6f)
        val cellWidth = WidgetLayout.cellWidth(contentWidth, WidgetLayout.columnsFor(landscape), gap)
        return WidgetLayout.Metrics(
            buttonHeight = (cellWidth * 0.9f).coerceIn(dp(56f), dp(96f)),
            mediaHeight = dp(116f),
            mediaButtonsHeight = dp(44f),
            // Volumen (docs/spec-v4.7-universo.md §4): altavoz de 44 dp, pista de 4 dp con pulgar de 18 dp, número de 3 cifras.
            volumeHeight = dp(56f),
            volumeIconWidth = dp(44f),
            volumeValueWidth = dp(32f),
            gap = gap,
            volumeTrackHeight = dp(4f),
            volumeThumbRadius = dp(9f),
            volumeMinTrack = dp(96f),
            volumeValueLineHeight = dp(18f),
        )
    }

    private fun relayout() {
        val w = width - 2 * contentLeft
        val h = height - 2 * contentTop
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
        if (inputBlocked) return true
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

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.BORDER
        strokeWidth = dp(1f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val iconText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = GlassTheme.TEXT
        textSize = dp(11f)
    }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT
        textSize = dp(15f)
        isFakeBoldText = true
    }
    private val artistText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT_SECONDARY
        textSize = dp(12f)
    }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = GlassTheme.TEXT
        textSize = dp(14f)
    }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        drawPanel(canvas)

        canvas.save()
        canvas.clipRect(contentLeft, contentTop, width - contentLeft, height - contentTop)
        canvas.translate(contentLeft, contentTop - controller.scrollY)
        val revealElapsed = SystemClock.uptimeMillis() - revealStartedAt
        val revealing = revealStartedAt != 0L && revealElapsed < PanelReveal.totalMs(layout.rowOfCell.maxOrNull()?.plus(1) ?: 0)
        layout.cells.forEachIndexed { i, cell ->
            // Fundido escalonado por filas al mostrar el panel (docs/spec-v3.1-ajustes.md §1.1).
            val alpha = if (revealing) PanelReveal.rowAlpha(layout.rowOfCell[i], revealElapsed) else 1f
            val saved = if (alpha < 1f) {
                canvas.saveLayerAlpha(cell.box.x, cell.box.y, cell.box.right, cell.box.bottom, (alpha * 255).toInt())
            } else {
                -1
            }
            when (cell.entry.kind) {
                WidgetKind.BUTTON -> drawButton(canvas, i, cell)
                WidgetKind.MEDIA -> drawMedia(canvas, i, cell)
                WidgetKind.VOLUME -> drawVolume(canvas, i, cell)
            }
            if (saved >= 0) canvas.restoreToCount(saved)
        }
        canvas.restore()
        if (revealing) postInvalidateOnAnimation()

        // Indicador de scroll discreto cuando no cabe todo.
        val max = WidgetLayout.maxScroll(layout.contentHeight, height - 2 * contentTop)
        if (max > 0f) {
            val viewH = height - 2 * contentTop
            val barH = viewH * viewH / layout.contentHeight
            val top = contentTop + (viewH - barH) * (controller.scrollY / max)
            fill.color = GlassTheme.TEXT_SECONDARY
            rect.set(width - dp(3f), top, width - dp(1f), top + barH)
            canvas.drawRoundRect(rect, dp(1f), dp(1f), fill)
        }
    }

    /** El panel: cristal con el fondo desenfocado (Android 12+) o color translúcido sin desenfoque, más borde fino. */
    private fun drawPanel(canvas: Canvas) {
        val radius = if (framed) dp(GlassTheme.RADIUS_PANEL) else 0f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val parent = parent as? View
        val blurred = parent != null && backdrop?.draw(
            canvas, rect, radius, left + translationX, top + translationY, parent.width, parent.height,
        ) == true
        panelPaint.color = if (blurred) GlassTheme.PANEL_FILL else GlassTheme.panelSolid
        canvas.drawRoundRect(rect, radius, radius, panelPaint)
        if (framed) {
            borderPaint.color = GlassTheme.BORDER
            val half = borderPaint.strokeWidth / 2
            rect.set(half, half, width - half, height - half)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    /** Tarjeta de cristal (radio 12) con el relleno dado; los botones activos van en el acento. */
    private fun card(canvas: Canvas, box: Box, color: Int, border: Int = GlassTheme.BORDER) {
        rect.set(box.x, box.y, box.right, box.bottom)
        fill.color = color
        canvas.drawRoundRect(rect, dp(GlassTheme.RADIUS_CARD), dp(GlassTheme.RADIUS_CARD), fill)
        borderPaint.color = border
        canvas.drawRoundRect(rect, dp(GlassTheme.RADIUS_CARD), dp(GlassTheme.RADIUS_CARD), borderPaint)
    }

    private fun buttonCard(canvas: Canvas, box: Box, active: Boolean, flashing: Boolean) = when {
        flashing -> card(canvas, box, GlassTheme.selectedFill, GlassTheme.selectedBorder)
        active -> card(canvas, box, GlassTheme.ELEVATED)
        else -> card(canvas, box, GlassTheme.CARD_FILL)
    }

    private fun drawButton(canvas: Canvas, index: Int, cell: WidgetLayout.Cell) {
        val target = WidgetTarget.Button(index)
        val box = cell.box
        buttonCard(canvas, box, controller.pressed == target, isFlashing(target))

        // Icono grande arriba; sin icono, las 2 primeras letras de la etiqueta.
        val icon = cell.entry.icon.ifEmpty { firstLetters(cell.entry.label, 2) }
        iconText.color = GlassTheme.TEXT
        iconText.textSize = box.height * 0.36f
        canvas.drawText(icon, box.centerX, box.y + box.height * 0.5f, iconText)
        canvas.drawText(ellipsize(cell.entry.label, box.width - dp(8f), labelText), box.centerX, box.bottom - dp(9f), labelText)
    }

    private fun drawMedia(canvas: Canvas, index: Int, cell: WidgetLayout.Cell) {
        val parts = cell.media ?: return
        card(canvas, cell.box, GlassTheme.CARD_FILL)

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
        buttonCard(canvas, box, controller.pressed == target, isFlashing(target))
        drawIcon(box)
    }

    // Iconos dibujados con trazados: no dependen de que la fuente del móvil tenga ⏮ ⏯ ⏭.
    private fun iconSize(box: Box) = minOf(box.height * 0.2f, dp(9f))

    private fun drawPlay(canvas: Canvas, box: Box) {
        val s = iconSize(box)
        fill.color = GlassTheme.TEXT
        path.reset()
        path.moveTo(box.centerX - s * 0.7f, box.centerY - s)
        path.lineTo(box.centerX + s * 0.9f, box.centerY)
        path.lineTo(box.centerX - s * 0.7f, box.centerY + s)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawPause(canvas: Canvas, box: Box) {
        val s = iconSize(box)
        fill.color = GlassTheme.TEXT
        canvas.drawRect(box.centerX - s * 0.8f, box.centerY - s, box.centerX - s * 0.2f, box.centerY + s, fill)
        canvas.drawRect(box.centerX + s * 0.2f, box.centerY - s, box.centerX + s * 0.8f, box.centerY + s, fill)
    }

    /** ⏭ (triángulo y barra a su derecha) o, con [forward] = false, ⏮ (su imagen en el espejo). */
    private fun drawSkip(canvas: Canvas, box: Box, forward: Boolean) {
        val s = iconSize(box)
        val d = if (forward) 1f else -1f
        fill.color = GlassTheme.TEXT
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
        val g = parts.geometry // pista, pulgar y dónde va el número: lógica pura en `core` (VolumeGeometry)
        card(canvas, cell.box, GlassTheme.CARD_FILL)

        val state = volumeState
        val muted = state?.muted == true
        val level = if (controller.draggingVolume) controller.dragLevel else state?.level ?: 0

        // Icono de altavoz (pulsar = silenciar / quitar el silencio).
        val iconTarget = WidgetTarget.MuteIcon(index)
        if (controller.pressed == iconTarget || isFlashing(iconTarget)) {
            buttonCard(canvas, Box(g.icon.x + dp(4f), g.icon.y + dp(4f), g.icon.width - dp(8f), g.icon.height - dp(8f)),
                controller.pressed == iconTarget, isFlashing(iconTarget))
        }
        drawSpeaker(canvas, g.icon, muted)

        // Pista de 4 dp: la parte llena con el acento (gris si está silenciado) y el resto blanco al 18 %.
        val track = g.track
        val half = track.height / 2
        fill.color = TRACK_REST
        rect.set(track.x, track.y, track.right, track.bottom)
        canvas.drawRoundRect(rect, half, half, fill)
        val x = g.thumbX(level)
        fill.color = if (muted) TRACK_MUTED else GlassTheme.accent
        rect.set(track.x, track.y, x, track.bottom)
        canvas.drawRoundRect(rect, half, half, fill)

        // Pulgar blanco de 18 dp con sombra suave; mientras se arrastra, con un halo del acento.
        val cy = track.centerY
        if (controller.draggingVolume) {
            fill.color = Color.argb(70, 0x7C, 0x8C, 0xFF)
            canvas.drawCircle(x, cy, g.thumbRadius + dp(5f), fill)
        }
        fill.color = Color.argb(48, 0, 0, 0)
        canvas.drawCircle(x, cy + dp(1.5f), g.thumbRadius + dp(1f), fill)
        fill.color = Color.WHITE
        canvas.drawCircle(x, cy, g.thumbRadius, fill)

        // El número, en texto normal y con ancho fijo para tres cifras; a la derecha, debajo de la pista o, si no cabe, sin mostrar.
        if (g.valuePlacement != VolumeGeometry.ValuePlacement.HIDDEN) {
            valueText.color = if (muted) GlassTheme.TEXT_SECONDARY else GlassTheme.TEXT
            canvas.drawText(level.toString(), g.value.right, g.value.centerY - (valueText.ascent() + valueText.descent()) / 2, valueText)
        }
    }

    private fun drawSpeaker(canvas: Canvas, box: Box, muted: Boolean) {
        val u = dp(1f)
        val cx = box.centerX - 3 * u
        val cy = box.centerY
        fill.color = GlassTheme.TEXT
        path.reset()
        path.moveTo(cx - 9 * u, cy - 4 * u)
        path.lineTo(cx - 4 * u, cy - 4 * u)
        path.lineTo(cx + 2 * u, cy - 9 * u)
        path.lineTo(cx + 2 * u, cy + 9 * u)
        path.lineTo(cx - 4 * u, cy + 4 * u)
        path.lineTo(cx - 9 * u, cy + 4 * u)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = if (muted) GlassTheme.TEXT_SECONDARY else GlassTheme.TEXT // silenciado: una × discreta, no un rojo chillón
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
        val TRACK_REST: Int = Color.argb(46, 255, 255, 255) // blanco al 18 %
        val TRACK_MUTED: Int = Color.parseColor("#8C90A8")
    }
}
