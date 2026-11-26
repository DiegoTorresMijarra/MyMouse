package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.MouseButton

/** Evento táctil abstracto (independiente de Android). Coordenadas en píxeles; tiempo en ms monotónicos. */
sealed interface TouchInput {
    val timeMs: Long

    data class Down(val id: Int, val x: Float, val y: Float, override val timeMs: Long) : TouchInput
    /**
     * Nueva posición de un dedo. Android entrega todos los dedos a la vez; al pasarlos uno a uno,
     * [endOfSample] = false indica que aún faltan dedos de la misma muestra (así no se decide
     * entre scroll y zoom con un estado a medias).
     */
    data class Move(
        val id: Int, val x: Float, val y: Float, override val timeMs: Long, val endOfSample: Boolean = true,
    ) : TouchInput
    data class Up(val id: Int, val x: Float, val y: Float, override val timeMs: Long) : TouchInput
    data class Cancel(override val timeMs: Long) : TouchInput
}

/** Acción decidida por el intérprete, lista para enviarse al host. */
sealed interface OutputAction {
    data class PointerMove(val dx: Int, val dy: Int) : OutputAction
    data class Button(val button: MouseButton, val pressed: Boolean) : OutputAction
    data class Scroll(val vertical: Int, val horizontal: Int) : OutputAction

    /** Unidades de rueda, múltiplos de 120. Positivo = acercar. */
    data class Zoom(val delta: Int) : OutputAction
}

/** Parámetros del trackpad. Valores por defecto según docs/spec-v0.md. */
data class TrackpadConfig(
    val touchSlopPx: Float = 8f,
    val tapTimeoutMs: Long = 180,
    val doubleTapTimeoutMs: Long = 250,
    val sensitivity: Float = 1.0f,
    val acceleration: Float = 0.6f,
    /** Velocidad (px/ms) a partir de la cual empieza la aceleración. */
    val accelThresholdPxPerMs: Float = 0.3f,
    val maxGain: Float = 3.0f,
    /** Unidades de rueda por píxel de desplazamiento del centroide. */
    val scrollSpeed: Float = 2.0f,
    /** true = el contenido sigue al dedo (como en el móvil). */
    val naturalScroll: Boolean = true,
    /** Arrastrar con doble toque. Si se desactiva, el clic no se retrasa. */
    val tapToDrag: Boolean = true,
    /** Cambio mínimo de la distancia entre 2 dedos para empezar el zoom. */
    val pinchSlopPx: Float = 24f,
    /** Cambio de distancia entre dedos que equivale a un paso de zoom (120 unidades). */
    val zoomStepPx: Float = 40f,
)
