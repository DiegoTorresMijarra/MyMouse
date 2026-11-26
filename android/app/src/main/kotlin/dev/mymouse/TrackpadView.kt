package dev.mymouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import dev.mymouse.core.gesture.GestureInterpreter
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.gesture.TouchInput

/**
 * Superficie del trackpad: traduce MotionEvent a [TouchInput] (incluidas las muestras históricas),
 * se los pasa al intérprete y envía las acciones resultantes.
 */
@SuppressLint("ViewConstructor")
class TrackpadView(
    context: Context,
    private val interpreter: GestureInterpreter,
    private val send: (OutputAction) -> Unit,
) : View(context) {

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

    /** Mostrar la chuleta de gestos en el centro. */
    var showCheatSheet: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val tick = Runnable {
        interpreter.onTick(SystemClock.uptimeMillis()).forEach(send)
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

        for (input in toInputs(event)) {
            interpreter.onTouch(input).forEach(send)
        }
        scheduleTick()
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
        if (showCheatSheet) cheatSheet.draw(canvas, width, height)
        if (framed) {
            val inset = framePaint.strokeWidth / 2
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 12 * density, 12 * density, framePaint)
        }
        // Indicador discreto de estado: verde = conectado, gris = desconectado.
        statusPaint.color = if (connected) Color.rgb(40, 160, 70) else Color.rgb(90, 90, 90)
        val r = 5 * density
        canvas.drawCircle(width - 16 * density, 16 * density, r, statusPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
