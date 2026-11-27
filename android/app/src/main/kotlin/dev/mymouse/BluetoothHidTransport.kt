package dev.mymouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import dev.mymouse.core.btactions.BtAction
import dev.mymouse.core.btactions.BtActions
import dev.mymouse.core.btactions.BtGestureConfig
import dev.mymouse.core.btactions.BtWidgetConfig
import dev.mymouse.core.btactions.KeyboardLayout
import dev.mymouse.core.bluetooth.BluetoothEventLog
import dev.mymouse.core.bluetooth.BluetoothFacts
import dev.mymouse.core.bluetooth.HidLinkState
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.keyboard.KeyEvent
import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidDescriptor
import dev.mymouse.core.hid.HidKeyboardState
import dev.mymouse.core.hid.HidLeds
import dev.mymouse.core.hid.HidMapper
import dev.mymouse.core.hid.HidMotionCoalescer
import dev.mymouse.core.hid.HidSendStats
import dev.mymouse.core.hid.HidReport
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.theme.SyncedTheme

/**
 * El móvil como ratón y teclado Bluetooth (docs/spec-v3-bluetooth.md): no hace falta nada en el PC.
 *
 * El registro HID es único para toda la app ([shared]) y se mantiene mientras haya alguna pantalla que lo use
 * (trackpad, emparejar): [start]/[stop] cuentan referencias y liberan el registro poco después del último [stop],
 * para que pasar de una pantalla a otra no corte la conexión.
 */
@SuppressLint("MissingPermission") // Los permisos se piden al elegir Bluetooth; sin ellos se captura SecurityException.
@RequiresApi(Build.VERSION_CODES.P) // BluetoothHidDevice existe desde Android 9.
class BluetoothHidTransport private constructor(context: Context) : Transport {

    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val mapper = HidMapper()

    /** Teclas del modo teclado que están pulsadas (informe de teclado id 2, docs/spec-v6-teclado.md §2). Solo desde el hilo principal. */
    private val keyboard = HidKeyboardState()

    /** ¿Ha escrito Windows el informe de salida de LEDs en esta conexión? Si no, el teclado del móvil supone las luces. */
    @Volatile var keyboardLedsReceived = false
        private set

    // ---- Acciones guardadas en el móvil (docs/spec-v5-bluetooth-completo.md §2 y §3) ----
    private val actionStore = BtActionStore(app)
    private var gestureConfig: BtGestureConfig = BtGestureConfig.DEFAULT
    private var widgetConfig: BtWidgetConfig = BtWidgetConfig.DEFAULT
    private var layout: KeyboardLayout = AppSettings().keyboardLayout

    /** Descriptor con el que se registró la app: con o sin scroll fino (el ajuste puede cambiar con la app registrada). */
    private var registeredFine = true

