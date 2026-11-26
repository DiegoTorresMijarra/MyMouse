package dev.mymouse

import android.content.Context
import dev.mymouse.core.shapes.P
import dev.mymouse.core.shapes.Recognizer
import dev.mymouse.core.shapes.ShapeCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Formas del modo gestos guardadas en el móvil (docs/spec-v1-gestos.md §5):
 * qué formas del catálogo están activas y las muestras grabadas por el usuario
 * (formas propias "custom:<nombre>" o versiones propias de las del catálogo).
 */
class ShapeStore(context: Context) {

    data class Recorded(val name: String, val samples: List<List<P>>)

    private val file = File(context.filesDir, "shapes.json")

    var enabledCatalog: Set<String> = ShapeCatalog.IDS.toSet()
        private set
    var recorded: List<Recorded> = emptyList()
        private set

    init {
        load()
    }

    /** Todas las formas activas: las del catálogo marcadas y todas las propias. */
    val enabled: Set<String>
        get() = enabledCatalog + recorded.map { it.name }.filter { it.startsWith(CUSTOM_PREFIX) }

    fun recognizer(minSizePx: Float): Recognizer {
        val templates = Recognizer.catalogTemplates() +
            recorded.flatMap { r -> r.samples.map { Recognizer.NamedTemplate(r.name, it) } }
        return Recognizer(templates, enabled, Recognizer.Config(minSizePx = minSizePx))
    }

    /** Puntos para dibujar la miniatura de una forma (la del catálogo o la primera muestra grabada). */
    fun preview(name: String): List<P>? =
        recorded.firstOrNull { it.name == name }?.samples?.firstOrNull() ?: ShapeCatalog.BY_ID[name]?.points

    fun setEnabled(id: String, enabled: Boolean) {
        enabledCatalog = if (enabled) enabledCatalog + id else enabledCatalog - id
        save()
    }

    fun put(name: String, samples: List<List<P>>) {
        recorded = recorded.filter { it.name != name } + Recorded(name, samples)
        save()
    }

    fun remove(name: String) {
        recorded = recorded.filter { it.name != name }
        save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            json.optJSONArray("enabled")?.let { arr ->
                enabledCatalog = (0 until arr.length()).map { arr.getString(it) }.filter { it in ShapeCatalog.BY_ID }.toSet()
            }
            val custom = json.optJSONArray("recorded") ?: JSONArray()
            recorded = (0 until custom.length()).map { i ->
                val o = custom.getJSONObject(i)
                val samples = o.getJSONArray("samples")
                Recorded(o.getString("name"), (0 until samples.length()).map { s ->
                    val flat = samples.getJSONArray(s)
                    (0 until flat.length() / 2).map { k -> P(flat.getDouble(2 * k).toFloat(), flat.getDouble(2 * k + 1).toFloat()) }
                })
            }
        } catch (e: Exception) {
            android.util.Log.w("MyMouse", "shapes.json ilegible, se usan los valores por defecto: ${e.message}")
        }
    }

    private fun save() {
        val json = JSONObject()
        json.put("enabled", JSONArray(enabledCatalog.sorted()))
        json.put("recorded", JSONArray().apply {
            for (r in recorded) {
                put(JSONObject().apply {
                    put("name", r.name)
                    put("samples", JSONArray().apply {
                        for (sample in r.samples) {
                            put(JSONArray().apply { for (p in sample) { put(p.x.toDouble()); put(p.y.toDouble()) } })
                        }
                    })
                })
            }
        })
        file.writeText(json.toString())
    }

    companion object {
        const val CUSTOM_PREFIX = "custom:"

        /** Nombre para mostrar: etiqueta del catálogo o el nombre propio sin prefijo. */
        fun label(name: String): String =
            ShapeCatalog.BY_ID[name]?.label ?: name.removePrefix(CUSTOM_PREFIX)
    }
}
