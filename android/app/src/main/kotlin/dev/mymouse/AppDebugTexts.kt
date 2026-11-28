package dev.mymouse

import android.content.Context
import dev.mymouse.core.debug.DebugText
import dev.mymouse.core.debug.DebugTexts
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.shapes.RejectReason

/** Los textos del modo depuración y de los avisos del panel de widgets, en el idioma del móvil. */
class AppDebugTexts(private val context: Context) : DebugTexts {
    override fun text(text: DebugText): String = context.getString(when (text) {
        DebugText.CLICK -> R.string.dbg_click
        DebugText.DOUBLE_CLICK -> R.string.dbg_double_click
        DebugText.RELEASE -> R.string.dbg_release
        DebugText.RIGHT_CLICK -> R.string.dbg_right_click
        DebugText.MIDDLE_CLICK -> R.string.dbg_middle_click
        DebugText.WIDGETS_SHOW -> R.string.dbg_widgets_show
        DebugText.WIDGETS_HIDE -> R.string.dbg_widgets_hide
        DebugText.TOOLBOX_OPEN -> R.string.dbg_toolbox_open
        DebugText.TOOLBOX_CLOSE -> R.string.dbg_toolbox_close
        DebugText.KEYBOARD_OPEN -> R.string.dbg_keyboard_open
        DebugText.GESTURES_START -> R.string.dbg_gestures_start
        DebugText.GESTURES_END -> R.string.dbg_gestures_end
        DebugText.TASK_VIEW -> R.string.dbg_task_view
        DebugText.SHOW_DESKTOP -> R.string.dbg_show_desktop
        DebugText.APP_SWITCH_START -> R.string.dbg_app_switch
        DebugText.APP_SWITCH_NEXT -> R.string.dbg_app_next
        DebugText.APP_SWITCH_PREVIOUS -> R.string.dbg_app_previous
        DebugText.APP_SWITCH_END -> R.string.dbg_app_end
        DebugText.WIDGET_NOT_RUN -> R.string.dbg_widget_not_run
    })

    override fun move(px: Int) = context.getString(R.string.dbg_move, px)
    override fun drag(px: Int) = context.getString(R.string.dbg_drag, px)
    override fun scroll(axes: String) = context.getString(R.string.dbg_scroll, axes)
    override fun zoom(positive: Boolean, steps: String) = context.getString(R.string.dbg_zoom, if (positive) "+" else "−", steps)
    override fun shape(name: String, scorePercent: Int) = context.getString(R.string.dbg_shape, UiText.shape(context, name), scorePercent)
    override fun shapeRejected(reason: RejectReason, other: String?) = context.getString(R.string.dbg_shape_rejected, UiText.reject(context, reason, other))
    override fun widget(label: String) = context.getString(R.string.dbg_widget, label)
    override fun volume(level: Int) = context.getString(R.string.dbg_volume, level)
    override fun mute(muted: Boolean) = context.getString(if (muted) R.string.dbg_mute else R.string.dbg_unmute)
    override fun media(command: MediaCommand) = context.getString(when (command) {
        MediaCommand.PLAY_PAUSE -> R.string.dbg_media_play_pause
        MediaCommand.NEXT -> R.string.dbg_media_next
        MediaCommand.PREVIOUS -> R.string.dbg_media_previous
    })
    override fun widgetUnassigned(label: String?) = context.getString(R.string.dbg_widget_unassigned, label ?: context.getString(R.string.widget_default_name))
}
