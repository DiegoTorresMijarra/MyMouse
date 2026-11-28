package dev.mymouse.core.debug

import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetDescriberTest {
    private val describer = WidgetDescriber(SpanishTexts)

    @Test
    fun debugTexts() {
        assertEquals("Widget: Chrome", describer.press("Chrome"))
        assertEquals("Volumen 55", describer.volume(55))
        assertEquals("⏭ Siguiente", describer.media(MediaCommand.NEXT))
        assertEquals("⏮ Anterior", describer.media(MediaCommand.PREVIOUS))
        assertEquals("⏯ Reproducir / pausar", describer.media(MediaCommand.PLAY_PAUSE))
        assertEquals("🔇 Silenciar", describer.mute(true))
        assertEquals("🔈 Quitar silencio", describer.mute(false))
    }

    @Test
    fun resultNotices() {
        val chrome = WidgetEntry(3, WidgetKind.BUTTON, "Chrome", "🌐")
        assertNull(describer.resultNotice(chrome, GestureStatus.EXECUTED))
        assertEquals("Chrome: sin acción en el PC", describer.resultNotice(chrome, GestureStatus.UNASSIGNED))
        assertEquals("Widget: sin acción en el PC", describer.resultNotice(null, GestureStatus.UNASSIGNED))
        assertEquals("El PC no lo ha ejecutado", describer.resultNotice(chrome, GestureStatus.ERROR))
    }
}
