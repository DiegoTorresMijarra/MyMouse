package dev.mymouse.core.hid

/**
 * Estado del *Resolution Multiplier* de la rueda y de la rueda horizontal (docs/spec-v5-bluetooth-completo.md §4).
 *
 * El descriptor con scroll fino ([HidDescriptor.build]) declara un informe *feature* de un byte: los bits 0–1 son el multiplicador
 * de la rueda y los bits 2–3 el de la rueda horizontal (valor lógico 0 = ×1, 1 = ×[FINE_MULTIPLIER]). Windows lo activa con un
 * `SET_REPORT` *feature*. Con el multiplicador activo, una unidad de rueda es 1/120 de paso y las unidades de `OutputAction.Scroll`
 * (120 = un paso) pasan casi tal cual; sin él, un paso entero por cada 120 unidades.
 */
class ScrollResolution {
    /** Unidades de rueda por paso que entiende el PC: 1 (pasos enteros) o [FINE_MULTIPLIER]. */
    var wheelMultiplier: Int = 1
        private set
    var panMultiplier: Int = 1
        private set

    /** ¿Ha activado el PC el scroll fino de la rueda? (También es lo que usa el zoom, que es Ctrl + rueda.) */
    val fine: Boolean get() = wheelMultiplier > 1

    /** Contenido del informe *feature* (sin el id): lo que se contesta a `GET_REPORT`. */
    fun featureData(): ByteArray {
        val wheel = if (wheelMultiplier > 1) 1 else 0
        val pan = if (panMultiplier > 1) 1 else 0
        return byteArrayOf((wheel or (pan shl 2)).toByte())
    }

    /**
     * Aplica el contenido de un `SET_REPORT` *feature*. Acepta el byte solo o precedido del id del informe (según la versión de
     * Android). Devuelve true si cambió algo; false si no cambió o los datos no son válidos.
     */
    fun applyFeature(data: ByteArray): Boolean {
        if (data.isEmpty() || data.size > 2) return false
        if (data.size == 2 && (data[0].toInt() and 0xFF) != HidMapper.MOUSE_ID) return false
        val value = data.last().toInt() and 0xFF
        return set(if (value and 0b0011 == 1) FINE_MULTIPLIER else 1, if ((value shr 2) and 0b0011 == 1) FINE_MULTIPLIER else 1)
    }

    /** Vuelve a pasos enteros (al desconectar: el PC lo vuelve a pedir al conectar). Devuelve true si cambió. */
    fun reset(): Boolean = set(1, 1)

    private fun set(wheel: Int, pan: Int): Boolean {
        val changed = wheel != wheelMultiplier || pan != panMultiplier
        wheelMultiplier = wheel
        panMultiplier = pan
        return changed
    }

    companion object {
        /** Multiplicador físico máximo del descriptor (el estándar de Windows). */
        const val FINE_MULTIPLIER = 120

        /** Tamaño del informe *feature* sin el id. */
        const val FEATURE_SIZE = 1
    }
}
