package dev.mymouse

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import dev.mymouse.core.keyboard.KeySize
import dev.mymouse.core.keyboard.KeyboardLayoutSetting
import dev.mymouse.core.theme.SyncedTheme
import dev.mymouse.core.theme.ThemeChoice
import dev.mymouse.core.theme.Themes
import dev.mymouse.core.widgets.WidgetPanelSize
import kotlin.math.roundToInt

/** Pantalla de ajustes, sin dependencias extra: barras deslizantes e interruptores. Guarda al salir. */
class SettingsActivity : Activity() {

    private lateinit var settings: AppSettings
    private val updatesUi by lazy { Flavor.updates.settingsSection(this) }
    private lateinit var screen: GlassTheme.Screen

    /** La tarjeta de cristal de la sección actual: lo que se añade a `list` va en ella. */
    internal lateinit var list: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings.load(this)
        title = getString(R.string.settings_window_title)
        buildUi()
        restoreScroll(savedInstanceState?.getInt(KEY_SCROLL_Y) ?: 0, savedInstanceState?.getInt(KEY_SWATCHES_X))
    }

    /** Construye (o reconstruye, al cambiar el tema) toda la pantalla con los ajustes y el tema actuales. */
    private fun buildUi() {
        swatchScroll = null
        selectedCell = null
        screen = GlassTheme.Screen(this, getString(R.string.settings_title))
        themeSection()
        section(getString(R.string.settings_trackpad))
        list.addView(slider(getString(R.string.settings_sensitivity), 0.3f, 3f, 0.1f, settings.sensitivity, { "×%.1f".format(it) }) {
            settings = settings.copy(sensitivity = it)
        })
        list.addView(slider(getString(R.string.settings_acceleration), 0f, 2f, 0.1f, settings.acceleration, { if (it == 0f) getString(R.string.settings_no_acceleration) else "%.1f".format(it) }) {
            settings = settings.copy(acceleration = it)
        })
        list.addView(slider(getString(R.string.settings_scroll_speed), 0.5f, 6f, 0.5f, settings.scrollSpeed, { "×%.1f".format(it) }) {
            settings = settings.copy(scrollSpeed = it)
        })
        list.addView(switch(getString(R.string.settings_natural_scroll), settings.naturalScroll) {
            settings = settings.copy(naturalScroll = it)
        })
        list.addView(switch(getString(R.string.settings_scroll_inertia), settings.scrollInertia) {
            settings = settings.copy(scrollInertia = it)
        })
        list.addView(slider(
            getString(R.string.settings_zoom_speed), AppSettings.MIN_ZOOM_SPEED, AppSettings.MAX_ZOOM_SPEED, 0.1f, settings.zoomSpeed,
            { "×%.1f".format(it) },
        ) { settings = settings.copy(zoomSpeed = it) })
        list.addView(switch(getString(R.string.settings_tap_to_drag), settings.tapToDrag) {
            settings = settings.copy(tapToDrag = it)
        })

        section(getString(R.string.settings_connection))
        list.addView(choice(
            getString(R.string.settings_connect_by),
            listOf(getString(R.string.settings_connect_usb), getString(R.string.settings_connect_bt)),
            settings.connection.ordinal,
        ) { settings = settings.copy(connection = AppSettings.Connection.entries[it]) })
        list.addView(slider(
            getString(R.string.settings_bt_interval),
            AppSettings.MIN_BLUETOOTH_INTERVAL_MS.toFloat(), AppSettings.MAX_BLUETOOTH_INTERVAL_MS.toFloat(), 1f,
            settings.bluetoothIntervalMs.toFloat(), { "${it.roundToInt()} ms" },
        ) { settings = settings.copy(bluetoothIntervalMs = it.roundToInt()) })
        list.addView(switch(getString(R.string.settings_bt_fine_scroll), settings.btFineScroll) {
            settings = settings.copy(btFineScroll = it)
        })
        list.addView(GlassTheme.note(
            this,
            getString(R.string.settings_bt_fine_scroll_note),
        ))
        list.addView(GlassTheme.button(this, getString(R.string.settings_pair_bt)) {
            settings.save(this@SettingsActivity)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                startActivity(android.content.Intent(this@SettingsActivity, BluetoothActivity::class.java))
            }
        }.apply { isEnabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_bt_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section(getString(R.string.settings_multi_finger))
        list.addView(switch(getString(R.string.settings_windows_gestures), settings.windowsGestures) {
            settings = settings.copy(windowsGestures = it)
        })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_windows_gestures_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })
        section(getString(R.string.settings_gesture_mode))
        list.addView(choice(
            getString(R.string.settings_activate_with),
            listOf(getString(R.string.settings_four_fingers_hold), getString(R.string.settings_off)),
            if (settings.gestureFingers == 0) 1 else 0,
        ) { settings = settings.copy(gestureFingers = listOf(4, 0)[it]) })
        list.addView(slider(
            getString(R.string.settings_stroke_timeout),
            AppSettings.MIN_STROKE_TIMEOUT_S.toFloat(), AppSettings.MAX_STROKE_TIMEOUT_S.toFloat(), 1f,
            settings.gestureStrokeTimeoutSeconds.toFloat(), { "${it.roundToInt()} s" },
        ) { settings = settings.copy(gestureStrokeTimeoutSeconds = it.roundToInt()) })
        list.addView(GlassTheme.button(this, getString(R.string.settings_shapes_button)) {
            startActivity(android.content.Intent(this@SettingsActivity, ShapesActivity::class.java))
        })
        list.addView(GlassTheme.button(this, getString(R.string.settings_bt_actions_button)) {
            settings.save(this@SettingsActivity) // el editor lee la conexión y la distribución del teclado
            startActivity(android.content.Intent(this@SettingsActivity, BtGesturesActivity::class.java))
        })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_gestures_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section(getString(R.string.settings_widgets))
        list.addView(switch(getString(R.string.settings_show_widgets), settings.showWidgets) {
            settings = settings.copy(showWidgets = it)
        })
        list.addView(choice(
            getString(R.string.settings_panel_size),
            listOf(getString(R.string.settings_panel_small), getString(R.string.settings_panel_medium), getString(R.string.settings_panel_large)),
            settings.widgetPanelSize.ordinal,
        ) { settings = settings.copy(widgetPanelSize = WidgetPanelSize.entries[it]) })
        list.addView(GlassTheme.button(this, getString(R.string.settings_bt_buttons_button)) {
            settings.save(this@SettingsActivity)
            startActivity(android.content.Intent(this@SettingsActivity, BtWidgetsActivity::class.java))
        })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_widgets_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section(getString(R.string.settings_keyboard))
        list.addView(choice(
            getString(R.string.settings_layout),
            KeyboardLayoutSetting.entries.map { UiText.layoutSetting(this, it) },
            settings.kbLayout.ordinal,
        ) { settings = settings.copy(kbLayout = KeyboardLayoutSetting.entries[it]) })
        list.addView(choice(
            getString(R.string.settings_key_size),
            KeySize.entries.map { UiText.keySize(this, it) },
            settings.kbKeySize.ordinal,
        ) { settings = settings.copy(kbKeySize = KeySize.entries[it]) })
        list.addView(switch(getString(R.string.settings_vibrate), settings.kbVibrate) { settings = settings.copy(kbVibrate = it) })
        list.addView(switch(getString(R.string.settings_key_repeat), settings.kbRepeat) { settings = settings.copy(kbRepeat = it) })
        list.addView(switch(getString(R.string.settings_trackpad_bar), settings.kbTrackpadBar) { settings = settings.copy(kbTrackpadBar = it) })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_keyboard_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section(getString(R.string.settings_screen))
        list.addView(switch(getString(R.string.settings_show_cheat), settings.showCheatSheet) {
            settings = settings.copy(showCheatSheet = it)
        })
        list.addView(slider(getString(R.string.settings_dim_after), 0f, 300f, 10f, settings.dimAfterSeconds.toFloat(), { if (it == 0f) getString(R.string.settings_never) else "${it.roundToInt()} s" }) {
            settings = settings.copy(dimAfterSeconds = it.roundToInt())
        })
        list.addView(choice(
            getString(R.string.settings_orientation),
            listOf(getString(R.string.settings_orient_free), getString(R.string.settings_orient_portrait), getString(R.string.settings_orient_landscape)),
            settings.orientation.ordinal,
        ) { settings = settings.copy(orientation = AppSettings.Orientation.entries[it]) })

        section(getString(R.string.settings_lock_mode))
        list.addView(switch(getString(R.string.settings_pin_app), settings.pinApp) {
            settings = settings.copy(pinApp = it)
        })
        list.addView(TextView(this).apply {
            text = getString(R.string.settings_pin_note)
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })
        list.addView(switch(getString(R.string.settings_auto_relock), settings.autoRelock) {
            settings = settings.copy(autoRelock = it)
        })

        updatesUi.build()

        section(getString(R.string.settings_debug))
        list.addView(switch(getString(R.string.settings_debug_mode), settings.debug) {
            settings = settings.copy(debug = it)
        })

        list = screen.card(null)
        list.addView(GlassTheme.button(this, getString(R.string.settings_defaults)) {
            settings = AppSettings()
            settings.save(this)
            rebuildWithCrossfade()
        })
        list.addView(TextView(this).apply {
            text = "MyMouse ${Flavor.updates.versionText(this@SettingsActivity)}"
            textSize = 13f
            setTextColor(GlassTheme.TEXT_TERTIARY)
            setPadding(0, (16 * density).toInt(), 0, 0)
        })
        // Android 15 dibuja la app bajo las barras del sistema: GlassTheme.Screen deja su hueco.
        screen.setContentView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SCROLL_Y, screen.scrollView?.scrollY ?: 0)
        outState.putInt(KEY_SWATCHES_X, swatchScroll?.scrollX ?: 0)
    }

    // ---- Tema (docs/spec-v4.8-temas.md §3) ----

    private var swatchScroll: HorizontalScrollView? = null
    private var selectedCell: View? = null

    /** La tarjeta "Tema": muestras con el fondo real, acento y estrellas. Cada cambio se aplica al momento (recreando la pantalla). */
    private fun themeSection() {
        val choice = settings.theme.normalized()
        val theme = choice.theme
        list = screen.card(getString(R.string.theme_title))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in Themes.ALL) {
            val isSelected = t.id == theme.id
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                contentDescription = UiText.theme(this@SettingsActivity, t.id, t.name)
                setOnClickListener { updateTheme(choice.copy(themeId = t.id)) }
            }
            cell.addView(ThemeSwatchView(this, t, isSelected))
            cell.addView(TextView(this).apply {
                text = UiText.theme(this@SettingsActivity, t.id, t.name)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(if (isSelected) GlassTheme.TEXT else GlassTheme.TEXT_SECONDARY)
                setPadding(0, (6 * density).toInt(), 0, 0)
            }, LinearLayout.LayoutParams((ThemeSwatchView.WIDTH_DP * density).toInt() + (8 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            if (isSelected) selectedCell = cell
            row.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * density).toInt()
            })
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
        swatchScroll = scroll
        list.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * density).toInt()
        })

        list.addView(label().apply {
            text = getString(R.string.accent_title)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        })
        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun dot(rgb: Int, auto: Boolean, id: String, name: String) {
            dots.addView(
                AccentDotView(this, rgb, auto, choice.accentId == id).apply {
                    contentDescription = name
                    setOnClickListener { updateTheme(choice.copy(accentId = id)) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * density).toInt()
                },
            )
        }
        dot(theme.accent, auto = true, id = Themes.ACCENT_AUTO, name = getString(R.string.accent_auto))
        for (a in Themes.ACCENTS) dot(a.color, auto = false, id = a.id, name = UiText.accent(this, a.id, a.name))
        list.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(dots)
        })

        if (theme.stars) {
            list.addView(switch(getString(R.string.stars_title), choice.stars) { updateTheme(choice.copy(stars = it)) })
            if (choice.stars) {
                list.addView(switch(getString(R.string.stars_twinkle), choice.twinkle) { updateTheme(choice.copy(twinkle = it)) })
                list.addView(switch(getString(R.string.stars_twinkle_trackpad), choice.twinkleTrackpad) {
                    updateTheme(choice.copy(twinkleTrackpad = it))
                })
            }
        } else {
            list.addView(GlassTheme.note(this, getString(R.string.stars_none)))
        }
        list.addView(GlassTheme.note(this, getString(R.string.theme_sync_note)))
    }

    /** Guarda y aplica la elección al momento: se recrea la pantalla para que todos los controles tomen el acento y el fondo nuevos. */
    private fun updateTheme(choice: ThemeChoice) {
        if (choice.normalized() == settings.theme) return
        val next = choice.normalized()
        // El tema, el acento o las estrellas cambian: se apunta cuándo, para la sincronización con el PC. La animación no viaja.
        val stamp = if (SyncedTheme.of(next, 0).sameLook(SyncedTheme.of(settings.theme, 0))) settings.themeChangedAt else System.currentTimeMillis()
        settings = settings.copy(theme = next, themeChangedAt = stamp)
        GlassTheme.applyTheme(settings.theme)
        settings.save(this)
        rebuildWithCrossfade()
    }

    /**
     * Aplica el tema sin parpadeo. Antes se hacía `recreate()`, que destruye la actividad: durante un instante se veía el
     * fondo de la ventana vacía. Ahora se rehace el contenido en la misma actividad, con la posición de scroll de antes,
     * y una foto de la pantalla anterior se funde encima (220 ms) para que el cambio sea un fundido suave.
     */
    private fun rebuildWithCrossfade() {
        val decor = window.decorView as ViewGroup
        val y = screen.scrollView?.scrollY ?: 0
        val x = swatchScroll?.scrollX
        val snapshot = if (decor.width > 0 && decor.height > 0) {
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
        } else null
        buildUi()
        decor.requestApplyInsets() // el contenido nuevo necesita los márgenes de las barras del sistema
        restoreScroll(y, x)
        if (snapshot == null) return
        val overlay = ImageView(this).apply {
            setImageBitmap(snapshot)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.animate().alpha(0f).setDuration(220).withEndAction {
            decor.removeView(overlay)
            snapshot.recycle()
        }.start()
    }

    /** Tras reconstruir, la pantalla vuelve a donde estaba; la primera vez, la fila de muestras deja a la vista la elegida. */
    private fun restoreScroll(y: Int, x: Int?) {
        screen.scrollView?.post { screen.scrollView?.scrollTo(0, y) }
        swatchScroll?.post {
            val target = x ?: ((selectedCell?.left ?: 0) - (16 * density).toInt()).coerceAtLeast(0)
            swatchScroll?.scrollTo(target, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        updatesUi.onShown()
    }

    override fun onPause() {
        settings.save(this)
        updatesUi.onHidden()
        super.onPause()
    }

    /** Empieza una sección: una tarjeta de cristal nueva con su título. */
    internal fun section(title: String) {
        list = screen.card(title)
    }

    internal fun label() = TextView(this).apply {
        textSize = 16f
        setTextColor(GlassTheme.TEXT)
    }

    private fun slider(
        name: String, min: Float, max: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit,
    ): LinearLayout {
        val text = label()
        val steps = ((max - min) / step).roundToInt()
        fun valueAt(progress: Int) = min + progress * step
        val bar = SeekBar(this).apply {
            GlassTheme.tint(this)
            this.max = steps
            progress = ((value - min) / step).roundToInt().coerceIn(0, steps)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    text.text = "$name: ${format(valueAt(progress))}"
                    if (fromUser) onChange(valueAt(progress))
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        text.text = "$name: ${format(valueAt(bar.progress))}"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            addView(text)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * density).toInt()))
        }
    }

    private fun choice(name: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            addView(label().apply { text = name })
            addView(RadioGroup(this@SettingsActivity).apply {
                options.forEachIndexed { i, option ->
                    addView(RadioButton(this@SettingsActivity).apply {
                        id = View.generateViewId()
                        text = option
                        textSize = 16f
                        setTextColor(GlassTheme.TEXT)
                        GlassTheme.tint(this)
                        isChecked = i == selected
                        setOnCheckedChangeListener { _, checked -> if (checked) onChange(i) }
                    })
                }
            })
        }

    @Suppress("UseSwitchCompatOrMaterialCode")
    internal fun switch(name: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = name
        textSize = 16f
        setTextColor(GlassTheme.TEXT)
        GlassTheme.tint(this)
        isChecked = value
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private companion object {
        const val KEY_SCROLL_Y = "scrollY"
        const val KEY_SWATCHES_X = "swatchesX"
    }
}
