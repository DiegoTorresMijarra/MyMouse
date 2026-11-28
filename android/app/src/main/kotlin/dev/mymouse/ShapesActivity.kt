package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import dev.mymouse.core.shapes.ShapeCatalog

/**
 * Formas del modo gestos (docs/spec-v1-gestos.md §2 y §5): activar las incluidas, grabar tu versión
 * de una incluida y crear formas propias. Las acciones de cada forma se configuran en el PC (cable) o en Acciones por Bluetooth.
 */
class ShapesActivity : Activity() {

    private lateinit var store: ShapeStore
    private lateinit var screen: GlassTheme.Screen

    /** La tarjeta de cristal en la que se está añadiendo (la nota inicial, "Formas incluidas" o "Formas propias"). */
    private lateinit var list: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = GlassTheme.Screen(this, getString(R.string.shapes_title))
        screen.setContentView()
    }

    override fun onResume() {
        super.onResume()
        store = ShapeStore(this) // puede haber cambiado al grabar
        build()
        // Si el PC añade o borra una forma con esta pantalla abierta, se refresca (docs/spec-v4-toolbox.md §7).
        ShapeStore.onExternalChange = { runOnUiThread { store = ShapeStore(this); build() } }
    }

    override fun onPause() {
        ShapeStore.onExternalChange = null
        super.onPause()
    }

    private fun build() {
        screen.content.removeAllViews()
        list = screen.card(null)
        list.addView(note(
            getString(R.string.shapes_intro),
        ))

        section(getString(R.string.shapes_included))
        for (shape in ShapeCatalog.ALL) {
            val mine = store.recorded.any { it.name == shape.id }
            list.addView(row(
                store.preview(shape.id),
                "${ShapeStore.label(this, shape.id)}   ",
                shape.id + if (mine) getString(R.string.shapes_your_version) else "",
            ) {
                addView(Switch(this@ShapesActivity).apply {
                    GlassTheme.tint(this)
                    isChecked = shape.id in store.enabledCatalog
                    setOnCheckedChangeListener { _, checked -> store.setEnabled(shape.id, checked) }
                })
            })
            if (shape.id !in ShapeCatalog.LINES) {
                list.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    addView(smallButton(if (mine) getString(R.string.shapes_rerecord) else getString(R.string.shapes_record)) { record(shape.id) })
                    if (mine) addView(smallButton(getString(R.string.shapes_remove_mine)) { store.remove(shape.id); build() })
                })
            }
        }

        section(getString(R.string.shapes_custom))
        val custom = store.recorded.filter { it.name.startsWith(ShapeStore.CUSTOM_PREFIX) }
        if (custom.isEmpty()) list.addView(note(getString(R.string.shapes_none_yet)))
        for (r in custom) {
            list.addView(row(r.samples.firstOrNull(), ShapeStore.label(this, r.name), getString(R.string.shapes_on_pc, r.name)) {
                addView(smallButton(getString(R.string.shapes_delete)) { confirmDelete(r.name) })
            })
        }
        list.addView(GlassTheme.button(this, getString(R.string.shapes_new), accent = true) { askName() })
    }

    private fun askName() {
        val input = EditText(this).apply {
            hint = getString(R.string.shapes_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.shapes_name_title)
            .setMessage(R.string.shapes_name_note)
            .setView(input)
            .setPositiveButton(R.string.shapes_draw) { _, _ ->
                val name = input.text.toString().trim().replace(Regex("\\s+"), "-").lowercase()
                val full = ShapeStore.CUSTOM_PREFIX + name
                when {
                    name.isEmpty() -> toast(getString(R.string.shapes_err_name))
                    full.toByteArray(Charsets.UTF_8).size > 64 -> toast(getString(R.string.shapes_err_long))
                    store.recorded.any { it.name == full } -> toast(getString(R.string.shapes_err_exists))
                    else -> record(full)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.shapes_delete_title, ShapeStore.label(this, name)))
            .setPositiveButton(R.string.shapes_delete) { _, _ -> store.remove(name); build() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun record(name: String) {
        startActivity(Intent(this, RecordShapeActivity::class.java).putExtra(RecordShapeActivity.EXTRA_NAME, name))
    }

    private fun toast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    /** Una tarjeta de cristal nueva con su título. */
    private fun section(title: String) {
        list = screen.card(title)
    }

    private fun note(text: String) = GlassTheme.note(this, text)

    private fun row(
        preview: List<dev.mymouse.core.shapes.P>?, title: String, subtitle: String,
        trailing: LinearLayout.() -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        // La misma fila que en el PC: baldosa de cristal con la forma, título y un chip con el detalle.
        val size = (40 * density).toInt()
        addView(ShapePreviewView(this@ShapesActivity, preview).apply {
            background = GlassTheme.rounded(this@ShapesActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_ROW)
        }, LinearLayout.LayoutParams(size, size))
        addView(LinearLayout(this@ShapesActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
            addView(TextView(this@ShapesActivity).apply {
                text = title
                textSize = 14f
                setTextColor(GlassTheme.TEXT)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@ShapesActivity).apply {
                text = subtitle
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(GlassTheme.TEXT_SECONDARY)
                background = GlassTheme.rounded(this@ShapesActivity, GlassTheme.ELEVATED, GlassTheme.RADIUS_CHIP)
                setPadding((6 * density).toInt(), (1 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        trailing()
    }

    private fun smallButton(text: String, onClick: () -> Unit) = GlassTheme.button(this, text, onClick = onClick).apply { textSize = 13f }
}
