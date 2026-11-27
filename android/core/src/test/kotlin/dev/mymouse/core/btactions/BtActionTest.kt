package dev.mymouse.core.btactions

import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.json.MiniJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BtActionTest {
    @Test
    fun parsesShortcuts() {
        assertEquals(BtAction.Keys(HidKeys.MOD_CTRL, 0x1D), BtActions.parseKeys("Ctrl+Z"))
        assertEquals(BtAction.Keys(HidKeys.MOD_CTRL or HidKeys.MOD_SHIFT, 0x17), BtActions.parseKeys("ctrl + shift + t"))
        assertEquals(BtAction.Keys(HidKeys.MOD_GUI or HidKeys.MOD_SHIFT, 0x16), BtActions.parseKeys("Win+Shift+S"))
        assertEquals(BtAction.Keys(0, 0x3E), BtActions.parseKeys("F5"))
        assertEquals(BtAction.Keys(HidKeys.MOD_ALT, 0x50), BtActions.parseKeys("Alt+Left"))
        assertEquals(BtAction.Keys(HidKeys.MOD_ALT, 0x50), BtActions.parseKeys("Alt+Izquierda"))
        assertEquals(BtAction.Keys(HidKeys.MOD_CTRL, 0x4C), BtActions.parseKeys("Control+Supr"))
        // Solo modificadores: se pulsa y se suelta (p. ej. "Win" abre Inicio).
        assertEquals(BtAction.Keys(HidKeys.MOD_GUI, 0), BtActions.parseKeys("Win"))
    }

    @Test
    fun rejectsWhatItCannotType() {
        for (bad in listOf("", "  ", "Ctrl+", "Ctrl+Ñ", "Ctrl+K, Ctrl+C", "A+B", "Z+Ctrl", "Ctrl+F24", "Hyper+A")) {
            assertFailsWith<IllegalArgumentException>(bad) { BtActions.parseKeys(bad) }
        }
    }

    @Test
    fun formatsInFixedOrderAndRoundTrips() {
        assertEquals("Ctrl+Shift+T", BtActions.formatKeys(BtActions.parseKeys("Shift+Ctrl+T")))
        assertEquals("Ctrl+Alt+Win+Delete", BtActions.formatKeys(BtActions.parseKeys("Win+Alt+Ctrl+Supr")))
        for (key in HidKeys.PICKER) {
            val keys = BtAction.Keys(HidKeys.MOD_CTRL, key.usage)
            assertEquals(keys, BtActions.parseKeys(BtActions.formatKeys(keys)), key.name)
        }
        assertEquals("Ctrl+Inicio", BtActions.displayKeys(BtActions.parseKeys("Ctrl+Home")))
    }

    @Test
    fun pickerHasUniqueNamesAndUsagesWithinTheDescriptorRange() {
        assertEquals(PICKER_SIZE, HidKeys.PICKER.size)
        assertEquals(HidKeys.PICKER.size, HidKeys.PICKER.map { it.name.lowercase() }.toSet().size)
        assertEquals(HidKeys.PICKER.size, HidKeys.PICKER.map { it.usage }.toSet().size)
        // El descriptor del teclado llega hasta el uso 0x65.
        assertEquals(true, HidKeys.PICKER.all { it.usage in 0x04..0x65 })
    }

    @Test
    fun mediaNamesRoundTrip() {
        for (option in BtActions.MEDIA) assertEquals(option.key, BtActions.parseMedia(option.name))
        assertEquals(ConsumerKey.PLAY_PAUSE, BtActions.parseMedia("playpause"))
        assertEquals(ConsumerKey.MUTE, BtActions.parseMedia("VolumeMute"))
        assertEquals(ConsumerKey.entries.size, BtActions.MEDIA.size)
        assertFailsWith<IllegalArgumentException> { BtActions.parseMedia("Stop") }
    }

    @Test
    fun jsonRoundTripForEveryType() {
        val actions = listOf(
            BtAction.Keys(HidKeys.MOD_CTRL or HidKeys.MOD_ALT, 0x4C),
            BtAction.Media(ConsumerKey.NEXT),
            BtAction.Text("Un saludo,\nDiego ñ"),
        )
        for (a in actions) {
            val json = MiniJson.parse(MiniJson.write(BtActions.toJson(a)))
            assertEquals(a, BtActions.fromJson(json))
        }
    }

    @Test
    fun fromJsonRejectsPcOnlyAndBrokenActions() {
        fun parse(s: String) = BtActions.fromJson(MiniJson.parse(s))
        for (bad in listOf(
            """{"type":"launch","target":"chrome.exe"}""", """{"type":"mymouse","command":"lock"}""", """{"keys":"Ctrl+Z"}""",
            """{"type":"keys"}""", """{"type":"text","text":""}""", """{"type":"media","key":"Stop"}""", "[]", "null",
        )) assertFailsWith<IllegalArgumentException>(bad) { parse(bad) }
    }

    @Test
    fun longTextIsTruncated() {
        val long = "a".repeat(BtAction.MAX_TEXT + 50)
        val a = BtActions.fromJson(mapOf("type" to "text", "text" to long)) as BtAction.Text
        assertEquals(BtAction.MAX_TEXT, a.text.length)
    }

    @Test
    fun describesActions() {
        assertEquals("Ctrl+Z", BtActions.describe(BtActions.parseKeys("ctrl+z")))
        assertEquals("Reproducir / pausar", BtActions.describe(BtAction.Media(ConsumerKey.PLAY_PAUSE)))
        assertEquals("Escribir \"hola\"", BtActions.describe(BtAction.Text("hola")))
        assertEquals("Escribir \"" + "x".repeat(20) + "…\"", BtActions.describe(BtAction.Text("x".repeat(30))))
    }

    private companion object {
        /** 26 letras + 10 cifras + 12 F + 4 flechas + 11 de edición/navegación. */
        const val PICKER_SIZE = 26 + 10 + 12 + 4 + 11
    }
}
