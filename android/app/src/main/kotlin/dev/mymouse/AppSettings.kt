package dev.mymouse

import android.content.Context
import dev.mymouse.core.gesture.TrackpadConfig
import dev.mymouse.core.widgets.WidgetPanelSize

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
    val orientation: Orientation = Orientation.FREE,
    /** Usar el fijado de apps de Android en el modo bloqueado (bloquea Inicio y Recientes). */
    val pinApp: Boolean = true,
    /** En modo ventana, volver solo al modo bloqueado tras 30 s sin tocar la pantalla. */
    val autoRelock: Boolean = false,
    /** Modo depuración: minipantalla con los dedos y las acciones enviadas. */
    val debug: Boolean = false,
    /** Dedos que activan el modo gestos: 3, 4 o 0 (desactivado). */
    val gestureFingers: Int = 3,
    /** USB (MyMouse en el PC) o Bluetooth (el móvil como ratón, sin nada en el PC). */
    val connection: Connection = Connection.USB,
    /** Segundos para empezar a dibujar la forma tras levantar los dedos de la activación. */
    val gestureStrokeTimeoutSeconds: Int = 5,
    /** Panel de widgets junto al trackpad (solo aparece si el PC ha enviado widgets). */
    val showWidgets: Boolean = true,
    val widgetPanelSize: WidgetPanelSize = WidgetPanelSize.MEDIUM,
) {
    enum class Orientation { FREE, PORTRAIT, LANDSCAPE }

    fun trackpadConfig() = TrackpadConfig(
        sensitivity = sensitivity,
        acceleration = acceleration,
        scrollSpeed = scrollSpeed,
        naturalScroll = naturalScroll,
        tapToDrag = tapToDrag,
        gestureFingers = gestureFingers,
        gestureStrokeTimeoutMs = gestureStrokeTimeoutSeconds * 1000L,
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
            .putString("orientation", orientation.name)
            .putBoolean("pinApp", pinApp)
            .putBoolean("autoRelock", autoRelock)
            .putBoolean("debug", debug)
            .putInt("gestureFingers", gestureFingers)
            .putString("connection", connection.name)
            .putInt("gestureStrokeTimeoutSeconds", gestureStrokeTimeoutSeconds)
            .putBoolean("showWidgets", showWidgets)
            .putString("widgetPanelSize", widgetPanelSize.name)
            .apply()
    }

    enum class Connection { USB, BLUETOOTH }

    companion object {
        const val MIN_STROKE_TIMEOUT_S = 2
        const val MAX_STROKE_TIMEOUT_S = 15
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
                orientation = p.getString("orientation", null)
                    ?.let { name -> Orientation.entries.firstOrNull { it.name == name } }
                    // v0.4 guardaba solo "landscape" (true = horizontal, false = vertical fijo, que era el defecto).
                    ?: if (p.getBoolean("landscape", false)) Orientation.LANDSCAPE else d.orientation,
                pinApp = p.getBoolean("pinApp", d.pinApp),
                autoRelock = p.getBoolean("autoRelock", d.autoRelock),
                debug = p.getBoolean("debug", d.debug),
                gestureFingers = p.getInt("gestureFingers", d.gestureFingers).takeIf { it in setOf(0, 3, 4) } ?: d.gestureFingers,
                gestureStrokeTimeoutSeconds = p.getInt("gestureStrokeTimeoutSeconds", d.gestureStrokeTimeoutSeconds)
                    .coerceIn(MIN_STROKE_TIMEOUT_S, MAX_STROKE_TIMEOUT_S),
                connection = p.getString("connection", null)
                    ?.let { name -> Connection.entries.firstOrNull { it.name == name } } ?: d.connection,
                showWidgets = p.getBoolean("showWidgets", d.showWidgets),
                widgetPanelSize = p.getString("widgetPanelSize", null)
                    ?.let { name -> WidgetPanelSize.entries.firstOrNull { it.name == name } } ?: d.widgetPanelSize,
            )
        }
    }
}
