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
        Message.WidgetPress(3) to "08 03",
        Message.VolumeSet(55) to "09 37",
        Message.MediaControl(MediaCommand.NEXT) to "0A 01",
        Message.VolumeMute(true) to "0B 01",
        Message.SystemGesture(SystemGestureCommand.TASK_VIEW) to "0C 00",
        Message.SystemGesture(SystemGestureCommand.APP_SWITCH_START) to "0C 02",
        Message.SystemGesture(SystemGestureCommand.APP_SWITCH_END) to "0C 05",
        Message.VolumeState(40, false) to "89 28 00",
        Message.WidgetResult(2, GestureStatus.EXECUTED) to "8B 02 00",
        Message.WidgetLayout(listOf(WidgetEntry(0, WidgetKind.VOLUME, "", ""))) to "88 05 00 01 00 02 00 00",
        Message.MediaState(true, "A", "B") to "8A 05 00 01 01 41 01 42",
        Message.Toolbox(true) to "0D 01",
        Message.Toolbox(false) to "0D 00",
        Message.GestureMode(true, 3000) to "0E 01 B8 0B",
        Message.GestureMode(false, 0) to "0E 00 00 00",
        Message.StrokePoints(listOf(StrokePoint(0, 0), StrokePoint(65535, 32768))) to "0F 09 00 02 00 00 00 00 FF FF 00 80",
        Message.StrokeEnd(StrokeResult.RECOGNIZED) to "10 00",
        Message.StrokeEnd(StrokeResult.TIMED_OUT) to "10 03",
        Message.ShapeInfo("a", listOf(listOf(StrokePoint(1, 2), StrokePoint(3, 4)))) to "11 0C 00 01 61 01 02 01 00 02 00 03 00 04 00",
        Message.ShapeListEnd(3) to "12 03",
        Message.ShapeResult("a", ShapeStatus.CLASHES, "z") to "13 05 00 01 61 01 01 7A",
        Message.ShapeResult("a", ShapeStatus.SAVED, "") to "13 04 00 01 61 00 00",
        Message.ShapeAdd("a", listOf(listOf(StrokePoint(1, 2), StrokePoint(3, 4))), force = true) to
            "8C 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 01",
        Message.ShapeDelete("a") to "8D 02 00 01 61",
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
        for (hex in listOf("06 00 00", "06 02 00 7A", "06 01 00 FF", "86 03", "87 02 00 00 01", "87 03 00 00 00 99", "FF", "02 05 00", "03 03 01", "03 00 02", "07 78",
            "0A 03", "0B 02", "0C 06", "0C", "89 65 00", "8B 02 03", "88 05 00 01 00 03 00 00", "88 06 00 01 00 02 00 00 99",
            "88 04 00 01 00 02 00", "8A 04 00 01 01 41 01", "8A 05 00 02 01 41 01 42", "08",
            "0D 02", "0E 02 00 00", "0F 01 00 00", "0F 02 00 00 00", "0F 05 00 02 00 00 00 00", "10 04", "11 02 00 01 61", "11 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 00",
            "13 05 00 01 61 06 01 7A", "8C 0C 00 01 61 01 02 01 00 02 00 03 00 04 00", "8C 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 02", "8D 02 00 01",
            "12")) {
            assertFailsWith<ProtocolException>(hex) { MessageReader(ByteArrayInputStream(hex(hex))).read() }
        }
    }

    @Test
    fun volumeSetAbove100IsKeptAsIs() {
        // El recorte a 100 lo hace el host, no el protocolo.
        assertContentEquals(hex("09 C8"), MessageCodec.encode(Message.VolumeSet(200)))
    }

    @Test
    fun layoutTextIsTruncatedWithoutBreakingCharacters() {
        val bytes = MessageCodec.encode(Message.WidgetLayout(listOf(WidgetEntry(0, WidgetKind.BUTTON, "é".repeat(200), ""))))
        val decoded = MessageReader(ByteArrayInputStream(bytes)).read() as Message.WidgetLayout
        assertEquals("é".repeat(127), decoded.entries[0].label)
    }

    @Test
    fun shapeWithAccentsAndManySamplesRoundTrips() {
        val sample = List(32) { StrokePoint(it * 2000, 65535 - it * 2000) }
        val m = Message.ShapeInfo("custom:café", List(5) { sample })
        assertEquals(m, MessageReader(ByteArrayInputStream(MessageCodec.encode(m))).read())
    }

    @Test
    fun strokePointsRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { StrokePoint(65536, 0) }
        assertFailsWith<IllegalArgumentException> { Message.StrokePoints(emptyList()) }
    }

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
