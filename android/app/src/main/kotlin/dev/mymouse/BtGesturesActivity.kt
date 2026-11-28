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
        val title = if (id == null) getString(R.string.btg_title) else ShapeStore.label(this, id)
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
            getString(R.string.btg_intro),
        ))

        card = screen.card(getString(R.string.btg_layout_card))
        card.addView(GlassTheme.note(this, getString(R.string.btg_layout_note)))
        card.addView(RadioGroup(this).apply {
            KeyboardLayouts.ALL.forEach { layout ->
                addView(RadioButton(this@BtGesturesActivity).apply {
                    id = View.generateViewId()
                    text = UiText.layout(this@BtGesturesActivity, layout.id)
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

        card = screen.card(getString(R.string.btg_shapes_card))
        val enabled = shapes.enabled
        val customs = shapes.recorded.map { it.name }.filter { it.startsWith(ShapeStore.CUSTOM_PREFIX) }
        for (id in ShapeCatalog.IDS + customs) card.addView(row(id, id in enabled))
        card.addView(GlassTheme.note(this, getString(R.string.btg_disabled_note)))

        card = screen.card(null)
        card.addView(GlassTheme.button(this, getString(R.string.bt_restore_defaults)) { confirmReset() })
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
                addView(GlassTheme.text(this@BtGesturesActivity, binding?.helpText { UiText.action(this@BtGesturesActivity, it) } ?: ShapeStore.label(this@BtGesturesActivity, id), 14f, bold = true))
                addView(TextView(this@BtGesturesActivity).apply {
                    text = binding?.let { UiText.action(this@BtGesturesActivity, it.action) } ?: getString(R.string.btg_no_action)
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
            .setTitle(R.string.bt_restore_defaults)
            .setMessage(R.string.btg_restore_message)
            .setPositiveButton(R.string.bt_restore) { _, _ ->
                store.resetGestures()
                reload()
                show()
            }
            .setNegativeButton(R.string.cancel, null)
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
                getString(R.string.btg_detail_note),
            ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (12 * density).toInt() })
        })

        card = screen.card(getString(R.string.bt_action_card))
        val form = BtActionForm(this, binding?.action, layout, allowNone = true)
        card.addView(form.view)
        val test = GlassTheme.button(this, getString(R.string.bt_try)) {
            val problem = form.problem()
            val action = form.action()
            when {
                problem != null -> toast(problem)
                action == null -> toast(getString(R.string.bt_no_action_to_try))
                else -> testAction(action)
            }
        }
        card.addView(test)
        card.addView(GlassTheme.note(
            this,
            getString(if (canTest()) R.string.bt_try_note_ok else R.string.bt_try_note_unavailable),
        ))
        test.isEnabled = canTest()

        card = screen.card(getString(R.string.btg_cheat_card))
        val help = BtActionForm.field(this, getString(R.string.btg_help_hint)).apply {
            setSingleLine()
            setText(binding?.help.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(BtGestureConfig.MAX_HELP))
        }
        card.addView(help, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val pin = Switch(this).apply {
            text = getString(R.string.btg_pin)
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
            addView(GlassTheme.button(this@BtGesturesActivity, getString(R.string.save), accent = true) {
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
            addView(GlassTheme.button(this@BtGesturesActivity, getString(R.string.cancel)) { onBackPressed() })
            if (binding != null && form.isNone.not()) {
                addView(GlassTheme.button(this@BtGesturesActivity, getString(R.string.btg_remove)) {
                    config = config.without(id)
                    store.saveGestures(config)
                    editing = null
                    show()
                })
            }
        })
    }

    private fun testAction(action: BtAction) {
        if (action == BtAction.Keyboard) {
            toast(getString(R.string.bt_test_keyboard))
            return
        }
        if (!canTest()) {
            toast(getString(R.string.bt_test_need_bt))
            return
        }
        val ok = BluetoothHidTransport.shared(this).runAction(action)
        toast(if (ok) getString(R.string.bt_test_sent, UiText.action(this, action)) else getString(R.string.bt_test_not_connected))
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val KEY_EDITING = "editing"
    }
}
