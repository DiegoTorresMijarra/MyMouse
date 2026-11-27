package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.btactions.BtButton
import dev.mymouse.core.btactions.BtWidgetConfig

/**
 * Ajustes → Widgets → "Botones por Bluetooth…" (docs/spec-v5-bluetooth-completo.md §3): los botones propios del panel de widgets
 * cuando se conecta por Bluetooth. Lista ordenable (↑ ↓), añadir, editar y borrar, con el mismo formulario de acción que las formas.
 * Se guarda en `bt-widgets.json` ([BtActionStore]); como mucho 16.
 */
class BtWidgetsActivity : Activity() {

    private lateinit var store: BtActionStore
    private lateinit var config: BtWidgetConfig
    private lateinit var settings: AppSettings
    private lateinit var screen: GlassTheme.Screen

    /** Índice del botón que se edita; [NEW] = uno nuevo; null = la lista. */
    private var editing: Int? = null
    private var transportStarted = false
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BtActionStore(this)
        settings = AppSettings.load(this)
        config = store.widgets()
        val saved = savedInstanceState?.getInt(KEY_EDITING, NONE) ?: NONE
        editing = if (saved == NONE || (saved != NEW && saved !in config.buttons.indices)) null else saved
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editing?.let { outState.putInt(KEY_EDITING, it) }
    }

    override fun onResume() {
        super.onResume()
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

    private fun canTest() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && settings.connection == AppSettings.Connection.BLUETOOTH

    private fun show() {
        val index = editing
        val title = when {
            index == null -> "Botones por Bluetooth"
            index == NEW -> "Botón nuevo"
            else -> config.buttons[index].label
        }
        screen = GlassTheme.Screen(this, title, onBack = { onBackPressed() })
        if (index == null) buildList() else buildDetail(index)
        screen.setContentView()
        window.decorView.requestApplyInsets()
    }

    // ---- Lista ----

    private fun buildList() {
        var card = screen.card(null)
        card.addView(GlassTheme.note(
            this,
            "Botones propios del panel de widgets cuando el móvil está conectado por Bluetooth. El panel lleva siempre multimedia " +
                "(⏮ ⏯ ⏭) y volumen (Vol −, Vol +, Silencio); estos botones van detrás, en la rejilla de siempre. " +
                "Cada uno pulsa un atajo, una tecla multimedia o escribe un texto en el PC (máximo ${BtWidgetConfig.MAX_BUTTONS}).",
        ))

        card = screen.card("Botones (${config.buttons.size} de ${BtWidgetConfig.MAX_BUTTONS})")
        if (config.buttons.isEmpty()) card.addView(GlassTheme.note(this, "No hay botones propios."))
        config.buttons.forEachIndexed { i, b -> card.addView(row(i, b)) }
        card.addView(GlassTheme.button(this, "Añadir un botón", accent = true) {
            editing = NEW
            show()
        }.apply { isEnabled = !config.isFull })
        if (config.isFull) card.addView(GlassTheme.note(this, "Ya hay ${BtWidgetConfig.MAX_BUTTONS} botones: borra alguno para añadir otro."))

        card = screen.card(null)
        card.addView(GlassTheme.button(this, "Restaurar los valores por defecto") { confirmReset() })
    }

    private fun row(index: Int, b: BtButton): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        val size = (40 * density).toInt()
        addView(TextView(this@BtWidgetsActivity).apply {
            text = b.icon
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(GlassTheme.TEXT)
            background = GlassTheme.rounded(this@BtWidgetsActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW)
        }, LinearLayout.LayoutParams(size, size))
        addView(LinearLayout(this@BtWidgetsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
            isClickable = true
            setOnClickListener {
                editing = index
                show()
            }
            addView(GlassTheme.text(this@BtWidgetsActivity, b.label, 14f, bold = true))
            addView(TextView(this@BtWidgetsActivity).apply {
                text = BtActions.describe(b.action)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(GlassTheme.TEXT_SECONDARY)
                background = GlassTheme.rounded(this@BtWidgetsActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_CHIP)
                setPadding((6 * density).toInt(), (1 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(smallButton("↑", enabled = index > 0, description = "Subir") { change(config.move(index, -1)) })
        addView(smallButton("↓", enabled = index < config.buttons.size - 1, description = "Bajar") { change(config.move(index, 1)) })
        addView(smallButton("✕", enabled = true, description = "Borrar") { confirmDelete(index, b) })
    }

    private fun smallButton(label: String, enabled: Boolean, description: String, onClick: () -> Unit) =
        GlassTheme.button(this, label, onClick = onClick).apply {
            minimumWidth = (44 * density).toInt()
            minWidth = (44 * density).toInt()
            contentDescription = description
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; marginEnd = (4 * density).toInt() }
        }

    private fun change(newConfig: BtWidgetConfig) {
        config = newConfig
        store.saveWidgets(config)
        show()
    }

    private fun confirmDelete(index: Int, b: BtButton) {
        AlertDialog.Builder(this)
            .setTitle("Borrar \"${b.label}\"")
            .setPositiveButton("Borrar") { _, _ -> change(config.remove(index)) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Restaurar los valores por defecto")
            .setMessage("Vuelven los cinco botones de fábrica: Copiar, Pegar, Captura, Deshacer y Bloquear. Se pierden los tuyos.")
            .setPositiveButton("Restaurar") { _, _ ->
                store.resetWidgets()
                config = store.widgets()
                show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---- Detalle ----

    private fun buildDetail(index: Int) {
        val existing = config.buttons.getOrNull(index)
        val layout = settings.keyboardLayout

        var card = screen.card("Botón")
        val label = BtActionForm.field(this, "Etiqueta (p. ej. Copiar)").apply {
            setSingleLine()
            setText(existing?.label.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(BtWidgetConfig.MAX_LABEL))
        }
        val icon = BtActionForm.field(this, "Icono: un emoji o 1–3 letras").apply {
            setSingleLine()
            setText(existing?.icon.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        }
        card.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(icon, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * density).toInt()
        })

        card = screen.card("Acción")
        val form = BtActionForm(this, existing?.action, layout, allowNone = false)
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

        card = screen.card(null)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(GlassTheme.button(this@BtWidgetsActivity, "Guardar", accent = true) {
                val name = label.text.toString().trim()
                val symbol = icon.text.toString().trim()
                val problem = when {
                    !BtWidgetConfig.validLabel(name) -> "Escribe una etiqueta (hasta ${BtWidgetConfig.MAX_LABEL} caracteres)"
                    !BtWidgetConfig.validIcon(symbol) -> "El icono es un emoji o de 1 a 3 letras"
                    else -> form.problem()
                }
                val action = form.action()
                if (problem != null || action == null) {
                    toast(problem ?: "Falta la acción")
                    return@button
                }
                val button = BtButton(name, symbol, action)
                saveAndClose(if (index == NEW) config.add(button) else config.replace(index, button))
            })
            addView(GlassTheme.button(this@BtWidgetsActivity, "Cancelar") { onBackPressed() })
        })
    }

    /** Guarda y vuelve a la lista. */
    private fun saveAndClose(newConfig: BtWidgetConfig) {
        editing = null
        change(newConfig)
    }

    private fun testAction(action: BtAction) {
        if (action == BtAction.Keyboard) {
            toast("Abre el teclado del móvil: pruébalo desde el trackpad")
            return
        }
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
        const val NEW = -1
        const val NONE = -2
    }
}
