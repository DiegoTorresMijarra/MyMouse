package dev.mymouse.core.btactions

import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.protocol.WidgetKind
import dev.mymouse.core.shapes.ShapeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BtGestureConfigTest {
    private val z = BtBinding("z", BtActions.parseKeys("Ctrl+Z"), "Deshacer", pinned = true)

    @Test
    fun defaultsUseRealShapeIdsAndTheSpecActions() {
        val d = BtGestureConfig.DEFAULT
        assertEquals(9, d.bindings.size)
        assertTrue(d.bindings.all { it.gesture in ShapeCatalog.BY_ID })
        assertEquals("Ctrl+Z", BtActions.formatKeys(d.find("z")!!.action as BtAction.Keys))
        assertEquals("Alt+Left", BtActions.formatKeys(d.find(ShapeCatalog.LINE_LEFT)!!.action as BtAction.Keys))
        assertEquals("Alt+Right", BtActions.formatKeys(d.find(ShapeCatalog.LINE_RIGHT)!!.action as BtAction.Keys))
        assertEquals("Win+D", BtActions.formatKeys(d.find(ShapeCatalog.LINE_DOWN)!!.action as BtAction.Keys))
        assertEquals("Win+Tab", BtActions.formatKeys(d.find(ShapeCatalog.LINE_UP)!!.action as BtAction.Keys))
        assertEquals(BtAction.Media(ConsumerKey.PLAY_PAUSE), d.find("v")!!.action)
        assertEquals(BtAction.Media(ConsumerKey.NEXT), d.find("gt")!!.action)
        assertEquals(BtAction.Media(ConsumerKey.PREVIOUS), d.find("lt")!!.action)
        assertEquals("Ctrl+S", BtActions.formatKeys(d.find("check")!!.action as BtAction.Keys))
        assertEquals("Deshacer", d.find("z")!!.help)
    }

    @Test
    fun defaultsSurviveAJsonRoundTrip() {
        val back = BtGestureConfig.parse(BtGestureConfig.DEFAULT.toJson())
        assertEquals(emptyList(), back.warnings)
        assertEquals(BtGestureConfig.DEFAULT, back.config)
    }

    @Test
    fun readsTheFileFormat() {
        val text = """
            { "version": 1, "bindings": [
              { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" }, "help": "Deshacer", "pin": true },
              { "gesture": "custom:firma", "action": { "type": "text", "text": "Un saludo,\nDiego" } },
              { "gesture": "v", "action": { "type": "media", "key": "PlayPause" } }
            ] }
        """.trimIndent()
        val r = BtGestureConfig.parse(text)
        assertEquals(emptyList(), r.warnings)
        assertEquals(listOf("z", "custom:firma", "v"), r.config.bindings.map { it.gesture })
        assertTrue(r.config.find("z")!!.pinned)
        assertEquals(BtAction.Text("Un saludo,\nDiego"), r.config.find("custom:firma")!!.action)
        assertFalse(r.config.find("v")!!.pinned)
    }

    @Test
    fun discardsBadRulesWithWarnings() {
        val text = """
            { "bindings": [
              { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Z" } },
              { "gesture": "z", "action": { "type": "keys", "keys": "Ctrl+Y" } },
              { "gesture": "pentagon", "action": { "type": "keys", "keys": "A" } },
              { "gesture": "v", "action": { "type": "launch", "target": "chrome.exe" } },
              { "gesture": "check", "action": { "type": "keys", "keys": "Ctrl+Ñ" } },
              { "action": { "type": "keys", "keys": "A" } },
              7
            ] }
        """.trimIndent()
        val r = BtGestureConfig.parse(text)
        assertEquals(listOf("z"), r.config.bindings.map { it.gesture })
        assertEquals("Ctrl+Z", BtActions.formatKeys(r.config.find("z")!!.action as BtAction.Keys))
        assertEquals(6, r.warnings.size)
        assertTrue(r.warnings[0].startsWith("Regla 2"))
    }

    @Test
    fun brokenFilesThrow() {
        assertFailsWith<IllegalArgumentException> { BtGestureConfig.parse("no es json") }
        assertFailsWith<IllegalArgumentException> { BtGestureConfig.parse("[]") }
        assertFailsWith<IllegalArgumentException> { BtGestureConfig.parse("{}") }
    }

    @Test
    fun editingReplacesInPlaceAndRemoves() {
        val c = BtGestureConfig.DEFAULT
        val changed = c.with(z.copy(action = BtActions.parseKeys("Ctrl+Y"), help = "Rehacer"))
        assertEquals(c.bindings.size, changed.bindings.size)
        assertEquals(0, changed.bindings.indexOfFirst { it.gesture == "z" })
        assertEquals("Rehacer", changed.find("z")!!.help)
        val added = c.with(BtBinding("circle", BtAction.Text("hola")))
        assertEquals("circle", added.bindings.last().gesture)
        assertNull(c.without("z").find("z"))
        assertEquals(c.bindings.size - 1, c.without("z").bindings.size)
    }

    @Test
    fun helpFallsBackToTheActionDescription() {
        val describe = { action: BtAction -> "<" + BtActions.logText(action) + ">" }
        assertEquals("<Ctrl+Z>", BtBinding("z", BtActions.parseKeys("Ctrl+Z")).helpText(describe))
        assertEquals("Atrás", BtBinding("z", BtActions.parseKeys("Ctrl+Z"), "Atrás").helpText(describe))
    }

    @Test
    fun listEntriesOnlyIncludeEnabledShapesWithAnAction() {
        val entries = BtGestureConfig.DEFAULT.listEntries(setOf("z", "v", "circle")) { BtActions.logText(it) }
        assertEquals(listOf("z", "v"), entries.map { it.name })
        assertEquals("Deshacer", entries[0].help)
        assertTrue(entries[0].pinned)
        assertTrue(entries[1].pinned)
    }

    @Test
    fun customShapesAreAccepted() {
        assertTrue(BtGestureConfig.isShape("custom:firma"))
        assertFalse(BtGestureConfig.isShape("custom:"))
        assertFalse(BtGestureConfig.isShape("nada"))
    }
}

