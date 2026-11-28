package dev.mymouse

import android.content.Context
import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.hid.HidSendStats
import dev.mymouse.core.keyboard.KeySize
import dev.mymouse.core.keyboard.KeyboardLayoutSetting
import dev.mymouse.core.shapes.RejectReason
import dev.mymouse.core.shapes.ShapeCatalog
import kotlin.math.roundToInt

/**
 * Los textos que el núcleo identifica solo con un id (formas, temas, distribuciones, teclas…). El núcleo no tiene idioma:
 * aquí cada id se traduce con los recursos del móvil (`values/` en inglés y `values-es/`).
 */
object UiText {
    /** Nombre de una forma: las del catálogo con texto lo traducen; las demás (V, Z, ✓…) son símbolos y se muestran tal cual. */
    fun shape(ctx: Context, id: String): String = when (id) {
        "line-right" -> ctx.getString(R.string.shape_line_right)
        "line-left" -> ctx.getString(R.string.shape_line_left)
        "line-up" -> ctx.getString(R.string.shape_line_up)
        "line-down" -> ctx.getString(R.string.shape_line_down)
        "circle" -> ctx.getString(R.string.shape_circle)
        "triangle" -> ctx.getString(R.string.shape_triangle)
        "square" -> ctx.getString(R.string.shape_square)
        else -> ShapeCatalog.BY_ID[id]?.let { symbolOf(it.id) } ?: id.removePrefix(ShapeStore.CUSTOM_PREFIX)
    }

    private fun symbolOf(id: String): String = when (id) {
        "v" -> "V"; "caret" -> "Λ"; "lt" -> "<"; "gt" -> ">"; "check" -> "✓"; "z" -> "Z"; "s" -> "S"; "l" -> "L"
        else -> id
    }

    /** Por qué no se reconoció una forma, en el idioma del móvil. */
    fun reject(ctx: Context, reason: RejectReason, other: String?): String = when (reason) {
        RejectReason.EMPTY_STROKE -> ctx.getString(R.string.reject_empty_stroke)
        RejectReason.TOO_SMALL -> ctx.getString(R.string.reject_too_small)
        RejectReason.NO_ACTIVE_SHAPES -> ctx.getString(R.string.reject_no_active_shapes)
        RejectReason.NOT_RECOGNIZED -> ctx.getString(R.string.reject_not_recognized)
        RejectReason.TIMEOUT -> ctx.getString(R.string.reject_timeout)
        RejectReason.CANCELLED -> ctx.getString(R.string.reject_cancelled)
        RejectReason.NO_RECOGNIZER -> ctx.getString(R.string.reject_no_recognizer)
        RejectReason.AMBIGUOUS -> ctx.getString(R.string.reject_ambiguous, other?.let { shape(ctx, it) }.orEmpty())
    }

    fun theme(ctx: Context, id: String, fallback: String): String = when (id) {
        "universe" -> ctx.getString(R.string.theme_universe)
        "midnight" -> ctx.getString(R.string.theme_midnight)
        "graphite" -> ctx.getString(R.string.theme_graphite)
        "ocean" -> ctx.getString(R.string.theme_ocean)
        "aurora" -> ctx.getString(R.string.theme_aurora)
        "oled" -> ctx.getString(R.string.theme_oled)
        "nebula" -> ctx.getString(R.string.theme_nebula)
        else -> fallback
    }

    fun accent(ctx: Context, id: String, fallback: String): String = when (id) {
        "steel" -> ctx.getString(R.string.accent_steel)
        "lavender" -> ctx.getString(R.string.accent_lavender)
        "turquoise" -> ctx.getString(R.string.accent_turquoise)
        "green" -> ctx.getString(R.string.accent_green)
        "amber" -> ctx.getString(R.string.accent_amber)
        "pink" -> ctx.getString(R.string.accent_pink)
        "white" -> ctx.getString(R.string.accent_white)
        else -> fallback
    }

    fun layout(ctx: Context, id: String): String = when (id) {
        "es" -> ctx.getString(R.string.layout_es)
        "us" -> ctx.getString(R.string.layout_us)
        else -> id
    }

    fun layoutSetting(ctx: Context, setting: KeyboardLayoutSetting): String = when (setting) {
        KeyboardLayoutSetting.AUTO -> ctx.getString(R.string.layout_auto)
        KeyboardLayoutSetting.ES -> layout(ctx, "es")
        KeyboardLayoutSetting.US -> layout(ctx, "us")
    }

