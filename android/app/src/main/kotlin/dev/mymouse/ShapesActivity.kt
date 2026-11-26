package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.mymouse.core.shapes.ShapeCatalog

/**
 * Formas del modo gestos (docs/spec-v1-gestos.md §2 y §5): activar las incluidas, grabar tu versión
 * de una incluida y crear formas propias. Las acciones de cada forma se configuran en el PC.
 */
class ShapesActivity : Activity() {

    private lateinit var store: ShapeStore
    private lateinit var list: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * density).toInt()
            setPadding(p, p, p, p)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(list)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
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
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "Formas"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        list.addView(note(
            "Activa solo las que uses: cuantas menos, menos se confunden (recomendado: hasta 10). " +
                "Qué hace cada forma se decide en el PC: toolbox → tarjeta Gestos (4 dedos ↑ en el trackpad). " +
                "El punto verde de cada dibujo marca dónde empieza el trazo.",
        ))

        list.addView(header("Formas incluidas"))
        for (shape in ShapeCatalog.ALL) {
            val mine = store.recorded.any { it.name == shape.id }
            list.addView(row(
                store.preview(shape.id),
                "${shape.label}   ",
                shape.id + if (mine) " · tu versión" else "",
            ) {
                addView(Switch(this@ShapesActivity).apply {
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

        list.addView(header("Formas propias"))
        val custom = store.recorded.filter { it.name.startsWith(ShapeStore.CUSTOM_PREFIX) }
        if (custom.isEmpty()) list.addView(note("Todavía no hay ninguna."))
        for (r in custom) {
            list.addView(row(r.samples.firstOrNull(), ShapeStore.label(r.name), "en el PC: \"${r.name}\"") {
                addView(smallButton("Borrar") { confirmDelete(r.name) })
            })
        }
        list.addView(Button(this).apply {
            text = "Nueva forma"
            isAllCaps = false
            setOnClickListener { askName() }
        })
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

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(100, 180, 255))
        setPadding(0, (20 * density).toInt(), 0, (6 * density).toInt())
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(150, 150, 150))
        setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
    }

    private fun row(
        preview: List<dev.mymouse.core.shapes.P>?, title: String, subtitle: String,
        trailing: LinearLayout.() -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        val size = (48 * density).toInt()
        addView(ShapePreviewView(this@ShapesActivity, preview), LinearLayout.LayoutParams(size, size))
        addView(LinearLayout(this@ShapesActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
            addView(TextView(this@ShapesActivity).apply {
                text = title
                textSize = 17f
                setTextColor(Color.rgb(230, 230, 230))
            })
            addView(TextView(this@ShapesActivity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.rgb(130, 130, 130))
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        trailing()
    }

    private fun smallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setOnClickListener { onClick() }
    }
}
