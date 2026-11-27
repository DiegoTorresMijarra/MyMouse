package dev.mymouse.core.bluetooth

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BluetoothDiagnosticsTest {
    private val good = BluetoothFacts(
        adapterEnabled = true, permissionsGranted = true, profileAvailable = true, appRegistered = true,
        preferredHostName = "PC-Sala", preferredHostBonded = true, link = HidLinkState.CONNECTED,
    )

    @Test
    fun logKeepsOnlyTheLast30() {
        val log = BluetoothEventLog()
        for (i in 1..45) log.add(i * 1000L, "evento $i")
        val e = log.entries()
        assertEquals(30, e.size)
        assertEquals("evento 16", e.first().text)
        assertEquals("evento 45", e.last().text)
    }

    @Test
    fun allGoodIsAllOk() {
        val checks = BluetoothDiagnostics.evaluate(good)
        assertEquals(6, checks.size)
        assertTrue(checks.all { it.status == CheckStatus.OK })
        assertEquals("Todo correcto", BluetoothDiagnostics.summary(checks))
        assertFalse(BluetoothDiagnostics.suspectsStalePairing(checks))
    }

    @Test
    fun noPreferredHostFails() {
        val checks = BluetoothDiagnostics.evaluate(good.copy(preferredHostName = null, link = null))
        assertEquals(CheckStatus.FAIL, checks.first { it.id == "host" }.status)
        assertEquals("PC preferido elegido y emparejado: ningún PC elegido", BluetoothDiagnostics.summary(checks))
    }

    @Test
    fun unbondedHostFails() {
        val c = BluetoothDiagnostics.evaluate(good.copy(preferredHostBonded = false)).first { it.id == "host" }
        assertEquals(CheckStatus.FAIL, c.status)
        assertTrue(c.detail.contains("PC-Sala"))
    }

    @Test
    fun disabledBluetoothAndMissingAdapter() {
        assertEquals("está apagado", BluetoothDiagnostics.evaluate(good.copy(adapterEnabled = false)).first().detail)
        assertEquals("este móvil no tiene Bluetooth", BluetoothDiagnostics.evaluate(good.copy(adapterPresent = false)).first().detail)
    }

    @Test
    fun unknownValuesAreNotFailures() {
        val checks = BluetoothDiagnostics.evaluate(BluetoothFacts())
        assertEquals(CheckStatus.UNKNOWN, checks.first { it.id == "permissions" }.status)
        assertEquals("Sin datos suficientes", BluetoothDiagnostics.summary(checks.filter { it.status == CheckStatus.UNKNOWN }))
    }

    @Test
    fun stalePairingIsSuspectedWhenPhoneIsFineButLinkIsDown() {
        for (link in listOf(HidLinkState.DISCONNECTED, HidLinkState.CONNECTING)) {
            assertTrue(BluetoothDiagnostics.suspectsStalePairing(BluetoothDiagnostics.evaluate(good.copy(link = link))))
        }
        // Si falla algo del propio móvil, la causa es otra.
        assertFalse(BluetoothDiagnostics.suspectsStalePairing(
            BluetoothDiagnostics.evaluate(good.copy(appRegistered = false, link = HidLinkState.DISCONNECTED))))
    }

    @Test
    fun reportListsChecksAndEvents() {
        val log = BluetoothEventLog()
        log.add(3_600_000L, "registro: true")
        log.add(3_605_000L, "connect() devolvió false")
        val text = BluetoothDiagnostics.report(good.copy(link = HidLinkState.DISCONNECTED), log.entries(), "Android 14", ZoneOffset.UTC)
        assertTrue(text.contains("Android 14"))
        assertTrue(text.contains("✓ Bluetooth activado"))
        assertTrue(text.contains("✗ Conexión HID con el PC: desconectado"))
        assertTrue(text.contains("Posible causa"))
        assertTrue(text.contains("01:00:00  registro: true"))
        assertTrue(text.contains("01:00:05  connect() devolvió false"))
    }

    @Test
    fun emptyLogReport() {
        assertTrue(BluetoothDiagnostics.report(good, emptyList(), zone = ZoneOffset.UTC).contains("(ninguno)"))
    }

    @Test
    fun helpTextsMentionTheKeySteps() {
        assertTrue(BluetoothDiagnostics.REPAIR_HELP.contains("Quitar dispositivo"))
        assertTrue(BluetoothDiagnostics.PAIRING_NOTE.contains("teléfono"))
        assertTrue(BluetoothDiagnostics.PAIRING_NOTE.contains("Emparejado"))
    }

    @Test
    fun keyboardLedsLineSaysWhetherTheHostWritesThem() {
        assertTrue(BluetoothDiagnostics.keyboardLedsLine(good.copy(keyboardLedsReceived = true)).contains("recibidas del PC"))
        val missing = BluetoothDiagnostics.keyboardLedsLine(good.copy(keyboardLedsReceived = false))
        assertTrue(missing.contains("no recibidas"))
        assertTrue(missing.contains("vuelve a emparejarlo"))
    }

    @Test
    fun reportIncludesTheKeyboardLedsLine() {
        assertTrue(BluetoothDiagnostics.report(good, emptyList(), zone = ZoneOffset.UTC).contains("Luces del teclado: no recibidas"))
        assertTrue(BluetoothDiagnostics.report(good.copy(keyboardLedsReceived = true), emptyList(), zone = ZoneOffset.UTC).contains("Luces del teclado: recibidas"))
    }
}
