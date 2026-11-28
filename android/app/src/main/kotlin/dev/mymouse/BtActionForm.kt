package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import androidx.annotation.StringRes
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.btactions.KeyboardLayout
import dev.mymouse.core.hid.HidKeys

/**
 * Formulario de una acción por Bluetooth (docs/spec-v5-bluetooth-completo.md §2.4), común a "Acciones por Bluetooth…" y
 * "Botones por Bluetooth…": tipo (Atajo / Multimedia / Texto / Ninguna), modificadores y tecla, lista multimedia o texto con el
 * aviso de caracteres que la distribución elegida no puede escribir. La lectura y el formato de las acciones son de `core`.
 *
 * [allowNone]: ofrece "Ninguna" (las formas sí; los botones no). [onChange] se llama con cada cambio.
 */
class BtActionForm(
    private val activity: Activity,
    initial: BtAction?,
    private val layout: KeyboardLayout,
    private val allowNone: Boolean,
    private val onChange: () -> Unit = {},
) {
    private enum class Type(@StringRes val label: Int) {
        KEYS(R.string.form_type_keys), MEDIA(R.string.form_type_media), TEXT(R.string.form_type_text),
        KEYBOARD(R.string.form_type_keyboard), NONE(R.string.form_type_none),
    }

    private var type: Type = when (initial) {
        is BtAction.Keys -> Type.KEYS
        is BtAction.Media -> Type.MEDIA
        is BtAction.Text -> Type.TEXT
        BtAction.Keyboard -> Type.KEYBOARD
        null -> if (allowNone) Type.NONE else Type.KEYS
    }
    private var modifiers = (initial as? BtAction.Keys)?.modifiers ?: 0
    private var key = (initial as? BtAction.Keys)?.key ?: 0
    private var mediaIndex = (initial as? BtAction.Media)?.let { m -> BtActions.MEDIA.indexOfFirst { it.key == m.key } } ?: 0
    private var typedText = (initial as? BtAction.Text)?.text.orEmpty()

    private val density get() = activity.resources.displayMetrics.density

    private val typeRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val summary = GlassTheme.text(activity, "", 13f, GlassTheme.TEXT_SECONDARY).apply {
        setPadding(0, (8 * density).toInt(), 0, 0)
    }

    private val warning = GlassTheme.text(activity, "", 13f, GlassTheme.AMBER).apply { setPadding(0, (6 * density).toInt(), 0, 0) }

    /** La vista del formulario, para añadirla a una tarjeta. */
    val view: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(typeRow)
        addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(summary)
    }

    init {
        buildTypeRow()
        buildBody()
        updateSummary()
    }

    /** ¿Se eligió "Ninguna"? */
    val isNone: Boolean get() = type == Type.NONE

    /** La acción que describe el formulario, o null si es "Ninguna" o está incompleta (ver [problem]). */
    fun action(): BtAction? = when (type) {
        Type.NONE -> null
        Type.KEYS -> if (modifiers == 0 && key == 0) null else BtAction.Keys(modifiers, key)
        Type.MEDIA -> BtAction.Media(BtActions.MEDIA[mediaIndex].key)
        Type.TEXT -> if (typedText.isEmpty() || layout.typeStrokes(typedText).isEmpty()) null else BtAction.Text(typedText)
        Type.KEYBOARD -> BtAction.Keyboard
    }

    /** Qué falta para poder guardar, o null si está bien (o es "Ninguna"). */
    fun problem(): String? = when (type) {
        Type.NONE -> null
        Type.KEYS -> if (modifiers == 0 && key == 0) activity.getString(R.string.form_pick_key) else null
        Type.MEDIA, Type.KEYBOARD -> null
        Type.TEXT -> when {
            typedText.isEmpty() -> activity.getString(R.string.form_type_something)
            layout.typeStrokes(typedText).isEmpty() -> activity.getString(R.string.form_no_typable, UiText.layout(activity, layout.id))
            else -> null
        }
    }

    // ---- Construcción ----

    private fun buildTypeRow() {
        typeRow.removeAllViews()
        for (t in Type.entries) {
            if (t == Type.NONE && !allowNone) continue
            typeRow.addView(chip(activity.getString(t.label), t == type) {
                type = t
                buildTypeRow()
                buildBody()
                updateSummary()
                onChange()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            })
        }
    }

    private fun buildBody() {
        body.removeAllViews()
        when (type) {
            Type.NONE -> body.addView(GlassTheme.note(activity, activity.getString(R.string.form_none_note)))
            Type.KEYS -> buildKeys()
            Type.MEDIA -> buildMedia()
            Type.TEXT -> buildText()
            Type.KEYBOARD -> body.addView(GlassTheme.note(activity, activity.getString(R.string.form_keyboard_note)))
        }
    }

    private fun buildKeys() {
        val mods = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, bit) in MODIFIERS) {
            mods.addView(chip(name, modifiers and bit != 0) {
                modifiers = modifiers xor bit
                buildBody()
                updateSummary()
                onChange()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            })
        }
        body.addView(mods, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * density).toInt()
        })
        val keyLabel = if (key == 0) activity.getString(R.string.form_key_none) else HidKeys.PICKER.firstOrNull { it.usage == key }?.let { UiText.key(activity, it) } ?: "?"
        body.addView(GlassTheme.button(activity, activity.getString(R.string.form_key_button, keyLabel)) { pickKey() })
    }

    /** Lista de teclas: letras, números, F1–F12, flechas, Inicio/Fin, RePág/AvPág, Supr, Retroceso, Intro, Esc, Tab, Espacio e Impr Pant. */
    private fun pickKey() {
        val keys = HidKeys.PICKER
        val labels: List<CharSequence> = listOf<CharSequence>(activity.getString(R.string.form_key_modifiers_only)) + keys.map { UiText.key(activity, it) as CharSequence }
        AlertDialog.Builder(activity)
            .setTitle(R.string.form_key_title)
            .setItems(labels.toTypedArray()) { _, which ->
                key = if (which == 0) 0 else keys[which - 1].usage
                buildBody()
                updateSummary()
                onChange()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun buildMedia() {
        body.addView(RadioGroup(activity).apply {
            BtActions.MEDIA.forEachIndexed { i, option ->
                addView(RadioButton(activity).apply {
                    id = android.view.View.generateViewId()
                    this.text = UiText.media(activity, option.key)
                    textSize = 16f
                    setTextColor(GlassTheme.TEXT)
                    GlassTheme.tint(this)
                    isChecked = i == mediaIndex
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            mediaIndex = i
                            updateSummary()
                            onChange()
                        }
                    }
                })
            }
        })
    }

    private fun buildText() {
        val field = field(activity, activity.getString(R.string.form_text_hint)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(InputFilter.LengthFilter(BtAction.MAX_TEXT))
            setText(typedText)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    typedText = s?.toString().orEmpty()
                    updateWarning()
                    updateSummary()
                    onChange()
                }
            })
        }
        body.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * density).toInt()
        })
        (warning.parent as? ViewGroup)?.removeView(warning)
        body.addView(warning)
        updateWarning()
        body.addView(GlassTheme.note(
            activity,
            activity.getString(R.string.form_text_note, UiText.layout(activity, layout.id), BtAction.MAX_TEXT),
        ))
    }

    /** Aviso de los caracteres que la distribución elegida no puede escribir (se saltarán). */
    private fun updateWarning() {
        val bad = layout.unsupported(typedText)
        warning.text = if (bad.isEmpty()) "" else
            activity.getString(R.string.form_text_warning, UiText.layout(activity, layout.id), bad.joinToString(" ") { if (it == "\t") "⇥" else it })
        warning.visibility = if (bad.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateSummary() {
        val a = action()
        summary.text = when {
            type == Type.NONE -> ""
            a != null -> activity.getString(R.string.form_will_do, UiText.action(activity, a))
            else -> problem().orEmpty()
        }
    }

    // ---- Vistas pequeñas ----

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = label
        textSize = 14f
        setTextColor(GlassTheme.TEXT)
        gravity = Gravity.CENTER
        minHeight = (40 * density).toInt()
        setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
        background = GlassTheme.ripple(
            activity,
            if (selected) GlassTheme.rounded(activity, GlassTheme.selectedFill, GlassTheme.RADIUS_ROW, GlassTheme.selectedBorder)
            else GlassTheme.rounded(activity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW),
            GlassTheme.RADIUS_ROW,
        )
        isClickable = true
        setOnClickListener { onClick() }
    }

    companion object {
        private val MODIFIERS = listOf(
            "Ctrl" to HidKeys.MOD_CTRL, "Alt" to HidKeys.MOD_ALT, "Shift" to HidKeys.MOD_SHIFT, "Win" to HidKeys.MOD_GUI,
        )

        /** Campo de texto con el estilo de cristal (sin ActionBar ni Material). */
        fun field(activity: Activity, hint: String): EditText = EditText(activity).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(GlassTheme.TEXT)
            setHintTextColor(GlassTheme.TEXT_TERTIARY)
            val d = activity.resources.displayMetrics.density
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GlassTheme.rounded(activity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW)
        }
    }
}
