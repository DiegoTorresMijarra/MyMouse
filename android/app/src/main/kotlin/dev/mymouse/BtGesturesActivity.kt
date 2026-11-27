package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.btactions.BtBinding
import dev.mymouse.core.btactions.BtGestureConfig
import dev.mymouse.core.btactions.KeyboardLayouts
import dev.mymouse.core.shapes.ShapeCatalog

/**
 * Ajustes → Modo gestos → "Acciones por Bluetooth…" (docs/spec-v5-bluetooth-completo.md §2.4): qué hace cada forma cuando se
 * conecta por Bluetooth. Lista de formas (miniatura, ayuda y chip de acción, como en la toolbox del PC) y pantalla de detalle con el
 * formulario de la acción, la ayuda, "Fijar en la chuleta" y "Probar". Se guarda en `bt-gestures.json` ([BtActionStore]).
 */
class BtGesturesActivity : Activity() {

    private lateinit var store: BtActionStore
    private lateinit var shapes: ShapeStore
    private lateinit var config: BtGestureConfig
    private lateinit var settings: AppSettings
    private lateinit var screen: GlassTheme.Screen

    /** La forma que se está editando (pantalla de detalle) o null (lista). */
    private var editing: String? = null
    private var transportStarted = false
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BtActionStore(this)
        editing = savedInstanceState?.getString(KEY_EDITING)
        reload()
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editing?.let { outState.putString(KEY_EDITING, it) }
    }

    private fun reload() {
        settings = AppSettings.load(this)
        shapes = ShapeStore(this)
        config = store.gestures()
    }

    override fun onResume() {
        super.onResume()
        // Para "Probar": con la conexión por Bluetooth, el registro HID se mantiene mientras esta pantalla esté abierta.
        if (canTest() && BluetoothActivity.hasPermissions(this) && !transportStarted) {
            BluetoothHidTransport.shared(this).start()
            transportStarted = true
        }
    }

    override fun onPause() {
        if (transportStarted) {
            BluetoothHidTransport.shared(this).stop()
            transportStarted = false
        }
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editing != null) {
            editing = null
            show()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    /** ¿Se puede probar? Solo con la conexión por Bluetooth elegida (y Android 9 o posterior). */
    private fun canTest() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && settings.connection == AppSettings.Connection.BLUETOOTH

    private fun show() {
        val id = editing
        val title = if (id == null) "Acciones por Bluetooth" else ShapeStore.label(id)
        screen = GlassTheme.Screen(this, title, onBack = { onBackPressed() })
        if (id == null) buildList() else buildDetail(id)
        screen.setContentView()
        window.decorView.requestApplyInsets()
    }

    // ---- Lista ----

    private fun buildList() {
        var card = screen.card(null)
        card.addView(GlassTheme.note(
            this,
            "Qué hace cada forma del modo gestos cuando el móvil está conectado por Bluetooth, sin MyMouse en el PC. " +
                "Por Bluetooth solo se pueden pulsar atajos, teclas multimedia y escribir texto; abrir programas, pausar MyMouse o " +
                "las reglas por aplicación necesitan el programa del PC (USB). Para bloquear el PC usa el atajo Win+L.",
        ))

        card = screen.card("Distribución del teclado del PC")
        card.addView(GlassTheme.note(this, "Solo cambia cómo se escribe el texto (los atajos no dependen de ella): Windows convierte las teclas en letras con su propia distribución."))
        card.addView(RadioGroup(this).apply {
            KeyboardLayouts.ALL.forEach { layout ->
                addView(RadioButton(this@BtGesturesActivity).apply {
                    id = View.generateViewId()
                    text = layout.label
                    textSize = 16f
                    setTextColor(GlassTheme.TEXT)
                    GlassTheme.tint(this)
                    isChecked = layout.id == settings.keyboardLayout.id
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            settings = settings.copy(btKeyboardLayout = layout.id)
                            settings.save(this@BtGesturesActivity)
                        }
                    }
                })
            }
        })

        card = screen.card("Formas")
        val enabled = shapes.enabled
        val customs = shapes.recorded.map { it.name }.filter { it.startsWith(ShapeStore.CUSTOM_PREFIX) }
        for (id in ShapeCatalog.IDS + customs) card.addView(row(id, id in enabled))
        card.addView(GlassTheme.note(this, "Las formas desactivadas no se reconocen: se activan en Ajustes → Formas."))

        card = screen.card(null)
        card.addView(GlassTheme.button(this, "Restaurar los valores por defecto") { confirmReset() })
    }

    private fun row(id: String, active: Boolean): LinearLayout {
        val binding = config.find(id)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            alpha = if (active) 1f else 0.55f
            isClickable = true
            background = GlassTheme.ripple(this@BtGesturesActivity, GlassTheme.rounded(this@BtGesturesActivity, 0, GlassTheme.RADIUS_ROW), GlassTheme.RADIUS_ROW)
            setOnClickListener {
                editing = id
                show()
            }
            val size = (40 * density).toInt()
            addView(ShapePreviewView(this@BtGesturesActivity, shapes.preview(id)).apply {
                background = GlassTheme.rounded(this@BtGesturesActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW)
            }, LinearLayout.LayoutParams(size, size))
            addView(LinearLayout(this@BtGesturesActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * density).toInt(), 0, 0, 0)
                addView(GlassTheme.text(this@BtGesturesActivity, binding?.helpText ?: ShapeStore.label(id), 14f, bold = true))
                addView(TextView(this@BtGesturesActivity).apply {
                    text = binding?.let { BtActions.describe(it.action) } ?: "Sin acción"
                    textSize = 11f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(GlassTheme.TEXT_SECONDARY)
                    background = GlassTheme.rounded(this@BtGesturesActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_CHIP)
                    setPadding((6 * density).toInt(), (1 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * density).toInt()
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!active) addView(GlassTheme.text(this@BtGesturesActivity, "desactivada", 11f, GlassTheme.TEXT_TERTIARY))
            if (binding?.pinned == true) addView(GlassTheme.text(this@BtGesturesActivity, "  📌", 13f))
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Restaurar los valores por defecto")
            .setMessage("Las acciones de todas las formas vuelven a las de fábrica (Z deshace, líneas ← → ↑ ↓, V reproduce…).")
            .setPositiveButton("Restaurar") { _, _ ->
                store.resetGestures()
                reload()
                show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---- Detalle ----

    private fun buildDetail(id: String) {
        val binding = config.find(id)
        val layout = settings.keyboardLayout

        var card = screen.card(null)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val size = (56 * density).toInt()
            addView(ShapePreviewView(this@BtGesturesActivity, shapes.preview(id)).apply {
                background = GlassTheme.rounded(this@BtGesturesActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW)
            }, LinearLayout.LayoutParams(size, size))
            addView(GlassTheme.note(
                this@BtGesturesActivity,
                "Lo que se hará en el PC al dibujar esta forma. El punto de color del dibujo marca dónde empieza el trazo.",
            ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (12 * density).toInt() })
        })

        card = screen.card("Acción")
        val form = BtActionForm(this, binding?.action, layout, allowNone = true)
        card.addView(form.view)
        val test = GlassTheme.button(this, "Probar") {
            val problem = form.problem()
            val action = form.action()
            when {
                problem != null -> toast(problem)
                action == null -> toast("No hay acción que probar")
                else -> testAction(action)
            }
        }
        card.addView(test)
        card.addView(GlassTheme.note(
            this,
            if (canTest()) "Probar ejecuta la acción en el PC al momento (el móvil debe estar conectado por Bluetooth)."
            else "Probar solo está disponible con la conexión por Bluetooth elegida en Ajustes.",
        ))
        test.isEnabled = canTest()

        card = screen.card("En la chuleta")
        val help = BtActionForm.field(this, "Texto de ayuda (si lo dejas vacío, se usa la acción)").apply {
            setSingleLine()
            setText(binding?.help.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(BtGestureConfig.MAX_HELP))
        }
        card.addView(help, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val pin = Switch(this).apply {
            text = "Fijar en la chuleta (sale en \"Tus formas\")"
            textSize = 16f
            setTextColor(GlassTheme.TEXT)
            GlassTheme.tint(this)
            isChecked = binding?.pinned == true
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        }
        card.addView(pin)

        card = screen.card(null)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(GlassTheme.button(this@BtGesturesActivity, "Guardar", accent = true) {
                val problem = form.problem()
                if (problem != null) {
                    toast(problem)
                    return@button
                }
                val action = form.action()
                config = if (action == null) config.without(id) else config.with(BtBinding(id, action, help.text.toString().trim(), pin.isChecked))
                store.saveGestures(config)
                editing = null
                show()
            })
            addView(GlassTheme.button(this@BtGesturesActivity, "Cancelar") { onBackPressed() })
            if (binding != null && form.isNone.not()) {
                addView(GlassTheme.button(this@BtGesturesActivity, "Quitar") {
                    config = config.without(id)
                    store.saveGestures(config)
                    editing = null
                    show()
                })
            }
        })
    }

    private fun testAction(action: BtAction) {
        if (!canTest()) {
            toast("Elige la conexión por Bluetooth en Ajustes")
            return
        }
        val ok = BluetoothHidTransport.shared(this).runAction(action)
        toast(if (ok) "Enviado al PC: ${BtActions.describe(action)}" else "El móvil no está conectado al PC por Bluetooth")
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val KEY_EDITING = "editing"
    }
}
