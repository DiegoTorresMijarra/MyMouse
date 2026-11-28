package dev.mymouse.core.bluetooth

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
        assertEquals(DiagnosticSummary.AllOk, BluetoothDiagnostics.summary(checks))
        assertFalse(BluetoothDiagnostics.suspectsStalePairing(checks))
    }

    @Test
    fun noPreferredHostFails() {
        val checks = BluetoothDiagnostics.evaluate(good.copy(preferredHostName = null, link = null))
        val host = checks.first { it.id == CheckId.HOST }
        assertEquals(CheckStatus.FAIL, host.status)
        assertEquals(DiagnosticDetail.NO_HOST, host.detail)
        assertEquals(DiagnosticSummary.Failed(host), BluetoothDiagnostics.summary(checks))
    }

    @Test
    fun unbondedHostFails() {
        val c = BluetoothDiagnostics.evaluate(good.copy(preferredHostBonded = false)).first { it.id == CheckId.HOST }
        assertEquals(CheckStatus.FAIL, c.status)
        assertEquals(DiagnosticDetail.HOST_UNBONDED, c.detail)
        assertEquals("PC-Sala", c.hostName)
    }

    @Test
    fun disabledBluetoothAndMissingAdapter() {
        assertEquals(DiagnosticDetail.OFF, BluetoothDiagnostics.evaluate(good.copy(adapterEnabled = false)).first().detail)
        assertEquals(DiagnosticDetail.NO_ADAPTER, BluetoothDiagnostics.evaluate(good.copy(adapterPresent = false)).first().detail)
    }

    @Test
    fun unknownValuesAreNotFailures() {
        val checks = BluetoothDiagnostics.evaluate(BluetoothFacts())
        assertEquals(CheckStatus.UNKNOWN, checks.first { it.id == CheckId.PERMISSIONS }.status)
        assertEquals(DiagnosticSummary.NotEnoughData, BluetoothDiagnostics.summary(checks.filter { it.status == CheckStatus.UNKNOWN }))
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
    fun linkDetailsFollowTheConnectionState() {
        fun detail(link: HidLinkState?) = BluetoothDiagnostics.evaluate(good.copy(link = link)).last().detail
        assertEquals(DiagnosticDetail.CONNECTED, detail(HidLinkState.CONNECTED))
        assertEquals(DiagnosticDetail.CONNECTING, detail(HidLinkState.CONNECTING))
        assertEquals(DiagnosticDetail.DISCONNECTED, detail(HidLinkState.DISCONNECTED))
        assertEquals(DiagnosticDetail.NONE, detail(null))
    }

    @Test
    fun fineScrollStateSaysWhySmoothScrollIsOff() {
        assertEquals(FineScrollState.DISABLED, BluetoothDiagnostics.fineScrollState(good.copy(fineScrollEnabled = false)))
        assertEquals(FineScrollState.ACTIVE, BluetoothDiagnostics.fineScrollState(good.copy(fineScrollActive = true)))
        assertEquals(FineScrollState.INACTIVE, BluetoothDiagnostics.fineScrollState(good))
    }
}
