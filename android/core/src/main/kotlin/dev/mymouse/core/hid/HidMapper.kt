package dev.mymouse.core.hid

import dev.mymouse.core.gesture.FractionalAccumulator
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.SystemGestureCommand

/** Un informe HID: [id] y los bytes que lo siguen (sin el id), como los espera `BluetoothHidDevice.sendReport`. */
class HidReport(val id: Int, val data: ByteArray) {
    override fun equals(other: Any?) = other is HidReport && id == other.id && data.contentEquals(other.data)
    override fun hashCode() = 31 * id + data.contentHashCode()
    override fun toString() = "HidReport($id, ${data.joinToString(" ") { "%02X".format(it) }})"
}

/** Usos de la página de consumo (multimedia) que envía el panel de widgets en modo Bluetooth. */
enum class ConsumerKey(val usage: Int) {
    PLAY_PAUSE(0xCD), NEXT(0xB5), PREVIOUS(0xB6), VOLUME_UP(0xE9), VOLUME_DOWN(0xEA), MUTE(0xE2),
}

/**
 * Traduce las acciones del trackpad a informes HID (docs/spec-v3-bluetooth.md §2): ratón (id 1),
 * teclado (id 2) y consumo (id 3). Mantiene el estado de los botones y acumula el scroll y el zoom fraccionarios
 * (por Bluetooth no hay deltas finos: solo pasos enteros de rueda).
 * Los gestos de 3 dedos de Windows (docs/spec-v3.1-ajustes.md §1) se envían como atajos de teclado.
 */
class HidMapper {
    private var buttons = 0
    private val wheel = FractionalAccumulator()
    private val pan = FractionalAccumulator()
    private val zoom = FractionalAccumulator()
    private var altHeld = false

    fun map(action: OutputAction): List<HidReport> = when (action) {
        is OutputAction.PointerMove -> move(action.dx, action.dy)
        is OutputAction.Button -> {
            val bit = when (action.button) {
                MouseButton.LEFT -> 1
                MouseButton.RIGHT -> 2
                MouseButton.MIDDLE -> 4
            }
            buttons = if (action.pressed) buttons or bit else buttons and bit.inv()
            listOf(mouse(0, 0, 0, 0))
        }
        is OutputAction.Scroll -> {
            // 120 unidades = 1 paso de rueda.
            val v = wheel.add(action.vertical / WHEEL_STEP.toFloat())
            val h = pan.add(action.horizontal / WHEEL_STEP.toFloat())
            steps(v, h)
        }
        is OutputAction.Zoom -> {
            // Zoom fino: se acumula hasta completar pasos enteros de rueda.
            val n = zoom.add(action.delta / WHEEL_STEP.toFloat())
            if (n == 0) emptyList() else listOf(keyboard(MOD_LEFT_CTRL)) + steps(n, 0) + keyboard(0)
        }
        is OutputAction.SystemGesture -> system(action.command)
        is OutputAction.Gesture, is OutputAction.GestureMode, is OutputAction.GestureRejected,
        is OutputAction.StrokePoints, is OutputAction.StrokeEnd,
        is OutputAction.WidgetPanel, is OutputAction.FourFingerSwipe -> emptyList()
    }

    private fun system(command: SystemGestureCommand): List<HidReport> = when (command) {
        SystemGestureCommand.TASK_VIEW -> chord(MOD_LEFT_GUI, KEY_TAB)
        SystemGestureCommand.SHOW_DESKTOP -> chord(MOD_LEFT_GUI, KEY_D)
        // Alt se mantiene pulsado hasta APP_SWITCH_END: cada Tab (con Shift, hacia atrás) se envía con Alt puesto.
        SystemGestureCommand.APP_SWITCH_START -> {
            altHeld = true
            listOf(keyboard(MOD_LEFT_ALT))
        }
        SystemGestureCommand.APP_SWITCH_NEXT ->
            if (altHeld) listOf(keyboard(MOD_LEFT_ALT, KEY_TAB), keyboard(MOD_LEFT_ALT)) else emptyList()
        SystemGestureCommand.APP_SWITCH_PREVIOUS ->
            if (altHeld) listOf(keyboard(MOD_LEFT_ALT or MOD_LEFT_SHIFT, KEY_TAB), keyboard(MOD_LEFT_ALT)) else emptyList()
        SystemGestureCommand.APP_SWITCH_END -> {
            val wasHeld = altHeld
            altHeld = false
            if (wasHeld) listOf(keyboard(0)) else emptyList()
        }
    }

    /** Pulsar y soltar una tecla con modificadores. */
    private fun chord(modifiers: Int, key: Int) = listOf(keyboard(modifiers, key), keyboard(0))

    /** Pulsar y soltar una tecla multimedia. */
    fun consumer(key: ConsumerKey): List<HidReport> = listOf(consumerReport(key.usage), consumerReport(0))

    /** Suelta todo (al desconectar o cambiar de modo). */
    fun releaseAll(): List<HidReport> {
        buttons = 0
        altHeld = false
        wheel.reset()
        pan.reset()
        zoom.reset()
        return listOf(mouse(0, 0, 0, 0), keyboard(0), consumerReport(0))
    }

    private fun move(dx: Int, dy: Int): List<HidReport> {
        val out = mutableListOf<HidReport>()
        var x = dx
        var y = dy
        while (x != 0 || y != 0) {
            val sx = x.coerceIn(-127, 127)
            val sy = y.coerceIn(-127, 127)
            out += mouse(sx, sy, 0, 0)
            x -= sx
            y -= sy
        }
        return out
    }

    private fun steps(vertical: Int, horizontal: Int): List<HidReport> {
        val out = mutableListOf<HidReport>()
        var v = vertical
        var h = horizontal
        while (v != 0 || h != 0) {
            val sv = v.coerceIn(-127, 127)
            val sh = h.coerceIn(-127, 127)
            out += mouse(0, 0, sv, sh)
            v -= sv
            h -= sh
        }
        return out
    }

    private fun mouse(x: Int, y: Int, wheel: Int, pan: Int) =
        HidReport(MOUSE_ID, byteArrayOf(buttons.toByte(), x.toByte(), y.toByte(), wheel.toByte(), pan.toByte()))

    private fun keyboard(modifiers: Int, key: Int = 0) =
        HidReport(KEYBOARD_ID, ByteArray(8).also {
            it[0] = modifiers.toByte()
            it[2] = key.toByte()
        })

    private fun consumerReport(usage: Int) =
        HidReport(CONSUMER_ID, byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte()))

    companion object {
        const val MOUSE_ID = 1
        const val KEYBOARD_ID = 2
        const val CONSUMER_ID = 3
        private const val WHEEL_STEP = 120
        private const val MOD_LEFT_CTRL = 0x01
        private const val MOD_LEFT_SHIFT = 0x02
        private const val MOD_LEFT_ALT = 0x04
        private const val MOD_LEFT_GUI = 0x08

        /** Códigos de uso de la página de teclado. */
        private const val KEY_D = 0x07
        private const val KEY_TAB = 0x2B

        /** Descriptor HID compuesto: ratón (botones, X, Y, rueda, AC Pan), teclado y consumo. */
        val DESCRIPTOR: ByteArray = intArrayOf(
            // Ratón (id 1)
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, MOUSE_ID,
            0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x05, 0x15, 0x00, 0x25, 0x01, 0x95, 0x05, 0x75, 0x01, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
            0xC0, 0xC0,
            // Teclado (id 2)
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD_ID,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            // Consumo (id 3)
            0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, CONSUMER_ID,
            0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
            0xC0,
        ).map { it.toByte() }.toByteArray()
    }
}
