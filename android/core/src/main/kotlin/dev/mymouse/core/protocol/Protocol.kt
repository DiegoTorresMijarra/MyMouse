package dev.mymouse.core.protocol

import java.io.InputStream

/** Constantes del protocolo. Fuente de verdad: protocol/PROTOCOL.md. */
object Protocol {
    /** Versión más alta que conoce el cliente. 2 = modo gestos, 3 = panel de widgets, 4 = gestos de Windows, 5 = toolbox del PC. */
    const val VERSION = 5
    const val GESTURES_VERSION = 2

    /** Primera versión con los mensajes del panel de widgets. */
    const val WIDGETS_VERSION = 3
    const val MAX_WIDGETS = 32

    /** Primera versión con `SYSTEM_GESTURE` (gestos de 3 dedos de Windows por USB). */
    const val SYSTEM_GESTURE_VERSION = 4

    /** Primera versión con la toolbox del PC: `TOOLBOX`, gesto en vivo (`GESTURE_MODE`, `STROKE_*`) y sincronización de formas (`SHAPE_*`). */
    const val TOOLBOX_VERSION = 5

    /** Puntos a los que se remuestrea cada muestra de una forma al enviarla (`SHAPE_INFO`). */
    const val SHAPE_SAMPLE_POINTS = 32

    /** Máximo de muestras por forma y de puntos por muestra o lote (los cuentan un `u8`). */
    const val MAX_SHAPE_SAMPLES = 255
    const val MAX_POINTS_PER_BATCH = 255

    /** Máximo de formas propias que se sincronizan (el total va en un `u8`). */
    const val MAX_SYNCED_SHAPES = 255
    const val DEFAULT_PORT = 47800
    const val PING_INTERVAL_MS = 1_000L
    const val IDLE_TIMEOUT_MS = 3_000L
    const val HANDSHAKE_TIMEOUT_MS = 2_000L
    const val MAX_GESTURE_NAME_BYTES = 64
}

enum class MouseButton(val code: Int) { LEFT(0), RIGHT(1), MIDDLE(2) }

enum class GestureStatus(val code: Int) { EXECUTED(0), UNASSIGNED(1), ERROR(2) }

data class GestureListEntry(val name: String, val help: String, val pinned: Boolean)

enum class WidgetKind(val code: Int) { BUTTON(0), MEDIA(1), VOLUME(2) }

/** Orden multimedia de [Message.MediaControl]. */
enum class MediaCommand(val code: Int) { PLAY_PAUSE(0), NEXT(1), PREVIOUS(2) }

/**
 * Gesto de sistema de Windows (protocolo v4, `SYSTEM_GESTURE`). El cambio de app mantiene `Alt` pulsado
 * entre [APP_SWITCH_START] y [APP_SWITCH_END].
 */
enum class SystemGestureCommand(val code: Int) {
    TASK_VIEW(0), SHOW_DESKTOP(1), APP_SWITCH_START(2), APP_SWITCH_NEXT(3), APP_SWITCH_PREVIOUS(4), APP_SWITCH_END(5),
}

/** Punto normalizado 0–65535 (trazos y muestras de formas del protocolo v5). */
data class StrokePoint(val x: Int, val y: Int) {
    init {
        require(x in 0..0xFFFF && y in 0..0xFFFF) { "Punto fuera de 0–65535: ($x, $y)" }
    }
}

/** Cómo terminó un trazo del modo gestos (`STROKE_END`). */
enum class StrokeResult(val code: Int) { RECOGNIZED(0), NOT_RECOGNIZED(1), CANCELLED(2), TIMED_OUT(3) }

/** Resultado de añadir o borrar una forma en el móvil (`SHAPE_RESULT`). */
enum class ShapeStatus(val code: Int) { SAVED(0), CLASHES(1), DUPLICATE_NAME(2), INVALID(3), DELETED(4), NOT_FOUND(5) }

/** Un widget del panel. [id] es su posición en el último [Message.WidgetLayout]. */
data class WidgetEntry(val id: Int, val kind: WidgetKind, val label: String, val icon: String)

sealed interface Message {
    data class Hello(val version: Int) : Message
    data class HelloAck(val version: Int) : Message

