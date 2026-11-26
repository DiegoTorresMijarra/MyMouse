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
 * de una incluida y crear formas propias. Las acciones de cada forma se configuran en el PC.
 */
class ShapesActivity : Activity() {

    private lateinit var store: ShapeStore
    private lateinit var screen: GlassTheme.Screen

    /** La tarjeta de cristal en la que se está añadiendo (la nota inicial, "Formas incluidas" o "Formas propias"). */
    private lateinit var list: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = GlassTheme.Screen(this, "Formas")
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
            "Activa solo las que uses: cuantas menos, menos se confunden (recomendado: hasta 10). " +
                "Qué hace cada forma se decide en el PC: toolbox → tarjeta Gestos (4 dedos ↑ en el trackpad). " +
                "El punto azul de cada dibujo marca dónde empieza el trazo.",
        ))

        section("Formas incluidas")
        for (shape in ShapeCatalog.ALL) {
            val mine = store.recorded.any { it.name == shape.id }
            list.addView(row(
                store.preview(shape.id),
                "${shape.label}   ",
                shape.id + if (mine) " · tu versión" else "",
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
                    addView(smallButton(if (mine) "Regrabar mi versión" else "Grabar mi versión") { record(shape.id) })
                    if (mine) addView(smallButton("Quitar mi versión") { store.remove(shape.id); build() })
                })
            }
        }

        section("Formas propias")
        val custom = store.recorded.filter { it.name.startsWith(ShapeStore.CUSTOM_PREFIX) }
        if (custom.isEmpty()) list.addView(note("Todavía no hay ninguna."))
        for (r in custom) {
            list.addView(row(r.samples.firstOrNull(), ShapeStore.label(r.name), "en el PC: \"${r.name}\"") {
                addView(smallButton("Borrar") { confirmDelete(r.name) })
            })
        }
        list.addView(GlassTheme.button(this, "Nueva forma", accent = true) { askName() })
    }

    private fun askName() {
        val input = EditText(this).apply {
            hint = "p. ej. firma"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Nombre de la forma")
            .setMessage("En el PC se usará como \"custom:<nombre>\".")
            .setView(input)
            .setPositiveButton("Dibujar") { _, _ ->
                val name = input.text.toString().trim().replace(Regex("\\s+"), "-").lowercase()
                val full = ShapeStore.CUSTOM_PREFIX + name
                when {
                    name.isEmpty() -> toast("Hace falta un nombre")
                    full.toByteArray(Charsets.UTF_8).size > 64 -> toast("Nombre demasiado largo")
                    store.recorded.any { it.name == full } -> toast("Ya hay una forma con ese nombre")
                    else -> record(full)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Borrar \"${ShapeStore.label(name)}\"")
            .setPositiveButton("Borrar") { _, _ -> store.remove(name); build() }
            .setNegativeButton("Cancelar", null)
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
