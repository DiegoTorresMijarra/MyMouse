package dev.mymouse.core.keyboard

/**
 * Repetición de la tecla mantenida **por cable** (docs/spec-v6-teclado.md §5.2): Windows no repite las teclas inyectadas, así que
 * el móvil repite el "pulsar" tras [delayMs] y luego cada [intervalMs]. Como en un teclado real, solo repite la última tecla pulsada.
 * Lógica pura: el llamador pasa la hora y llama a [onTick] cuando llega [nextDeadlineMs].
 */
class KeyRepeat(
    private val delayMs: Long = DELAY_MS,
    private val intervalMs: Long = INTERVAL_MS,
) {
    private var usage: Int? = null
    private var nextAt = 0L

    /** La tecla que se repite ahora, si hay alguna. */
    val active: Int? get() = usage

    /** Instante en que hay que volver a llamar a [onTick], o null si no se repite nada. */
    val nextDeadlineMs: Long? get() = if (usage != null) nextAt else null

    fun start(usage: Int, nowMs: Long) {
        this.usage = usage
        nextAt = nowMs + delayMs
    }

    /** Deja de repetir [usage] (si es la que se repetía). */
    fun stop(usage: Int) {
        if (this.usage == usage) this.usage = null
    }

    fun stopAll() {
        usage = null
    }

    /** Los "pulsar" que tocan ahora: normalmente uno; si el llamador se retrasó, los que se perdieron, hasta [MAX_CATCH_UP]. */
    fun onTick(nowMs: Long): List<Int> {
        val u = usage ?: return emptyList()
        if (nowMs < nextAt) return emptyList()
        val due = (((nowMs - nextAt) / intervalMs) + 1).toInt()
        nextAt += due * intervalMs
        return List(due.coerceAtMost(MAX_CATCH_UP)) { u }
    }

    companion object {
        const val DELAY_MS = 400L
        const val INTERVAL_MS = 40L
        const val MAX_CATCH_UP = 3
    }
}
