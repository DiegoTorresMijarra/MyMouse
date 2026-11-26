package dev.mymouse.core.shapes

/** Una forma grabada por el usuario: formas propias (`custom:<nombre>`) o su versión de una forma del catálogo. */
data class RecordedShape(val name: String, val samples: List<List<P>>)

/**
 * Las formas del móvil: cuáles del catálogo están activas y las grabadas (docs/spec-v1-gestos.md §5).
 * Inmutable y sin Android, para poder probar la lógica de confusión y de sincronización con el PC.
 */
data class ShapeLibrary(
    val enabledCatalog: Set<String> = ShapeCatalog.IDS.toSet(),
    val recorded: List<RecordedShape> = emptyList(),
) {
    /** Todas las formas activas: las del catálogo marcadas y todas las propias. */
    val enabled: Set<String>
        get() = enabledCatalog + recorded.map { it.name }.filter { it.startsWith(CUSTOM_PREFIX) }

    /** Formas propias (`custom:`), en el orden en que se grabaron. */
    val custom: List<RecordedShape> get() = recorded.filter { it.name.startsWith(CUSTOM_PREFIX) }

    /** [exclude]: forma que se deja fuera (al grabarla, para ver con qué otras se confunde). */
    fun recognizer(minSizePx: Float, exclude: String? = null): Recognizer {
        val templates = Recognizer.catalogTemplates() +
            recorded.filter { it.name != exclude }.flatMap { r -> r.samples.map { Recognizer.NamedTemplate(r.name, it) } }
        return Recognizer(templates, enabled - setOfNotNull(exclude), Recognizer.Config(minSizePx = minSizePx))
    }

    /**
     * ¿Con qué otras formas activas se confunde [name]? Se reconocen las [samples] con todas las demás
     * (sin esta); devuelve los nombres de las formas distintas que salen, sin repetir.
     */
    fun clashes(name: String, samples: List<List<P>>, minSizePx: Float): List<String> {
        val recognizer = recognizer(minSizePx, exclude = name)
        return samples.mapNotNull { (recognizer.recognize(it) as? Recognizer.Result.Match)?.name }
            .filter { it != name }
            .distinct()
    }

    /** Guarda (o sustituye) las muestras de [name]. */
    fun withRecorded(name: String, samples: List<List<P>>): ShapeLibrary =
        copy(recorded = recorded.filter { it.name != name } + RecordedShape(name, samples))

    fun without(name: String): ShapeLibrary = copy(recorded = recorded.filter { it.name != name })

    companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}
