package dev.mymouse.core.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemesTest {
    @Test
    fun hasTheSevenThemesAndUniverseIsTheDefault() {
        assertEquals(listOf("universe", "midnight", "graphite", "ocean", "aurora", "oled", "nebula"), Themes.ALL.map { it.id })
        assertEquals("universe", Themes.DEFAULT_ID)
        assertEquals("universe", ThemeChoice().theme.id)
        assertEquals("universe", Themes.resolve(null).id)
    }

    @Test
    fun migrationFromNoThemeOrUnknownThemeGivesTheSoberUniverse() {
        val migrated = ThemeChoice(themeId = "no-existe", accentId = "no-existe").normalized()
        assertEquals("universe", migrated.themeId)
        assertEquals(Themes.ACCENT_AUTO, migrated.accentId)
        // Universo es la paleta sobria: sin el violeta intenso de Nebulosa.
        val universe = Themes.resolve(null)
        assertEquals(listOf(0x05070D, 0x0A0F1C, 0x0E1424), universe.base)
        assertEquals(2, universe.nebulas.size)
        assertTrue(universe.nebulas.none { it.color == 0x7C3AED || it.color == 0xDB2777 })
        assertEquals(listOf(0x070616, 0x120D2E, 0x1B0F36), Themes.byId("nebula")!!.base)
    }

    @Test
    fun autoAccentIsTheThemeAccentAndAnOverrideWins() {
        for (theme in Themes.ALL) {
            assertEquals(theme.accent, Themes.accentColor(theme, Themes.ACCENT_AUTO), theme.id)
            assertEquals(theme.accent, Themes.accentColor(theme, "desconocido"), theme.id)
            assertEquals(0xF5B94A, Themes.accentColor(theme, "amber"), theme.id)
        }
        assertEquals(0x7AA2F7, ThemeChoice().accent)
        assertEquals(0xF472B6, ThemeChoice(themeId = "graphite", accentId = "pink").accent)
        assertEquals(0x9AA4B2, ThemeChoice(themeId = "graphite").accent)
        assertEquals(listOf("steel", "lavender", "turquoise", "green", "amber", "pink", "white"), Themes.ACCENTS.map { it.id })
    }

    @Test
    fun starsOnlyShowInThemesThatHaveThemAndWhenChecked() {
        assertTrue(ThemeChoice().showStars)
        assertFalse(ThemeChoice(stars = false).showStars)
        for (id in listOf("midnight", "graphite", "oled")) assertFalse(ThemeChoice(themeId = id).showStars, id)
        for (id in listOf("universe", "ocean", "aurora", "nebula")) assertTrue(ThemeChoice(themeId = id).showStars, id)
    }

    @Test
    fun twinkleIsOffInTheTrackpadByDefaultAndFollowsTheToggles() {
        val c = ThemeChoice()
        assertFalse(c.twinkles(trackpad = true))
        assertTrue(c.twinkles(trackpad = false))
        assertTrue(c.copy(twinkleTrackpad = true).twinkles(trackpad = true))
        assertFalse(c.copy(twinkle = false).twinkles(trackpad = false))
        assertFalse(c.copy(stars = false).twinkles(trackpad = false))
        assertFalse(ThemeChoice(themeId = "oled").twinkles(trackpad = false))
    }

    @Test
    fun starCountScalesWithTheThemeDensity() {
        val full = Themes.starCount(Themes.byId("nebula")!!, 400f, 600f)
        assertEquals(90, full)
        assertEquals(72, Themes.starCount(Themes.byId("universe")!!, 400f, 600f)) // ×0,8
        assertEquals(45, Themes.starCount(Themes.byId("ocean")!!, 400f, 600f)) // ×0,5
        assertEquals(0, Themes.starCount(Themes.byId("oled")!!, 400f, 600f))
    }

    @Test
    fun derivedColors() {
        assertEquals(0x000000, Themes.mix(0x000000, 0xFFFFFF, 0f))
        assertEquals(0xFFFFFF, Themes.mix(0x000000, 0xFFFFFF, 1f))
        assertEquals(0x7F7F7F, Themes.mix(0x000000, 0xFFFFFF, 0.5f))
        // El degradado de los botones es más oscuro que el acento, con cualquier acento (el texto es blanco).
        for (a in Themes.ACCENTS) {
            assertTrue(Themes.accentStart(a.color) < a.color || (a.color and 0xFF) == 0)
            assertTrue(luminance(Themes.accentEnd(a.color)) < luminance(Themes.accentStart(a.color)), a.id)
            assertTrue(luminance(Themes.accentStart(a.color)) < 0.6, a.id)
        }
        assertEquals("#0A0F1C", Themes.hex(0x0A0F1C))
        assertEquals(0x7AA2F7, Themes.parseHex("#7AA2F7"))
        assertNull(Themes.parseHex("7AA2F7"))
        assertNull(Themes.parseHex("#7AA2F"))
        assertNull(Themes.parseHex("#GGGGGG"))
    }

    private fun luminance(rgb: Int): Double {
        val r = (rgb shr 16 and 0xFF) / 255.0
        val g = (rgb shr 8 and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}

class ThemeJsonTest {
    private fun sample(extra: String = "") = """
        {"version":1,"defaultTheme":"a","accents":[{"id":"x","name":"X","color":"#112233"}],
         "themes":[{"id":"a","name":"A","base":["#000000","#101010"],"nebulas":[{"color":"#222222","opacity":0.5,"x":0,"y":1,"r":0.7}],
         "stars":true,"starDensity":1,"twinkleDepth":1,"accent":"#445566"$extra}]}
    """.trimIndent()

    @Test
    fun parsesAValidFile() {
        val f = ThemeJson.parse(sample())
        assertEquals("a", f.defaultTheme)
        assertEquals(listOf(AccentOption("x", "X", 0x112233)), f.accents)
        val t = f.themes.single()
        assertEquals(listOf(0x000000, 0x101010), t.base)
        assertEquals(Nebula(0x222222, 0.5f, 0f, 1f, 0.7f), t.nebulas.single())
        assertEquals(0x445566, t.accent)
    }

    @Test
    fun rejectsInvalidFiles() {
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse("no es json") }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse("[]") }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("\"defaultTheme\":\"a\"", "\"defaultTheme\":\"zz\"")) }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("#445566", "445566")) }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("\"opacity\":0.5", "\"opacity\":1.5")) }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("\"r\":0.7", "\"r\":0")) }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("[\"#000000\",\"#101010\"]", "[]")) }
        assertFailsWith<IllegalArgumentException> { ThemeJson.parse(sample().replace("\"id\":\"x\"", "\"id\":\"auto\"")) }
    }
}

