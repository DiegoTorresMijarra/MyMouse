package dev.mymouse.core.btactions

import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.json.MiniJson
import dev.mymouse.core.protocol.GestureListEntry
import dev.mymouse.core.shapes.ShapeCatalog

/** Una regla de `bt-gestures.json`: forma → acción, con su texto de ayuda y si se fija en la chuleta. */
data class BtBinding(val gesture: String, val action: BtAction, val help: String = "", val pinned: Boolean = false) {
    /** El texto de ayuda: el propio o, si está en blanco, lo que hace la acción. */
    val helpText: String get() = help.ifBlank { BtActions.describe(action) }
}

/** Resultado de leer el fichero: la configuración válida y los avisos de lo que se descartó. */
data class BtParseResult<T>(val config: T, val warnings: List<String>)

/**
 * Acciones de las formas por Bluetooth (docs/spec-v5-bluetooth-completo.md §2.2), guardadas en el móvil en `bt-gestures.json`.
 * Una forma tiene como mucho una acción (por Bluetooth no hay reglas por aplicación). Inmutable.
 */
class BtGestureConfig(bindings: List<BtBinding>) {
    val bindings: List<BtBinding> = bindings.distinctBy { it.gesture }

    fun find(gesture: String): BtBinding? = bindings.firstOrNull { it.gesture == gesture }

    /** Pone (o sustituye, en su sitio) la regla de una forma. */
    fun with(binding: BtBinding): BtGestureConfig =
        if (find(binding.gesture) == null) BtGestureConfig(bindings + binding)
        else BtGestureConfig(bindings.map { if (it.gesture == binding.gesture) binding else it })

    /** Quita la acción de una forma ("Ninguna"). */
    fun without(gesture: String): BtGestureConfig = BtGestureConfig(bindings.filterNot { it.gesture == gesture })

    /**
     * La lista que ve el modo gestos del móvil (la misma forma que `GESTURE_LIST` del PC): las reglas de las formas activas,
     * con su ayuda. Las formas sin acción no salen.
     */
    fun listEntries(enabledShapes: Collection<String>): List<GestureListEntry> =
        bindings.filter { it.gesture in enabledShapes }.map { GestureListEntry(it.gesture, it.helpText, it.pinned) }

    /** Contenido del fichero: `{"version": 1, "bindings": [...]}`. */
    fun toJson(): String = MiniJson.write(
        linkedMapOf(
            "version" to 1,
            "bindings" to bindings.map { b ->
                linkedMapOf<String, Any?>("gesture" to b.gesture, "action" to BtActions.toJson(b.action)).also {
                    if (b.help.isNotBlank()) it["help"] = b.help
                    if (b.pinned) it["pin"] = true
                }
            },
        ),
    )

    override fun equals(other: Any?) = other is BtGestureConfig && bindings == other.bindings
    override fun hashCode() = bindings.hashCode()

    companion object {
        const val MAX_HELP = 60

        /** ¿Es una forma que puede tener acción? Las del catálogo y las propias (`custom:…`). */
        fun isShape(id: String) = id in ShapeCatalog.BY_ID || (id.startsWith("custom:") && id.length > "custom:".length)

        /** Los valores de la primera vez (docs/spec-v5-bluetooth-completo.md §2.2), con los ids reales del catálogo (`>` es `gt` y `<` es `lt`). */
        val DEFAULT = BtGestureConfig(listOf(
            BtBinding("z", keys(HidKeys.MOD_CTRL, "Z"), "Deshacer", pinned = true),
            BtBinding(ShapeCatalog.LINE_LEFT, keys(HidKeys.MOD_ALT, "Left"), "Atrás"),
            BtBinding(ShapeCatalog.LINE_RIGHT, keys(HidKeys.MOD_ALT, "Right"), "Adelante"),
            BtBinding(ShapeCatalog.LINE_DOWN, keys(HidKeys.MOD_GUI, "D"), "Escritorio"),
            BtBinding(ShapeCatalog.LINE_UP, keys(HidKeys.MOD_GUI, "Tab"), "Vista de tareas"),
            BtBinding("v", BtAction.Media(ConsumerKey.PLAY_PAUSE), "Reproducir / pausar", pinned = true),
            BtBinding("gt", BtAction.Media(ConsumerKey.NEXT), "Siguiente canción"),
            BtBinding("lt", BtAction.Media(ConsumerKey.PREVIOUS), "Canción anterior"),
            BtBinding("check", keys(HidKeys.MOD_CTRL, "S"), "Guardar"),
        ))

        private fun keys(modifiers: Int, key: String) = BtAction.Keys(modifiers, HidKeys.byName(key)!!.usage)

        /**
         * Lee el fichero. Las reglas no válidas (forma desconocida, acción rara, repetida) se descartan y se cuentan en los avisos;
         * si el JSON entero no es válido, lanza [IllegalArgumentException].
         */
        fun parse(text: String): BtParseResult<BtGestureConfig> {
            val root = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("bt-gestures.json: la raíz debe ser un objeto")
            val list = root["bindings"] as? List<*> ?: throw IllegalArgumentException("bt-gestures.json: falta la lista 'bindings'")
            val warnings = mutableListOf<String>()
            val bindings = mutableListOf<BtBinding>()
            list.forEachIndexed { i, item ->
                try {
                    val o = item as? Map<*, *> ?: throw IllegalArgumentException("la regla debe ser un objeto")
                    val gesture = o["gesture"] as? String ?: throw IllegalArgumentException("falta 'gesture'")
                    if (!isShape(gesture)) throw IllegalArgumentException("forma desconocida: '$gesture'")
                    if (bindings.any { it.gesture == gesture }) throw IllegalArgumentException("la forma '$gesture' ya tiene acción")
                    val action = BtActions.fromJson(o["action"])
                    val help = (o["help"] as? String).orEmpty().trim().take(MAX_HELP)
                    bindings += BtBinding(gesture, action, help, o["pin"] == true)
                } catch (e: IllegalArgumentException) {
                    warnings += "Regla ${i + 1}: ${e.message}"
                }
            }
            return BtParseResult(BtGestureConfig(bindings), warnings)
        }
    }
}
