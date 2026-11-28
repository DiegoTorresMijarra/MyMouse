package dev.mymouse.core.debug

import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.shapes.RejectReason

/** Textos de prueba (los reales están en los recursos de la app). */
object SpanishTexts : DebugTexts {
    override fun text(text: DebugText) = when (text) {
        DebugText.CLICK -> "Clic"
        DebugText.DOUBLE_CLICK -> "Doble clic"
        DebugText.RELEASE -> "Soltar"
        DebugText.RIGHT_CLICK -> "Clic derecho"
        DebugText.MIDDLE_CLICK -> "Clic central"
        DebugText.WIDGETS_SHOW -> "4 dedos juntar: mostrar widgets"
        DebugText.WIDGETS_HIDE -> "4 dedos separar: ocultar widgets"
        DebugText.TOOLBOX_OPEN -> "4 dedos ↑: abrir la toolbox del PC"
        DebugText.TOOLBOX_CLOSE -> "4 dedos ↓: cerrar la toolbox del PC"
        DebugText.KEYBOARD_OPEN -> "4 dedos doble toque: abrir el teclado"
        DebugText.GESTURES_START -> "Modo gestos: dibuja una forma"
        DebugText.GESTURES_END -> "Fin del modo gestos"
        DebugText.TASK_VIEW -> "3 dedos ↑: Vista de tareas"
        DebugText.SHOW_DESKTOP -> "3 dedos ↓: Escritorio"
        DebugText.APP_SWITCH_START -> "3 dedos ← →: cambiar de app"
        DebugText.APP_SWITCH_NEXT -> "Siguiente app"
        DebugText.APP_SWITCH_PREVIOUS -> "App anterior"
        DebugText.APP_SWITCH_END -> "Fin del cambio de app"
        DebugText.WIDGET_NOT_RUN -> "El PC no lo ha ejecutado"
    }

    override fun move(px: Int) = "Mover ($px px)"
    override fun drag(px: Int) = "Arrastrar ($px px)"
    override fun scroll(axes: String) = "Scroll $axes"
    override fun zoom(positive: Boolean, steps: String) = "Zoom ${if (positive) "+" else "−"} $steps"
    override fun shape(name: String, scorePercent: Int) = "Forma: $name ($scorePercent %)"
    override fun shapeRejected(reason: RejectReason, other: String?) = "Forma: " + reason.name.lowercase()
    override fun widget(label: String) = "Widget: $label"
    override fun volume(level: Int) = "Volumen $level"
    override fun mute(muted: Boolean) = if (muted) "🔇 Silenciar" else "🔈 Quitar silencio"
    override fun media(command: MediaCommand) = when (command) {
        MediaCommand.PLAY_PAUSE -> "⏯ Reproducir / pausar"
        MediaCommand.NEXT -> "⏭ Siguiente"
        MediaCommand.PREVIOUS -> "⏮ Anterior"
    }
    override fun widgetUnassigned(label: String?) = "${label ?: "Widget"}: sin acción en el PC"
}
