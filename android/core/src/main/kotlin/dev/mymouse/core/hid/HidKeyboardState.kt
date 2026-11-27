package dev.mymouse.core.hid

import dev.mymouse.core.keyboard.KeyEvent

/**
 * Estado del informe de teclado HID (id 2) para el modo teclado por Bluetooth (docs/spec-v6-teclado.md §2): los modificadores
 * (usos `0xE0`–`0xE7` = bits 0–7) y hasta seis teclas normales a la vez. Más de seis teclas normales se ignoran.
 */
class HidKeyboardState {
    private var modifiers = 0
    private val keys = ArrayList<Int>(MAX_KEYS)

    /** Aplica un evento y devuelve el informe nuevo, o null si no cambia nada (tecla de más de seis o ya en ese estado). */
    fun apply(event: KeyEvent): HidReport? {
        val u = event.usage
        if (u in 0xE0..0xE7) {
            val bit = 1 shl (u - 0xE0)
            val next = if (event.down) modifiers or bit else modifiers and bit.inv()
            if (next == modifiers) return null
            modifiers = next
            return report()
        }
        if (u !in 1..0xDF) return null
        if (event.down) {
            // Un "pulsar" repetido de una tecla que ya está abajo no cambia el informe.
            if (u in keys) return null
            if (keys.size >= MAX_KEYS) return null
            keys += u
        } else {
            if (!keys.remove(u)) return null
        }
        return report()
    }

    /** El informe vacío (todo suelto) y el estado a cero. Siempre se envía: al salir del modo teclado o al desconectar. */
    fun releaseAll(): HidReport {
        modifiers = 0
        keys.clear()
        return report()
    }

    val isEmpty: Boolean get() = modifiers == 0 && keys.isEmpty()

    private fun report() = HidReport(HidMapper.KEYBOARD_ID, ByteArray(8).also {
        it[0] = modifiers.toByte()
        keys.forEachIndexed { i, k -> it[2 + i] = k.toByte() }
    })

    companion object {
        const val MAX_KEYS = 6
    }
}

/** Informe de salida de LEDs del teclado HID (docs/spec-v6-teclado.md §5.4). */
object HidLeds {
    /** Bits del informe de salida (los de la página de LEDs): 0 = Num, 1 = Mayús, 2 = Despl. */
    private const val HID_NUM = 1
    private const val HID_CAPS = 2
    private const val HID_SCROLL = 4

    /**
     * Convierte el informe de salida recibido (con o sin el id del teclado delante) en los bits del protocolo
     * (1 = Mayús, 2 = Num, 4 = Despl.). Devuelve null si no es un informe de LEDs válido.
     */
    fun toProtocolBits(data: ByteArray): Int? {
        val byte = when {
            data.size == 1 -> data[0].toInt() and 0xFF
            data.size == 2 && (data[0].toInt() and 0xFF) == HidMapper.KEYBOARD_ID -> data[1].toInt() and 0xFF
            else -> return null
        }
        return (if (byte and HID_CAPS != 0) 1 else 0) or (if (byte and HID_NUM != 0) 2 else 0) or (if (byte and HID_SCROLL != 0) 4 else 0)
    }
}
