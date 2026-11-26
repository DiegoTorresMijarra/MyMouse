package dev.mymouse.core.protocol

import java.io.InputStream

/** Constantes del protocolo. Fuente de verdad: protocol/PROTOCOL.md. */
object Protocol {
    /** Versión más alta que conoce el cliente. 2 = modo gestos. */
    const val VERSION = 2
    const val GESTURES_VERSION = 2
    const val DEFAULT_PORT = 47800
    const val PING_INTERVAL_MS = 1_000L
    const val IDLE_TIMEOUT_MS = 3_000L
    const val HANDSHAKE_TIMEOUT_MS = 2_000L
    const val MAX_GESTURE_NAME_BYTES = 64
}

enum class MouseButton(val code: Int) { LEFT(0), RIGHT(1), MIDDLE(2) }

enum class GestureStatus(val code: Int) { EXECUTED(0), UNASSIGNED(1), ERROR(2) }

data class GestureListEntry(val name: String, val help: String, val pinned: Boolean)

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
    private const val HELLO_ACK = 0x81
    private const val PONG = 0x85
    private const val GESTURE_RESULT = 0x86
    private const val GESTURE_LIST = 0x87

    /** Tamaño fijo del payload, o null si el tipo es variable. */
    fun payloadSize(type: Int): Int? = when (type) {
        HELLO, HELLO_ACK, GESTURE_RESULT -> 1
        MOVE, SCROLL -> 4
        BUTTON, ZOOM -> 2
        PING, PONG -> 0
        GESTURE, GESTURE_LIST -> null
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
        var pos = 0
        fun byte(): Int = if (pos < p.size) p[pos++].toInt() and 0xFF else throw ProtocolException("GESTURE_LIST truncado")
        fun str(): String {
            val len = byte()
            if (pos + len > p.size) throw ProtocolException("GESTURE_LIST truncado")
            return utf8(p, pos, len).also { pos += len }
        }
        val context = str()
        val n = byte()
        val entries = List(n) {
            val flags = byte()
            GestureListEntry(name = str(), help = str(), pinned = flags and 1 != 0)
        }
        if (pos != p.size) throw ProtocolException("Bytes sobrantes en GESTURE_LIST")
        return Message.GestureList(context, entries)
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
