package dev.mymouse.core.debug

import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.SystemGestureCommand
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Traduce las acciones enviadas al PC a textos legibles para el modo depuración ("Clic", "Doble clic",
 * "Scroll ↓ 2 pasos"...). Las acciones seguidas del mismo tipo se agrupan en una sola entrada.
 */
class ActionDescriber(private val doubleClickMs: Long = 500) {

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
            Label(px, "${if (kind == Kind.DRAG) "Arrastrar" else "Mover"} (${movePx.roundToInt()} px)")
        }
        is OutputAction.Scroll -> {
            val key = continueOrStart(Kind.SCROLL) { scrollV = 0; scrollH = 0 }
            scrollV += action.vertical
            scrollH += action.horizontal
            Label(key, "Scroll " + listOfNotNull(
                axis(scrollV, "↑", "↓"),
                axis(scrollH, "→", "←"),
            ).joinToString(" "))
        }
        is OutputAction.Zoom -> {
            val key = continueOrStart(Kind.ZOOM) { zoom = 0 }
            zoom += action.delta
            Label(key, "Zoom ${if (zoom >= 0) "+" else "−"} ${steps(zoom)}")
        }
        is OutputAction.SystemGesture -> {
            currentKind = null
            newLabel(system(action.command))
        }
        is OutputAction.WidgetPanel -> {
            currentKind = null
            newLabel(if (action.show) "4 dedos juntar: mostrar widgets" else "4 dedos separar: ocultar widgets")
        }
        is OutputAction.FourFingerSwipe -> {
            currentKind = null
            newLabel(if (action.up) "4 dedos ↑: abrir la toolbox del PC" else "4 dedos ↓: cerrar la toolbox del PC")
        }
        // Los lotes de puntos son demasiado frecuentes para listarlos; el fin del trazo ya sale en "Forma: …".
        is OutputAction.StrokePoints, is OutputAction.StrokeEnd -> null
        is OutputAction.GestureMode -> {
            currentKind = null
            newLabel(if (action.active) "Modo gestos: dibuja una forma" else "Fin del modo gestos")
        }
        is OutputAction.Gesture -> {
            currentKind = null
            newLabel("Forma: ${action.name} (${(action.score * 100).roundToInt()} %)")
        }
        is OutputAction.GestureRejected -> {
            currentKind = null
            newLabel("Forma: ${action.reason}")
        }
    }

    private fun system(command: SystemGestureCommand) = when (command) {
        SystemGestureCommand.TASK_VIEW -> "3 dedos ↑: Vista de tareas"
        SystemGestureCommand.SHOW_DESKTOP -> "3 dedos ↓: Escritorio"
        SystemGestureCommand.APP_SWITCH_START -> "3 dedos ← →: cambiar de app"
        SystemGestureCommand.APP_SWITCH_NEXT -> "Siguiente app"
        SystemGestureCommand.APP_SWITCH_PREVIOUS -> "App anterior"
        SystemGestureCommand.APP_SWITCH_END -> "Fin del cambio de app"
    }

    private fun button(b: OutputAction.Button, nowMs: Long): Label? {
        val endsDrag = currentKind == Kind.DRAG
        currentKind = null
        return when {
            b.button == MouseButton.LEFT && b.pressed -> {
                leftDown = true
                val last = lastClickAt
                if (last != null && nowMs - last <= doubleClickMs) {
                    lastClickAt = null
                    newLabel("Doble clic")
                } else {
                    lastClickAt = nowMs
                    newLabel("Clic")
                }
            }
            b.button == MouseButton.LEFT -> {
                leftDown = false
                if (endsDrag) newLabel("Soltar") else null
            }
            b.pressed -> newLabel(if (b.button == MouseButton.RIGHT) "Clic derecho" else "Clic central")
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
