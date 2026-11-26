package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        }

    var connected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    val cheatSheet = CheatSheet(density)

    /** Modo ventana: dibuja un borde alrededor del trackpad. */
    var framed: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(90, 90, 90)
        strokeWidth = 2 * resources.displayMetrics.density
    }

    /** Modo depuración: minipantalla con los dedos y lista de acciones (null = desactivado). */
    var debugOverlay: DebugOverlay? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Juntar (true) o separar (false) 4 dedos: mostrar u ocultar el panel de widgets. Lo anima [MainActivity]. */
    var onPanelGesture: (Boolean) -> Unit = {}

    /** ¿Entiende el destino los gestos de 3 dedos de Windows? Si no (host anterior a la v4), solo se anotan en depuración. */
    var systemGesturesAvailable: () -> Boolean = { true }

    /** Envía al PC y, si el modo depuración está activo, lo anota. */
    private fun emit(action: OutputAction) {
        send(action)
        debugOverlay?.onAction(action, SystemClock.uptimeMillis())
        when (action) {
            is OutputAction.WidgetPanel -> onPanelGesture(action.show)
            is OutputAction.SystemGesture -> if (!systemGesturesAvailable() && action.command.isStart()) {
                noteDebug("Gestos de 3 dedos: el PC necesita MyMouse 3.1 o posterior")
            }
            is OutputAction.GestureMode -> {
                if (action.active) vibrate()
                invalidate()
            }
            is OutputAction.Gesture -> {
                lastGesture = action.name
                val item = gestureItems().firstOrNull { it.name == action.name }
                showResult(
                    "${ShapeStore.label(action.name)}${item?.help?.takeIf { it.isNotEmpty() }?.let { " → $it" } ?: ""}",
                    ok = true,
                )
            }
            is OutputAction.GestureRejected -> showResult(action.reason, ok = false)
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

    private val gestureSheet = GestureSheet(density)
    private var lastGesture: String? = null
    private var resultText: String? = null
    private var resultOk = true
    private var resultUntil = 0L

    private fun gestureItems(): List<GestureSheet.Item> =
        shapeStore?.let { GestureSheet.items(it, hostGestureList?.entries) } ?: emptyList()

    private fun showResult(text: String, ok: Boolean) {
        resultText = text
        resultOk = ok
        resultUntil = SystemClock.uptimeMillis() + RESULT_MS
        invalidate()
    }

    /** Respuesta del PC a la última forma: solo se avisa si no se ha ejecutado. */
    fun showGestureStatus(status: GestureStatus) {
        when (status) {
            GestureStatus.EXECUTED -> Unit
            GestureStatus.UNASSIGNED -> showResult("${lastGesture?.let(ShapeStore::label) ?: "Forma"}: sin acción en el PC", ok = false)
            GestureStatus.ERROR -> showResult("El PC no la ha ejecutado (¿en pausa?)", ok = false)
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

    private val gestureBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(80, 170, 255)
        strokeWidth = 4 * resources.displayMetrics.density
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(120, 200, 255)
        strokeWidth = 5 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val resultBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 20, 20, 20) }
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

    init {
        setBackgroundColor(Color.BLACK)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Recibir eventos en cuanto llegan, sin esperar al vsync (Android 11+).
            requestUnbufferedDispatch(event)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gestureMode = interpreter.gestureModeActive
        if (gestureMode) {
            val list = hostGestureList
            val note = when {
                !connected -> "Sin conexión: las formas no llegarán al PC"
                list == null -> "El PC no tiene modo gestos (actualiza el host)"
                else -> null
            }
            gestureSheet.draw(canvas, width, height, gestureItems(), list?.context ?: "", note, null)
        } else if (showCheatSheet) {
            cheatSheet.draw(canvas, width, height, atBottom = debugOverlay != null)
        }
        debugOverlay?.let {
            it.draw(canvas, width, height, SystemClock.uptimeMillis(), connected)
            if (it.animating) postInvalidateDelayed(50) // trazos y textos que se desvanecen
        }
        if (gestureMode) {
            val inset = gestureBorder.strokeWidth / 2
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 12 * density, 12 * density, gestureBorder)
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
            resultPaint.color = if (resultOk) Color.rgb(120, 230, 120) else Color.rgb(255, 170, 90)
            val tw = resultPaint.measureText(text)
            val cy = height * 0.82f
            canvas.drawRoundRect(
                width / 2f - tw / 2 - 16 * density, cy - 30 * density, width / 2f + tw / 2 + 16 * density, cy + 14 * density,
                12 * density, 12 * density, resultBg,
            )
            canvas.drawText(text, width / 2f, cy, resultPaint)
            postInvalidateDelayed(resultUntil - now)
        }
        if (framed && !gestureMode) {
            val inset = framePaint.strokeWidth / 2
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 12 * density, 12 * density, framePaint)
        }
        // Indicador discreto de estado: verde = conectado, gris = desconectado.
        statusPaint.color = if (connected) Color.rgb(40, 160, 70) else Color.rgb(90, 90, 90)
        val r = 5 * density
        canvas.drawCircle(width - 16 * density, 16 * density, r, statusPaint)
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
