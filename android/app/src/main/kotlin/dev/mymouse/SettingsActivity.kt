package dev.mymouse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
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
        list.addView(switch("Doble toque y mover = arrastrar (retrasa el clic ~¼ s)", settings.tapToDrag) {
            settings = settings.copy(tapToDrag = it)
        })

        list.addView(header("Pantalla"))
        list.addView(switch("Mostrar la chuleta de gestos", settings.showCheatSheet) {
            settings = settings.copy(showCheatSheet = it)
        })
        list.addView(slider("Atenuar tras", 0f, 300f, 10f, settings.dimAfterSeconds.toFloat(), { if (it == 0f) "nunca" else "${it.roundToInt()} s" }) {
            settings = settings.copy(dimAfterSeconds = it.roundToInt())
        })
        list.addView(switch("Horizontal (apaisado)", settings.landscape) {
            settings = settings.copy(landscape = it)
        })

        list.addView(Button(this).apply {
            text = "Valores por defecto"
            isAllCaps = false
            setOnClickListener {
                settings = AppSettings() // onPause lo guarda
                recreate()
            }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(list)
        })
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
