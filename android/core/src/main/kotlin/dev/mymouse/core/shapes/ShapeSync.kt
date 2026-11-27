package dev.mymouse.core.shapes

import dev.mymouse.core.protocol.Message
import dev.mymouse.core.protocol.Protocol
import dev.mymouse.core.protocol.ShapeStatus
import dev.mymouse.core.protocol.StrokePoint
import kotlin.math.roundToInt

/**
 * Sincronización de formas con el PC (docs/spec-v4-toolbox.md §4.2 y §5). El móvil es el dueño de las
 * formas: envía las propias y aplica lo que pide el PC.
 */
object ShapeSync {
    /** Resultado de aplicar una petición del PC: la biblioteca nueva (igual si no cambió nada) y la respuesta. */
    data class Outcome(val library: ShapeLibrary, val result: Message.ShapeResult) {
        val changed: Boolean get() = result.status == ShapeStatus.SAVED || result.status == ShapeStatus.DELETED
    }

    /** Todos los mensajes de la lista: un `SHAPE_INFO` por forma propia y `SHAPE_LIST_END`. */
    fun listMessages(library: ShapeLibrary): List<Message> {
        val custom = library.custom.take(Protocol.MAX_SYNCED_SHAPES)
        return custom.mapNotNull { info(it) } + Message.ShapeListEnd(custom.size)
    }

    /** `SHAPE_INFO` de una forma, con cada muestra remuestreada a 32 puntos y normalizada. Null si no tiene muestras válidas. */
    fun info(shape: RecordedShape): Message.ShapeInfo? {
        val samples = shape.samples.filter { it.size >= 2 }.take(Protocol.MAX_SHAPE_SAMPLES).map(::toWire)
        return if (samples.isEmpty() || shape.name.isEmpty()) null else Message.ShapeInfo(shape.name, samples)
    }

    /**
     * Remuestrea a [Protocol.SHAPE_SAMPLE_POINTS] puntos y escala a 0–65535 con la misma escala en los dos ejes (el
     * lado largo ocupa todo el rango), para no deformar la forma. El origen es la esquina de su caja.
     */
    fun toWire(sample: List<P>): List<StrokePoint> {
        val pts = Geometry.resample(sample, Protocol.SHAPE_SAMPLE_POINTS)
        val b = Geometry.bounds(pts)
        val side = maxOf(b.width, b.height)
        val scale = if (side > 0f) 65535f / side else 0f
        return pts.map {
            StrokePoint(
                ((it.x - b.minX) * scale).roundToInt().coerceIn(0, 65535),
                ((it.y - b.minY) * scale).roundToInt().coerceIn(0, 65535),
            )
        }
    }

    /** Puntos recibidos como coordenadas (la escala da igual: el reconocedor es invariante a ella). */
    fun fromWire(sample: List<StrokePoint>): List<P> = sample.map { P(it.x.toFloat(), it.y.toFloat()) }

    /** Aplica un `SHAPE_ADD`: valida, comprueba confusiones (salvo con `force`) y guarda. */
    fun applyAdd(library: ShapeLibrary, add: Message.ShapeAdd): Outcome {
        fun reply(status: ShapeStatus, other: String = "") = Message.ShapeResult(add.name, status, other)
        val nameBytes = add.name.toByteArray(Charsets.UTF_8).size
        val valid = add.name.startsWith(ShapeLibrary.CUSTOM_PREFIX) &&
            add.name.length > ShapeLibrary.CUSTOM_PREFIX.length && nameBytes <= Protocol.MAX_GESTURE_NAME_BYTES &&
            add.samples.isNotEmpty() && add.samples.all { isUsable(it) }
        if (!valid) return Outcome(library, reply(ShapeStatus.INVALID))
        if (library.recorded.any { it.name == add.name }) return Outcome(library, reply(ShapeStatus.DUPLICATE_NAME))

        val samples = add.samples.map(::fromWire)
        if (!add.force) {
            // Las muestras vienen en 0–65535: el tamaño mínimo de un trazo del móvil (en píxeles) no aplica.
            val clash = library.clashes(add.name, samples, minSizePx = 1f).firstOrNull()
            if (clash != null) return Outcome(library, reply(ShapeStatus.CLASHES, clash))
        }
        return Outcome(library.withRecorded(add.name, samples), reply(ShapeStatus.SAVED))
    }

    /** Aplica un `SHAPE_DELETE`: solo se borran formas propias. */
    fun applyDelete(library: ShapeLibrary, delete: Message.ShapeDelete): Outcome {
        val exists = delete.name.startsWith(ShapeLibrary.CUSTOM_PREFIX) && library.recorded.any { it.name == delete.name }
        return if (exists) {
            Outcome(library.without(delete.name), Message.ShapeResult(delete.name, ShapeStatus.DELETED, ""))
        } else {
            Outcome(library, Message.ShapeResult(delete.name, ShapeStatus.NOT_FOUND, ""))
        }
    }

    /** Una muestra sirve si tiene al menos 2 puntos y no es casi un punto (menos del 2 % del rango). */
    private fun isUsable(sample: List<StrokePoint>): Boolean {
        if (sample.size < 2) return false
        val xs = sample.map { it.x }
        val ys = sample.map { it.y }
        return maxOf(xs.max() - xs.min(), ys.max() - ys.min()) >= MIN_EXTENT
    }

    private const val MIN_EXTENT = 1300 // 2 % de 65535
}
