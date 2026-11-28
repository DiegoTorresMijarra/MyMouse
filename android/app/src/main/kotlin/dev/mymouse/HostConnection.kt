package dev.mymouse

import android.util.Log
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.keyboard.KeyEvent
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.theme.SyncedTheme
import dev.mymouse.core.protocol.MessageCodec
import dev.mymouse.core.protocol.MessageReader
import dev.mymouse.core.protocol.Protocol
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Conexión con el host de Windows (127.0.0.1:47800, redirigido por `adb reverse`).
 *
 * - Hilo de escritura: conecta, hace el handshake, envía la cola y un PING por segundo; reconecta cada segundo.
 * - Hilo de lectura: recibe PONG; si pasan 3 s sin nada, da la conexión por muerta.
 * - Los movimientos consecutivos en cola se fusionan, para no acumular retraso si la red se atasca.
 * - Sin conexión, las acciones se descartan (no tiene sentido reproducir entrada antigua).
 */
class HostConnection(private val port: Int, private val appVersion: String = "") : Transport {

    override val supportsGestures = true

    override val supportsSystemGestures get() = connected

    override val supportsToolbox get() = connected

    override val supportsKeyboard get() = connected

    override val hostRepeatsKeys = false

    override val keyboardUnavailableRes get() = R.string.no_pc_connection

    override val toolboxUnavailableRes get() = R.string.no_pc_connection

    override var onConnectionChanged: (Boolean) -> Unit = {}

    /** Lista de formas del PC (para la chuleta). Se llama desde el hilo de lectura. */
    override var onGestureList: (Message.GestureList) -> Unit = {}

    /** Respuesta del PC a una forma enviada. Se llama desde el hilo de lectura. */
    override var onGestureResult: (GestureStatus) -> Unit = {}

    /** Widgets del PC. Se llaman desde el hilo de lectura. */
    override var onWidgetLayout: (Message.WidgetLayout) -> Unit = {}
    override var onVolumeState: (Message.VolumeState) -> Unit = {}
    override var onMediaState: (Message.MediaState) -> Unit = {}
    override var onWidgetResult: (Message.WidgetResult) -> Unit = {}

    /** Peticiones del PC para añadir o borrar formas. Se llaman desde el hilo de lectura. */
    override var onShapeAdd: (Message.ShapeAdd) -> Unit = {}
    override var onShapeDelete: (Message.ShapeDelete) -> Unit = {}

    /** Modo teclado. Se llaman desde el hilo de lectura. */
    override var onKeyboardOpen: () -> Unit = {}
    override var onKeyboardLeds: (Int) -> Unit = {}
    override var onKeyboardLayout: (String) -> Unit = {}

    /** Tema sincronizado. */
    override var localTheme: () -> SyncedTheme? = { null }
    override var onHostTheme: (SyncedTheme) -> Unit = {}

    /** Versión de MyMouse del PC (`APP_VERSION`). `null` al desconectar. */
    override var onHostAppVersion: (String?) -> Unit = {}

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val queue = ArrayDeque<Message>()

    @Volatile private var connected = false
    @Volatile private var socket: Socket? = null

    /** Hilo activo. Un hilo antiguo que ya no es [worker] termina por su cuenta (start/stop rápidos). */
    @Volatile private var worker: Thread? = null

    private val isActive get() = worker === Thread.currentThread()

    override fun start() {
        if (worker != null) return
        val t = thread(name = "MyMouse-connection", isDaemon = true, start = false) { runLoop() }
        worker = t // asignar antes de arrancar: runLoop comprueba isActive
        t.start()
    }

    override fun stop() {
        val old = worker ?: return
        worker = null
        closeSocket(socket)
        old.interrupt()
    }

    override fun send(action: OutputAction) {
        if (!connected) return
        lock.withLock {
            enqueue(action)
            notEmpty.signal()
        }
    }

