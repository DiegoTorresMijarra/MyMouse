package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.gesture.TouchInput
import dev.mymouse.core.gesture.TrackpadEngine
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.SystemGestureCommand

/**
 * Superficie del trackpad: traduce MotionEvent a [TouchInput] (incluidas las muestras históricas),
 * se los pasa al motor (trackpad + modo gestos) y envía las acciones resultantes.
 */
@SuppressLint("ViewConstructor")
class TrackpadView(
    context: Context,
    engine: TrackpadEngine,
    private val send: (OutputAction) -> Unit,
) : View(context) {

    /** Se puede sustituir (p. ej. al cambiar los ajustes); el clic pendiente del anterior se suelta. */
    var interpreter: TrackpadEngine = engine
        set(value) {
            field.onTouch(TouchInput.Cancel(SystemClock.uptimeMillis())).forEach(send)
            removeCallbacks(tick)
            field = value
            value.setArea(width.toFloat(), height.toFloat())
        }

    var connected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    val cheatSheet = CheatSheet(density, resources)

    /** Modo ventana: dibuja un borde alrededor del trackpad. */
    var framed: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Cristal: panel (blanco al 6 %, radio 16 en modo ventana; a pantalla completa y sin radio en modo bloqueado) y su borde fino.
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.PANEL_FILL }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.BORDER
        strokeWidth = resources.displayMetrics.density
    }
    private val panelRect = RectF()
    private var backdropDrawable = GlassTheme.universe(context, trackpad = true)

    /** Con un toque en el punto de estado (solo en modo Bluetooth): abre el diagnóstico. */
    var onStatusDotTap: (() -> Unit)? = null
        set(value) {
            field = value
            invalidate()
        }
    private var dotTouch = false

    /** Texto junto al punto de estado en modo Bluetooth: "Bluetooth · 120 inf/s · 8 ms" (intervalo real entre informes, no un ping). */
    var linkInfo: (() -> String?)? = null
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassTheme.TEXT_SECONDARY
        textSize = 11 * resources.displayMetrics.density
        textAlign = Paint.Align.RIGHT
    }

    private fun inStatusDot(x: Float, y: Float): Boolean {
        val r = 28 * density
        val dx = x - (width - 16 * density)
        val dy = y - 16 * density
        return dx * dx + dy * dy <= r * r
    }

    /**
     * Lo que hay detrás de los paneles de cristal (degradado y trazo del modo gestos), en coordenadas del padre, para que los paneles
     * lo desenfoquen en Android 12+. [w] y [h] son el tamaño del padre.
     */
    fun drawBackdrop(canvas: Canvas, w: Int, h: Int) {
        backdropDrawable.setBounds(0, 0, w, h)
        backdropDrawable.draw(canvas)
        val stroke = interpreter.currentStroke
        if (interpreter.gestureModeActive && stroke.size >= 2) {
            canvas.save()
            canvas.translate(left.toFloat(), top.toFloat())
            strokePath.reset()
            strokePath.moveTo(stroke[0].x, stroke[0].y)
            for (k in 1 until stroke.size) strokePath.lineTo(stroke[k].x, stroke[k].y)
            canvas.drawPath(strokePath, strokePaint)
            canvas.restore()
        }
    }

    /** Modo depuración: minipantalla con los dedos y lista de acciones (null = desactivado). */
    var debugOverlay: DebugOverlay? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Juntar (true) o separar (false) 4 dedos: mostrar u ocultar el panel de widgets. Lo anima [MainActivity]. */
    var onPanelGesture: (Boolean) -> Unit = {}

    /** Doble toque con 4 dedos: abrir el teclado (docs/spec-v6-teclado.md §3). Lo resuelve [MainActivity]. */
    var onKeyboardGesture: () -> Unit = {}

    /** Suelta lo que haya a medias (clic pendiente, arrastre, modo gestos) al entrar en el modo teclado. */
    fun cancelTouches() {
        interpreter.onTouch(TouchInput.Cancel(SystemClock.uptimeMillis())).forEach(::emit)
        removeCallbacks(tick)
        invalidate()
    }

    /** ¿Hay toolbox en el PC? Si no (sin conexión o por Bluetooth), 4 dedos ↑ / ↓ solo avisan con [toolboxUnavailableRes]. */
    var toolboxAvailable: () -> Boolean = { true }
    var toolboxUnavailableRes: () -> Int = { R.string.no_pc_connection }

    /** ¿Puede el destino hacer los gestos de 3 dedos de Windows? Si no (sin conexión), solo se anotan en depuración. */
    var systemGesturesAvailable: () -> Boolean = { true }

    /** Envía al PC y, si el modo depuración está activo, lo anota. */
    private fun emit(action: OutputAction) {
        send(action)
        debugOverlay?.onAction(action, SystemClock.uptimeMillis())
        when (action) {
            is OutputAction.WidgetPanel -> onPanelGesture(action.show)
            is OutputAction.SystemGesture -> if (!systemGesturesAvailable() && action.command.isStart()) {
                noteDebug(context.getString(R.string.trackpad_pc_needs_3_1))
            }
            is OutputAction.FourFingerSwipe -> if (!toolboxAvailable()) showNotice(context.getString(toolboxUnavailableRes()))
            is OutputAction.FourFingerDoubleTap -> onKeyboardGesture()
            is OutputAction.GestureMode -> {
                if (action.active) vibrate()
                invalidate()
            }
            is OutputAction.Gesture -> {
                lastGesture = action.name
                val item = gestureItems().firstOrNull { it.name == action.name }
                showResult(
                    "${ShapeStore.label(context, action.name)}${item?.help?.takeIf { it.isNotEmpty() }?.let { " → $it" } ?: ""}",
                    ok = true,
                )
            }
            is OutputAction.GestureRejected -> showResult(UiText.reject(context, action.reason, action.other), ok = false)
            else -> Unit
        }
    }

    // ---- Modo gestos ----

    /** Formas guardadas en el móvil (activas, miniaturas). */
    var shapeStore: ShapeStore? = null

    /** Última lista de formas recibida del PC (null = ninguna: sin conexión o PC sin modo gestos). */
    var hostGestureList: dev.mymouse.core.protocol.Message.GestureList? = null
        set(value) {
            field = value
            invalidate()
        }

    private val gestureSheet = GestureSheet(density, resources)
    private var lastGesture: String? = null
    private var resultText: String? = null
    private var resultOk = true
    private var resultUntil = 0L

    private fun gestureItems(): List<GestureSheet.Item> =
        shapeStore?.let { GestureSheet.items(context, it, hostGestureList?.entries) } ?: emptyList()

    private fun showResult(text: String, ok: Boolean) {
        resultText = text
        resultOk = ok
        resultUntil = SystemClock.uptimeMillis() + RESULT_MS
        invalidate()
    }

    /** Lo que se dice de una forma sin acción (recurso): "sin acción en el PC" por USB; por Bluetooth las acciones son del móvil. */
    var unassignedRes: Int = R.string.trackpad_unassigned_pc

    /** Respuesta del PC a la última forma: solo se avisa si no se ha ejecutado. */
    fun showGestureStatus(status: GestureStatus) {
        when (status) {
            GestureStatus.EXECUTED -> Unit
            GestureStatus.UNASSIGNED -> showResult(context.getString(R.string.trackpad_shape_unassigned, lastGesture?.let { ShapeStore.label(context, it) } ?: context.getString(R.string.shape_generic), context.getString(unassignedRes)), ok = false)
            GestureStatus.ERROR -> showResult(context.getString(R.string.trackpad_pc_not_run), ok = false)
        }
    }

    /** Aviso de 1,5 s sobre el trackpad (p. ej. el PC no ha ejecutado un widget). */
    fun showNotice(text: String) = showResult(text, ok = false)

    /** Anota en el modo depuración algo que no viene del trackpad (acciones del panel de widgets). */
    fun noteDebug(text: String) {
        debugOverlay?.let {
            it.note(text, SystemClock.uptimeMillis())
            invalidate()
        }
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: SecurityException) {
            // Sin permiso de vibración: no es imprescindible.
        }
    }

    /** Se cambió el tema en Ajustes: el acento del borde del modo gestos y el fondo con el que se desenfoca (docs/spec-v4.8-temas.md §3). */
    fun onThemeChanged() {
        gestureBorder.color = GlassTheme.accent
        backdropDrawable = GlassTheme.universe(context, trackpad = true)
        invalidate()
    }

    private val gestureBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.accent
        strokeWidth = 3 * resources.displayMetrics.density
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GlassTheme.TEXT
        strokeWidth = 5 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val resultBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassTheme.panelSolid }
    private val strokePath = Path()

    /** Mostrar la chuleta de gestos en el centro. */
    var showCheatSheet: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val tick = Runnable {
        interpreter.onTick(SystemClock.uptimeMillis()).forEach(::emit)
        scheduleTick()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Recibir eventos en cuanto llegan, sin esperar al vsync (Android 11+).
            requestUnbufferedDispatch(event)
        }

        // Toque en el punto de estado (solo con [onStatusDotTap]): no llega al motor del trackpad.
        val dotTap = onStatusDotTap
        if (dotTap != null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) dotTouch = inStatusDot(event.x, event.y)
            if (dotTouch) {
                if (event.actionMasked == MotionEvent.ACTION_UP && inStatusDot(event.x, event.y)) dotTap()
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) dotTouch = false
                return true
            }
        }

        val debug = debugOverlay
        for (input in toInputs(event)) {
            debug?.onTouch(input)
            interpreter.onTouch(input).forEach(::emit)
        }
        scheduleTick()
        if (debug != null || interpreter.gestureModeActive) invalidate()
        return true
    }

    private fun toInputs(e: MotionEvent): List<TouchInput> {
        val i = e.actionIndex
        return when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                listOf(TouchInput.Down(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime))

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                listOf(TouchInput.Up(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime))

            MotionEvent.ACTION_MOVE -> buildList {
                // Android agrupa varias muestras por frame: procesarlas todas da más resolución.
                val last = e.pointerCount - 1
                for (h in 0 until e.historySize) {
                    for (p in 0..last) {
                        add(TouchInput.Move(e.getPointerId(p), e.getHistoricalX(p, h), e.getHistoricalY(p, h), e.getHistoricalEventTime(h), p == last))
                    }
                }
                for (p in 0..last) {
                    add(TouchInput.Move(e.getPointerId(p), e.getX(p), e.getY(p), e.eventTime, p == last))
                }
            }

            MotionEvent.ACTION_CANCEL -> listOf(TouchInput.Cancel(e.eventTime))
            else -> emptyList()
        }
    }

    /** MotionEvent.eventTime usa la base de uptimeMillis, igual que postDelayed. */
    private fun scheduleTick() {
        removeCallbacks(tick)
        val deadline = interpreter.nextDeadlineMs ?: return
        postDelayed(tick, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        interpreter.setArea(w.toFloat(), h.toFloat()) // para normalizar el trazo que se envía al PC
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gestureMode = interpreter.gestureModeActive
        val panelRadius = if (framed) GlassTheme.dp(context, GlassTheme.RADIUS_PANEL) else 0f
        panelRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(panelRect, panelRadius, panelRadius, panelPaint)
        if (gestureMode) {
            val list = hostGestureList
            val note = when {
                !connected -> context.getString(R.string.trackpad_shapes_offline)
                list == null -> context.getString(R.string.trackpad_pc_no_gesture_mode)
                else -> null
            }
            gestureSheet.draw(canvas, width, height, gestureItems(), list?.context ?: "", note, null, debugOverlay?.occupiedArea(width, height))
        } else if (showCheatSheet) {
            cheatSheet.draw(canvas, width, height, debug = debugOverlay?.occupiedArea(width, height))
        }
        debugOverlay?.let {
            it.draw(canvas, width, height, SystemClock.uptimeMillis(), connected)
            if (it.animating) postInvalidateDelayed(50) // trazos y textos que se desvanecen
        }
        if (gestureMode) {
            val inset = gestureBorder.strokeWidth / 2
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, panelRadius, panelRadius, gestureBorder)
            val stroke = interpreter.currentStroke
            if (stroke.size >= 2) {
                strokePath.reset()
                strokePath.moveTo(stroke[0].x, stroke[0].y)
                for (k in 1 until stroke.size) strokePath.lineTo(stroke[k].x, stroke[k].y)
                canvas.drawPath(strokePath, strokePaint)
            }
        }
        val now = SystemClock.uptimeMillis()
        val text = resultText
        if (text != null && now < resultUntil) {
            resultPaint.color = if (resultOk) GlassTheme.GREEN else GlassTheme.AMBER
            val tw = resultPaint.measureText(text)
            val cy = height * 0.82f
            canvas.drawRoundRect(
                width / 2f - tw / 2 - 16 * density, cy - 30 * density, width / 2f + tw / 2 + 16 * density, cy + 14 * density,
                12 * density, 12 * density, resultBg.apply { color = GlassTheme.panelSolid },
            )
            canvas.drawRoundRect(
                width / 2f - tw / 2 - 16 * density, cy - 30 * density, width / 2f + tw / 2 + 16 * density, cy + 14 * density,
                12 * density, 12 * density, framePaint,
            )
            canvas.drawText(text, width / 2f, cy, resultPaint)
            postInvalidateDelayed(resultUntil - now)
        }
        if (framed && !gestureMode) {
            val inset = framePaint.strokeWidth / 2
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, panelRadius, panelRadius, framePaint)
        }
        // Indicador discreto de estado: verde = conectado, gris = desconectado.
        statusPaint.color = if (connected) GlassTheme.GREEN else GlassTheme.TEXT_TERTIARY
        val r = 5 * density
        canvas.drawCircle(width - 16 * density, 16 * density, r, statusPaint)
        val info = if (connected) linkInfo?.invoke() else null
        debugOverlay?.linkInfo = info
        if (info != null) canvas.drawText(info, width - 30 * density, 16 * density + linkPaint.textSize / 3, linkPaint)
        if (linkInfo != null) postInvalidateDelayed(500) // la cifra se refresca sola
        if (onStatusDotTap != null) canvas.drawCircle(width - 16 * density, 16 * density, r + 4 * density, framePaint) // se puede tocar
    }

    /** Primer mensaje de cada gesto de 3 dedos (el cambio de app envía varios). */
    private fun SystemGestureCommand.isStart() =
        this == SystemGestureCommand.TASK_VIEW || this == SystemGestureCommand.SHOW_DESKTOP ||
            this == SystemGestureCommand.APP_SWITCH_START

    private companion object {
        const val RESULT_MS = 1_500L
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