    /** Desplazamiento relativo en píxeles, ya acelerado. Rango i16. */
    data class Move(val dx: Int, val dy: Int) : Message {
        init {
            require(dx in Short.MIN_VALUE..Short.MAX_VALUE && dy in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    data class Button(val button: MouseButton, val pressed: Boolean) : Message

    /** Unidades de rueda (120 = un paso). Vertical positivo = arriba; horizontal positivo = derecha. Rango i16. */
    data class Scroll(val vertical: Int, val horizontal: Int) : Message {
        init {
            require(vertical in Short.MIN_VALUE..Short.MAX_VALUE && horizontal in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    /** Zoom en unidades de rueda (120 = un paso). Positivo = acercar. Rango i16. */
    data class Zoom(val delta: Int) : Message {
        init {
            require(delta in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    /** Forma reconocida (protocolo v2). */
    data class Gesture(val name: String) : Message {
        init {
            require(name.toByteArray(Charsets.UTF_8).size in 1..Protocol.MAX_GESTURE_NAME_BYTES) { "Nombre de forma inválido: $name" }
        }
    }

    data class GestureResult(val status: GestureStatus) : Message

    /** Formas con acción en el PC, para la chuleta. [context]: app activa con reglas propias, o vacío. */
    data class GestureList(val context: String, val entries: List<GestureListEntry>) : Message

    /** Toque en un widget de tipo botón (protocolo v3). */
    data class WidgetPress(val id: Int) : Message {
        init {
            require(id in 0..255)
        }
    }

    /** Volumen pedido, 0–100 (protocolo v3). */
    data class VolumeSet(val level: Int) : Message {
        init {
            require(level in 0..255)
        }
    }

    data class MediaControl(val command: MediaCommand) : Message

    /** Gesto de 3 dedos de Windows (protocolo v4). */
    data class SystemGesture(val command: SystemGestureCommand) : Message

    data class VolumeMute(val muted: Boolean) : Message

    /** Mostrar u ocultar la toolbox del PC: 4 dedos arriba / abajo (protocolo v5). */
    data class Toolbox(val show: Boolean) : Message

    /** El modo gestos se activa o termina (protocolo v5). [startTimeoutMs]: tiempo para empezar a dibujar. */
    data class GestureMode(val active: Boolean, val startTimeoutMs: Int) : Message {
        init {
            require(startTimeoutMs in 0..0xFFFF)
        }
    }

    /** Lote de puntos del trazo en curso, normalizados (protocolo v5). */
    data class StrokePoints(val points: List<StrokePoint>) : Message {
        init {
            require(points.size in 1..Protocol.MAX_POINTS_PER_BATCH)
        }
    }

    data class StrokeEnd(val result: StrokeResult) : Message

    /** Una forma propia del móvil, para que el PC la dibuje (protocolo v5). */
    data class ShapeInfo(val name: String, val samples: List<List<StrokePoint>>) : Message

    /** Fin de la lista de formas propias; [count] = cuántas se han enviado. */
    data class ShapeListEnd(val count: Int) : Message {
        init {
            require(count in 0..255)
        }
    }

    /** Respuesta del móvil a [ShapeAdd] o [ShapeDelete]. [other]: forma con la que se confunde, o vacío. */
    data class ShapeResult(val name: String, val status: ShapeStatus, val other: String) : Message

    /** El PC añade una forma grabada con el ratón. [force]: guardarla aunque se confunda con otra. */
    data class ShapeAdd(val name: String, val samples: List<List<StrokePoint>>, val force: Boolean) : Message

    data class ShapeDelete(val name: String) : Message

    /** Widgets configurados en el PC; un layout nuevo invalida los ids anteriores. */
    data class WidgetLayout(val entries: List<WidgetEntry>) : Message

    data class VolumeState(val level: Int, val muted: Boolean) : Message

    /** Lo que suena en el PC; título y artista vacíos si no suena nada. */
    data class MediaState(val playing: Boolean, val title: String, val artist: String) : Message

    /** Resultado de pulsar un widget. Mismos valores que [GestureStatus]. */
    data class WidgetResult(val id: Int, val status: GestureStatus) : Message

    data object Ping : Message
    data object Pong : Message
}

class ProtocolException(message: String) : Exception(message)

/** Tipos de tamaño fijo: type + payload. Tipos variables: type + u16 longitud + payload. Ver protocol/PROTOCOL.md. */
object MessageCodec {
    private const val HELLO = 0x01
    private const val MOVE = 0x02
    private const val BUTTON = 0x03
    private const val SCROLL = 0x04
    private const val PING = 0x05
    private const val GESTURE = 0x06
    private const val ZOOM = 0x07
    private const val WIDGET_PRESS = 0x08
    private const val VOLUME_SET = 0x09
    private const val MEDIA_COMMAND = 0x0A
    private const val VOLUME_MUTE = 0x0B
    private const val SYSTEM_GESTURE = 0x0C
    private const val TOOLBOX = 0x0D
    private const val GESTURE_MODE = 0x0E
    private const val STROKE_POINTS = 0x0F
    private const val STROKE_END = 0x10
    private const val SHAPE_INFO = 0x11
    private const val SHAPE_LIST_END = 0x12
    private const val SHAPE_RESULT = 0x13
    private const val HELLO_ACK = 0x81
    private const val PONG = 0x85
    private const val GESTURE_RESULT = 0x86
    private const val GESTURE_LIST = 0x87
    private const val WIDGET_LAYOUT = 0x88
    private const val VOLUME_STATE = 0x89
    private const val MEDIA_STATE = 0x8A
    private const val WIDGET_RESULT = 0x8B
    private const val SHAPE_ADD = 0x8C
    private const val SHAPE_DELETE = 0x8D

    /** Tamaño fijo del payload, o null si el tipo es variable. */
    fun payloadSize(type: Int): Int? = when (type) {
        HELLO, HELLO_ACK, GESTURE_RESULT, WIDGET_PRESS, VOLUME_SET, MEDIA_COMMAND, VOLUME_MUTE, SYSTEM_GESTURE,
        TOOLBOX, STROKE_END, SHAPE_LIST_END -> 1
        MOVE, SCROLL -> 4
        BUTTON, ZOOM, VOLUME_STATE, WIDGET_RESULT -> 2
        GESTURE_MODE -> 3
        PING, PONG -> 0
        GESTURE, GESTURE_LIST, WIDGET_LAYOUT, MEDIA_STATE,
        STROKE_POINTS, SHAPE_INFO, SHAPE_RESULT, SHAPE_ADD, SHAPE_DELETE -> null
        else -> throw ProtocolException("Tipo de mensaje desconocido: 0x%02X".format(type))
    }

    fun encode(message: Message): ByteArray {
        val (type, payload) = when (message) {
            is Message.Hello -> HELLO to byteArrayOf(message.version.toByte())
            is Message.HelloAck -> HELLO_ACK to byteArrayOf(message.version.toByte())
            is Message.Move -> MOVE to i16(message.dx) + i16(message.dy)
            is Message.Button -> BUTTON to byteArrayOf(message.button.code.toByte(), (if (message.pressed) 1 else 0).toByte())
            is Message.Scroll -> SCROLL to i16(message.vertical) + i16(message.horizontal)
            is Message.Zoom -> ZOOM to i16(message.delta)
            is Message.Gesture -> GESTURE to message.name.toByteArray(Charsets.UTF_8)
            is Message.GestureResult -> GESTURE_RESULT to byteArrayOf(message.status.code.toByte())
            is Message.GestureList -> GESTURE_LIST to encodeList(message)
            is Message.WidgetPress -> WIDGET_PRESS to byteArrayOf(message.id.toByte())
            is Message.VolumeSet -> VOLUME_SET to byteArrayOf(message.level.toByte())
            is Message.MediaControl -> MEDIA_COMMAND to byteArrayOf(message.command.code.toByte())
            is Message.VolumeMute -> VOLUME_MUTE to byteArrayOf((if (message.muted) 1 else 0).toByte())
            is Message.SystemGesture -> SYSTEM_GESTURE to byteArrayOf(message.command.code.toByte())
            is Message.WidgetLayout -> WIDGET_LAYOUT to encodeLayout(message)
            is Message.VolumeState -> VOLUME_STATE to byteArrayOf(
                message.level.coerceIn(0, 100).toByte(), (if (message.muted) 1 else 0).toByte(),
            )
            is Message.MediaState -> MEDIA_STATE to encodeMedia(message)
            is Message.WidgetResult -> WIDGET_RESULT to byteArrayOf(message.id.toByte(), message.status.code.toByte())
            is Message.Toolbox -> TOOLBOX to byteArrayOf((if (message.show) 1 else 0).toByte())
            is Message.GestureMode -> GESTURE_MODE to byteArrayOf((if (message.active) 1 else 0).toByte()) + u16(message.startTimeoutMs)
            is Message.StrokePoints -> STROKE_POINTS to encodePoints(message.points)
            is Message.StrokeEnd -> STROKE_END to byteArrayOf(message.result.code.toByte())
            is Message.ShapeInfo -> SHAPE_INFO to encodeShape(message.name, message.samples)
            is Message.ShapeListEnd -> SHAPE_LIST_END to byteArrayOf(message.count.toByte())
            is Message.ShapeResult -> SHAPE_RESULT to encodeShapeResult(message)
            is Message.ShapeAdd -> SHAPE_ADD to encodeShape(message.name, message.samples) + byteArrayOf((if (message.force) 1 else 0).toByte())
            is Message.ShapeDelete -> SHAPE_DELETE to encodeName(message.name)
            Message.Ping -> PING to ByteArray(0)
            Message.Pong -> PONG to ByteArray(0)
        }
        return if (payloadSize(type) == null) {
            require(payload.size <= 0xFFFF) { "Payload demasiado grande" }
            byteArrayOf(type.toByte()) + u16(payload.size) + payload
        } else {
            byteArrayOf(type.toByte()) + payload
        }
    }

    fun decode(type: Int, payload: ByteArray): Message {
        val expected = payloadSize(type)
        if (expected != null && payload.size != expected) {
            throw ProtocolException("Payload de 0x%02X debe tener %d bytes, tiene %d".format(type, expected, payload.size))
        }
        return when (type) {
            HELLO -> Message.Hello(payload.u8(0))
            HELLO_ACK -> Message.HelloAck(payload.u8(0))
            MOVE -> Message.Move(payload.i16(0), payload.i16(2))
            BUTTON -> Message.Button(
                MouseButton.entries.firstOrNull { it.code == payload.u8(0) }
                    ?: throw ProtocolException("Botón inválido: ${payload.u8(0)}"),
                when (payload.u8(1)) {
                    0 -> false
                    1 -> true
                    else -> throw ProtocolException("Booleano inválido: ${payload.u8(1)}")
                },
            )
            SCROLL -> Message.Scroll(payload.i16(0), payload.i16(2))
            ZOOM -> Message.Zoom(payload.i16(0))
            GESTURE -> {
                if (payload.size !in 1..Protocol.MAX_GESTURE_NAME_BYTES) throw ProtocolException("Nombre de forma de ${payload.size} bytes")
                Message.Gesture(utf8(payload, 0, payload.size))
            }
            GESTURE_RESULT -> Message.GestureResult(
                GestureStatus.entries.firstOrNull { it.code == payload.u8(0) }
                    ?: throw ProtocolException("Estado inválido: ${payload.u8(0)}"),
            )
            GESTURE_LIST -> decodeList(payload)
            WIDGET_PRESS -> Message.WidgetPress(payload.u8(0))
            VOLUME_SET -> Message.VolumeSet(payload.u8(0))
            MEDIA_COMMAND -> Message.MediaControl(
                MediaCommand.entries.firstOrNull { it.code == payload.u8(0) }
                    ?: throw ProtocolException("Orden multimedia inválida: ${payload.u8(0)}"),
            )
            VOLUME_MUTE -> Message.VolumeMute(bool(payload.u8(0)))
            SYSTEM_GESTURE -> Message.SystemGesture(
                SystemGestureCommand.entries.firstOrNull { it.code == payload.u8(0) }
                    ?: throw ProtocolException("Gesto de sistema inválido: ${payload.u8(0)}"),
            )
            WIDGET_LAYOUT -> decodeLayout(payload)
            VOLUME_STATE -> Message.VolumeState(
                payload.u8(0).also { if (it > 100) throw ProtocolException("Nivel de volumen inválido: $it") },
                bool(payload.u8(1)),
            )
            MEDIA_STATE -> decodeMedia(payload)
            WIDGET_RESULT -> Message.WidgetResult(
                payload.u8(0),
                GestureStatus.entries.firstOrNull { it.code == payload.u8(1) }
                    ?: throw ProtocolException("Estado inválido: ${payload.u8(1)}"),
            )
            TOOLBOX -> Message.Toolbox(bool(payload.u8(0)))
            GESTURE_MODE -> Message.GestureMode(bool(payload.u8(0)), payload.u8(1) or (payload.u8(2) shl 8))
            STROKE_POINTS -> decodePoints(payload)
            STROKE_END -> Message.StrokeEnd(
                StrokeResult.entries.firstOrNull { it.code == payload.u8(0) }
                    ?: throw ProtocolException("Resultado de trazo inválido: ${payload.u8(0)}"),
            )
            SHAPE_INFO -> decodeShape(payload, withForce = false).let { Message.ShapeInfo(it.name, it.samples) }
            SHAPE_LIST_END -> Message.ShapeListEnd(payload.u8(0))
            SHAPE_RESULT -> decodeShapeResult(payload)
            SHAPE_ADD -> decodeShape(payload, withForce = true).let { Message.ShapeAdd(it.name, it.samples, it.force) }
            SHAPE_DELETE -> decodeName(payload)
            PING -> Message.Ping
            PONG -> Message.Pong
            else -> throw ProtocolException("Tipo de mensaje desconocido: 0x%02X".format(type))
        }
    }

    private fun encodeList(m: Message.GestureList): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        str8(out, m.context)
        val entries = m.entries.take(64)
        out.write(entries.size)
        for (e in entries) {
            out.write(if (e.pinned) 1 else 0)
            str8(out, e.name)
            str8(out, e.help)
        }
        return out.toByteArray()
    }

    private fun decodeList(p: ByteArray): Message.GestureList {
        val c = Cursor(p)
        val context = c.str()
        val n = c.byte()
        val entries = List(n) {
            val flags = c.byte()
            GestureListEntry(name = c.str(), help = c.str(), pinned = flags and 1 != 0)
        }
        c.requireEnd("GESTURE_LIST")
        return Message.GestureList(context, entries)
    }

    private fun encodeLayout(m: Message.WidgetLayout): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val entries = m.entries.take(Protocol.MAX_WIDGETS)
        out.write(entries.size)
        for (e in entries) {
            out.write(e.id)
            out.write(e.kind.code)
            str8(out, e.label)
            str8(out, e.icon)
        }
        return out.toByteArray()
    }

    private fun decodeLayout(p: ByteArray): Message.WidgetLayout {
        val c = Cursor(p)
        val n = c.byte()
        if (n > Protocol.MAX_WIDGETS) throw ProtocolException("WIDGET_LAYOUT con $n widgets")
        val entries = List(n) {
            val id = c.byte()
            val kind = c.byte().let { code ->
                WidgetKind.entries.firstOrNull { it.code == code } ?: throw ProtocolException("Tipo de widget inválido: $code")
            }
            WidgetEntry(id, kind, label = c.str(), icon = c.str())
        }
        c.requireEnd("WIDGET_LAYOUT")
        return Message.WidgetLayout(entries)
    }

    private fun encodeMedia(m: Message.MediaState): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(if (m.playing) 1 else 0)
        str8(out, m.title)
        str8(out, m.artist)
        return out.toByteArray()
    }

    private fun decodeMedia(p: ByteArray): Message.MediaState {
        val c = Cursor(p)
        val playing = bool(c.byte())
        val state = Message.MediaState(playing, title = c.str(), artist = c.str())
        c.requireEnd("MEDIA_STATE")
        return state
    }

    private fun encodePoints(points: List<StrokePoint>): ByteArray {
        require(points.size <= Protocol.MAX_POINTS_PER_BATCH) { "Demasiados puntos" }
        val out = java.io.ByteArrayOutputStream()
        out.write(points.size)
        for (p in points) {
            out.write(u16(p.x))
            out.write(u16(p.y))
        }
        return out.toByteArray()
    }

    private fun Cursor.points(): List<StrokePoint> {
        val n = byte()
        return List(n) { StrokePoint(u16(), u16()) }
    }

    private fun decodePoints(p: ByteArray): Message.StrokePoints {
        val c = Cursor(p)
        val points = c.points()
        c.requireEnd("STROKE_POINTS")
        if (points.isEmpty()) throw ProtocolException("STROKE_POINTS vacío")
        return Message.StrokePoints(points)
    }

    private fun encodeShape(name: String, samples: List<List<StrokePoint>>): ByteArray {
        require(samples.size <= Protocol.MAX_SHAPE_SAMPLES) { "Demasiadas muestras" }
        val out = java.io.ByteArrayOutputStream()
        str8(out, name)
        out.write(samples.size)
        for (s in samples) out.write(encodePoints(s))
        return out.toByteArray()
    }

    private class ShapePayload(val name: String, val samples: List<List<StrokePoint>>, val force: Boolean)

    private fun decodeShape(p: ByteArray, withForce: Boolean): ShapePayload {
        val c = Cursor(p)
        val name = c.str()
        val count = c.byte()
        val samples = List(count) { c.points() }
        val force = if (withForce) bool(c.byte()) else false
        c.requireEnd(if (withForce) "SHAPE_ADD" else "SHAPE_INFO")
        return ShapePayload(name, samples, force)
    }

    private fun encodeName(name: String): ByteArray = java.io.ByteArrayOutputStream().also { str8(it, name) }.toByteArray()

    private fun decodeName(p: ByteArray): Message.ShapeDelete {
        val c = Cursor(p)
        val name = c.str()
        c.requireEnd("SHAPE_DELETE")
        return Message.ShapeDelete(name)
    }

    private fun encodeShapeResult(m: Message.ShapeResult): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        str8(out, m.name)
        out.write(m.status.code)
        str8(out, m.other)
        return out.toByteArray()
    }

    private fun decodeShapeResult(p: ByteArray): Message.ShapeResult {
        val c = Cursor(p)
        val name = c.str()
        val code = c.byte()
        val status = ShapeStatus.entries.firstOrNull { it.code == code } ?: throw ProtocolException("Estado de forma inválido: $code")
        val other = c.str()
        c.requireEnd("SHAPE_RESULT")
        return Message.ShapeResult(name, status, other)
    }

    private fun bool(value: Int): Boolean = when (value) {
        0 -> false
        1 -> true
        else -> throw ProtocolException("Booleano inválido: $value")
    }

    /** Lee un payload variable campo a campo; lanza [ProtocolException] si se queda corto. */
    private class Cursor(private val p: ByteArray) {
        private var pos = 0

        fun byte(): Int = if (pos < p.size) p[pos++].toInt() and 0xFF else throw ProtocolException("Mensaje truncado")

        fun str(): String {
            val len = byte()
            if (pos + len > p.size) throw ProtocolException("Mensaje truncado")
            return utf8(p, pos, len).also { pos += len }
        }

        fun u16(): Int = byte() or (byte() shl 8)

        fun requireEnd(name: String) {
            if (pos != p.size) throw ProtocolException("Bytes sobrantes en $name")
        }
    }

    private fun str8(out: java.io.ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var len = minOf(bytes.size, 255)
        while (len < bytes.size && len > 0 && (bytes[len].toInt() and 0xC0) == 0x80) len-- // no partir caracteres
        out.write(len)
        out.write(bytes, 0, len)
    }

    private fun utf8(bytes: ByteArray, offset: Int, length: Int): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        throw ProtocolException("Texto UTF-8 inválido")
    }

    private fun i16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    private fun u16(value: Int) = i16(value)
    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
    private fun ByteArray.i16(index: Int) = ((this[index].toInt() and 0xFF) or (this[index + 1].toInt() shl 8)).toShort().toInt()
}

/** Lee mensajes completos de un flujo. Devuelve null si el flujo termina limpiamente entre mensajes. */
class MessageReader(private val input: InputStream) {
    fun read(): Message? {
        val type = input.read()
        if (type < 0) return null
        val size = MessageCodec.payloadSize(type) ?: run {
            val len = readExactly(2, type)
            (len[0].toInt() and 0xFF) or ((len[1].toInt() and 0xFF) shl 8)
        }
        return MessageCodec.decode(type, readExactly(size, type))
    }

    private fun readExactly(size: Int, type: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = input.read(buffer, offset, size - offset)
            if (n < 0) throw ProtocolException("Flujo terminado a mitad del mensaje 0x%02X".format(type))
            offset += n
        }
        return buffer
    }
}
