package dev.mymouse.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tema sincronizado (protocolo v8). Mismos casos que ThemeSyncTests en el PC. */
class ThemeSyncTest {
    private val universe = SyncedTheme("universe", "auto", true, 1000)

    @Test
    fun newerRemoteWins() {
        val remote = universe.copy(themeId = "ocean", changedAtMs = 2000)
        assertEquals(remote, ThemeSync.resolve(universe, remote, localIsPhone = true))
    }

    @Test
    fun olderRemoteIsIgnored() {
        assertNull(ThemeSync.resolve(universe, universe.copy(themeId = "ocean", changedAtMs = 500), localIsPhone = false))
    }

    @Test
    fun sameInstantDifferentLookPhoneWins() {
        val phone = SyncedTheme("graphite", "auto", true, 0)
        val pc = SyncedTheme("universe", "auto", true, 0)
        assertNull(ThemeSync.resolve(phone, pc, localIsPhone = true))
        assertEquals(phone, ThemeSync.resolve(pc, phone, localIsPhone = false))
    }

    @Test
    fun sameLookOnlyTakesANewerInstant() {
        assertEquals(universe.copy(changedAtMs = 5000), ThemeSync.resolve(universe, universe.copy(changedAtMs = 5000), localIsPhone = false))
        assertNull(ThemeSync.resolve(universe, universe, localIsPhone = true))
    }

    @Test
    fun applyKeepsTheLocalAnimation() {
        val mine = ThemeChoice(themeId = "graphite", twinkle = false, twinkleTrackpad = true)
        val applied = SyncedTheme("ocean", "green", false, 1).applyTo(mine)
        assertEquals(ThemeChoice("ocean", "green", stars = false, twinkle = false, twinkleTrackpad = true), applied)
    }
}
