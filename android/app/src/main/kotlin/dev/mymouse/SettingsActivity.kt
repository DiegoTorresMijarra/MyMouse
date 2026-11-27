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
import dev.mymouse.core.theme.ThemeChoice
import dev.mymouse.core.theme.Themes
import dev.mymouse.core.widgets.WidgetPanelSize
import kotlin.math.roundToInt

/** Pantalla de ajustes, sin dependencias extra: barras deslizantes e interruptores. Guarda al salir. */
class SettingsActivity : Activity() {

    private lateinit var settings: AppSettings
    private lateinit var screen: GlassTheme.Screen

    /** La tarjeta de cristal de la sección actual: lo que se añade a `list` va en ella. */
    private lateinit var list: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings.load(this)
        title = "Ajustes de MyMouse"
        buildUi()
        restoreScroll(savedInstanceState?.getInt(KEY_SCROLL_Y) ?: 0, savedInstanceState?.getInt(KEY_SWATCHES_X))
    }

    /** Construye (o reconstruye, al cambiar el tema) toda la pantalla con los ajustes y el tema actuales. */
    private fun buildUi() {
        swatchScroll = null
        selectedCell = null
        screen = GlassTheme.Screen(this, "Ajustes")
        themeSection()
        section("Trackpad")
        list.addView(slider("Sensibilidad", 0.3f, 3f, 0.1f, settings.sensitivity, { "×%.1f".format(it) }) {
            settings = settings.copy(sensitivity = it)
        })
        list.addView(slider("Aceleración", 0f, 2f, 0.1f, settings.acceleration, { if (it == 0f) "sin aceleración" else "%.1f".format(it) }) {
            settings = settings.copy(acceleration = it)
        })
        list.addView(slider("Velocidad de scroll", 0.5f, 6f, 0.5f, settings.scrollSpeed, { "×%.1f".format(it) }) {
            settings = settings.copy(scrollSpeed = it)
        })
        list.addView(switch("Scroll natural (el contenido sigue al dedo)", settings.naturalScroll) {
            settings = settings.copy(naturalScroll = it)
        })
        list.addView(switch("Inercia al hacer scroll (sigue un momento al levantar los dedos)", settings.scrollInertia) {
            settings = settings.copy(scrollInertia = it)
        })
        list.addView(slider(
            "Velocidad del zoom", AppSettings.MIN_ZOOM_SPEED, AppSettings.MAX_ZOOM_SPEED, 0.1f, settings.zoomSpeed,
            { "×%.1f".format(it) },
        ) { settings = settings.copy(zoomSpeed = it) })
        list.addView(switch("Doble toque y mover = arrastrar (retrasa el clic ~¼ s)", settings.tapToDrag) {
            settings = settings.copy(tapToDrag = it)
        })

        section("Conexión")
        list.addView(choice(
            "Conectar por",
            listOf("USB (con MyMouse en el PC)", "Bluetooth (sin nada en el PC)"),
            settings.connection.ordinal,
        ) { settings = settings.copy(connection = AppSettings.Connection.entries[it]) })
        list.addView(slider(
            "Intervalo Bluetooth (avanzado)",
            AppSettings.MIN_BLUETOOTH_INTERVAL_MS.toFloat(), AppSettings.MAX_BLUETOOTH_INTERVAL_MS.toFloat(), 1f,
            settings.bluetoothIntervalMs.toFloat(), { "${it.roundToInt()} ms" },
        ) { settings = settings.copy(bluetoothIntervalMs = it.roundToInt()) })
        list.addView(switch("Scroll fino por Bluetooth (avanzado)", settings.btFineScroll) {
            settings = settings.copy(btFineScroll = it)
        })
        list.addView(GlassTheme.note(
            this,
            "Scroll y zoom suaves, como un ratón de precisión. Si Windows no los acepta, se usan pasos enteros. " +
                "Al cambiarlo hay que quitar el móvil en Windows y volver a emparejarlo: Windows guarda en caché la descripción del ratón. " +
                "Desactívalo solo si algún PC se porta mal.",
        ))
        list.addView(GlassTheme.button(this, "Emparejar por Bluetooth…") {
            settings.save(this@SettingsActivity)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                startActivity(android.content.Intent(this@SettingsActivity, BluetoothActivity::class.java))
            }
        }.apply { isEnabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P })
        list.addView(TextView(this).apply {
            text = "Por Bluetooth funcionan el trackpad, el zoom, el modo gestos y el panel de widgets con multimedia, volumen y tus botones: " +
                "sus acciones se configuran en este móvil (Modo gestos → Acciones por Bluetooth; Widgets → Botones por Bluetooth). " +
                "Abrir programas, la toolbox y los widgets con volumen deslizante necesitan MyMouse en el PC (USB). " +
                "El intervalo es cada cuánto se envía el movimiento: menos ms = puntero más fluido y más tráfico " +
                "(8 ms va bien; sube a 12–16 si se nota retraso acumulado). En el trackpad se ve el intervalo real entre informes, no un ping."
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section("Gestos de varios dedos")
        list.addView(switch("Gestos de Windows con 3 dedos (↑ vista de tareas, ↓ escritorio, ← → cambiar de app)", settings.windowsGestures) {
            settings = settings.copy(windowsGestures = it)
        })
        list.addView(TextView(this).apply {
            text = "Pueden interferir con los gestos de 3 dedos de Xiaomi/HyperOS (captura y recorte). " +
                "Si es así, desactívalos aquí o en los ajustes del sistema (Ajustes adicionales → Atajos de gestos). " +
                "Por USB, el PC necesita MyMouse 3.1 o posterior."
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })
        section("Modo gestos")
        list.addView(choice(
            "Activar con",
            listOf("4 dedos quietos", "Desactivado"),
            if (settings.gestureFingers == 0) 1 else 0,
        ) { settings = settings.copy(gestureFingers = listOf(4, 0)[it]) })
        list.addView(slider(
            "Tiempo para empezar a dibujar",
            AppSettings.MIN_STROKE_TIMEOUT_S.toFloat(), AppSettings.MAX_STROKE_TIMEOUT_S.toFloat(), 1f,
            settings.gestureStrokeTimeoutSeconds.toFloat(), { "${it.roundToInt()} s" },
        ) { settings = settings.copy(gestureStrokeTimeoutSeconds = it.roundToInt()) })
        list.addView(GlassTheme.button(this, "Formas…") {
            startActivity(android.content.Intent(this@SettingsActivity, ShapesActivity::class.java))
        })
        list.addView(GlassTheme.button(this, "Acciones por Bluetooth…") {
            settings.save(this@SettingsActivity) // el editor lee la conexión y la distribución del teclado
            startActivity(android.content.Intent(this@SettingsActivity, BtGesturesActivity::class.java))
        })
        list.addView(TextView(this).apply {
            text = "Por USB, lo que hace cada forma se configura en la toolbox del PC (clic en el icono de la bandeja o 4 dedos ↑) → \"Gestos y widgets\". " +
                "Por Bluetooth, en \"Acciones por Bluetooth…\" (atajos, multimedia y texto)."
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section("Widgets")
        list.addView(switch("Mostrar el panel de widgets (juntar o separar 4 dedos lo cambia)", settings.showWidgets) {
            settings = settings.copy(showWidgets = it)
        })
        list.addView(choice(
            "Tamaño del panel",
            listOf("Pequeño (22 %)", "Mediano (30 %)", "Grande (40 %)"),
            settings.widgetPanelSize.ordinal,
        ) { settings = settings.copy(widgetPanelSize = WidgetPanelSize.entries[it]) })
        list.addView(GlassTheme.button(this, "Botones por Bluetooth…") {
            settings.save(this@SettingsActivity)
            startActivity(android.content.Intent(this@SettingsActivity, BtWidgetsActivity::class.java))
        })
        list.addView(TextView(this).apply {
            text = "Por USB, el panel solo aparece si el PC (MyMouse 2 o posterior) tiene widgets configurados: " +
                "toolbox del PC → \"Gestos y widgets\". Por Bluetooth, el panel lleva multimedia, volumen y los botones de \"Botones por Bluetooth…\"."
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })

        section("Pantalla")
        list.addView(switch("Mostrar la chuleta de gestos", settings.showCheatSheet) {
            settings = settings.copy(showCheatSheet = it)
        })
        list.addView(slider("Atenuar tras", 0f, 300f, 10f, settings.dimAfterSeconds.toFloat(), { if (it == 0f) "nunca" else "${it.roundToInt()} s" }) {
            settings = settings.copy(dimAfterSeconds = it.roundToInt())
        })
        list.addView(choice(
            "Orientación",
            listOf("Libre (gira con el móvil)", "Bloqueada en vertical", "Bloqueada en horizontal"),
            settings.orientation.ordinal,
        ) { settings = settings.copy(orientation = AppSettings.Orientation.entries[it]) })

        section("Modo bloqueado")
        list.addView(switch("Fijar la app con Android (Inicio y Recientes no salen de la app)", settings.pinApp) {
            settings = settings.copy(pinApp = it)
        })
        list.addView(TextView(this).apply {
            text = "Si al salir del bloqueo (Vol− ×2) el móvil pide el PIN o muestra la pantalla de bloqueo, " +
                "desactiva en los ajustes del sistema la opción de pedir desbloqueo al dejar de fijar una app " +
                "(suele estar en Seguridad → Fijar aplicaciones), o desactiva esta opción. " +
                "Sin fijar, el modo bloqueado solo oculta las barras y anula el botón Atrás."
            textSize = 13f
            setTextColor(GlassTheme.TEXT_SECONDARY)
        })
        list.addView(switch("Volver solo al modo bloqueado tras 30 s sin tocar", settings.autoRelock) {
            settings = settings.copy(autoRelock = it)
        })

        section("Depuración")
        list.addView(switch("Modo depuración: ver los dedos y las acciones en pantalla", settings.debug) {
            settings = settings.copy(debug = it)
        })

        list = screen.card(null)
        list.addView(GlassTheme.button(this, "Valores por defecto") {
            settings = AppSettings()
            settings.save(this)
            rebuildWithCrossfade()
        })
        list.addView(TextView(this).apply {
            text = "MyMouse ${packageManager.getPackageInfo(packageName, 0).versionName}"
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
        list = screen.card("Tema")

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in Themes.ALL) {
            val isSelected = t.id == theme.id
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                contentDescription = t.name
                setOnClickListener { updateTheme(choice.copy(themeId = t.id)) }
            }
            cell.addView(ThemeSwatchView(this, t, isSelected))
            cell.addView(TextView(this).apply {
                text = t.name
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
            text = "Acento"
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
        dot(theme.accent, auto = true, id = Themes.ACCENT_AUTO, name = "Automático (el del tema)")
        for (a in Themes.ACCENTS) dot(a.color, auto = false, id = a.id, name = a.name)
        list.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(dots)
        })

        if (theme.stars) {
            list.addView(switch("Estrellas", choice.stars) { updateTheme(choice.copy(stars = it)) })
            if (choice.stars) {
                list.addView(switch("Animación de estrellas (parpadeo)", choice.twinkle) { updateTheme(choice.copy(twinkle = it)) })
                list.addView(switch("Animación también en el trackpad (gasta algo más de batería)", choice.twinkleTrackpad) {
                    updateTheme(choice.copy(twinkleTrackpad = it))
                })
            }
        } else {
            list.addView(GlassTheme.note(this, "Este tema no lleva estrellas."))
        }
        list.addView(GlassTheme.note(this, "Solo afecta a este móvil: el PC tiene su propio tema en los ajustes de la toolbox."))
    }

    /** Guarda y aplica la elección al momento: se recrea la pantalla para que todos los controles tomen el acento y el fondo nuevos. */
    private fun updateTheme(choice: ThemeChoice) {
        if (choice.normalized() == settings.theme) return
        settings = settings.copy(theme = choice.normalized())
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

    override fun onPause() {
        settings.save(this)
        super.onPause()
    }

    /** Empieza una sección: una tarjeta de cristal nueva con su título. */
    private fun section(title: String) {
        list = screen.card(title)
    }

    private fun label() = TextView(this).apply {
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
    private fun switch(name: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
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
