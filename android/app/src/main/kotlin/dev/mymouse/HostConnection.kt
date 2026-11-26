package dev.mymouse

import android.util.Log
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.Message
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
class HostConnection(private val port: Int) : Transport {

    override val supportsGestures = true

    override var onConnectionChanged: (Boolean) -> Unit = {}

    /** Lista de formas del PC (para la chuleta). Se llama desde el hilo de lectura. */
    override var onGestureList: (Message.GestureList) -> Unit = {}

    /** Respuesta del PC a una forma enviada. Se llama desde el hilo de lectura. */
    override var onGestureResult: (GestureStatus) -> Unit = {}

    /** Widgets del PC (protocolo v3). Se llaman desde el hilo de lectura. */
    override var onWidgetLayout: (Message.WidgetLayout) -> Unit = {}
    override var onVolumeState: (Message.VolumeState) -> Unit = {}
    override var onMediaState: (Message.MediaState) -> Unit = {}
    override var onWidgetResult: (Message.WidgetResult) -> Unit = {}

    /** Versión acordada con el host (0 sin conexión). Con 1, el PC no entiende el modo gestos. */
    @Volatile var protocolVersion = 0
        private set

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

    /**
     * Envía una orden del panel de widgets. Solo con versión ≥ 3: un host anterior no la entiende y cerraría la conexión.
     * Dos `VOLUME_SET` seguidos en cola se fusionan: solo importa el último nivel.
     */
    override fun sendWidget(message: Message) {
        require(message is Message.WidgetPress || message is Message.VolumeSet ||
            message is Message.MediaControl || message is Message.VolumeMute) { "No es una orden de widget: $message" }
        if (!connected || protocolVersion < Protocol.WIDGETS_VERSION) return
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
            is OutputAction.Gesture ->
                if (protocolVersion >= Protocol.GESTURES_VERSION) queue.addLast(Message.Gesture(action.name))
            is OutputAction.GestureMode, is OutputAction.GestureRejected -> Unit // solo feedback local
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
        protocolVersion = ack.version

        s.soTimeout = Protocol.IDLE_TIMEOUT_MS.toInt()
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
        if (!value) protocolVersion = 0
        if (connected == value) return
        connected = value
        onConnectionChanged(value)
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
