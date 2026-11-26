package dev.mymouse.core.widgets

/**
 * Tiempos de la animación del panel de widgets (docs/spec-v3.1-ajustes.md §1.1). Lógica pura: la vista solo
 * aplica los valores. El deslizamiento del panel y el encogimiento del trackpad usan [SLIDE_MS]; al aparecer,
 * los widgets entran con un fundido escalonado por filas ([rowAlpha]).
 */
object PanelReveal {
    /** Duración del deslizamiento al mostrar el panel. */
    const val SLIDE_IN_MS = 280L

    /** Al ocultarlo, un poco más rápido (acelera). */
    const val SLIDE_OUT_MS = 220L

    /** Retraso entre una fila y la siguiente. */
    const val STAGGER_MS = 30L

    /** Duración del fundido de cada fila. */
    const val FADE_MS = 140L

    /** Opacidad (0 a 1) de la fila [row] cuando han pasado [elapsedMs] desde que empezó a mostrarse el panel. */
    fun rowAlpha(row: Int, elapsedMs: Long): Float {
        val t = elapsedMs - row * STAGGER_MS
        return (t.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    /** Tiempo total hasta que la última de [rows] filas es del todo visible (y acaba también el deslizamiento). */
    fun totalMs(rows: Int): Long = maxOf(SLIDE_IN_MS, (rows - 1).coerceAtLeast(0) * STAGGER_MS + FADE_MS)
}
