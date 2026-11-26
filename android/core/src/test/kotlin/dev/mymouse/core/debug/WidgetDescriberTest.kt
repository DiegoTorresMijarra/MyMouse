package dev.mymouse.core.debug

import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetDescriberTest {
    @Test
    fun debugTexts() {
        assertEquals("Widget: Chrome", WidgetDescriber.press("Chrome"))
        assertEquals("Volumen 55", WidgetDescriber.volume(55))
        assertEquals("⏭ Siguiente", WidgetDescriber.media(MediaCommand.NEXT))
        assertEquals("⏮ Anterior", WidgetDescriber.media(MediaCommand.PREVIOUS))
        assertEquals("⏯ Reproducir / pausar", WidgetDescriber.media(MediaCommand.PLAY_PAUSE))
        assertEquals("🔇 Silenciar", WidgetDescriber.mute(true))
        assertEquals("🔈 Quitar silencio", WidgetDescriber.mute(false))
    }

    @Test
    fun resultNotices() {
        val chrome = WidgetEntry(3, WidgetKind.BUTTON, "Chrome", "🌐")
        assertNull(WidgetDescriber.resultNotice(chrome, GestureStatus.EXECUTED))
        assertEquals("Chrome: sin acción en el PC", WidgetDescriber.resultNotice(chrome, GestureStatus.UNASSIGNED))
        assertEquals("Widget: sin acción en el PC", WidgetDescriber.resultNotice(null, GestureStatus.UNASSIGNED))
        assertEquals("El PC no lo ha ejecutado", WidgetDescriber.resultNotice(chrome, GestureStatus.ERROR))
    }
}
