package dev.mymouse.core.hid

import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.KeyboardLayout
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
 * (sin el multiplicador de resolución del PC, por Bluetooth solo hay pasos enteros de rueda; con él, unidades finas de 1/120).
 * Los gestos de 3 dedos de Windows (docs/spec-v3.1-ajustes.md §1) se envían como atajos de teclado.
 */
class HidMapper(
    /** Estado del scroll fino (docs/spec-v5-bluetooth-completo.md §4): lo cambia el PC con un `SET_REPORT` *feature* ([applyScrollFeature]). */
    val resolution: ScrollResolution = ScrollResolution(),
) {
    private var buttons = 0
    private val wheel = FractionalAccumulator()
    private val pan = FractionalAccumulator()
    private val zoom = FractionalAccumulator()
    private var altHeld = false
    private var ctrlHeldForZoom = false

    fun map(action: OutputAction): List<HidReport> {
        // Con scroll fino, el zoom deja Ctrl pulsado entre un gesto y el siguiente: cualquier otra cosa lo suelta antes.
        val release = if (action is OutputAction.Zoom) emptyList() else endZoom()
        return release + mapAction(action)
    }

    private fun mapAction(action: OutputAction): List<HidReport> = when (action) {
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
            // 120 unidades = 1 paso de rueda. Con el multiplicador del PC activo, una unidad del informe es 1/120 de paso.
            val v = wheel.add(action.vertical * resolution.wheelMultiplier / WHEEL_STEP.toFloat())
            val h = pan.add(action.horizontal * resolution.panMultiplier / WHEEL_STEP.toFloat())
            steps(v, h)
        }
        is OutputAction.Zoom -> zoom(action.delta)
        is OutputAction.SystemGesture -> system(action.command)
        is OutputAction.Gesture, is OutputAction.GestureMode, is OutputAction.GestureRejected,
        is OutputAction.StrokePoints, is OutputAction.StrokeEnd,
        is OutputAction.WidgetPanel, is OutputAction.FourFingerSwipe -> emptyList()
    }

    private fun zoom(delta: Int): List<HidReport> {
        // Zoom fino: se acumula hasta completar unidades enteras (pasos de rueda; o 1/120 con el scroll fino del PC).
        val n = zoom.add(delta * resolution.wheelMultiplier / WHEEL_STEP.toFloat())
        if (n == 0) return emptyList()
        if (!resolution.fine) return listOf(keyboard(MOD_LEFT_CTRL)) + steps(n, 0) + keyboard(0)
        // Con unidades finas llegan muchas por segundo: Ctrl se queda pulsado y lo suelta [endZoom] (si no, 3 informes por unidad).
        val press = if (ctrlHeldForZoom) emptyList() else listOf(keyboard(MOD_LEFT_CTRL))
        ctrlHeldForZoom = true
        return press + steps(n, 0)
    }

    /** ¿Está Ctrl pulsado por un zoom fino? Quien envía debe llamar a [endZoom] cuando el zoom se detiene (unos 150 ms sin zoom). */
    val zoomHeld: Boolean get() = ctrlHeldForZoom

    /** Suelta el Ctrl del zoom fino, si estaba pulsado. */
    fun endZoom(): List<HidReport> {
        if (!ctrlHeldForZoom) return emptyList()
        ctrlHeldForZoom = false
        return listOf(keyboard(0))
    }

    /**
     * El PC ha escrito el informe *feature* del multiplicador (`SET_REPORT`): se aplica y se descarta lo acumulado, que estaba en
     * otras unidades. Devuelve true si cambió el multiplicador.
     */
    fun applyScrollFeature(data: ByteArray): Boolean {
        val changed = resolution.applyFeature(data)
        if (changed) resetScrollAccumulators()
        return changed
    }

    /** Al desconectar, el multiplicador vuelve a ×1 hasta que el PC lo active otra vez. */
    fun resetScrollResolution(): Boolean {
        val changed = resolution.reset()
        if (changed) resetScrollAccumulators()
        return changed
    }

    private fun resetScrollAccumulators() {
        wheel.reset()
        pan.reset()
        zoom.reset()
    }

    /**
     * Ejecuta una acción guardada en el móvil (docs/spec-v5-bluetooth-completo.md §2): un atajo (pulsar y soltar), una tecla multimedia
     * o texto tecleado con la distribución [layout] (lo que no existe en ella se salta).
     */
    fun action(action: BtAction, layout: KeyboardLayout): List<HidReport> = endZoom() + when (action) {
        is BtAction.Keys -> chord(action.modifiers, action.key)
        is BtAction.Media -> consumer(action.key)
        is BtAction.Text -> typeText(action.text, layout)
    }

    /** Teclea [text]: por cada carácter, sus pulsaciones (pulsar y soltar). Los saltos de línea son Intro. */
    fun typeText(text: String, layout: KeyboardLayout): List<HidReport> =
        layout.typeStrokes(text).flatMap { listOf(keyboard(it.modifiers, it.usage), keyboard(0)) }

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
        ctrlHeldForZoom = false
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

        /** Descriptor antiguo (sin scroll fino). El que se registra según el ajuste es [HidDescriptor.build]. */
        val DESCRIPTOR: ByteArray = HidDescriptor.build(fineScroll = false)
    }
}
