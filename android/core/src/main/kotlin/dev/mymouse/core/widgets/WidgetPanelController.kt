package dev.mymouse.core.widgets

import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.WidgetKind

/** Deja pasar como mucho un aviso cada [intervalMs]; sirve para no inundar al PC con `VOLUME_SET` al arrastrar. */
class SendThrottle(private val intervalMs: Long) {
    private var last: Long? = null

    fun tryAcquire(nowMs: Long): Boolean {
        val l = last
        if (l != null && nowMs - l < intervalMs) return false
        last = nowMs
        return true
    }

    fun reset() {
        last = null
    }
}

/** Qué ha tocado el dedo en el panel; [cell] es la posición en [WidgetLayout.Result.cells]. */
sealed interface WidgetTarget {
    val cell: Int

    data class Button(override val cell: Int) : WidgetTarget
    data class MediaButton(override val cell: Int, val command: MediaCommand) : WidgetTarget
    data class MuteIcon(override val cell: Int) : WidgetTarget
    data class VolumeBar(override val cell: Int) : WidgetTarget
}

/** Lo que el panel pide enviar al PC. */
sealed interface WidgetEvent {
    val target: WidgetTarget

    /** Botón pulsado (al levantar el dedo dentro). [id] es el del protocolo. */
    data class Press(override val target: WidgetTarget, val id: Int) : WidgetEvent

    data class Media(override val target: WidgetTarget, val command: MediaCommand) : WidgetEvent

    /** Nivel pedido; [final] es el último al soltar el dedo (siempre se envía, aunque el límite de 50 ms lo frenaría). */
    data class SetVolume(override val target: WidgetTarget, val level: Int, val final: Boolean) : WidgetEvent

    /** Pulsar el icono: silenciar o quitar el silencio (decide quien conoce el estado). */
    data class ToggleMute(override val target: WidgetTarget) : WidgetEvent
}

/**
 * Máquina de toques del panel (docs/spec-v2-widgets.md §2). Sigue un solo dedo:
 * - Toque en un botón o en ⏮ ⏯ ⏭ / silencio: se ejecuta al **levantar** dentro del mismo sitio; si el dedo sale, se cancela.
 * - Arrastrar en vertical más de [slopPx] hace scroll (si el contenido no cabe) y cancela el toque.
 * - Sobre la barra de volumen, el dedo la arrastra desde el primer momento (sin scroll) y envía el nivel como mucho cada 50 ms y al soltar.
 * Las coordenadas son las de la vista del panel (sin scroll); el controlador suma el scroll.
 */
