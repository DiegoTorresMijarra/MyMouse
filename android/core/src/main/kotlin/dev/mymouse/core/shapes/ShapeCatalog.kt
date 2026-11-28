package dev.mymouse.core.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Formas incluidas (docs/spec-v1-gestos.md §2). Coordenadas de pantalla: y crece hacia abajo.
 * Las líneas no tienen plantilla: se reconocen por geometría.
 */
object ShapeCatalog {

    data class Shape(
        val id: String,
        /** Trazo de referencia, en el orden en que se dibuja. Vacío para las líneas. */
        val points: List<P>,
        /** Cerrada: da igual el punto de inicio y el sentido. */
        val closed: Boolean = false,
    )

    const val LINE_RIGHT = "line-right"
    const val LINE_LEFT = "line-left"
    const val LINE_UP = "line-up"
    const val LINE_DOWN = "line-down"
    val LINES = setOf(LINE_RIGHT, LINE_LEFT, LINE_UP, LINE_DOWN)

    val ALL: List<Shape> = listOf(
        Shape(LINE_RIGHT, listOf(P(0f, 0.5f), P(1f, 0.5f))),
        Shape(LINE_LEFT, listOf(P(1f, 0.5f), P(0f, 0.5f))),
        Shape(LINE_UP, listOf(P(0.5f, 1f), P(0.5f, 0f))),
        Shape(LINE_DOWN, listOf(P(0.5f, 0f), P(0.5f, 1f))),
        Shape("v", listOf(P(0f, 0f), P(0.5f, 1f), P(1f, 0f))),
        Shape("caret", listOf(P(0f, 1f), P(0.5f, 0f), P(1f, 1f))),
        Shape("lt", listOf(P(0.8f, 0f), P(0f, 0.5f), P(0.8f, 1f))),
        Shape("gt", listOf(P(0f, 0f), P(0.8f, 0.5f), P(0f, 1f))),
        Shape("circle", circle(), closed = true),
        Shape("triangle", listOf(P(0.5f, 0f), P(0f, 0.9f), P(1f, 0.9f), P(0.5f, 0f)), closed = true),
        Shape("square", listOf(P(0f, 0f), P(0f, 1f), P(1f, 1f), P(1f, 0f), P(0f, 0f)), closed = true),
        Shape("check", listOf(P(0f, 0.55f), P(0.3f, 1f), P(1f, 0f))),
        Shape("z", listOf(P(0f, 0f), P(1f, 0f), P(0f, 1f), P(1f, 1f))),
        Shape("s", sCurve()),
        Shape("l", listOf(P(0f, 0f), P(0f, 1f), P(0.6f, 1f))),
    )

    val BY_ID: Map<String, Shape> = ALL.associateBy { it.id }

    val IDS: List<String> = ALL.map { it.id }

    private fun circle(): List<P> = List(33) { i ->
        // Empieza arriba y gira en sentido antihorario, como se suele dibujar una "O".
        val a = -PI / 2 - 2 * PI * i / 32
        P((0.5 + 0.5 * cos(a)).toFloat(), (0.5 + 0.5 * sin(a)).toFloat())
    }

    private fun sCurve(): List<P> = listOf(
        P(0.9f, 0.1f), P(0.6f, 0f), P(0.3f, 0f), P(0.1f, 0.12f), P(0.12f, 0.35f),
        P(0.5f, 0.5f), P(0.88f, 0.65f), P(0.9f, 0.88f), P(0.7f, 1f), P(0.4f, 1f), P(0.1f, 0.9f),
    )
}
