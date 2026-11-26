package dev.mymouse.core.protocol

import java.io.InputStream

/** Constantes del protocolo. Fuente de verdad: protocol/PROTOCOL.md. */
object Protocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 47800
    const val PING_INTERVAL_MS = 1_000L
    const val IDLE_TIMEOUT_MS = 3_000L
    const val HANDSHAKE_TIMEOUT_MS = 2_000L
}

enum class MouseButton(val code: Int) { LEFT(0), RIGHT(1), MIDDLE(2) }

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

    data object Ping : Message
    data object Pong : Message
}

class ProtocolException(message: String) : Exception(message)

object MessageCodec {
    private const val HELLO = 0x01
    private const val MOVE = 0x02
    private const val BUTTON = 0x03
    private const val SCROLL = 0x04
    private const val PING = 0x05
    private const val ZOOM = 0x07
    private const val HELLO_ACK = 0x81
    private const val PONG = 0x85

    fun payloadSize(type: Int): Int = when (type) {
        HELLO, HELLO_ACK -> 1
        MOVE, SCROLL -> 4
        BUTTON, ZOOM -> 2
        PING, PONG -> 0
        else -> throw ProtocolException("Tipo de mensaje desconocido: 0x%02X".format(type))
    }

    fun encode(message: Message): ByteArray = when (message) {
        is Message.Hello -> byteArrayOf(HELLO.toByte(), message.version.toByte())
        is Message.HelloAck -> byteArrayOf(HELLO_ACK.toByte(), message.version.toByte())
        is Message.Move -> byteArrayOf(MOVE.toByte()) + i16(message.dx) + i16(message.dy)
        is Message.Button -> byteArrayOf(
            BUTTON.toByte(), message.button.code.toByte(), (if (message.pressed) 1 else 0).toByte(),
        )
        is Message.Scroll -> byteArrayOf(SCROLL.toByte()) + i16(message.vertical) + i16(message.horizontal)
        is Message.Zoom -> byteArrayOf(ZOOM.toByte()) + i16(message.delta)
        Message.Ping -> byteArrayOf(PING.toByte())
        Message.Pong -> byteArrayOf(PONG.toByte())
    }

    fun decode(type: Int, payload: ByteArray): Message {
        val expected = payloadSize(type)
        if (payload.size != expected) {
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
            PING -> Message.Ping
            PONG -> Message.Pong
            else -> throw ProtocolException("Tipo de mensaje desconocido: 0x%02X".format(type))
        }
    }

    private fun i16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
    private fun ByteArray.i16(index: Int) = ((this[index].toInt() and 0xFF) or (this[index + 1].toInt() shl 8)).toShort().toInt()
}

/** Lee mensajes completos de un flujo. Devuelve null si el flujo termina limpiamente entre mensajes. */
class MessageReader(private val input: InputStream) {
    fun read(): Message? {
        val type = input.read()
        if (type < 0) return null
        val payload = ByteArray(MessageCodec.payloadSize(type))
        var offset = 0
        while (offset < payload.size) {
            val n = input.read(payload, offset, payload.size - offset)
            if (n < 0) throw ProtocolException("Flujo terminado a mitad del mensaje 0x%02X".format(type))
            offset += n
        }
        return MessageCodec.decode(type, payload)
    }
}