    // ---- Envío agrupado (docs/spec-v4.5-estilo.md §4.2) ----
    // Un hilo propio saca UN informe de ratón por tic con el movimiento acumulado; botones, teclado y multimedia salen enseguida y en orden.
    private val coalescer = HidMotionCoalescer()
    private val senderThread = HandlerThread("mymouse-bt-sender", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
    private val sender = Handler(senderThread.looper)
    @Volatile private var tickScheduled = false
    private var lastMouseSendAt = 0L

    /** Cadencia del envío de movimiento, en ms (6–20; 8 por defecto). */
    @Volatile var intervalMs: Long = DEFAULT_INTERVAL_MS
        set(value) {
            field = value.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        }

    /** Estadística del último segundo: informes por segundo y tiempo medio de `sendReport` (no es un ping: HID no tiene confirmaciones). */
    val stats = HidSendStats()

    /** "Bluetooth · 120 inf/s · 8 ms" mientras haya conexión: el intervalo real entre informes. */
    fun linkInfo(): String? = if (host == null) null else "Bluetooth · " + stats.snapshot(SystemClock.uptimeMillis()).label()

    private var users = 0
    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var holdUntil = 0L // elapsedRealtime hasta el que se mantiene el registro HID aunque no haya pantallas
    @Volatile private var host: BluetoothDevice? = null

    override var onConnectionChanged: (Boolean) -> Unit = {}
    override var onGestureList: (Message.GestureList) -> Unit = {}
    override var onGestureResult: (GestureStatus) -> Unit = {}
    override var onWidgetLayout: (Message.WidgetLayout) -> Unit = {}
    override var onVolumeState: (Message.VolumeState) -> Unit = {}
    override var onMediaState: (Message.MediaState) -> Unit = {}
    override var onWidgetResult: (Message.WidgetResult) -> Unit = {}
    override var onShapeAdd: (Message.ShapeAdd) -> Unit = {}
    override var onShapeDelete: (Message.ShapeDelete) -> Unit = {}
    override var onHostAppVersion: (String?) -> Unit = {}
    override var onKeyboardOpen: () -> Unit = {}
    override var onKeyboardLeds: (Int) -> Unit = {}
    override var onKeyboardLayout: (String) -> Unit = {} // Windows no dice cuál es su distribución por Bluetooth: "Automática" usa el ajuste de texto
    override val supportsKeyboard = true // informes de teclado HID
    override val hostRepeatsKeys = true // la repetición al mantener pulsada la tecla la hace Windows
    override val keyboardUnavailableText = "Sin conexión Bluetooth con el PC"
    override val supportsGestures = true // las acciones de las formas viven en el móvil (bt-gestures.json)
    override val supportsSystemGestures = true // atajos de teclado HID (HidMapper)
    override val supportsToolbox = false // sin MyMouse en el PC no hay toolbox
    override val toolboxUnavailableText = "La toolbox del PC solo funciona por USB"

    /** Registro de depuración (spec v4.5 §4): los últimos 30 eventos, para la sección "Diagnóstico". */
    val events = BluetoothEventLog()
    var eventListener: () -> Unit = {}

    /** Estado legible para la pantalla de emparejar. */
    var status: String = "Sin iniciar"
        private set(value) {
            field = value
            Log.i(TAG, "Bluetooth: $value")
            log("estado: $value")
            statusListener(value)
        }
    var statusListener: (String) -> Unit = {}

    /** Se puede llamar desde el hilo emisor: el registro solo se toca desde el principal. */
    private fun log(text: String) {
        val time = System.currentTimeMillis()
        val add = Runnable {
            events.add(time, text)
            eventListener()
        }
        if (Looper.myLooper() === Looper.getMainLooper()) add.run() else handler.post(add)
    }

    // El emparejado lo hace Windows, no la app: se anota cada cambio para saber si el servicio HID estaba registrado en ese momento.
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            @Suppress("DEPRECATION")
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val state = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> "emparejado"
                BluetoothDevice.BOND_BONDING -> "emparejando…"
                BluetoothDevice.BOND_NONE -> "sin emparejar"
                else -> "desconocido"
            }
            val name = device?.let { nameOf(it) } ?: "?"
            log("emparejado con $name: $state (app HID registrada: ${if (registered) "sí" else "no"})")
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(bondReceiver, filter)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo escuchar el emparejado: ${e.message}")
        }
    }

    /**
     * Mantiene el registro HID al menos [millis] ms aunque ninguna pantalla lo use (por ejemplo, mientras Windows empareja y
     * salen diálogos del sistema): si se libera, Windows no ve el servicio HID al consultar el móvil.
     */
    fun holdRegistration(millis: Long) {
        holdUntil = maxOf(holdUntil, SystemClock.elapsedRealtime() + millis)
        log("registro HID mantenido ${millis / 1000} s")
        if (users == 0 && hid != null) scheduleRelease()
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(release)
        val wait = maxOf(RELEASE_DELAY_MS, holdUntil - SystemClock.elapsedRealtime())
        handler.postDelayed(release, wait)
    }

    /** Lo que el móvil sabe ahora, para evaluar las comprobaciones del diagnóstico. */
    fun facts(permissionsGranted: Boolean): BluetoothFacts {
        val a = adapter
        val preferred = preferredAddress()
        val bonded = bondedDevices().firstOrNull { it.address == preferred }
        val link = bonded?.let { device ->
            try {
                when (hid?.getConnectionState(device)) {
                    BluetoothProfile.STATE_CONNECTED -> HidLinkState.CONNECTED
                    BluetoothProfile.STATE_CONNECTING -> HidLinkState.CONNECTING
                    BluetoothProfile.STATE_DISCONNECTED -> HidLinkState.DISCONNECTED
                    else -> null
                }
            } catch (e: SecurityException) {
                null
            }
        }
        return BluetoothFacts(
            adapterPresent = a != null,
            adapterEnabled = a?.isEnabled,
            permissionsGranted = permissionsGranted,
            profileAvailable = if (hid != null) true else null,
            appRegistered = if (hid != null) registered else null,
            preferredHostName = bonded?.let { nameOf(it) } ?: preferred,
            preferredHostBonded = bonded != null,
            link = link,
            fineScrollEnabled = registeredFine,
            fineScrollActive = mapper.resolution.fine,
            keyboardLedsReceived = keyboardLedsReceived,
        )
    }

    /** "Reintentar conexión": vuelve a llamar a `connect` con el PC preferido. */
    fun retryConnection() {
        val target = preferredHost()
        if (target == null) {
            log("reintentar: no hay PC preferido emparejado")
            return
        }
        if (hid == null || !registered) log("reintentar: la app aún no está registrada como ratón")
        connect(target)
    }

    /** "Volver a registrar": anula el registro HID y lo repite. */
    fun reregister() {
        val h = hid
        if (h == null) {
            log("volver a registrar: sin perfil HID, se reinicia")
            return
        }
        try {
            if (registered) {
                log("anulando el registro HID (a petición del usuario)")
                h.unregisterApp()
                registered = false
            }
        } catch (e: SecurityException) {
            log("error al anular el registro: sin permiso")
        }
        handler.postDelayed({ register() }, 500)
    }

    val connectedHost: BluetoothDevice? get() = host

    private val release = Runnable { releaseNow() }

    /** Vuelve a leer los ajustes y los ficheros de acciones (se llama al abrir una pantalla: el editor puede haberlos cambiado). */
    fun reloadActions() {
        val settings = AppSettings.load(app)
        layout = settings.keyboardLayout
        gestureConfig = actionStore.gestures()
        widgetConfig = actionStore.widgets()
    }

    /** Lista de formas con acción (la misma forma que `GESTURE_LIST` del PC) para la hoja del modo gestos de la pantalla del trackpad. */
    fun gestureList(enabledShapes: Collection<String>): Message.GestureList =
        Message.GestureList("por Bluetooth", gestureConfig.listEntries(enabledShapes))

    /**
     * Ejecuta una acción en el PC al momento (botón "Probar" de los editores). Devuelve false si el móvil no está conectado al PC por
     * Bluetooth. Desde el hilo principal.
     */
    fun runAction(action: BtAction): Boolean {
        val device = host ?: return false
        reloadActions()
        if (action == BtAction.Keyboard) {
            onKeyboardOpen()
            return true
        }
        val reports = mapper.action(action, layout)
        sendActionReports(device, action, reports)
        return true
    }

    override fun start() {
        handler.removeCallbacks(release)
        users++
        log("pantalla en uso (usuarios: $users)")
        reloadActions()
        if (hid != null || users > 1) {
            // Si se cambió "Scroll fino" con la app ya registrada, hay que registrar el otro descriptor.
            if (hid != null && registered && registeredFine != AppSettings.load(app).btFineScroll) {
                log("el ajuste de scroll fino cambió: se vuelve a registrar el descriptor")
                reregister()
            }
            replayState()
            return
        }
        val a = adapter
        when {
            a == null -> status = "Este móvil no tiene Bluetooth"
            !a.isEnabled -> status = "El Bluetooth está apagado"
            else -> try {
                val ok = a.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        hid = proxy as BluetoothHidDevice
                        log("perfil HID disponible")
                        register()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        hid = null
                        registered = false
                        setHost(null)
                        log("registro HID perdido: el sistema cerró el perfil")
                        status = "El sistema cerró el perfil Bluetooth HID"
                    }
                }, BluetoothProfile.HID_DEVICE)
                log("getProfileProxy(HID_DEVICE) devolvió $ok")
                status = if (ok) "Iniciando el perfil Bluetooth HID…" else "Este móvil no permite hacer de ratón Bluetooth (perfil HID no disponible)"
            } catch (e: SecurityException) {
                status = "Falta el permiso de Bluetooth"
            }
        }
    }

    override fun stop() {
        if (users == 0) return
        users--
        log("pantalla liberada (usuarios: $users)")
        if (users == 0) scheduleRelease()
    }

    private fun releaseNow() {
        val h = hid ?: return
        // Se descarta lo pendiente del hilo emisor y se sueltan botones y teclas directamente.
        sender.removeCallbacksAndMessages(null)
        tickScheduled = false
        coalescer.reset()
        host?.let { device -> (mapper.releaseAll() + keyboard.releaseAll()).forEach { transmit(device, it) } }
        log("anulando el registro HID: ninguna pantalla lo usa desde hace un rato")
        try {
            if (registered) h.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo liberar el perfil HID: ${e.message}")
        }
        hid = null
        registered = false
        setHost(null)
        status = "Desconectado"
    }

    private fun register() {
        val h = hid ?: return
        registeredFine = AppSettings.load(app).btFineScroll
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "MyMouse", "Trackpad MyMouse", "MyMouse", BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.build(registeredFine),
        )
        try {
            // QoS del canal de salida (interrupción, del móvil al PC): mejor esfuerzo con poco retardo (11,25 ms).
            val outQos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, QOS_TOKEN_RATE, QOS_TOKEN_BUCKET, 0, QOS_LATENCY_US,
                BluetoothHidDeviceAppQosSettings.MAX,
            )
            val ok = h.registerApp(sdp, null, outQos, app.mainExecutor, callback)
            log("registerApp() devolvió $ok (descriptor ${if (registeredFine) "con scroll fino" else "antiguo, sin scroll fino"}; QoS de salida: mejor esfuerzo, tasa $QOS_TOKEN_RATE B/s, cubo $QOS_TOKEN_BUCKET, latencia $QOS_LATENCY_US µs; entrada: sin QoS)")
            status = if (ok) "Registrando como ratón Bluetooth…" else "No se pudo registrar como ratón Bluetooth"
        } catch (e: SecurityException) {
            status = "Falta el permiso de Bluetooth"
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidTransport.registered = registered
            log("onAppStatusChanged registered=$registered, dispositivo=${pluggedDevice?.let { nameOf(it) } ?: "ninguno"}")
            if (!registered) {
                setHost(null)
                status = "Registro Bluetooth cancelado"
                return
            }
            status = "Listo: esperando al PC"
            val target = pluggedDevice ?: preferredHost()
            if (target != null) connect(target)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val label = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "conectado"
                BluetoothProfile.STATE_CONNECTING -> "conectando"
                BluetoothProfile.STATE_DISCONNECTING -> "desconectando"
                BluetoothProfile.STATE_DISCONNECTED -> "desconectado"
                else -> "estado $state"
            }
            log("onConnectionStateChanged ${nameOf(device)}: $label")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    setHost(device)
                    savePreferredHost(device.address)
                    status = "Conectado a ${nameOf(device)}"
                }
                BluetoothProfile.STATE_CONNECTING -> status = "Conectando con ${nameOf(device)}…"
                BluetoothProfile.STATE_DISCONNECTED -> if (host == null || host?.address == device.address) {
                    setHost(null)
                    status = "Listo: esperando al PC"
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            try {
                if (type == BluetoothHidDevice.REPORT_TYPE_FEATURE) {
                    // Multiplicador de resolución del scroll (docs/spec-v5-bluetooth-completo.md §4): se contesta con el valor actual.
                    if (id.toInt() == HidMapper.MOUSE_ID && registeredFine) {
                        hid?.replyReport(device, type, id, mapper.resolution.featureData())
                        log("el PC leyó el multiplicador de scroll")
                    } else {
                        hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                    }
                    return
                }
                if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == HidMapper.KEYBOARD_ID) {
                    hid?.replyReport(device, type, id, ByteArray(HidDescriptor.LED_REPORT_SIZE)) // LEDs: sin saber más, todo apagado
                    return
                }
                // Algunos sistemas piden el estado inicial de cada informe: se responde con ceros.
                val size = when (id.toInt()) {
                    HidMapper.MOUSE_ID -> 5
                    HidMapper.KEYBOARD_ID -> 8
                    HidMapper.CONSUMER_ID -> 2
                    else -> 0
                }
                if (size > 0) hid?.replyReport(device, type, id, ByteArray(size))
                else hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            } catch (e: SecurityException) {
                Log.w(TAG, "onGetReport sin permiso")
            }
        }

        /** Windows activa el scroll fino escribiendo el informe feature del multiplicador (`SET_REPORT`) y manda las luces del teclado (salida). */
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            try {
                if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == HidMapper.KEYBOARD_ID) {
                    onKeyboardLedReport(data, "SET_REPORT")
                    hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                    return
                }
                if (type == BluetoothHidDevice.REPORT_TYPE_FEATURE && id.toInt() == HidMapper.MOUSE_ID && registeredFine) {
                    val changed = mapper.applyScrollFeature(data)
                    log("el PC escribió el multiplicador de scroll (${data.joinToString(" ") { "%02X".format(it) }}): " +
                        if (mapper.resolution.fine) "scroll fino activado" else "pasos enteros")
                    hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                    if (changed) {
                        sender.post { coalescer.dropScroll() } // lo acumulado estaba en otras unidades
                        statusListener(status)
                    }
                } else {
                    hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "onSetReport sin permiso")
            }
        }

        /** Windows también puede mandar las luces por el canal de interrupción (informe de salida del teclado). */
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (reportId.toInt() == HidMapper.KEYBOARD_ID) onKeyboardLedReport(data, "interrupción")
        }
    }

    /** Un informe de salida de LEDs del PC: Bloq Mayús, Bloq Num y Bloq Despl. (docs/spec-v6-teclado.md §5.4). */
    private fun onKeyboardLedReport(data: ByteArray, channel: String) {
        val bits = HidLeds.toProtocolBits(data)
        if (bits == null) {
            log("luces del teclado ($channel): informe no válido (${data.joinToString(" ") { "%02X".format(it) }})")
            return
        }
        if (!keyboardLedsReceived) log("luces del teclado recibidas del PC ($channel): descriptor con LEDs aceptado")
        keyboardLedsReceived = true
        onKeyboardLeds(bits)
        statusListener(status)
    }

    /** Conecta con un PC ya emparejado. */
    fun connect(device: BluetoothDevice) {
        try {
            val ok = hid?.connect(device)
            log("connect(${nameOf(device)}) devolvió $ok")
            savePreferredHost(device.address)
        } catch (e: SecurityException) {
            status = "Falta el permiso de Bluetooth"
        }
    }

    fun bondedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList().orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }

    fun nameOf(device: BluetoothDevice): String = try {
        device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    private fun setHost(device: BluetoothDevice?) {
        val changed = host?.address != device?.address
        host = device
        if (!changed) return
        stats.reset()
        // El multiplicador de scroll es de cada conexión: el PC lo vuelve a escribir al conectar. Solo se reinicia al perderla: el
        // SET_REPORT puede llegar justo antes del aviso de "conectado" y no debe borrarse.
        handler.removeCallbacks(zoomRelease)
        if (device == null) {
            keyboard.releaseAll() // las teclas del modo teclado no pueden quedarse "pulsadas" para la próxima conexión
            if (keyboardLedsReceived) log("luces del teclado: sin conexión, se vuelven a suponer")
            keyboardLedsReceived = false
            if (mapper.resetScrollResolution()) log("scroll fino desactivado (sin conexión)")
            sender.post { coalescer.reset() }
        }
        onConnectionChanged(device != null)
        if (device != null) replayState() else onWidgetLayout(Message.WidgetLayout(emptyList()))
    }

    /** Panel de widgets en modo Bluetooth: multimedia y volumen fijos más los botones propios de `bt-widgets.json` (spec v5 §3). */
    private fun replayState() {
        if (host == null) return
        onConnectionChanged(true)
        onWidgetLayout(Message.WidgetLayout(widgetConfig.entries()))
        onMediaState(Message.MediaState(false, "Multimedia del PC", "por Bluetooth"))
    }

    override fun send(action: OutputAction) {
        val device = host ?: return
        if (action is OutputAction.Gesture) {
            runGesture(device, action.name)
            return
        }
        val reports = mapper.map(action)
        if (reports.isNotEmpty()) sender.post { process(device, reports) }
        // Con scroll fino el zoom deja Ctrl pulsado: se suelta cuando el zoom se detiene.
        if (action is OutputAction.Zoom && mapper.zoomHeld) {
            handler.removeCallbacks(zoomRelease)
            handler.postDelayed(zoomRelease, ZOOM_RELEASE_MS)
        }
    }

    /** Suelta Ctrl cuando pasa un rato sin zoom (hilo principal: el mapeador solo se toca desde ahí). */
    private val zoomRelease = Runnable {
        val device = host
        val reports = mapper.endZoom()
        if (device != null && reports.isNotEmpty()) sender.post { process(device, reports) }
    }

    /** Forma reconocida: se busca su acción en `bt-gestures.json` y se teclea en el PC (spec v5 §2.3). */
    private fun runGesture(device: BluetoothDevice, name: String) {
        val binding = gestureConfig.find(name)
        if (binding == null) {
            onGestureResult(GestureStatus.UNASSIGNED)
            return
        }
        if (binding.action == BtAction.Keyboard) {
            log("forma $name → abrir el teclado del móvil")
            onKeyboardOpen()
            onGestureResult(GestureStatus.EXECUTED)
            return
        }
        val reports = mapper.action(binding.action, layout)
        log("forma $name → ${BtActions.describe(binding.action)}")
        sendActionReports(device, binding.action, reports)
        onGestureResult(GestureStatus.EXECUTED)
    }

    /** Los informes de una acción van en orden y tras el movimiento pendiente; el texto, con un hueco entre teclas para no desbordar la pila. */
    private fun sendActionReports(device: BluetoothDevice, action: BtAction, reports: List<HidReport>) {
        if (reports.isEmpty()) return
        if (action is BtAction.Text && reports.size > TEXT_BURST) {
            reports.forEachIndexed { i, report -> sender.postDelayed({ process(device, listOf(report)) }, i * TEXT_GAP_MS) }
        } else {
            sender.post { process(device, reports) }
        }
    }

    override fun sendWidget(message: Message) {
        val device = host ?: return
        if (message is Message.WidgetPress) {
            val button = widgetConfig.buttonFor(message.id)
            if (button != null && button.action == BtAction.Keyboard) {
                log("botón ${button.label} → abrir el teclado del móvil")
                onKeyboardOpen()
                onWidgetResult(Message.WidgetResult(message.id, GestureStatus.EXECUTED))
                return
            }
            if (button != null) {
                val reports = mapper.action(button.action, layout)
                log("botón ${button.label} → ${BtActions.describe(button.action)}")
                sendActionReports(device, button.action, reports)
                onWidgetResult(Message.WidgetResult(message.id, GestureStatus.EXECUTED))
                return
            }
        }
        val key = when (message) {
            is Message.MediaControl -> when (message.command) {
                MediaCommand.PLAY_PAUSE -> ConsumerKey.PLAY_PAUSE
                MediaCommand.NEXT -> ConsumerKey.NEXT
                MediaCommand.PREVIOUS -> ConsumerKey.PREVIOUS
            }
            is Message.WidgetPress -> when (message.id) {
                BtWidgetConfig.ID_VOLUME_DOWN -> ConsumerKey.VOLUME_DOWN
                BtWidgetConfig.ID_VOLUME_UP -> ConsumerKey.VOLUME_UP
                BtWidgetConfig.ID_MUTE -> ConsumerKey.MUTE
                else -> null
            }
            else -> null // sin volumen absoluto por Bluetooth
        } ?: return
        val reports = mapper.endZoom() + mapper.consumer(key)
        sender.post { process(device, reports) }
    }

    override fun sendKey(event: KeyEvent) {
        val device = host ?: return
        val report = keyboard.apply(event) ?: return
        sender.post { process(device, listOf(report)) }
    }

    override fun setKeyboardMode(on: Boolean) {
        if (on) return
        // Al salir se envía el informe vacío del teclado: nada se queda pulsado en el PC.
        val report = keyboard.releaseAll()
        val device = host ?: return
        sender.post { process(device, listOf(report)) }
    }

    override fun sendShapes(messages: List<Message>) = Unit // sin PC no hay con quién sincronizar las formas
    override fun sendTheme(theme: SyncedTheme) = Unit // ni el tema: por Bluetooth no hay MyMouse en el PC con quien hablar
    override var localTheme: () -> SyncedTheme? = { null }
    override var onHostTheme: (SyncedTheme) -> Unit = {}

    /** En el hilo emisor: lo que no es movimiento sale ya (tras soltar el movimiento pendiente) y el movimiento espera a su tic. */
    private fun process(device: BluetoothDevice, reports: List<HidReport>) {
        if (host?.address != device.address) return // se desconectó entre tanto
        for (report in reports) coalescer.offer(report).forEach { transmit(device, it) }
        if (coalescer.hasPending) scheduleTick()
    }

    private fun scheduleTick() {
        if (tickScheduled) return
        tickScheduled = true
        val wait = (lastMouseSendAt + intervalMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        sender.postDelayed(tick, wait)
    }

    private val tick = Runnable {
        tickScheduled = false
        val device = host
        if (device == null) {
            coalescer.reset()
            return@Runnable
        }
        val report = coalescer.tick() ?: return@Runnable
        // Si el sistema rechaza el informe, el movimiento no se pierde: vuelve a la cuenta para el siguiente tic.
        if (!transmit(device, report)) coalescer.restore(report)
        if (coalescer.hasPending) scheduleTick()
    }

    /** Un `sendReport`, medido. Devuelve false si el sistema lo rechazó. */
    private fun transmit(device: BluetoothDevice, report: HidReport): Boolean {
        val started = System.nanoTime()
        val ok = try {
            hid?.sendReport(device, report.id, report.data) ?: false
        } catch (e: SecurityException) {
            status = "Falta el permiso de Bluetooth"
            false
        }
        val now = SystemClock.uptimeMillis()
        stats.record(now, (System.nanoTime() - started) / 1_000L, ok)
        if (report.id == HidMapper.MOUSE_ID) lastMouseSendAt = now
        if (!ok && report.id != HidMapper.MOUSE_ID) log("sendReport rechazado (informe ${report.id})")
        return ok
    }

    private fun prefs() = app.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)

    private fun preferredAddress(): String? = prefs().getString(KEY_HOST, null)

    private fun preferredHost(): BluetoothDevice? {
        val address = preferredAddress() ?: return null
        return bondedDevices().firstOrNull { it.address == address }
    }

    private fun savePreferredHost(address: String) {
        prefs().edit().putString(KEY_HOST, address).apply()
    }

    companion object {
        private const val TAG = "MyMouse"
        private const val KEY_HOST = "host"
        const val DEFAULT_INTERVAL_MS = 8L
        const val MIN_INTERVAL_MS = 6L
        const val MAX_INTERVAL_MS = 20L
        private const val QOS_TOKEN_RATE = 800
        private const val QOS_TOKEN_BUCKET = 9
        private const val QOS_LATENCY_US = 11_250
        private const val RELEASE_DELAY_MS = 60_000L // spec v4.5 §4: no cortar el registro HID mientras Windows empareja
        private const val ZOOM_RELEASE_MS = 150L // sin zoom durante este tiempo, se suelta el Ctrl del zoom fino
        private const val TEXT_BURST = 8 // hasta 8 informes (4 teclas) salen de golpe; el texto más largo, con hueco
        private const val TEXT_GAP_MS = 5L

        @Volatile private var instance: BluetoothHidTransport? = null

        /** Instancia única: solo puede haber un registro HID. Se usa desde el hilo principal. */
        fun shared(context: Context): BluetoothHidTransport =
            instance ?: BluetoothHidTransport(context).also { instance = it }
    }
}
