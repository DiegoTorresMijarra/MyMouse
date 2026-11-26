package dev.mymouse.core.widgets

import kotlin.math.max

/**
 * Geometría del widget de volumen (docs/spec-v4.7-universo.md §4): una fila con el altavoz a la izquierda, la pista ocupando todo
 * el ancho restante y el número a la derecha, con ancho fijo para tres cifras. Si el panel es estrecho, el número pasa debajo de la
 * pista (o se oculta) antes de que la pista baje de [Params.minTrack]. Lógica pura: la vista solo dibuja lo que sale de aquí.
 */
object VolumeGeometry {
    /** Dónde va el número: a la derecha de la pista, debajo de ella o no se muestra. */
    enum class ValuePlacement { RIGHT, BELOW, HIDDEN }

    /** Medidas en píxeles. */
    data class Params(
        /** Ancho (y toque) del icono de altavoz. */
        val iconWidth: Float,
        /** Ancho fijo del número, para tres cifras. */
        val valueWidth: Float,
        val trackHeight: Float,
        val thumbRadius: Float,
        /** Largo mínimo de la pista: por debajo, el número pasa debajo o se oculta. */
        val minTrack: Float,
        /** Alto de la línea del número cuando va debajo. */
        val valueLineHeight: Float,
        val gap: Float,
    )

    /**
     * [icon]: zona del altavoz (silenciar). [bar]: zona táctil de la pista; su borde izquierdo es el nivel 0 y el derecho el 100.
     * [track]: la pista que se dibuja (las mismas x que [bar], centrada en su alto). [value]: donde va el número.
     */
    data class Parts(
        val icon: Box,
        val bar: Box,
        val track: Box,
        val value: Box,
        val valuePlacement: ValuePlacement,
        val thumbRadius: Float,
    ) {
        /** Posición x del centro del pulgar para un nivel de 0 a 100. */
        fun thumbX(level: Int): Float = bar.x + bar.width * level.coerceIn(0, 100) / 100f
    }

    fun compute(row: Box, p: Params): Parts {
        val icon = Box(row.x, row.y, p.iconWidth, row.height)
        // El pulgar sobresale medio ancho en los extremos: no puede pisar el altavoz ni el número.
        val side = p.thumbRadius + p.gap / 2
        val barX = icon.right + side

        val valueRight = Box(row.right - p.valueWidth, row.y, p.valueWidth, row.height)
        val trackWithValue = valueRight.x - side - barX
        if (trackWithValue >= p.minTrack) {
            return build(icon, barX, valueRight.x - side, row.y, row.height, row, valueRight, ValuePlacement.RIGHT, p)
        }

        val barRightFull = row.right - p.thumbRadius
        val trackFull = barRightFull - barX
        val canStack = row.height >= p.valueLineHeight + 2 * p.thumbRadius + p.gap
        if (canStack && trackFull >= p.minTrack) {
            // El número, a la derecha y debajo de la pista.
            val barH = row.height - p.valueLineHeight
            val value = Box(barX, row.y + barH, max(0f, barRightFull - barX), p.valueLineHeight)
            return build(icon, barX, barRightFull, row.y, barH, row, value, ValuePlacement.BELOW, p)
        }
        // Ni así cabe: se oculta el número y la pista se queda con todo el ancho.
        return build(icon, barX, barRightFull, row.y, row.height, row, Box(row.right, row.y, 0f, row.height), ValuePlacement.HIDDEN, p)
    }

    private fun build(
        icon: Box, barX: Float, barRight: Float, y: Float, h: Float, row: Box, value: Box, placement: ValuePlacement, p: Params,
    ): Parts {
        val width = max(0f, barRight - barX)
        val bar = Box(barX, y, width, h)
        val track = Box(barX, bar.centerY - p.trackHeight / 2, width, p.trackHeight)
        return Parts(icon, bar, track, value, placement, p.thumbRadius)
    }
}
