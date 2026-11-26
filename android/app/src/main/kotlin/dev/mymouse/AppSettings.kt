package dev.mymouse

import android.content.Context
import dev.mymouse.core.gesture.TrackpadConfig

/** Ajustes de la app (docs/spec-v0.md §2.5), guardados en SharedPreferences. */
data class AppSettings(
    val sensitivity: Float = 1.0f,
    val acceleration: Float = 0.6f,
    val scrollSpeed: Float = 2.0f,
    val naturalScroll: Boolean = true,
    val tapToDrag: Boolean = true,
    val showCheatSheet: Boolean = true,
    /** Segundos sin tocar la pantalla hasta atenuarla; 0 = nunca. */
    val dimAfterSeconds: Int = 60,
    val landscape: Boolean = false,
) {
    fun trackpadConfig() = TrackpadConfig(
        sensitivity = sensitivity,
        acceleration = acceleration,
        scrollSpeed = scrollSpeed,
        naturalScroll = naturalScroll,
        tapToDrag = tapToDrag,
    )

    fun save(context: Context) {
        prefs(context).edit()
            .putFloat("sensitivity", sensitivity)
            .putFloat("acceleration", acceleration)
            .putFloat("scrollSpeed", scrollSpeed)
            .putBoolean("naturalScroll", naturalScroll)
            .putBoolean("tapToDrag", tapToDrag)
            .putBoolean("showCheatSheet", showCheatSheet)
            .putInt("dimAfterSeconds", dimAfterSeconds)
            .putBoolean("landscape", landscape)
            .apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        fun load(context: Context): AppSettings {
            val p = prefs(context)
            val d = AppSettings()
            return AppSettings(
                sensitivity = p.getFloat("sensitivity", d.sensitivity),
                acceleration = p.getFloat("acceleration", d.acceleration),
                scrollSpeed = p.getFloat("scrollSpeed", d.scrollSpeed),
                naturalScroll = p.getBoolean("naturalScroll", d.naturalScroll),
                tapToDrag = p.getBoolean("tapToDrag", d.tapToDrag),
                showCheatSheet = p.getBoolean("showCheatSheet", d.showCheatSheet),
                dimAfterSeconds = p.getInt("dimAfterSeconds", d.dimAfterSeconds),
                landscape = p.getBoolean("landscape", d.landscape),
            )
        }
    }
}
