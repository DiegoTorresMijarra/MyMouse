package dev.mymouse.core.debug

import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetEntry

/** Textos del modo depuración y de los avisos para las acciones del panel de widgets. */
object WidgetDescriber {
    fun press(label: String) = "Widget: $label"

    fun volume(level: Int) = "Volumen $level"

    fun mute(muted: Boolean) = if (muted) "🔇 Silenciar" else "🔈 Quitar silencio"

    fun media(command: MediaCommand) = when (command) {
        MediaCommand.PLAY_PAUSE -> "⏯ Reproducir / pausar"
        MediaCommand.NEXT -> "⏭ Siguiente"
        MediaCommand.PREVIOUS -> "⏮ Anterior"
    }

    /**
     * Aviso (1,5 s) para un `WIDGET_RESULT`, o null si se ejecutó. [entry] es el widget del layout actual, si sigue existiendo.
     * El host responde 2 (error) también cuando está en pausa o el widget ya no existe.
     */
    fun resultNotice(entry: WidgetEntry?, status: GestureStatus): String? = when (status) {
        GestureStatus.EXECUTED -> null
        GestureStatus.UNASSIGNED -> "${entry?.label?.takeIf { it.isNotEmpty() } ?: "Widget"}: sin acción en el PC"
        GestureStatus.ERROR -> "El PC no lo ha ejecutado"
    }
}
