package dev.mymouse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.mymouse.core.widgets.WidgetPanelSize
import kotlin.math.roundToInt

/** Pantalla de ajustes, sin dependencias extra: barras deslizantes e interruptores. Guarda al salir. */
class SettingsActivity : Activity() {

    private lateinit var settings: AppSettings
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings.load(this)
        title = "Ajustes de MyMouse"

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * density).toInt()
            setPadding(p, p, p, p)
        }

        list.addView(TextView(this).apply {
            text = "Ajustes"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        list.addView(header("Trackpad"))
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

        list.addView(header("Conexión"))
        list.addView(choice(
            "Conectar por",
            listOf("USB (con MyMouse en el PC)", "Bluetooth (sin nada en el PC)"),
            settings.connection.ordinal,
        ) { settings = settings.copy(connection = AppSettings.Connection.entries[it]) })
        list.addView(Button(this).apply {
            text = "Emparejar por Bluetooth…"
            isAllCaps = false
            isEnabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
            setOnClickListener {
                settings.save(this@SettingsActivity)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    startActivity(android.content.Intent(this@SettingsActivity, BluetoothActivity::class.java))
                }
            }
        })
        list.addView(TextView(this).apply {
            text = "Por Bluetooth funcionan el trackpad, el zoom y un panel fijo de multimedia y volumen. " +
                "El modo gestos y los widgets configurables necesitan MyMouse en el PC (USB)."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
        })

        list.addView(header("Gestos de varios dedos"))
        list.addView(switch("Gestos de Windows con 3 dedos (↑ vista de tareas, ↓ escritorio, ← → cambiar de app)", settings.windowsGestures) {
            settings = settings.copy(windowsGestures = it)
        })
        list.addView(TextView(this).apply {
            text = "Pueden interferir con los gestos de 3 dedos de Xiaomi/HyperOS (captura y recorte). " +
                "Si es así, desactívalos aquí o en los ajustes del sistema (Ajustes adicionales → Atajos de gestos). " +
                "Por USB, el PC necesita MyMouse 3.1 o posterior."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
        })
        list.addView(header("Modo gestos"))
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
        list.addView(Button(this).apply {
            text = "Formas…"
            isAllCaps = false
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, ShapesActivity::class.java)) }
        })
        list.addView(TextView(this).apply {
            text = "Lo que hace cada forma se configura en la toolbox del PC (clic en el icono de la bandeja o 4 dedos ↑) → \"Gestos y widgets\"."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
        })

        list.addView(header("Widgets"))
        list.addView(switch("Mostrar el panel de widgets (juntar o separar 4 dedos lo cambia)", settings.showWidgets) {
            settings = settings.copy(showWidgets = it)
        })
        list.addView(choice(
            "Tamaño del panel",
            listOf("Pequeño (22 %)", "Mediano (30 %)", "Grande (40 %)"),
            settings.widgetPanelSize.ordinal,
        ) { settings = settings.copy(widgetPanelSize = WidgetPanelSize.entries[it]) })
        list.addView(TextView(this).apply {
            text = "El panel solo aparece si el PC (MyMouse 2 o posterior) tiene widgets configurados: " +
                "toolbox del PC → \"Gestos y widgets\"."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
        })

        list.addView(header("Pantalla"))
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

        list.addView(header("Modo bloqueado"))
        list.addView(switch("Fijar la app con Android (Inicio y Recientes no salen de la app)", settings.pinApp) {
            settings = settings.copy(pinApp = it)
        })
        list.addView(TextView(this).apply {
            text = "Si al salir del bloqueo (Vol− ×2) el móvil pide el PIN o muestra la pantalla de bloqueo, " +
                "desactiva en los ajustes del sistema la opción de pedir desbloqueo al dejar de fijar una app " +
                "(suele estar en Seguridad → Fijar aplicaciones), o desactiva esta opción. " +
                "Sin fijar, el modo bloqueado solo oculta las barras y anula el botón Atrás."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
        })
        list.addView(switch("Volver solo al modo bloqueado tras 30 s sin tocar", settings.autoRelock) {
            settings = settings.copy(autoRelock = it)
        })

        list.addView(header("Depuración"))
        list.addView(switch("Modo depuración: ver los dedos y las acciones en pantalla", settings.debug) {
            settings = settings.copy(debug = it)
        })

        list.addView(Button(this).apply {
            text = "Valores por defecto"
            isAllCaps = false
            setOnClickListener {
                settings = AppSettings() // onPause lo guarda
                recreate()
            }
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(list)
        }
        // Android 15 dibuja la app bajo las barras del sistema: dejar su hueco a mano.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        list.addView(TextView(this).apply {
            text = "MyMouse ${packageManager.getPackageInfo(packageName, 0).versionName}"
            textSize = 13f
            setTextColor(Color.rgb(120, 120, 120))
            setPadding(0, (16 * density).toInt(), 0, 0)
        })
        setContentView(scroll)
    }

    override fun onPause() {
        settings.save(this)
        super.onPause()
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(100, 180, 255))
        setPadding(0, (16 * density).toInt(), 0, (4 * density).toInt())
    }

    private fun label() = TextView(this).apply {
        textSize = 16f
        setTextColor(Color.rgb(230, 230, 230))
    }

    private fun slider(
        name: String, min: Float, max: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit,
    ): LinearLayout {
        val text = label()
        val steps = ((max - min) / step).roundToInt()
        fun valueAt(progress: Int) = min + progress * step
        val bar = SeekBar(this).apply {
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
                        setTextColor(Color.rgb(230, 230, 230))
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
        setTextColor(Color.rgb(230, 230, 230))
        isChecked = value
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }
}