    /** Envía una orden del panel de widgets. Dos `VOLUME_SET` seguidos en cola se fusionan: solo importa el último nivel. */
    override fun sendWidget(message: Message) {
        require(message is Message.WidgetPress || message is Message.VolumeSet ||
            message is Message.MediaControl || message is Message.VolumeMute) { "No es una orden de widget: $message" }
        if (!connected) return
        lock.withLock {
            val last = queue.lastOrNull()
            if (message is Message.VolumeSet && last is Message.VolumeSet) {
                queue[queue.lastIndex] = message
            } else {
                queue.addLast(message)
            }
            notEmpty.signal()
        }
    }

    /** Una tecla (`KEY`). Nunca se fusionan ni se reordenan: el orden de pulsar y soltar importa, y la repetición son `KEY down` repetidos. */
    override fun sendKey(event: KeyEvent) {
        if (!connected) return
        lock.withLock {
            queue.addLast(Message.Key(event.usage, event.down))
            notEmpty.signal()
        }
    }

    override fun setKeyboardMode(on: Boolean) {
        if (!connected) return
        lock.withLock {
            queue.addLast(Message.KeyboardMode(on))
            notEmpty.signal()
        }
    }

    /** Lista de formas o respuestas de formas para el PC. */
    override fun sendShapes(messages: List<Message>) {
        if (!connected) return
        lock.withLock {
            queue.addAll(messages)
            notEmpty.signal()
        }
    }

    override fun sendTheme(theme: SyncedTheme) {
        if (!connected) return
        lock.withLock {
            queue.addLast(theme.toMessage())
            notEmpty.signal()
        }
    }

    private fun enqueue(action: OutputAction) {
        val last = queue.lastOrNull()
        when (action) {
            is OutputAction.PointerMove ->
                if (last is Message.Move && fitsI16(last.dx + action.dx) && fitsI16(last.dy + action.dy)) {
                    queue[queue.lastIndex] = Message.Move(last.dx + action.dx, last.dy + action.dy)
                } else {
                    queue.addLast(Message.Move(action.dx, action.dy))
                }
            is OutputAction.Scroll ->
                if (last is Message.Scroll && fitsI16(last.vertical + action.vertical) && fitsI16(last.horizontal + action.horizontal)) {
                    queue[queue.lastIndex] = Message.Scroll(last.vertical + action.vertical, last.horizontal + action.horizontal)
                } else {
                    queue.addLast(Message.Scroll(action.vertical, action.horizontal))
                }
            is OutputAction.Button -> queue.addLast(Message.Button(action.button, action.pressed))
            is OutputAction.Gesture -> queue.addLast(Message.Gesture(action.name))
            is OutputAction.SystemGesture -> queue.addLast(Message.SystemGesture(action.command))
            is OutputAction.FourFingerSwipe -> queue.addLast(Message.Toolbox(action.up))
            is OutputAction.GestureMode -> queue.addLast(Message.GestureMode(action.active, action.startTimeoutMs))
            is OutputAction.StrokePoints -> queue.addLast(Message.StrokePoints(action.points))
            is OutputAction.StrokeEnd -> queue.addLast(Message.StrokeEnd(action.result))
            // Solo feedback local.
            is OutputAction.GestureRejected, is OutputAction.WidgetPanel, is OutputAction.FourFingerDoubleTap -> Unit
            is OutputAction.Zoom ->
                if (last is Message.Zoom && fitsI16(last.delta + action.delta)) {
                    queue[queue.lastIndex] = Message.Zoom(last.delta + action.delta)
                } else {
                    queue.addLast(Message.Zoom(action.delta))
                }
        }
    }

