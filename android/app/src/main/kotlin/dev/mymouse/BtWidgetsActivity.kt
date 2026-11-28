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
            index == null -> getString(R.string.btw_title)
            index == NEW -> getString(R.string.btw_new)
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
            getString(R.string.btw_intro, BtWidgetConfig.MAX_BUTTONS),
        ))

        card = screen.card(getString(R.string.btw_buttons_card, config.buttons.size, BtWidgetConfig.MAX_BUTTONS))
        if (config.buttons.isEmpty()) card.addView(GlassTheme.note(this, getString(R.string.btw_none)))
        config.buttons.forEachIndexed { i, b -> card.addView(row(i, b)) }
        card.addView(GlassTheme.button(this, getString(R.string.btw_add), accent = true) {
            editing = NEW
            show()
        }.apply { isEnabled = !config.isFull })
        if (config.isFull) card.addView(GlassTheme.note(this, getString(R.string.btw_full, BtWidgetConfig.MAX_BUTTONS)))

        card = screen.card(null)
        card.addView(GlassTheme.button(this, getString(R.string.bt_restore_defaults)) { confirmReset() })
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
                text = UiText.action(this@BtWidgetsActivity, b.action)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(GlassTheme.TEXT_SECONDARY)
                background = GlassTheme.rounded(this@BtWidgetsActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_CHIP)
                setPadding((6 * density).toInt(), (1 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(smallButton("↑", enabled = index > 0, description = getString(R.string.btw_up)) { change(config.move(index, -1)) })
        addView(smallButton("↓", enabled = index < config.buttons.size - 1, description = getString(R.string.btw_down)) { change(config.move(index, 1)) })
        addView(smallButton("✕", enabled = true, description = getString(R.string.btw_delete)) { confirmDelete(index, b) })
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
            .setTitle(getString(R.string.btw_delete_title, b.label))
            .setPositiveButton(R.string.btw_delete) { _, _ -> change(config.remove(index)) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.bt_restore_defaults)
            .setMessage(R.string.btw_restore_message)
            .setPositiveButton(R.string.bt_restore) { _, _ ->
                store.resetWidgets()
                config = store.widgets()
                show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Detalle ----

    private fun buildDetail(index: Int) {
        val existing = config.buttons.getOrNull(index)
        val layout = settings.keyboardLayout

        var card = screen.card(getString(R.string.btw_button_card))
        val label = BtActionForm.field(this, getString(R.string.btw_label_hint)).apply {
            setSingleLine()
            setText(existing?.label.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(BtWidgetConfig.MAX_LABEL))
        }
        val icon = BtActionForm.field(this, getString(R.string.btw_icon_hint)).apply {
            setSingleLine()
            setText(existing?.icon.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        }
        card.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(icon, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * density).toInt()
        })

        card = screen.card(getString(R.string.bt_action_card))
        val form = BtActionForm(this, existing?.action, layout, allowNone = false)
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

        card = screen.card(null)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(GlassTheme.button(this@BtWidgetsActivity, getString(R.string.save), accent = true) {
                val name = label.text.toString().trim()
                val symbol = icon.text.toString().trim()
                val problem = when {
                    !BtWidgetConfig.validLabel(name) -> getString(R.string.btw_err_label, BtWidgetConfig.MAX_LABEL)
                    !BtWidgetConfig.validIcon(symbol) -> getString(R.string.btw_err_icon)
                    else -> form.problem()
                }
                val action = form.action()
                if (problem != null || action == null) {
                    toast(problem ?: getString(R.string.btw_err_action))
                    return@button
                }
                val button = BtButton(name, symbol, action)
                saveAndClose(if (index == NEW) config.add(button) else config.replace(index, button))
            })
            addView(GlassTheme.button(this@BtWidgetsActivity, getString(R.string.cancel)) { onBackPressed() })
        })
    }

    /** Guarda y vuelve a la lista. */
    private fun saveAndClose(newConfig: BtWidgetConfig) {
        editing = null
        change(newConfig)
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
        const val NEW = -1
        const val NONE = -2
    }
}
