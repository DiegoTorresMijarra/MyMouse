package dev.mymouse.core.debug

import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetEntry

/** Textos del modo depuración y de los avisos para las acciones del panel de widgets (los escribe [DebugTexts]). */
class WidgetDescriber(private val texts: DebugTexts) {
    fun press(label: String) = texts.widget(label)

    fun volume(level: Int) = texts.volume(level)

    fun mute(muted: Boolean) = texts.mute(muted)

    fun media(command: MediaCommand) = texts.media(command)

    /**
     * Aviso (1,5 s) para un `WIDGET_RESULT`, o null si se ejecutó. [entry] es el widget del layout actual, si sigue existiendo.
     * El host responde 2 (error) también cuando está en pausa o el widget ya no existe.
     */
    fun resultNotice(entry: WidgetEntry?, status: GestureStatus): String? = when (status) {
        GestureStatus.EXECUTED -> null
        GestureStatus.UNASSIGNED -> texts.widgetUnassigned(entry?.label?.takeIf { it.isNotEmpty() })
        GestureStatus.ERROR -> texts.text(DebugText.WIDGET_NOT_RUN)
    }
}