    private fun runLoop() {
        while (isActive) {
            val s = Socket()
            try {
                session(s)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                // IOException, ProtocolException...: se reintenta.
                Log.d(TAG, "Conexión perdida: ${e.message}")
            } finally {
                closeSocket(s)
                if (isActive || worker == null) setConnected(false)
                lock.withLock { queue.clear() }
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MS)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun session(s: Socket) {
        socket = s
        s.tcpNoDelay = true
        s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)

        val out = BufferedOutputStream(s.getOutputStream())
        val reader = MessageReader(s.getInputStream())

        out.write(MessageCodec.encode(Message.Hello(Protocol.VERSION)))
        out.flush()
        s.soTimeout = Protocol.HANDSHAKE_TIMEOUT_MS.toInt()
        val ack = reader.read()
        if (ack !is Message.HelloAck || ack.version !in 1..Protocol.VERSION) throw IOException("Handshake inválido: $ack")

        s.soTimeout = Protocol.IDLE_TIMEOUT_MS.toInt()
        // Versión de la app, una sola vez justo tras el handshake, para los avisos de versión cruzados.
        if (appVersion.isNotEmpty()) {
            out.write(MessageCodec.encode(Message.AppVersion(appVersion.take(Protocol.MAX_APP_VERSION_BYTES))))
            out.flush()
        }
        // El tema del móvil: el PC manda el suyo y cada lado se queda con el cambio más reciente.
        localTheme()?.let { out.write(MessageCodec.encode(it.toMessage())); out.flush() }
        val readerThread = thread(name = "MyMouse-reader", isDaemon = true) { readLoop(s, reader) }
        setConnected(true)
        Log.i(TAG, "Conectado al host")

        try {
            var lastPing = 0L
            while (isActive && !s.isClosed) {
                val batch = takeBatch(Protocol.PING_INTERVAL_MS)
                for (m in batch) out.write(MessageCodec.encode(m))
                val now = System.nanoTime() / 1_000_000
                if (now - lastPing >= Protocol.PING_INTERVAL_MS) {
                    out.write(MessageCodec.encode(Message.Ping))
                    lastPing = now
                }
                out.flush()
            }
        } finally {
            readerThread.interrupt()
        }
    }

    private fun readLoop(s: Socket, reader: MessageReader) {
        try {
            while (!s.isClosed) {
                // Llega un PONG por segundo; cualquier silencio de 3 s salta como SocketTimeoutException.
                when (val m = reader.read() ?: break) {
                    is Message.GestureList -> onGestureList(m)
                    is Message.GestureResult -> onGestureResult(m.status)
                    is Message.WidgetLayout -> onWidgetLayout(m)
                    is Message.VolumeState -> onVolumeState(m)
                    is Message.MediaState -> onMediaState(m)
                    is Message.WidgetResult -> onWidgetResult(m)
                    is Message.ShapeAdd -> onShapeAdd(m)
                    is Message.ShapeDelete -> onShapeDelete(m)
                    is Message.HostAppVersion -> onHostAppVersion(m.version)
                    is Message.KeyboardOpen -> onKeyboardOpen()
                    is Message.KeyboardLeds -> onKeyboardLeds(m.bits)
                    is Message.KeyboardLayoutId -> onKeyboardLayout(m.id)
                    is Message.Theme -> if (m.fromHost) onHostTheme(SyncedTheme.of(m))
                    else -> Unit // PONG
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "Host sin responder")
        } catch (e: Exception) {
            Log.d(TAG, "Lectura terminada: ${e.message}")
        } finally {
            closeSocket(s) // desbloquea al hilo de escritura
        }
    }

    /** Espera hasta [timeoutMs] a que haya mensajes y devuelve todos los pendientes. */
    private fun takeBatch(timeoutMs: Long): List<Message> = lock.withLock {
        if (queue.isEmpty()) notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS)
        val batch = queue.toList()
        queue.clear()
        batch
    }

    private fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        onConnectionChanged(value)
        if (!value) onHostAppVersion(null) // el PC ya no está: sin aviso cruzado
    }

    private fun closeSocket(s: Socket?) {
        try {
            s?.close()
        } catch (e: IOException) {
        }
    }

    private fun fitsI16(v: Int) = v in Short.MIN_VALUE..Short.MAX_VALUE

    private companion object {
        const val TAG = "MyMouse"
        const val RECONNECT_DELAY_MS = 1_000L
        const val CONNECT_TIMEOUT_MS = 1_000
    }
}
