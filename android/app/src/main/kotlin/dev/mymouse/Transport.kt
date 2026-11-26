package dev.mymouse

import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.Message

/**
 * Cómo llegan las acciones al PC: por USB con MyMouse en el PC ([HostConnection]) o como ratón
 * Bluetooth sin nada instalado ([BluetoothHidTransport]). Los avisos pueden llegar desde otros hilos.
 */
interface Transport {
    var onConnectionChanged: (Boolean) -> Unit
    var onGestureList: (Message.GestureList) -> Unit
    var onGestureResult: (GestureStatus) -> Unit
    var onWidgetLayout: (Message.WidgetLayout) -> Unit
    var onVolumeState: (Message.VolumeState) -> Unit
    var onMediaState: (Message.MediaState) -> Unit
    var onWidgetResult: (Message.WidgetResult) -> Unit

    /** ¿Admite el modo gestos? (Las acciones de las formas viven en el PC.) */
    val supportsGestures: Boolean

    /**
     * ¿Puede ejecutar los gestos de Windows de 3 dedos? Por USB, solo con un host de protocolo ≥ 4 (se sabe al conectar);
     * por Bluetooth siempre (el móvil envía las teclas).
     */
    val supportsSystemGestures: Boolean

    fun start()
    fun stop()
    fun send(action: OutputAction)

    /** Orden del panel de widgets (`WIDGET_PRESS`, `VOLUME_SET`, `MEDIA_COMMAND`, `VOLUME_MUTE`). */
    fun sendWidget(message: Message)
}
