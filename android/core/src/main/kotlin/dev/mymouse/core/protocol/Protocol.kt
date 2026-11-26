package dev.mymouse.core.protocol

import java.io.InputStream

/** Constantes del protocolo. Fuente de verdad: protocol/PROTOCOL.md. */
object Protocol {
    /** Versión más alta que conoce el cliente. 2 = modo gestos, 3 = panel de widgets, 4 = gestos de Windows. */
    const val VERSION = 4
    const val GESTURES_VERSION = 2

    /** Primera versión con los mensajes del panel de widgets. */
    const val WIDGETS_VERSION = 3
    const val MAX_WIDGETS = 32

    /** Primera versión con `SYSTEM_GESTURE` (gestos de 3 dedos de Windows por USB). */
    const val SYSTEM_GESTURE_VERSION = 4
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
    private const val HELLO_ACK = 0x81
    private const val PONG = 0x85
    private const val GESTURE_RESULT = 0x86
    private const val GESTURE_LIST = 0x87
    private const val WIDGET_LAYOUT = 0x88
    private const val VOLUME_STATE = 0x89
    private const val MEDIA_STATE = 0x8A
    private const val WIDGET_RESULT = 0x8B

    /** Tamaño fijo del payload, o null si el tipo es variable. */
    fun payloadSize(type: Int): Int? = when (type) {
        HELLO, HELLO_ACK, GESTURE_RESULT, WIDGET_PRESS, VOLUME_SET, MEDIA_COMMAND, VOLUME_MUTE, SYSTEM_GESTURE -> 1
        MOVE, SCROLL -> 4
        BUTTON, ZOOM, VOLUME_STATE, WIDGET_RESULT -> 2
        PING, PONG -> 0
        GESTURE, GESTURE_LIST, WIDGET_LAYOUT, MEDIA_STATE -> null
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