class BtWidgetConfigTest {
    private val button = BtButton("Atrás", "←", BtActions.parseKeys("Alt+Left"))

    @Test
    fun defaultsAreTheSixSpecButtons() {
        val d = BtWidgetConfig.DEFAULT
        assertEquals(listOf("Copiar", "Pegar", "Captura", "Deshacer", "Bloquear", "Teclado"), d.buttons.map { it.label })
        assertEquals(listOf("📋", "📄", "✂️", "↩️", "🔒", "⌨"), d.buttons.map { it.icon })
        assertEquals(BtAction.Keyboard, d.buttons[5].action)
        assertEquals("Ctrl+C", BtActions.formatKeys(d.buttons[0].action as BtAction.Keys))
        assertEquals("Ctrl+V", BtActions.formatKeys(d.buttons[1].action as BtAction.Keys))
        assertEquals("Shift+Win+S", BtActions.formatKeys(d.buttons[2].action as BtAction.Keys))
        assertEquals("Ctrl+Z", BtActions.formatKeys(d.buttons[3].action as BtAction.Keys))
        assertEquals("Win+L", BtActions.formatKeys(d.buttons[4].action as BtAction.Keys))
        assertTrue(d.buttons.all { BtWidgetConfig.validIcon(it.icon) && BtWidgetConfig.validLabel(it.label) })
    }

    @Test
    fun panelHasMediaVolumeThenCustomButtons() {
        val entries = BtWidgetConfig.DEFAULT.entries()
        assertEquals(4 + 6, entries.size)
        assertEquals(WidgetKind.MEDIA, entries[0].kind)
        assertEquals(listOf("Vol −", "Vol +", "Silencio"), entries.subList(1, 4).map { it.label })
        assertEquals(listOf("Copiar", "Pegar", "Captura", "Deshacer", "Bloquear", "Teclado"), entries.drop(4).map { it.label })
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertTrue(entries.drop(1).all { it.kind == WidgetKind.BUTTON })
    }

    @Test
    fun widgetIdsPointToTheirButton() {
        val d = BtWidgetConfig.DEFAULT
        val entries = d.entries()
        assertEquals("Copiar", d.buttonFor(entries[4].id)!!.label)
        assertEquals("Teclado", d.buttonFor(entries.last().id)!!.label)
        assertNull(d.buttonFor(BtWidgetConfig.ID_VOLUME_UP))
        assertNull(d.buttonFor(BtWidgetConfig.FIRST_CUSTOM_ID + 6))
        assertNull(d.buttonFor(-1))
    }