    fun keySize(ctx: Context, size: KeySize): String = ctx.getString(when (size) {
        KeySize.SMALL -> R.string.size_small
        KeySize.MEDIUM -> R.string.size_medium
        KeySize.LARGE -> R.string.size_large
    })

    /** Etiqueta de una tecla del selector del editor de atajos. */
    fun key(ctx: Context, key: HidKeys.Key): String = when (key.name) {
        "Home" -> ctx.getString(R.string.key_Home)
        "End" -> ctx.getString(R.string.key_End)
        "PageUp" -> ctx.getString(R.string.key_PageUp)
        "PageDown" -> ctx.getString(R.string.key_PageDown)
        "Delete" -> ctx.getString(R.string.key_Delete)
        "Backspace" -> ctx.getString(R.string.key_Backspace)
        "Enter" -> ctx.getString(R.string.key_Enter)
        "Space" -> ctx.getString(R.string.key_Space)
        "PrintScreen" -> ctx.getString(R.string.key_PrintScreen)
        else -> key.label
    }

    /** Etiqueta de un bloque del teclado (`arrows`, `nav`…) para la barra de estado. */
    fun keyboardBlock(ctx: Context, id: String): String = when (id) {
        "arrows" -> ctx.getString(R.string.kbblock_arrows)
        "nav" -> ctx.getString(R.string.kbblock_nav)
        "num" -> ctx.getString(R.string.kbblock_num)
        "numonly" -> ctx.getString(R.string.kbblock_numonly)
        "fn" -> ctx.getString(R.string.kbblock_fn)
        else -> id
    }

    fun media(ctx: Context, key: ConsumerKey): String = when (BtActions.mediaOption(key).name) {
        "PlayPause" -> ctx.getString(R.string.media_PlayPause)
        "Next" -> ctx.getString(R.string.media_Next)
        "Previous" -> ctx.getString(R.string.media_Previous)
        "VolumeUp" -> ctx.getString(R.string.media_VolumeUp)
        "VolumeDown" -> ctx.getString(R.string.media_VolumeDown)
        "Mute" -> ctx.getString(R.string.media_Mute)
        else -> ""
    }

    /** Un atajo como se ve en pantalla: `Ctrl+Inicio`. */
    fun keys(ctx: Context, keys: BtAction.Keys): String = buildList {
        if (keys.modifiers and HidKeys.MOD_CTRL != 0) add("Ctrl")
        if (keys.modifiers and HidKeys.MOD_SHIFT != 0) add("Shift")
        if (keys.modifiers and HidKeys.MOD_ALT != 0) add("Alt")
        if (keys.modifiers and HidKeys.MOD_GUI != 0) add("Win")
        if (keys.key != 0) add(HidKeys.PICKER.firstOrNull { it.usage == keys.key }?.let { key(ctx, it) } ?: "0x%02X".format(keys.key))
    }.joinToString("+")

    /** Texto corto de lo que hace una acción por Bluetooth. */
    fun action(ctx: Context, action: BtAction): String = when (action) {
        is BtAction.Keys -> keys(ctx, action)
        is BtAction.Media -> media(ctx, action.key)
        is BtAction.Text -> ctx.getString(R.string.action_write,
            action.text.let { if (it.length > 20) it.take(20) + "…" else it }.replace('\n', ' '))
        BtAction.Keyboard -> ctx.getString(R.string.action_open_keyboard)
    }

    /** "120 rep/s · 8 ms" mientras haya envíos, o "en reposo". */
    fun statsLabel(ctx: Context, s: HidSendStats.Snapshot): String = when {
        s.reportsPerSecond == 0 -> ctx.getString(R.string.stats_idle)
        s.avgIntervalMs == null -> ctx.getString(R.string.stats_label_no_interval, s.reportsPerSecond)
        else -> ctx.getString(R.string.stats_label, s.reportsPerSecond, (s.avgIntervalMs ?: 0.0).roundToInt())
    }

    /** Texto largo para el diagnóstico. */
    fun statsDetail(ctx: Context, s: HidSendStats.Snapshot): String {
        if (s.reportsPerSecond == 0) return ctx.getString(R.string.stats_detail_idle)
        fun ms(v: Double?) = v?.let { ((it * 10).roundToInt() / 10.0).toString() } ?: "—"
        val failures = if (s.failures > 0) ctx.getString(R.string.stats_detail_failures, s.failures) else ""
        return ctx.getString(R.string.stats_detail, s.reportsPerSecond, ms(s.avgIntervalMs), ms(s.avgSendMs), failures)
    }
}
