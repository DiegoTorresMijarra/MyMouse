package dev.mymouse.core.protocol

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Vectores de protocol/PROTOCOL.md. Si cambian allí, deben cambiar aquí (y en .NET). */
class MessageCodecTest {
    private val vectors = listOf(
        Message.Hello(1) to "01 01",
        Message.HelloAck(1) to "81 01",
        Message.Move(5, -3) to "02 05 00 FD FF",
        Message.Move(-300, 1000) to "02 D4 FE E8 03",
        Message.Button(MouseButton.LEFT, true) to "03 00 01",
        Message.Button(MouseButton.RIGHT, false) to "03 01 00",
        Message.Scroll(120, -40) to "04 78 00 D8 FF",
        Message.Zoom(-120) to "07 88 FF",
        Message.Gesture("z") to "06 01 00 7A",
        Message.GestureResult(GestureStatus.UNASSIGNED) to "86 01",
        Message.GestureList("", listOf(GestureListEntry("z", "Deshacer", pinned = true))) to
            "87 0E 00 00 01 01 01 7A 08 44 65 73 68 61 63 65 72",
        Message.Ping to "05",
        Message.Pong to "85",
    )

    @Test
    fun encodeMatchesSpecVectors() {
        for ((message, hex) in vectors) {
            assertContentEquals(hex(hex), MessageCodec.encode(message), "encode $message")
        }
    }

    @Test
    fun readMatchesSpecVectors() {
        for ((message, hex) in vectors) {
            val reader = MessageReader(ByteArrayInputStream(hex(hex)))
            assertEquals(message, reader.read())
            assertNull(reader.read())
        }
    }

    @Test
    fun invalidInputThrows() {
        for (hex in listOf("06 00 00", "06 02 00 7A", "06 01 00 FF", "86 03", "87 02 00 00 01", "87 03 00 00 00 99", "FF", "02 05 00", "03 03 01", "03 00 02", "07 78")) {
            assertFailsWith<ProtocolException>(hex) { MessageReader(ByteArrayInputStream(hex(hex))).read() }
        }
    }

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
