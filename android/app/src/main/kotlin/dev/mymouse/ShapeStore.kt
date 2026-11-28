package dev.mymouse

import android.content.Context
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.shapes.P
import dev.mymouse.core.shapes.RecordedShape
import dev.mymouse.core.shapes.Recognizer
import dev.mymouse.core.shapes.ShapeCatalog
import dev.mymouse.core.shapes.ShapeLibrary
import dev.mymouse.core.shapes.ShapeSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Formas del modo gestos guardadas en el móvil (docs/spec-v1-gestos.md §5):
 * qué formas del catálogo están activas y las muestras grabadas por el usuario
 * (formas propias "custom:<nombre>" o versiones propias de las del catálogo). La lógica (confusiones, sincronización
 * con el PC) está en `core` ([ShapeLibrary], [ShapeSync]); esta clase solo la guarda en `shapes.json`.
 */
class ShapeStore(context: Context) {

    private val file = File(context.filesDir, "shapes.json")

    var enabledCatalog: Set<String> = ShapeCatalog.IDS.toSet()
        private set
    var recorded: List<RecordedShape> = emptyList()
        private set

    init {
        load()
    }

    /** Estado actual como biblioteca de `core`. */
    val library: ShapeLibrary get() = ShapeLibrary(enabledCatalog, recorded)

    /** Todas las formas activas: las del catálogo marcadas y todas las propias. */
    val enabled: Set<String> get() = library.enabled

    /** [exclude]: forma que se deja fuera (al grabarla, para ver con qué otras se confunde). */
    fun recognizer(minSizePx: Float, exclude: String? = null): Recognizer = library.recognizer(minSizePx, exclude)

    /** ¿Con qué otras formas activas se confunde [name]? (Misma lógica al grabar en el móvil y al recibirla del PC.) */
    fun clashes(name: String, samples: List<List<P>>, minSizePx: Float): List<String> = library.clashes(name, samples, minSizePx)

    /** Mensajes de la lista de formas propias para el PC: `SHAPE_INFO` por forma y `SHAPE_LIST_END`. */
    fun syncMessages(): List<Message> = ShapeSync.listMessages(library)

    /** Aplica una petición del PC y guarda si cambió algo. Devuelve la respuesta para el PC. */
    fun applyRequest(request: Message): Message.ShapeResult {
        val outcome = when (request) {
            is Message.ShapeAdd -> ShapeSync.applyAdd(library, request)
            is Message.ShapeDelete -> ShapeSync.applyDelete(library, request)
            else -> throw IllegalArgumentException("Not a shapes request: $request")
        }
        if (outcome.changed) {
            recorded = outcome.library.recorded
            save()
        }
        return outcome.result
    }

    /** Puntos para dibujar la miniatura de una forma (la del catálogo o la primera muestra grabada). */
    fun preview(name: String): List<P>? =
        recorded.firstOrNull { it.name == name }?.samples?.firstOrNull() ?: ShapeCatalog.BY_ID[name]?.points

    fun setEnabled(id: String, enabled: Boolean) {
        enabledCatalog = if (enabled) enabledCatalog + id else enabledCatalog - id
        save()
    }

    fun put(name: String, samples: List<List<P>>) {
        recorded = library.withRecorded(name, samples).recorded
        save()
    }

    fun remove(name: String) {
        recorded = library.without(name).recorded
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
                RecordedShape(o.getString("name"), (0 until samples.length()).map { s ->
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
        /** Lo pone [ShapesActivity] mientras está abierta: se llama cuando el PC cambia las formas. */
        @Volatile var onExternalChange: (() -> Unit)? = null

        const val CUSTOM_PREFIX = ShapeLibrary.CUSTOM_PREFIX

        /** Nombre para mostrar: el del catálogo, traducido, o el nombre propio sin prefijo. */
        fun label(context: Context, name: String): String = UiText.shape(context, name)
    }
}