class WidgetPanelController(
    private val slopPx: Float,
    volumeIntervalMs: Long = VOLUME_INTERVAL_MS,
) {
    private val throttle = SendThrottle(volumeIntervalMs)

    private var layout = WidgetLayout.Result.EMPTY
    private var viewHeight = 0f

    /** Desplazamiento vertical actual del contenido, en píxeles. */
    var scrollY = 0f
        private set

    /** Lo que el dedo tiene pulsado ahora mismo (para iluminarlo), o null. */
    var pressed: WidgetTarget? = null
        private set

    /** Se está arrastrando la barra de volumen. */
    var draggingVolume = false
        private set

    /** Nivel bajo el dedo mientras se arrastra el volumen. */
    var dragLevel = 0
        private set

    val scrolling get() = state == State.SCROLL

    private enum class State { IDLE, PENDING, SCROLL, VOLUME }

    private var state = State.IDLE
    private var downY = 0f
    private var downScroll = 0f
    private var dragBar: Box? = null

    fun setLayout(result: WidgetLayout.Result, viewHeight: Float) {
        layout = result
        this.viewHeight = viewHeight
        scrollY = scrollY.coerceIn(0f, maxScroll())
        cancel()
    }

    private fun maxScroll() = WidgetLayout.maxScroll(layout.contentHeight, viewHeight)

    fun onDown(x: Float, y: Float, nowMs: Long): List<WidgetEvent> {
        cancel()
        downY = y
        downScroll = scrollY
        val target = hit(x, y + scrollY)
        if (target is WidgetTarget.VolumeBar) {
            val bar = layout.cells[target.cell].volume!!.bar
            state = State.VOLUME
            draggingVolume = true
            dragBar = bar
            pressed = target
            throttle.reset()
            return volumeEvent(target, bar, x, nowMs, final = false)
        }
        state = State.PENDING
        pressed = target
        return emptyList()
    }

    fun onMove(x: Float, y: Float, nowMs: Long): List<WidgetEvent> {
        when (state) {
            State.VOLUME -> {
                val bar = dragBar ?: return emptyList()
                return volumeEvent(pressed ?: return emptyList(), bar, x, nowMs, final = false)
            }
            State.PENDING -> {
                if (kotlin.math.abs(y - downY) > slopPx && maxScroll() > 0f) {
                    state = State.SCROLL
                    pressed = null
                    scrollY = (downScroll - (y - downY)).coerceIn(0f, maxScroll())
                } else if (pressed != null && hit(x, y + scrollY) != pressed) {
                    pressed = null // el dedo se ha ido del botón: se cancela
                }
            }
            State.SCROLL -> scrollY = (downScroll - (y - downY)).coerceIn(0f, maxScroll())
            State.IDLE -> Unit
        }
        return emptyList()
    }

    fun onUp(x: Float, y: Float, nowMs: Long): List<WidgetEvent> {
        val result = when (state) {
            State.VOLUME -> {
                val bar = dragBar
                val target = pressed
                if (bar != null && target != null) volumeEvent(target, bar, x, nowMs, final = true) else emptyList()
            }
            State.PENDING -> {
                val target = pressed
                if (target != null && hit(x, y + scrollY) == target) listOfNotNull(toEvent(target)) else emptyList()
            }
            else -> emptyList()
        }
        clear()
        return result
    }

    /** El sistema ha cancelado el toque: se suelta sin enviar nada (salvo el último volumen si se estaba arrastrando). */
    fun onCancel(nowMs: Long): List<WidgetEvent> {
        val result = if (state == State.VOLUME) {
            val bar = dragBar
            val target = pressed
            if (bar != null && target != null) listOf(WidgetEvent.SetVolume(target, dragLevel, final = true)) else emptyList()
        } else emptyList()
        clear()
        return result
    }

    private fun cancel() = clear()

    private fun clear() {
        state = State.IDLE
        pressed = null
        draggingVolume = false
        dragBar = null
    }

    private fun volumeEvent(target: WidgetTarget, bar: Box, x: Float, nowMs: Long, final: Boolean): List<WidgetEvent> {
        dragLevel = WidgetLayout.volumeLevelAt(bar, x)
        if (!final && !throttle.tryAcquire(nowMs)) return emptyList()
        return listOf(WidgetEvent.SetVolume(target, dragLevel, final))
    }

    private fun toEvent(target: WidgetTarget): WidgetEvent? = when (target) {
        is WidgetTarget.Button -> WidgetEvent.Press(target, layout.cells[target.cell].entry.id)
        is WidgetTarget.MediaButton -> WidgetEvent.Media(target, target.command)
        is WidgetTarget.MuteIcon -> WidgetEvent.ToggleMute(target)
        is WidgetTarget.VolumeBar -> null
    }

    /** Qué hay bajo el punto ([x], [contentY]) del contenido (con el scroll ya sumado). */
    fun hit(x: Float, contentY: Float): WidgetTarget? {
        layout.cells.forEachIndexed { i, cell ->
            if (!cell.box.contains(x, contentY)) return@forEachIndexed
            return when (cell.entry.kind) {
                WidgetKind.BUTTON -> WidgetTarget.Button(i)
                WidgetKind.MEDIA -> cell.media!!.let {
                    when {
                        it.previous.contains(x, contentY) -> WidgetTarget.MediaButton(i, MediaCommand.PREVIOUS)
                        it.playPause.contains(x, contentY) -> WidgetTarget.MediaButton(i, MediaCommand.PLAY_PAUSE)
                        it.next.contains(x, contentY) -> WidgetTarget.MediaButton(i, MediaCommand.NEXT)
                        else -> null
                    }
                }
                WidgetKind.VOLUME -> cell.volume!!.let {
                    when {
                        it.icon.contains(x, contentY) -> WidgetTarget.MuteIcon(i)
                        it.bar.contains(x, contentY) -> WidgetTarget.VolumeBar(i)
                        else -> null
                    }
                }
            }
        }
        return null
    }

    companion object {
        /** Spec §2.1: el volumen se envía como mucho cada 50 ms mientras se arrastra. */
        const val VOLUME_INTERVAL_MS = 50L
    }
}
