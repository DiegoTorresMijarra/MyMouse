package dev.mymouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import dev.mymouse.core.bluetooth.BluetoothEventLog
import dev.mymouse.core.bluetooth.BluetoothFacts
import dev.mymouse.core.bluetooth.HidLinkState
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidMapper
import dev.mymouse.core.hid.HidReport
import dev.mymouse.core.protocol.GestureStatus
import dev.mymouse.core.protocol.MediaCommand
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind

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
    override val supportsGestures = false
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

    private fun log(text: String) {
        events.add(System.currentTimeMillis(), text)
        eventListener()
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

    override fun start() {
        handler.removeCallbacks(release)
        users++
        log("pantalla en uso (usuarios: $users)")
        if (hid != null || users > 1) {
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
        host?.let { device -> mapper.releaseAll().forEach { sendReport(device, it) } }
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
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "MyMouse", "Trackpad MyMouse", "MyMouse", BluetoothHidDevice.SUBCLASS1_COMBO, HidMapper.DESCRIPTOR,
        )
        try {
            val ok = h.registerApp(sdp, null, null, app.mainExecutor, callback)
            log("registerApp() devolvió $ok")
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
            // Algunos sistemas piden el estado inicial de cada informe: se responde con ceros.
            val size = when (id.toInt()) {
                HidMapper.MOUSE_ID -> 5
                HidMapper.KEYBOARD_ID -> 8
                HidMapper.CONSUMER_ID -> 2
                else -> 0
            }
            try {
                if (size > 0) hid?.replyReport(device, type, id, ByteArray(size))
                else hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            } catch (e: SecurityException) {
                Log.w(TAG, "onGetReport sin permiso")
            }
        }
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
        onConnectionChanged(device != null)
        if (device != null) replayState() else onWidgetLayout(Message.WidgetLayout(emptyList()))
    }

    /** Panel fijo de widgets en modo Bluetooth (spec §1): multimedia y volumen por teclas multimedia. */
    private fun replayState() {
        if (host == null) return
        onConnectionChanged(true)
        onWidgetLayout(Message.WidgetLayout(WIDGETS))
        onMediaState(Message.MediaState(false, "Multimedia del PC", "por Bluetooth"))
    }

    override fun send(action: OutputAction) {
        val device = host ?: return
        mapper.map(action).forEach { sendReport(device, it) }
    }

    override fun sendWidget(message: Message) {
        val device = host ?: return
        val key = when (message) {
            is Message.MediaControl -> when (message.command) {
                MediaCommand.PLAY_PAUSE -> ConsumerKey.PLAY_PAUSE
                MediaCommand.NEXT -> ConsumerKey.NEXT
                MediaCommand.PREVIOUS -> ConsumerKey.PREVIOUS
            }
            is Message.WidgetPress -> when (message.id) {
                ID_VOLUME_DOWN -> ConsumerKey.VOLUME_DOWN
                ID_VOLUME_UP -> ConsumerKey.VOLUME_UP
                ID_MUTE -> ConsumerKey.MUTE
                else -> null
            }
            else -> null // sin volumen absoluto por Bluetooth
        } ?: return
        mapper.consumer(key).forEach { sendReport(device, it) }
    }

    override fun sendShapes(messages: List<Message>) = Unit // sin PC no hay con quién sincronizar las formas

    private fun sendReport(device: BluetoothDevice, report: HidReport) {
        try {
            hid?.sendReport(device, report.id, report.data)
        } catch (e: SecurityException) {
            status = "Falta el permiso de Bluetooth"
        }
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
        private const val RELEASE_DELAY_MS = 60_000L // spec v4.5 §4: no cortar el registro HID mientras Windows empareja
        private const val ID_VOLUME_DOWN = 1
        private const val ID_VOLUME_UP = 2
        private const val ID_MUTE = 3

        private val WIDGETS = listOf(
            WidgetEntry(0, WidgetKind.MEDIA, "", ""),
            WidgetEntry(ID_VOLUME_DOWN, WidgetKind.BUTTON, "Vol −", "🔉"),
            WidgetEntry(ID_VOLUME_UP, WidgetKind.BUTTON, "Vol +", "🔊"),
            WidgetEntry(ID_MUTE, WidgetKind.BUTTON, "Silencio", "🔇"),
        )

        @Volatile private var instance: BluetoothHidTransport? = null

        /** Instancia única: solo puede haber un registro HID. Se usa desde el hilo principal. */
        fun shared(context: Context): BluetoothHidTransport =
            instance ?: BluetoothHidTransport(context).also { instance = it }
    }
}