    @Test
    fun maxSixteenButtons() {
        var c = BtWidgetConfig(emptyList())
        repeat(20) { c = c.add(button.copy(label = "B$it")) }
        assertEquals(16, c.buttons.size)
        assertTrue(c.isFull)
        assertEquals("B15", c.buttons.last().label)
        assertEquals(16, BtWidgetConfig(List(30) { button }).buttons.size)
    }

    @Test
    fun movesRemovesAndReplaces() {
        val c = BtWidgetConfig.DEFAULT
        assertEquals(listOf("Pegar", "Copiar"), c.move(0, 1).buttons.map { it.label }.take(2))
        assertEquals(c, c.move(0, -1))
        assertEquals(c, c.move(5, 1))
        assertEquals(listOf("Copiar", "Captura", "Pegar"), c.move(1, 1).buttons.map { it.label }.take(3))
        assertEquals(5, c.remove(2).buttons.size)
        assertEquals(c, c.remove(9))
        assertEquals("Atrás", c.replace(1, button).buttons[1].label)
    }

    @Test
    fun jsonRoundTripKeepsEmojiWithVariationSelectors() {
        val back = BtWidgetConfig.parse(BtWidgetConfig.DEFAULT.toJson())
        assertEquals(emptyList(), back.warnings)
        assertEquals(BtWidgetConfig.DEFAULT, back.config)
    }

    @Test
    fun discardsInvalidButtonsWithWarnings() {
        val text = """
            { "buttons": [
              { "label": "Ok", "icon": "OK", "action": { "type": "keys", "keys": "Ctrl+S" } },
              { "label": "", "icon": "X", "action": { "type": "keys", "keys": "Ctrl+S" } },
              { "label": "Sin icono", "icon": " ", "action": { "type": "keys", "keys": "Ctrl+S" } },
              { "label": "Largo", "icon": "ABCDE", "action": { "type": "keys", "keys": "Ctrl+S" } },
              { "label": "Rara", "icon": "R", "action": { "type": "launch" } },
              { "label": "Texto", "icon": "T", "action": { "type": "text", "text": "hola" } }
            ] }
        """.trimIndent()
        val r = BtWidgetConfig.parse(text)
        assertEquals(listOf("Ok", "Texto"), r.config.buttons.map { it.label })
        assertEquals(4, r.warnings.size)
        assertFailsWith<IllegalArgumentException> { BtWidgetConfig.parse("[]") }
        assertFailsWith<IllegalArgumentException> { BtWidgetConfig.parse("{}") }
    }

    @Test
    fun iconAndLabelValidation() {
        assertTrue(BtWidgetConfig.validIcon("📋"))
        assertTrue(BtWidgetConfig.validIcon("✂️"))
        assertTrue(BtWidgetConfig.validIcon("ABC"))
        assertFalse(BtWidgetConfig.validIcon(""))
        assertFalse(BtWidgetConfig.validIcon("ABCDE"))
        assertFalse(BtWidgetConfig.validLabel(" "))
        assertFalse(BtWidgetConfig.validLabel("x".repeat(21)))
        assertTrue(BtWidgetConfig.validLabel("x".repeat(20)))
        assertEquals(HidKeys.MOD_CTRL, (BtWidgetConfig.DEFAULT.buttons[0].action as BtAction.Keys).modifiers)
    }

    @Test
    fun factoryDefaultsComeInBothLanguages() {
        val en = BtWidgetConfig.defaultFor("en")
        assertEquals(listOf("Copy", "Paste", "Snip", "Undo", "Lock", "Keyboard"), en.buttons.map { it.label })
        assertEquals(BtWidgetConfig.DEFAULT.buttons.map { it.action }, en.buttons.map { it.action })
        assertEquals(BtWidgetConfig.DEFAULT, BtWidgetConfig.defaultFor("es"))
        val gestures = BtGestureConfig.defaultFor("fr")
        assertEquals("Undo", gestures.find("z")!!.help)
        assertEquals(BtGestureConfig.DEFAULT.bindings.map { it.gesture to it.action }, gestures.bindings.map { it.gesture to it.action })
    }
}
