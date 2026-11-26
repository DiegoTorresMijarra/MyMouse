package dev.mymouse.core.bluetooth

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Un evento del registro de depuración del modo Bluetooth (docs/spec-v4.5-estilo.md §4). */
data class BluetoothEvent(val timeMillis: Long, val text: String)

/** Registro circular de los últimos eventos (30 por defecto). No es seguro entre hilos: se usa desde el principal. */
class BluetoothEventLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val items = ArrayDeque<BluetoothEvent>()

    init {
        require(capacity > 0) { "capacity debe ser positiva" }
    }

    fun add(timeMillis: Long, text: String) {
        items.addLast(BluetoothEvent(timeMillis, text))
        while (items.size > capacity) items.removeFirst()
    }

    /** Del más antiguo al más reciente. */
    fun entries(): List<BluetoothEvent> = items.toList()

    fun clear() = items.clear()

    companion object {
        const val DEFAULT_CAPACITY = 30
    }
}

/** Estado de la conexión HID con el PC preferido (`BluetoothHidDevice.getConnectionState`). */
enum class HidLinkState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Lo que la app sabe del Bluetooth en este momento. `null` significa "no se pudo averiguar" (por ejemplo, sin permiso).
 */
data class BluetoothFacts(
    val adapterPresent: Boolean = true,
    val adapterEnabled: Boolean? = null,
    val permissionsGranted: Boolean? = null,
    val profileAvailable: Boolean? = null,
    val appRegistered: Boolean? = null,
    /** Nombre del PC preferido, si hay uno guardado. */
    val preferredHostName: String? = null,
    /** El PC preferido sigue en la lista de emparejados. */
    val preferredHostBonded: Boolean = false,
    val link: HidLinkState? = null,
)

enum class CheckStatus { OK, FAIL, UNKNOWN }

data class DiagnosticCheck(val id: String, val label: String, val status: CheckStatus, val detail: String = "") {
    /** Marca de texto para la pantalla y el informe. */
    val mark: String get() = when (status) { CheckStatus.OK -> "✓"; CheckStatus.FAIL -> "✗"; CheckStatus.UNKNOWN -> "?" }
}

object BluetoothDiagnostics {
    /** Texto de ayuda fijo con la causa más probable de "Esperando al PC" (spec §4, causa 1). */
    const val REPAIR_HELP =
        "Si emparejaste el móvil antes de elegir Bluetooth en MyMouse: en Windows, Configuración → Bluetooth → " +
            "el móvil → Quitar dispositivo; aquí, Hacer visible; y vuelve a añadirlo desde Windows."

    /** Las seis comprobaciones de la spec, en orden. */
    fun evaluate(f: BluetoothFacts): List<DiagnosticCheck> {
        fun check(id: String, label: String, value: Boolean?, ok: String = "", fail: String = "") =
            DiagnosticCheck(id, label, when (value) { true -> CheckStatus.OK; false -> CheckStatus.FAIL; null -> CheckStatus.UNKNOWN },
                when (value) { true -> ok; false -> fail; null -> "" })

        val bluetooth = if (!f.adapterPresent) check("bluetooth", "Bluetooth activado", false, fail = "este móvil no tiene Bluetooth")
        else check("bluetooth", "Bluetooth activado", f.adapterEnabled, fail = "está apagado")
        val permissions = check("permissions", "Permisos de Bluetooth", f.permissionsGranted, fail = "faltan permisos")
        val profile = check("profile", "Perfil HID disponible", f.profileAvailable, fail = "el sistema no ofrece el perfil HID")
        val registered = check("registered", "App registrada como ratón", f.appRegistered, fail = "sin registrar")

        val hostLabel = "PC preferido elegido y emparejado"
        val host = when {
            f.preferredHostName == null -> DiagnosticCheck("host", hostLabel, CheckStatus.FAIL, "ningún PC elegido")
            !f.preferredHostBonded -> DiagnosticCheck("host", hostLabel, CheckStatus.FAIL, "${f.preferredHostName} ya no está emparejado")
            else -> DiagnosticCheck("host", hostLabel, CheckStatus.OK, f.preferredHostName)
        }

        val linkLabel = "Conexión HID con el PC"
        val link = when (f.link) {
            HidLinkState.CONNECTED -> DiagnosticCheck("link", linkLabel, CheckStatus.OK, "conectado")
            HidLinkState.CONNECTING -> DiagnosticCheck("link", linkLabel, CheckStatus.FAIL, "conectando…")
            HidLinkState.DISCONNECTED -> DiagnosticCheck("link", linkLabel, CheckStatus.FAIL, "desconectado")
            null -> DiagnosticCheck("link", linkLabel, CheckStatus.UNKNOWN, "")
        }
        return listOf(bluetooth, permissions, profile, registered, host, link)
    }

    /** Resumen de una línea: la primera comprobación que falla o "todo correcto". */
    fun summary(checks: List<DiagnosticCheck>): String {
        val bad = checks.firstOrNull { it.status == CheckStatus.FAIL }
            ?: return if (checks.all { it.status == CheckStatus.OK }) "Todo correcto" else "Sin datos suficientes"
        return if (bad.detail.isEmpty()) bad.label else "${bad.label}: ${bad.detail}"
    }

    /**
     * Cuando todo lo del móvil está bien pero el PC no se conecta, la causa típica es haber emparejado antes
     * de activar el modo Bluetooth: se sugiere volver a emparejar.
     */
    fun suspectsStalePairing(checks: List<DiagnosticCheck>): Boolean {
        val byId = checks.associateBy { it.id }
        return listOf("bluetooth", "permissions", "profile", "registered", "host").all { byId[it]?.status == CheckStatus.OK } &&
            byId["link"]?.status != CheckStatus.OK
    }

    /** Texto para "Copiar diagnóstico": comprobaciones y registro, pensado para pegarlo en un chat. */
    fun report(
        facts: BluetoothFacts,
        events: List<BluetoothEvent>,
        appInfo: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val checks = evaluate(facts)
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        return buildString {
            append("MyMouse · diagnóstico Bluetooth\n")
            if (appInfo.isNotBlank()) append(appInfo).append('\n')
            append("Resumen: ").append(summary(checks)).append("\n\n")
            for (c in checks) {
                append(c.mark).append(' ').append(c.label)
                if (c.detail.isNotEmpty()) append(": ").append(c.detail)
                append('\n')
            }
            if (suspectsStalePairing(checks)) append("\nPosible causa: emparejado antes de activar el modo Bluetooth.\n")
            append("\nÚltimos eventos (").append(events.size).append("):\n")
            if (events.isEmpty()) append("(ninguno)\n")
            for (e in events) append(fmt.format(Instant.ofEpochMilli(e.timeMillis))).append("  ").append(e.text).append('\n')
        }
    }
}
