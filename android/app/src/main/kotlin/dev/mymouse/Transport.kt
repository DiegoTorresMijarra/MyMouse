package dev.mymouse

import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.keyboard.KeyEvent
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.theme.SyncedTheme

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

    /** El PC pide añadir o borrar una forma (protocolo v5). Se llaman desde el hilo de lectura. */
    var onShapeAdd: (Message.ShapeAdd) -> Unit
    var onShapeDelete: (Message.ShapeDelete) -> Unit

    /** Versión de MyMouse del PC (`APP_VERSION`, protocolo v6), o null al perder la conexión. Solo por USB. Se llama desde el hilo de lectura. */
    var onHostAppVersion: (String?) -> Unit

    /** El PC pide abrir el teclado (`KEYBOARD_OPEN`, protocolo v7; o el widget Teclado por Bluetooth). Puede llamarse desde otro hilo. */
    var onKeyboardOpen: () -> Unit

    /** Luces del teclado del PC (`KEYBOARD_LEDS`: 1 = Mayús, 2 = Num, 4 = Despl.) o del informe de salida de Bluetooth. Desde otro hilo. */
    var onKeyboardLeds: (Int) -> Unit

    /** Distribución activa del PC (`KEYBOARD_LAYOUT`: `es` o `us`), solo por USB. Desde otro hilo. */
    var onKeyboardLayout: (String) -> Unit

    /** Tema del móvil que se envía al PC nada más conectar (`THEME`, protocolo v8). Solo por USB. Se llama desde el hilo de conexión. */
    var localTheme: () -> SyncedTheme?

    /** Tema del PC (`THEME`, protocolo v8). Solo por USB. Se llama desde el hilo de lectura. */
    var onHostTheme: (SyncedTheme) -> Unit

    /** ¿Admite el modo gestos? (Las acciones de las formas viven en el PC.) */
    val supportsGestures: Boolean

    /**
     * ¿Puede ejecutar los gestos de Windows de 3 dedos? Por USB, solo con un host de protocolo ≥ 4 (se sabe al conectar);
     * por Bluetooth siempre (el móvil envía las teclas).
     */
    val supportsSystemGestures: Boolean

    /**
     * ¿Hay toolbox en el PC? Por USB, solo con un host de protocolo ≥ 5 (se sabe al conectar); por Bluetooth no.
     * De ello dependen `TOOLBOX`, el gesto en vivo y la sincronización de formas.
     */
    val supportsToolbox: Boolean

    /** ¿Admite el modo teclado? Por USB, solo con un host de protocolo ≥ 7 (se sabe al conectar); por Bluetooth siempre. */
    val supportsKeyboard: Boolean

    /** Por Bluetooth la repetición de teclas la hace Windows; por USB, el móvil (docs/spec-v6-teclado.md §5.2). */
    val hostRepeatsKeys: Boolean

    /** Qué decirle al usuario cuando no se puede abrir el teclado ([supportsKeyboard] = false). */
    val keyboardUnavailableText: String

    /** Qué decirle al usuario cuando 4 dedos ↑ / ↓ no pueden abrir la toolbox ([supportsToolbox] = false). */
    val toolboxUnavailableText: String

    fun start()
    fun stop()
    fun send(action: OutputAction)

    /** Orden del panel de widgets (`WIDGET_PRESS`, `VOLUME_SET`, `MEDIA_COMMAND`, `VOLUME_MUTE`). */
    fun sendWidget(message: Message)

    /** Pulsar o soltar una tecla física (`KEY`, protocolo v7; por Bluetooth, el informe de teclado). Se descarta sin conexión o sin [supportsKeyboard]. */
    fun sendKey(event: KeyEvent)

    /** El móvil entra o sale del modo teclado (`KEYBOARD_MODE`). Por Bluetooth, al salir se envía el informe vacío del teclado. */
    fun setKeyboardMode(on: Boolean)

    /** Lista de formas propias (`SHAPE_INFO`… `SHAPE_LIST_END`) o respuestas `SHAPE_RESULT`. Se descartan si [supportsToolbox] es false. */
    fun sendShapes(messages: List<Message>)

    /** Se cambió el tema en el móvil: se manda al PC (`THEME`, v8). Se descarta sin conexión o con un PC anterior, y por Bluetooth. */
    fun sendTheme(theme: SyncedTheme)
}
