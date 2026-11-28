package dev.mymouse.core.gesture

import dev.mymouse.core.protocol.MouseButton
import dev.mymouse.core.protocol.StrokePoint
import dev.mymouse.core.protocol.StrokeResult
import dev.mymouse.core.protocol.SystemGestureCommand
import dev.mymouse.core.shapes.RejectReason

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

    /** Unidades de rueda (120 = un paso), con deltas finos (no múltiplos de 120). Positivo = acercar. */
    data class Zoom(val delta: Int) : OutputAction

    /** Gesto de 3 dedos de Windows (vista de tareas, escritorio, cambio de app). Ver docs/spec-v3.1-ajustes.md §1. */
    data class SystemGesture(val command: SystemGestureCommand) : OutputAction

    /** Forma reconocida en el modo gestos: se envía al PC. */
    data class Gesture(val name: String, val score: Float) : OutputAction

    /**
     * El modo gestos empieza o termina. Se envía al PC (`GESTURE_MODE`) y sirve de feedback local.
     * [startTimeoutMs]: al empezar, tiempo que se espera a que se empiece a dibujar (lo rellena [TrackpadEngine]).
     */
    data class GestureMode(val active: Boolean, val startTimeoutMs: Int = 0) : OutputAction

    /** Lote de puntos del trazo en curso, normalizados (`STROKE_POINTS`): como mucho uno cada 16 ms. */
    data class StrokePoints(val points: List<StrokePoint>) : OutputAction

    /** Fin del trazo (`STROKE_END`). Precede a [Gesture] si se reconoció. */
    data class StrokeEnd(val result: StrokeResult) : OutputAction

    /** Doble toque con 4 dedos: abre el teclado (docs/spec-v6-teclado.md §3). Solo local, no se envía. */
    data object FourFingerDoubleTap : OutputAction

    // ---- Solo locales (feedback en el móvil, no se envían) ----

    /** Trazo dibujado en el modo gestos que no se ha reconocido (o se ha cancelado). */
    data class GestureRejected(val reason: RejectReason, val other: String? = null) : OutputAction

    /** 4 dedos juntándose ([show] = true: mostrar el panel de widgets) o separándose (ocultarlo). */
    data class WidgetPanel(val show: Boolean) : OutputAction

    /** Deslizar 4 dedos arriba ([up] = true) o abajo: mostrar u ocultar la toolbox del PC (`TOOLBOX`). */
    data class FourFingerSwipe(val up: Boolean) : OutputAction
}

/** Parámetros del trackpad. Valores por defecto según docs/spec-v0.md y docs/spec-v3.1-ajustes.md. */
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
    /** Cambio relativo mínimo de la distancia entre 2 dedos para decidir el zoom (8 %). */
    val pinchRatio: Float = 0.08f,
    /** Ya en scroll, cambio relativo de la distancia (desde que empezó) que lo convierte en zoom (25 %). */
    val scrollToZoomRatio: Float = 0.25f,
    /** Velocidad del zoom: multiplicador sobre [ZOOM_UNITS_PER_LN] (×0,5 a ×4). */
    val zoomSpeed: Float = 1.5f,
    /** Intervalo mínimo entre dos envíos de zoom; el resto se acumula. */
    val zoomIntervalMs: Long = 16,
    /** Dedos que activan el modo gestos: 4, o 0 = desactivado. */
    val gestureFingers: Int = 4,
    /** Tiempo máximo entre el primer y el último dedo del gesto (también para contar los dedos). */
    val gestureWindowMs: Long = 150,
    /** Tiempo que hay que mantener los dedos quietos para activar. */
    val gestureHoldMs: Long = 300,
    /** Tras levantar los dedos de la activación, tiempo para empezar a dibujar. */
    val gestureStrokeTimeoutMs: Long = 3_000,
    /** Gestos de Windows con 3 dedos (vista de tareas, escritorio, cambio de app). */
    val windowsGestures: Boolean = true,
    /** Recorrido del centro de los dedos que decide un deslizamiento de 3 o 4 dedos. */
    val multiSwipePx: Float = 48f,
    /** Cambio relativo de la distancia media al centro que decide un pellizco de 3 o 4 dedos (20 %). */
    val multiPinchRatio: Float = 0.20f,
    /** Recorrido horizontal por cada paso del cambio de app (Tab). */
    val appSwitchStepPx: Float = 120f,
    /** Inercia al hacer scroll con 2 dedos. */
    val scrollInertia: Boolean = true,
    /** Vida media de la inercia: cada tanto tiempo la velocidad se reduce a la mitad. */
    val inertiaHalfLifeMs: Long = 300,
    /** Velocidad mínima (unidades de rueda por ms) para que haya inercia al levantar los dedos. */
    val inertiaMinVelocity: Float = 0.2f,
    /** Intervalo entre envíos durante la inercia. */
    val inertiaTickMs: Long = 16,
    /** Doble toque con 4 dedos: tiempo máximo desde que se levanta el primer toque hasta que empieza el segundo (docs/spec-v6-teclado.md §3). */
    val fourFingerDoubleTapMs: Long = 350,
) {
    companion object {
        /**
         * Unidades de rueda por unidad de `ln(distancia / distancia inicial)` con [zoomSpeed] = 1.
         * Equivale a 120 unidades por cada 40 px con los dedos a unos 200 px (ln 1,2 ≈ 0,18 → ~660).
         */
        const val ZOOM_UNITS_PER_LN = 660f
    }
}
