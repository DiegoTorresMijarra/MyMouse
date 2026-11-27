package dev.mymouse

import android.content.Context
import dev.mymouse.core.btactions.KeyboardLayouts
import dev.mymouse.core.gesture.TrackpadConfig
import dev.mymouse.core.keyboard.KeySize
import dev.mymouse.core.keyboard.KeyboardLayoutSetting
import dev.mymouse.core.theme.ThemeChoice
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
    /** Dedos que activan el modo gestos: 4, o 0 (desactivado). */
    val gestureFingers: Int = 4,
    /** Gestos de Windows con 3 dedos: vista de tareas, escritorio y cambio de app (docs/spec-v3.1-ajustes.md §1). */
    val windowsGestures: Boolean = true,
    /** Velocidad del zoom con 2 dedos (×0,5 a ×4). */
    val zoomSpeed: Float = 1.5f,
    /** Scroll con inercia al levantar los 2 dedos con velocidad. */
    val scrollInertia: Boolean = true,
    /** USB (MyMouse en el PC) o Bluetooth (el móvil como ratón, sin nada en el PC). */
    val connection: Connection = Connection.USB,
    /** Segundos para empezar a dibujar la forma tras levantar los dedos de la activación. */
    val gestureStrokeTimeoutSeconds: Int = 5,
    /** Panel de widgets junto al trackpad (solo aparece si el PC ha enviado widgets). */
    val showWidgets: Boolean = true,
    val widgetPanelSize: WidgetPanelSize = WidgetPanelSize.MEDIUM,
    /** Cadencia del envío del movimiento por Bluetooth, en ms (ajuste avanzado, docs/spec-v4.5-estilo.md §4.2). */
    val bluetoothIntervalMs: Int = 8,
    /** Distribución del teclado del PC para escribir texto por Bluetooth: `es` (Español de España) o `us` (docs/spec-v5-bluetooth-completo.md §2.1). */
    val btKeyboardLayout: String = "es",
    /** Scroll fino por Bluetooth (docs/spec-v5-bluetooth-completo.md §4). Apagado, se registra el descriptor HID antiguo. */
    val btFineScroll: Boolean = true,
    /** Tema, acento y estrellas de este móvil (docs/spec-v4.8-temas.md). Por defecto, Universo. Por cable se sincroniza con el PC (v6.1). */
    val theme: ThemeChoice = ThemeChoice(),
    /** Cuándo se eligió el tema, el acento o las estrellas por última vez (ms desde 1970, UTC; 0 = nunca). Al sincronizar con el PC, gana el más reciente. */
    val themeChangedAt: Long = 0,
    /** Modo teclado (docs/spec-v6-teclado.md §6): distribución que se dibuja ("Automática" = la del PC por cable, el ajuste de texto por Bluetooth). */
    val kbLayout: KeyboardLayoutSetting = KeyboardLayoutSetting.AUTO,
    val kbKeySize: KeySize = KeySize.MEDIUM,
    val kbVibrate: Boolean = true,
    val kbRepeat: Boolean = true,
    val kbTrackpadBar: Boolean = false,
    /** Combinación de bloques recordada por orientación (`fn,nav,num`…); null = la de por defecto. */
    val kbComboPortrait: String? = null,
    val kbComboLandscape: String? = null,
) {
    enum class Orientation { FREE, PORTRAIT, LANDSCAPE }

    /** La distribución del teclado del PC elegida. */
    val keyboardLayout get() = KeyboardLayouts.byId(btKeyboardLayout)

    fun trackpadConfig() = TrackpadConfig(
        sensitivity = sensitivity,
        acceleration = acceleration,
        scrollSpeed = scrollSpeed,
        naturalScroll = naturalScroll,
        tapToDrag = tapToDrag,
        gestureFingers = gestureFingers,
        windowsGestures = windowsGestures,
        zoomSpeed = zoomSpeed,
        scrollInertia = scrollInertia,
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
            .putBoolean("windowsGestures", windowsGestures)
            .putFloat("zoomSpeed", zoomSpeed)
            .putBoolean("scrollInertia", scrollInertia)
            .putString("connection", connection.name)
            .putInt("gestureStrokeTimeoutSeconds", gestureStrokeTimeoutSeconds)
            .putBoolean("showWidgets", showWidgets)
            .putString("widgetPanelSize", widgetPanelSize.name)
            .putInt("bluetoothIntervalMs", bluetoothIntervalMs)
            .putString("btKeyboardLayout", btKeyboardLayout)
            .putBoolean("btFineScroll", btFineScroll)
            .putString("theme", theme.themeId)
            .putString("accent", theme.accentId)
            .putBoolean("themeStars", theme.stars)
            .putBoolean("themeTwinkle", theme.twinkle)
            .putBoolean("themeTwinkleTrackpad", theme.twinkleTrackpad)
            .putLong("themeChangedAt", themeChangedAt)
            .putString("kbLayout", kbLayout.id)
            .putString("kbKeySize", kbKeySize.id)
            .putBoolean("kbVibrate", kbVibrate)
            .putBoolean("kbRepeat", kbRepeat)
            .putBoolean("kbTrackpadBar", kbTrackpadBar)
            .putString("kbComboPortrait", kbComboPortrait)
            .putString("kbComboLandscape", kbComboLandscape)
            .apply()
    }

    enum class Connection { USB, BLUETOOTH }

    companion object {
        const val MIN_STROKE_TIMEOUT_S = 2
        const val MAX_STROKE_TIMEOUT_S = 15
        const val MIN_BLUETOOTH_INTERVAL_MS = 6
        const val MAX_BLUETOOTH_INTERVAL_MS = 20
        const val MIN_ZOOM_SPEED = 0.5f
        const val MAX_ZOOM_SPEED = 4f
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
                    ?: d.orientation,
                pinApp = p.getBoolean("pinApp", d.pinApp),
                autoRelock = p.getBoolean("autoRelock", d.autoRelock),
                debug = p.getBoolean("debug", d.debug),
                // Con 3 dedos los gestos son los de Windows: cualquier valor distinto de 0 es 4.
                gestureFingers = when (p.getInt("gestureFingers", d.gestureFingers)) {
                    0 -> 0
                    else -> 4
                },
                windowsGestures = p.getBoolean("windowsGestures", d.windowsGestures),
                zoomSpeed = p.getFloat("zoomSpeed", d.zoomSpeed).coerceIn(MIN_ZOOM_SPEED, MAX_ZOOM_SPEED),
                scrollInertia = p.getBoolean("scrollInertia", d.scrollInertia),
                gestureStrokeTimeoutSeconds = p.getInt("gestureStrokeTimeoutSeconds", d.gestureStrokeTimeoutSeconds)
                    .coerceIn(MIN_STROKE_TIMEOUT_S, MAX_STROKE_TIMEOUT_S),
                connection = p.getString("connection", null)
                    ?.let { name -> Connection.entries.firstOrNull { it.name == name } } ?: d.connection,
                showWidgets = p.getBoolean("showWidgets", d.showWidgets),
                widgetPanelSize = p.getString("widgetPanelSize", null)
                    ?.let { name -> WidgetPanelSize.entries.firstOrNull { it.name == name } } ?: d.widgetPanelSize,
                bluetoothIntervalMs = p.getInt("bluetoothIntervalMs", d.bluetoothIntervalMs)
                    .coerceIn(MIN_BLUETOOTH_INTERVAL_MS, MAX_BLUETOOTH_INTERVAL_MS),
                btKeyboardLayout = KeyboardLayouts.byId(p.getString("btKeyboardLayout", null)).id,
                btFineScroll = p.getBoolean("btFineScroll", d.btFineScroll),
                // Sin tema guardado (versiones anteriores a la 4.8) o con uno desconocido: Universo, con la paleta sobria.
                theme = ThemeChoice(
                    themeId = p.getString("theme", null) ?: d.theme.themeId,
                    accentId = p.getString("accent", null) ?: d.theme.accentId,
                    stars = p.getBoolean("themeStars", d.theme.stars),
                    twinkle = p.getBoolean("themeTwinkle", d.theme.twinkle),
                    twinkleTrackpad = p.getBoolean("themeTwinkleTrackpad", d.theme.twinkleTrackpad),
                ).normalized(),
                themeChangedAt = p.getLong("themeChangedAt", d.themeChangedAt),
                kbLayout = KeyboardLayoutSetting.fromId(p.getString("kbLayout", null)),
                kbKeySize = KeySize.fromId(p.getString("kbKeySize", null)),
                kbVibrate = p.getBoolean("kbVibrate", d.kbVibrate),
                kbRepeat = p.getBoolean("kbRepeat", d.kbRepeat),
                kbTrackpadBar = p.getBoolean("kbTrackpadBar", d.kbTrackpadBar),
                kbComboPortrait = p.getString("kbComboPortrait", null),
                kbComboLandscape = p.getString("kbComboLandscape", null),
            )
        }
    }
}
