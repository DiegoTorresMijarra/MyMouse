package dev.mymouse.core.debug

import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.shapes.RejectReason

/** Textos fijos del modo depuración que no llevan datos. */
enum class DebugText {
    CLICK, DOUBLE_CLICK, RELEASE, RIGHT_CLICK, MIDDLE_CLICK,
    WIDGETS_SHOW, WIDGETS_HIDE, TOOLBOX_OPEN, TOOLBOX_CLOSE, KEYBOARD_OPEN, GESTURES_START, GESTURES_END,
    TASK_VIEW, SHOW_DESKTOP, APP_SWITCH_START, APP_SWITCH_NEXT, APP_SWITCH_PREVIOUS, APP_SWITCH_END,
    WIDGET_NOT_RUN,
}

/**
 * Quien pone el texto del modo depuración y de los avisos del panel de widgets: el núcleo solo decide *qué* pasó y la app
 * lo escribe en el idioma del móvil. Los tests usan una implementación propia.
 */
interface DebugTexts {
    fun text(text: DebugText): String
    fun move(px: Int): String
    fun drag(px: Int): String

    /** [axes]: las flechas con sus pasos, ya unidas (`↓ 2 → 1`). */
    fun scroll(axes: String): String
    fun zoom(positive: Boolean, steps: String): String
    fun shape(name: String, scorePercent: Int): String
    fun shapeRejected(reason: RejectReason, other: String?): String
    fun widget(label: String): String
    fun volume(level: Int): String
    fun mute(muted: Boolean): String
    fun media(command: MediaCommand): String

    /** El PC no tiene acción para el widget [label] (null: se desconoce el nombre). */
    fun widgetUnassigned(label: String?): String
}
