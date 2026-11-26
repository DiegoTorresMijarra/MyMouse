package dev.mymouse.core.hid

/**
 * Agrupa el movimiento del ratón antes de enviarlo por Bluetooth (docs/spec-v4.5-estilo.md §4.2).
 *
 * Enviar un informe por cada `PointerMove` llena la pila Bluetooth más deprisa de lo que el enlace los vacía y el puntero va
 * acumulando retraso. Aquí el movimiento (dx, dy, rueda y desplazamiento horizontal) se suma y [tick] saca **un solo informe de
 * ratón** por tic, con el último estado de los botones. Lo que no es movimiento (cambios de botones, teclado, multimedia) sale
 * enseguida y en orden: antes se suelta el movimiento pendiente, para que un clic caiga donde estaba el puntero.
 *
 * Los informes de ratón llevan 5 bytes: botones, X, Y, rueda y pan (con signo, de −127 a 127). Un movimiento mayor se trocea en
 * varios informes, uno por tic. No es seguro entre hilos: lo usa solo el hilo emisor.
 */
class HidMotionCoalescer {
    private var dx = 0
    private var dy = 0
    private var wheel = 0
    private var pan = 0
    private var buttons = 0

    /** ¿Hay movimiento por enviar? */
    val hasPending: Boolean get() = dx != 0 || dy != 0 || wheel != 0 || pan != 0

    /** Estado de botones con el que saldrá el movimiento pendiente. */
    val buttonState: Int get() = buttons

    /**
     * Recibe un informe del [HidMapper]. Devuelve lo que hay que enviar **ahora**, en orden (puede ser nada: el movimiento puro
     * se queda acumulado hasta el siguiente [tick]).
     */
    fun offer(report: HidReport): List<HidReport> {
        if (report.id == HidMapper.MOUSE_ID && report.data.size >= MOUSE_SIZE) {
            val b = report.data[0].toInt() and 0xFF
            val x = report.data[1].toInt()
            val y = report.data[2].toInt()
            val w = report.data[3].toInt()
            val p = report.data[4].toInt()
            if (x != 0 || y != 0 || w != 0 || p != 0) {
                // Movimiento: si los botones cambiaron de golpe, lo anterior sale con el estado antiguo.
                val out = if (b != buttons) flush() else emptyList()
                buttons = b
                dx += x
                dy += y
                wheel += w
                pan += p
                return out
            }
            // Solo botones: primero el movimiento pendiente (con los botones de antes) y después el cambio.
            val out = flush()
            buttons = b
            return out + report
        }
        return flush() + report
    }

    /** Un informe con el movimiento acumulado (hasta ±127 por eje), o null si no hay nada. El resto queda para el siguiente tic. */
    fun tick(): HidReport? {
        if (!hasPending) return null
        val sx = dx.coerceIn(-MAX_STEP, MAX_STEP)
        val sy = dy.coerceIn(-MAX_STEP, MAX_STEP)
        val sw = wheel.coerceIn(-MAX_STEP, MAX_STEP)
        val sp = pan.coerceIn(-MAX_STEP, MAX_STEP)
        dx -= sx
        dy -= sy
        wheel -= sw
        pan -= sp
        return HidReport(HidMapper.MOUSE_ID, byteArrayOf(buttons.toByte(), sx.toByte(), sy.toByte(), sw.toByte(), sp.toByte()))
    }

    /** Todo el movimiento pendiente, troceado, para soltarlo antes de un cambio de botones o una tecla. */
    fun flush(): List<HidReport> {
        val out = mutableListOf<HidReport>()
        while (true) out += tick() ?: break
        return out
    }

    /** `sendReport` devolvió false: el movimiento de ese informe vuelve a la cuenta y se reintenta en el siguiente tic. */
    fun restore(report: HidReport) {
        if (report.id != HidMapper.MOUSE_ID || report.data.size < MOUSE_SIZE) return
        dx += report.data[1].toInt()
        dy += report.data[2].toInt()
        wheel += report.data[3].toInt()
        pan += report.data[4].toInt()
    }

    /** Descarta el movimiento pendiente y los botones (al desconectar). */
    fun reset() {
        dx = 0
        dy = 0
        wheel = 0
        pan = 0
        buttons = 0
    }

    private companion object {
        const val MOUSE_SIZE = 5
        const val MAX_STEP = 127
    }
}