/** Compara [Themes] con `shared/themes.json`, el mismo fichero que lee el test de C# (`ThemeCatalogTests`). */
class ThemesFixtureTest {
    private fun shared(): ThemeFile {
        val text = assertNotNull(javaClass.getResourceAsStream("/themes.json")).bufferedReader(Charsets.UTF_8).readText()
        return ThemeJson.parse(text)
    }

    @Test
    fun definitionsMatchTheSharedFile() {
        val file = shared()
        assertEquals(Themes.DEFAULT_ID, file.defaultTheme)
        assertEquals(Themes.ACCENTS, file.accents)
        assertEquals(Themes.ALL.map { it.id }, file.themes.map { it.id })
        for ((mine, theirs) in Themes.ALL.zip(file.themes)) {
            assertEquals(mine.name, theirs.name, mine.id)
            assertEquals(mine.base, theirs.base, mine.id)
            assertEquals(mine.stars, theirs.stars, mine.id)
            assertEquals(mine.accent, theirs.accent, mine.id)
            assertTrue(abs(mine.starDensity - theirs.starDensity) < 1e-6f, mine.id)
            assertTrue(abs(mine.twinkleDepth - theirs.twinkleDepth) < 1e-6f, mine.id)
            assertEquals(mine.nebulas.size, theirs.nebulas.size, mine.id)
            for ((a, b) in mine.nebulas.zip(theirs.nebulas)) {
                assertEquals(a.color, b.color, mine.id)
                assertTrue(
                    abs(a.opacity - b.opacity) < 1e-6f && abs(a.x - b.x) < 1e-6f && abs(a.y - b.y) < 1e-6f && abs(a.r - b.r) < 1e-6f,
                    "${mine.id}: $a vs $b",
                )
            }
        }
    }
}
