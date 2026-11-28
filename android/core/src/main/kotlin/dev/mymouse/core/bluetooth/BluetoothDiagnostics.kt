package dev.mymouse.core.bluetooth

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
    /** Ajuste "Scroll fino por Bluetooth": con él apagado se registra el descriptor antiguo, sin multiplicador. */
    val fineScrollEnabled: Boolean = true,
    /** ¿Ha activado el PC el multiplicador de resolución? (docs/spec-v5-bluetooth-completo.md §4). */
    val fineScrollActive: Boolean = false,
    /** ¿Ha escrito el PC alguna vez el informe de salida de LEDs del teclado? (docs/spec-v6-teclado.md §5.4). */
    val keyboardLedsReceived: Boolean = false,
)

enum class CheckStatus { OK, FAIL, UNKNOWN }

/** Qué comprobación es. La app pone el texto en el idioma del móvil; aquí solo hay códigos. */
enum class CheckId(val key: String) {
    BLUETOOTH("bluetooth"), PERMISSIONS("permissions"), PROFILE("profile"),
    REGISTERED("registered"), HOST("host"), LINK("link"),
}

/** El detalle de una comprobación, también como código. [DiagnosticCheck.hostName] acompaña a [HOST_OK] y [HOST_UNBONDED]. */
enum class DiagnosticDetail {
    NONE, NO_ADAPTER, OFF, MISSING_PERMISSIONS, NO_HID_PROFILE, NOT_REGISTERED,
    NO_HOST, HOST_UNBONDED, HOST_OK, CONNECTED, CONNECTING, DISCONNECTED,
}

data class DiagnosticCheck(
    val id: CheckId,
    val status: CheckStatus,
    val detail: DiagnosticDetail = DiagnosticDetail.NONE,
    val hostName: String? = null,
) {
    /** Marca de texto para la pantalla y el informe. */
    val mark: String get() = when (status) { CheckStatus.OK -> "✓"; CheckStatus.FAIL -> "✗"; CheckStatus.UNKNOWN -> "?" }
}

/** Estado del scroll fino por Bluetooth, para la línea del diagnóstico. */
enum class FineScrollState { DISABLED, ACTIVE, INACTIVE }

/** Resumen de una línea de las comprobaciones. */
sealed interface DiagnosticSummary {
    data object AllOk : DiagnosticSummary
    data object NotEnoughData : DiagnosticSummary
    data class Failed(val check: DiagnosticCheck) : DiagnosticSummary
}

object BluetoothDiagnostics {
    fun fineScrollState(f: BluetoothFacts): FineScrollState = when {
        !f.fineScrollEnabled -> FineScrollState.DISABLED
        f.fineScrollActive -> FineScrollState.ACTIVE
        else -> FineScrollState.INACTIVE
    }

    /** Las seis comprobaciones de la spec, en orden. */
    fun evaluate(f: BluetoothFacts): List<DiagnosticCheck> {
        fun check(id: CheckId, value: Boolean?, fail: DiagnosticDetail = DiagnosticDetail.NONE) =
            DiagnosticCheck(id, when (value) { true -> CheckStatus.OK; false -> CheckStatus.FAIL; null -> CheckStatus.UNKNOWN },
                if (value == false) fail else DiagnosticDetail.NONE)

        val bluetooth = if (!f.adapterPresent) check(CheckId.BLUETOOTH, false, DiagnosticDetail.NO_ADAPTER)
        else check(CheckId.BLUETOOTH, f.adapterEnabled, DiagnosticDetail.OFF)
        val permissions = check(CheckId.PERMISSIONS, f.permissionsGranted, DiagnosticDetail.MISSING_PERMISSIONS)
        val profile = check(CheckId.PROFILE, f.profileAvailable, DiagnosticDetail.NO_HID_PROFILE)
        val registered = check(CheckId.REGISTERED, f.appRegistered, DiagnosticDetail.NOT_REGISTERED)

        val host = when {
            f.preferredHostName == null -> DiagnosticCheck(CheckId.HOST, CheckStatus.FAIL, DiagnosticDetail.NO_HOST)
            !f.preferredHostBonded -> DiagnosticCheck(CheckId.HOST, CheckStatus.FAIL, DiagnosticDetail.HOST_UNBONDED, f.preferredHostName)
            else -> DiagnosticCheck(CheckId.HOST, CheckStatus.OK, DiagnosticDetail.HOST_OK, f.preferredHostName)
        }

        val link = when (f.link) {
            HidLinkState.CONNECTED -> DiagnosticCheck(CheckId.LINK, CheckStatus.OK, DiagnosticDetail.CONNECTED)
            HidLinkState.CONNECTING -> DiagnosticCheck(CheckId.LINK, CheckStatus.FAIL, DiagnosticDetail.CONNECTING)
            HidLinkState.DISCONNECTED -> DiagnosticCheck(CheckId.LINK, CheckStatus.FAIL, DiagnosticDetail.DISCONNECTED)
            null -> DiagnosticCheck(CheckId.LINK, CheckStatus.UNKNOWN)
        }
        return listOf(bluetooth, permissions, profile, registered, host, link)
    }

    /** Resumen: la primera comprobación que falla, o "todo correcto" / "sin datos suficientes". */
    fun summary(checks: List<DiagnosticCheck>): DiagnosticSummary {
        val bad = checks.firstOrNull { it.status == CheckStatus.FAIL }
            ?: return if (checks.all { it.status == CheckStatus.OK }) DiagnosticSummary.AllOk else DiagnosticSummary.NotEnoughData
        return DiagnosticSummary.Failed(bad)
    }

    /**
     * Cuando todo lo del móvil está bien pero el PC no se conecta, la causa típica es haber emparejado antes
     * de activar el modo Bluetooth: se sugiere volver a emparejar.
     */
    fun suspectsStalePairing(checks: List<DiagnosticCheck>): Boolean {
        val byId = checks.associateBy { it.id }
        return listOf(CheckId.BLUETOOTH, CheckId.PERMISSIONS, CheckId.PROFILE, CheckId.REGISTERED, CheckId.HOST)
            .all { byId[it]?.status == CheckStatus.OK } && byId[CheckId.LINK]?.status != CheckStatus.OK
    }
}
