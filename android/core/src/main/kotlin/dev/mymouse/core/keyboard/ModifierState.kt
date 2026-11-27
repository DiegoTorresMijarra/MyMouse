package dev.mymouse.core.keyboard

/** Pulsar (`down` = true) o soltar una tecla física por su uso HID (página 0x07). Es lo que se envía al PC. */
data class KeyEvent(val usage: Int, val down: Boolean)

/**
 * Modificadores inteligentes del teclado (docs/spec-v6-teclado.md §5.1): Shift, Ctrl, Alt, AltGr y Win.
 * - **Un toque**: queda en espera ([Mode.PENDING]) y se aplica a la siguiente tecla normal; se suelta cuando esa tecla se suelta.
 * - **Doble toque** (el segundo en menos de [doubleTapMs]): queda fijado ([Mode.LOCKED]) hasta otro toque.
 * - **Mantener** mientras se pulsa otra tecla: como en un teclado normal; se suelta al levantar el dedo.
 *
 * El modificador **baja al tocarlo** (se devuelve su [KeyEvent]) y sube cuando corresponde. Lógica pura, sin tiempo propio: el
 * llamador pasa la marca de tiempo en ms.
 */
class ModifierState(
    private val doubleTapMs: Long = DOUBLE_TAP_MS,
    private val longHoldMs: Long = LONG_HOLD_MS,
) {
    enum class Mode {
        OFF,

        /** El dedo está sobre la tecla. */
        HELD,

        /** Un toque: espera a la siguiente tecla normal. */
        PENDING,

        /** Doble toque: fijado. */
        LOCKED,
    }

    private class Slot {
        var mode = Mode.OFF
        var touching = false
        var usedWhileHeld = false
        var changedAt = 0L

        /** El toque actual ya hizo su efecto al bajar (fijar o quitar el fijado): al levantar no hace nada. */
        var consumed = false
    }

    private val slots = HashMap<Int, Slot>()

    private fun slot(usage: Int) = slots.getOrPut(usage) { Slot() }

    fun mode(usage: Int): Mode = slots[usage]?.mode ?: Mode.OFF

    /** ¿Está el modificador bajado en el PC (en cualquier modo)? */
    fun isDown(usage: Int): Boolean = mode(usage) != Mode.OFF

    /** Los modificadores bajados, en el orden de sus usos. */
    fun downUsages(): List<Int> = slots.filter { it.value.mode != Mode.OFF }.keys.sorted()

    /** Se toca un modificador (el dedo baja). */
    fun onModifierDown(usage: Int, nowMs: Long): List<KeyEvent> {
        require(KeyUsages.isModifier(usage)) { "No es un modificador: $usage" }
        val s = slot(usage)
        if (s.mode == Mode.HELD) return emptyList() // segundo dedo sobre la misma tecla: se ignora
        s.touching = true
        s.consumed = false
        s.usedWhileHeld = false
        return when (s.mode) {
            Mode.OFF -> {
                s.mode = Mode.HELD
                s.changedAt = nowMs
                listOf(KeyEvent(usage, true))
            }
            Mode.PENDING -> {
                s.consumed = true
                if (nowMs - s.changedAt <= doubleTapMs) {
                    s.mode = Mode.LOCKED
                    emptyList()
                } else {
                    s.mode = Mode.OFF
                    listOf(KeyEvent(usage, false))
                }
            }
            Mode.LOCKED -> {
                s.consumed = true
                s.mode = Mode.OFF
                listOf(KeyEvent(usage, false))
            }
            Mode.HELD -> emptyList()
        }
    }

    /** Se levanta el dedo de un modificador. */
    fun onModifierUp(usage: Int, nowMs: Long): List<KeyEvent> {
        val s = slots[usage] ?: return emptyList()
        if (!s.touching) return emptyList()
        s.touching = false
        if (s.consumed || s.mode != Mode.HELD) return emptyList()
        // Usado con otra tecla, o mantenido sin más: se suelta como en un teclado normal. Un toque corto queda en espera.
        if (s.usedWhileHeld || nowMs - s.changedAt > longHoldMs) {
            s.mode = Mode.OFF
            return listOf(KeyEvent(usage, false))
        }
        s.mode = Mode.PENDING
        s.changedAt = nowMs
        return emptyList()
    }

    /** Baja una tecla normal: los modificadores con el dedo encima quedan "usados" (se soltarán al levantar el dedo). */
    fun onNormalKeyDown() {
        for (s in slots.values) if (s.mode == Mode.HELD) s.usedWhileHeld = true
    }

    /** Sube una tecla normal (o termina un clic de la barra de trackpad): se sueltan los modificadores en espera. */
    fun onNormalKeyUp(): List<KeyEvent> = releasePending()

    private fun releasePending(): List<KeyEvent> {
        val out = mutableListOf<KeyEvent>()
        for ((usage, s) in slots.entries.sortedBy { it.key }) {
            if (s.mode == Mode.PENDING) {
                s.mode = Mode.OFF
                out += KeyEvent(usage, false)
            }
        }
        return out
    }

    /** ¿Hay algún modificador en espera, fijado o con el dedo encima? (Por ejemplo, para Ctrl+clic en la barra de trackpad.) */
    val anyDown: Boolean get() = slots.values.any { it.mode != Mode.OFF }

    /** Suelta todo (al salir del modo teclado, perder la conexión o cambiar de bloques). */
    fun releaseAll(): List<KeyEvent> {
        val out = mutableListOf<KeyEvent>()
        for ((usage, s) in slots.entries.sortedBy { it.key }) {
            if (s.mode != Mode.OFF) out += KeyEvent(usage, false)
            s.mode = Mode.OFF
            s.touching = false
            s.consumed = false
            s.usedWhileHeld = false
        }
        return out
    }

    companion object {
        /** Máximo entre el primer toque y el segundo para fijar un modificador. */
        const val DOUBLE_TAP_MS = 400L

        /** Un modificador mantenido más tiempo que esto sin pulsar otra tecla no queda en espera al soltarlo. */
        const val LONG_HOLD_MS = 600L
    }
}
