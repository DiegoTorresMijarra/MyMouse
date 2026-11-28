package dev.mymouse.core.debug

import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.SystemGestureCommand
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Convierte las acciones enviadas al PC en textos legibles para el modo depuración (clic, doble clic,
 * scroll ↓ 2 pasos...), que pone [DebugTexts] en el idioma del móvil. Las acciones seguidas del mismo tipo se agrupan en una sola entrada.
 */
class ActionDescriber(private val texts: DebugTexts, private val doubleClickMs: Long = 500) {

    /** [key] identifica la entrada: si coincide con la anterior, la sustituye en lugar de añadir otra. */
    data class Label(val key: Long, val text: String)

    private var nextKey = 0L
    private var currentKey = -1L
    private var currentKind: Kind? = null

    private enum class Kind { MOVE, DRAG, SCROLL, ZOOM }

    private var leftDown = false
    private var lastClickAt: Long? = null
    private var movePx = 0f
    private var scrollV = 0
    private var scrollH = 0
    private var zoom = 0

    fun describe(action: OutputAction, nowMs: Long): Label? = when (action) {
        is OutputAction.Button -> button(action, nowMs)
        is OutputAction.PointerMove -> {
            val kind = if (leftDown) Kind.DRAG else Kind.MOVE
            if (kind == Kind.DRAG) lastClickAt = null
            val px = continueOrStart(kind) { movePx = 0f }
            movePx += hypot(action.dx.toFloat(), action.dy.toFloat())
            Label(px, if (kind == Kind.DRAG) texts.drag(movePx.roundToInt()) else texts.move(movePx.roundToInt()))
        }
        is OutputAction.Scroll -> {
            val key = continueOrStart(Kind.SCROLL) { scrollV = 0; scrollH = 0 }
            scrollV += action.vertical
            scrollH += action.horizontal
            Label(key, texts.scroll(listOfNotNull(
                axis(scrollV, "↑", "↓"),
                axis(scrollH, "→", "←"),
            ).joinToString(" ")))
        }
        is OutputAction.Zoom -> {
            val key = continueOrStart(Kind.ZOOM) { zoom = 0 }
            zoom += action.delta
            Label(key, texts.zoom(zoom >= 0, steps(zoom)))
        }
        is OutputAction.SystemGesture -> {
            currentKind = null
            newLabel(system(action.command))
        }
        is OutputAction.WidgetPanel -> {
            currentKind = null
            newLabel(texts.text(if (action.show) DebugText.WIDGETS_SHOW else DebugText.WIDGETS_HIDE))
        }
        is OutputAction.FourFingerSwipe -> {
            currentKind = null
            newLabel(texts.text(if (action.up) DebugText.TOOLBOX_OPEN else DebugText.TOOLBOX_CLOSE))
        }
        is OutputAction.FourFingerDoubleTap -> {
            currentKind = null
            newLabel(texts.text(DebugText.KEYBOARD_OPEN))
        }
        // Los lotes de puntos son demasiado frecuentes para listarlos; el fin del trazo ya sale en "Forma: …".
        is OutputAction.StrokePoints, is OutputAction.StrokeEnd -> null
        is OutputAction.GestureMode -> {
            currentKind = null
            newLabel(texts.text(if (action.active) DebugText.GESTURES_START else DebugText.GESTURES_END))
        }
        is OutputAction.Gesture -> {
            currentKind = null
            newLabel(texts.shape(action.name, (action.score * 100).roundToInt()))
        }
        is OutputAction.GestureRejected -> {
            currentKind = null
            newLabel(texts.shapeRejected(action.reason, action.other))
        }
    }

    private fun system(command: SystemGestureCommand) = texts.text(when (command) {
        SystemGestureCommand.TASK_VIEW -> DebugText.TASK_VIEW
        SystemGestureCommand.SHOW_DESKTOP -> DebugText.SHOW_DESKTOP
        SystemGestureCommand.APP_SWITCH_START -> DebugText.APP_SWITCH_START
        SystemGestureCommand.APP_SWITCH_NEXT -> DebugText.APP_SWITCH_NEXT
        SystemGestureCommand.APP_SWITCH_PREVIOUS -> DebugText.APP_SWITCH_PREVIOUS
        SystemGestureCommand.APP_SWITCH_END -> DebugText.APP_SWITCH_END
    })

    private fun button(b: OutputAction.Button, nowMs: Long): Label? {
        val endsDrag = currentKind == Kind.DRAG
        currentKind = null
        return when {
            b.button == MouseButton.LEFT && b.pressed -> {
                leftDown = true
                val last = lastClickAt
                if (last != null && nowMs - last <= doubleClickMs) {
                    lastClickAt = null
                    newLabel(texts.text(DebugText.DOUBLE_CLICK))
                } else {
                    lastClickAt = nowMs
                    newLabel(texts.text(DebugText.CLICK))
                }
            }
            b.button == MouseButton.LEFT -> {
                leftDown = false
                if (endsDrag) newLabel(texts.text(DebugText.RELEASE)) else null
            }
            b.pressed -> newLabel(texts.text(if (b.button == MouseButton.RIGHT) DebugText.RIGHT_CLICK else DebugText.MIDDLE_CLICK))
            else -> null
        }
    }

    /** Devuelve la clave de la entrada en curso si es del mismo tipo; si no, empieza una nueva. */
    private inline fun continueOrStart(kind: Kind, reset: () -> Unit): Long {
        if (currentKind == kind) return currentKey
        currentKind = kind
        reset()
        currentKey = nextKey++
        return currentKey
    }

    private fun newLabel(text: String) = Label(nextKey++, text)

    private fun axis(value: Int, positive: String, negative: String): String? =
        if (value == 0) null else "${if (value > 0) positive else negative} ${steps(value)}"

    private fun steps(units: Int): String {
        val s = abs(units) / 120f
        return if (s == s.roundToInt().toFloat()) s.roundToInt().toString() else "%.1f".format(s)
    }
}
