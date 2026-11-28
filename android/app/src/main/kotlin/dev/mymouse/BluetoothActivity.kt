package dev.mymouse

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import dev.mymouse.core.bluetooth.BluetoothDiagnostics
import dev.mymouse.core.bluetooth.CheckStatus
import dev.mymouse.core.bluetooth.BluetoothEvent
import dev.mymouse.core.bluetooth.BluetoothFacts
import dev.mymouse.core.bluetooth.CheckId
import dev.mymouse.core.bluetooth.DiagnosticCheck
import dev.mymouse.core.bluetooth.DiagnosticDetail
import dev.mymouse.core.bluetooth.DiagnosticSummary
import dev.mymouse.core.bluetooth.FineScrollState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Emparejar el móvil como ratón Bluetooth (docs/spec-v3-bluetooth.md §2.1) y diagnosticarlo (docs/spec-v4.5-estilo.md §4):
 * comprobaciones con ✓/✗, registro de los últimos 30 eventos, reintentar la conexión, volver a registrar y copiar el diagnóstico.
 * La evaluación y el registro están en `core` (con tests); aquí solo se alimentan y se muestran.
 */
@RequiresApi(Build.VERSION_CODES.P)
class BluetoothActivity : Activity() {

    private lateinit var transport: BluetoothHidTransport
    private lateinit var screen: GlassTheme.Screen
    private lateinit var statusBody: LinearLayout
    private lateinit var devicesBody: LinearLayout
    private lateinit var diagCard: LinearLayout
    private lateinit var diagBody: LinearLayout
    private lateinit var statsText: TextView
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val statsTick = object : Runnable {
        override fun run() {
            updateStats()
            ui.postDelayed(this, 1_000)
        }
    }
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transport = BluetoothHidTransport.shared(this)
        screen = GlassTheme.Screen(this, getString(R.string.bt_title))
        statusBody = body(screen.card(getString(R.string.bt_card_status)))
        devicesBody = body(screen.card(getString(R.string.bt_card_devices)))
        diagCard = screen.card(getString(R.string.bt_card_diagnostics))
        diagBody = body(diagCard)
        screen.setContentView()
        if (intent.getBooleanExtra(EXTRA_SHOW_DIAGNOSTICS, false)) {
            screen.scrollView?.post { screen.scrollView?.scrollTo(0, diagCard.top) }
        }
    }

    /** Una zona de la tarjeta que se vuelve a rellenar cuando cambia algo. */
    private fun body(card: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        card.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onResume() {
        super.onResume()
        transport.statusListener = { runOnUiThread { refresh() } }
        transport.eventListener = { runOnUiThread { refresh() } }
        if (hasPermissions()) transport.start()
        refresh()
        ui.post(statsTick)
    }

    private fun updateStats() {
        if (::statsText.isInitialized) statsText.text = statsLine()
    }

    private fun statsLine(): String = getString(R.string.bt_stats_prefix, UiText.statsDetail(this, transport.stats.snapshot(android.os.SystemClock.uptimeMillis())))

    override fun onPause() {
        ui.removeCallbacks(statsTick)
        transport.statusListener = {}
        transport.eventListener = {}
        if (hasPermissions()) transport.stop()
        super.onPause()
    }

    private fun refresh() {
        buildStatus()
        buildDevices()
        buildDiagnostics()
    }

    private fun buildStatus() {
        statusBody.removeAllViews()
        val granted = hasPermissions()
        statusBody.addView(GlassTheme.text(this, if (granted) transport.status else getString(R.string.bt_status_no_permission), 16f, GlassTheme.GREEN))
        if (!granted) {
            statusBody.addView(GlassTheme.button(this, getString(R.string.bt_grant_permission), accent = true) {
                requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
            })
            return
        }
        statusBody.addView(GlassTheme.note(
            this,
            getString(R.string.bt_pairing_steps, getString(R.string.bt_repair_fine_scroll)),
        ))
        statusBody.addView(GlassTheme.button(this, getString(R.string.bt_make_discoverable)) {
            // Mientras Windows empareja salen diálogos del sistema: el registro HID se mantiene 3 minutos pase lo que pase.
            transport.holdRegistration(HOLD_PAIRING_MS)
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180),
            )
        })
    }

    private fun buildDevices() {
        devicesBody.removeAllViews()
        if (!hasPermissions()) {
            devicesBody.addView(GlassTheme.note(this, getString(R.string.bt_grant_to_see_devices)))
            return
        }
        val devices = transport.bondedDevices()
        if (devices.isEmpty()) devicesBody.addView(GlassTheme.note(this, getString(R.string.bt_no_devices)))
        val connected = transport.connectedHost?.address
        for (d in devices) {
            devicesBody.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(GlassTheme.text(this@BluetoothActivity, transport.nameOf(d) + if (d.address == connected) getString(R.string.bt_device_connected_suffix) else "", 15f),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (d.address != connected) addView(GlassTheme.button(this@BluetoothActivity, getString(R.string.bt_use_this)) { transport.connect(d) })
            })
        }
    }

    // ---- Diagnóstico ----

    private fun buildDiagnostics() {
        diagBody.removeAllViews()
        val facts = transport.facts(hasPermissions())
        val checks = BluetoothDiagnostics.evaluate(facts)
        for (c in checks) {
            val color = when (c.status) {
                CheckStatus.OK -> GlassTheme.GREEN
                CheckStatus.FAIL -> GlassTheme.RED
                CheckStatus.UNKNOWN -> GlassTheme.AMBER
            }
            diagBody.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                addView(GlassTheme.text(this@BluetoothActivity, c.mark, 15f, color, bold = true),
                    LinearLayout.LayoutParams((24 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(GlassTheme.text(this@BluetoothActivity, checkText(c), 14f),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        if (BluetoothDiagnostics.suspectsStalePairing(checks)) {
            diagBody.addView(GlassTheme.text(
                this, getString(R.string.bt_stale_pairing),
                13f, GlassTheme.AMBER,
            ).apply { setPadding(0, (8 * density).toInt(), 0, 0) })
        }

        diagBody.addView(GlassTheme.text(this, fineScrollLine(facts), 13f, GlassTheme.TEXT_SECONDARY).apply {
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        // Luces del teclado (modo teclado): si no llegan, el teclado las supone y aquí se sugiere volver a emparejar.
        diagBody.addView(GlassTheme.text(
            this, keyboardLedsLine(facts), 13f,
            if (facts.keyboardLedsReceived) GlassTheme.TEXT_SECONDARY else GlassTheme.AMBER,
        ).apply { setPadding(0, (8 * density).toInt(), 0, 0) })
        statsText = GlassTheme.text(this, statsLine(), 12f, GlassTheme.TEXT_SECONDARY).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        diagBody.addView(statsText)
        diagBody.addView(GlassTheme.note(
            this,
            getString(R.string.bt_pointer_note),
        ))
        diagBody.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(GlassTheme.button(this@BluetoothActivity, getString(R.string.bt_retry)) { transport.retryConnection() })
            addView(GlassTheme.button(this@BluetoothActivity, getString(R.string.bt_reregister)) { transport.reregister() })
        })
        diagBody.addView(GlassTheme.button(this, getString(R.string.bt_copy_diagnostics)) { copyDiagnostics() })

        diagBody.addView(GlassTheme.note(this, getString(R.string.bt_repair_help)))
        diagBody.addView(GlassTheme.note(this, getString(R.string.bt_pairing_note)))
        diagBody.addView(GlassTheme.note(this, getString(R.string.bt_repair_fine_scroll)))

        diagBody.addView(GlassTheme.cardTitle(this, getString(R.string.bt_last_events)).apply { setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt()) })
        val events = transport.events.entries()
        if (events.isEmpty()) diagBody.addView(GlassTheme.note(this, getString(R.string.bt_no_events)))
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        for (e in events) {
            diagBody.addView(GlassTheme.text(this, "${fmt.format(Instant.ofEpochMilli(e.timeMillis))}  ${e.text}", 11f, GlassTheme.TEXT_SECONDARY).apply {
                typeface = Typeface.MONOSPACE
            })
        }
    }

    // ---- Textos del diagnóstico: el núcleo devuelve códigos y aquí se traducen ----

    private fun checkLabel(c: DiagnosticCheck): String = getString(when (c.id) {
        CheckId.BLUETOOTH -> R.string.diag_check_bluetooth
        CheckId.PERMISSIONS -> R.string.diag_check_permissions
        CheckId.PROFILE -> R.string.diag_check_profile
        CheckId.REGISTERED -> R.string.diag_check_registered
        CheckId.HOST -> R.string.diag_check_host
        CheckId.LINK -> R.string.diag_check_link
    })

    private fun checkDetail(c: DiagnosticCheck): String = when (c.detail) {
        DiagnosticDetail.NONE -> ""
        DiagnosticDetail.NO_ADAPTER -> getString(R.string.diag_detail_no_adapter)
        DiagnosticDetail.OFF -> getString(R.string.diag_detail_off)
        DiagnosticDetail.MISSING_PERMISSIONS -> getString(R.string.diag_detail_missing_permissions)
        DiagnosticDetail.NO_HID_PROFILE -> getString(R.string.diag_detail_no_hid_profile)
        DiagnosticDetail.NOT_REGISTERED -> getString(R.string.diag_detail_not_registered)
        DiagnosticDetail.NO_HOST -> getString(R.string.diag_detail_no_host)
        DiagnosticDetail.HOST_UNBONDED -> getString(R.string.diag_detail_host_unbonded, c.hostName.orEmpty())
        DiagnosticDetail.HOST_OK -> c.hostName.orEmpty()
        DiagnosticDetail.CONNECTED -> getString(R.string.diag_detail_connected)
        DiagnosticDetail.CONNECTING -> getString(R.string.diag_detail_connecting)
        DiagnosticDetail.DISCONNECTED -> getString(R.string.diag_detail_disconnected)
    }

    private fun checkText(c: DiagnosticCheck): String {
        val detail = checkDetail(c)
        return if (detail.isEmpty()) checkLabel(c) else "${checkLabel(c)}: $detail"
    }

    private fun summaryText(checks: List<DiagnosticCheck>): String = when (val s = BluetoothDiagnostics.summary(checks)) {
        DiagnosticSummary.AllOk -> getString(R.string.diag_summary_ok)
        DiagnosticSummary.NotEnoughData -> getString(R.string.diag_summary_no_data)
        is DiagnosticSummary.Failed -> checkText(s.check)
    }

    private fun fineScrollLine(f: BluetoothFacts): String = getString(when (BluetoothDiagnostics.fineScrollState(f)) {
        FineScrollState.DISABLED -> R.string.diag_fine_scroll_disabled
        FineScrollState.ACTIVE -> R.string.diag_fine_scroll_active
        FineScrollState.INACTIVE -> R.string.diag_fine_scroll_inactive
    })

    private fun keyboardLedsLine(f: BluetoothFacts): String =
        if (f.keyboardLedsReceived) getString(R.string.diag_leds_received)
        else getString(R.string.diag_leds_missing, getString(R.string.diag_repair_leds))

    /** Texto para "Copiar diagnóstico": comprobaciones y registro, pensado para pegarlo en un chat. */
    private fun buildReport(facts: BluetoothFacts, events: List<BluetoothEvent>, appInfo: String): String {
        val checks = BluetoothDiagnostics.evaluate(facts)
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        return buildString {
            append(getString(R.string.diag_report_title)).append('\n')
            if (appInfo.isNotBlank()) append(appInfo).append('\n')
            append(getString(R.string.diag_report_summary, summaryText(checks))).append("\n\n")
            for (c in checks) append(c.mark).append(' ').append(checkText(c)).append('\n')
            append('\n').append(fineScrollLine(facts)).append('\n')
            append(keyboardLedsLine(facts)).append('\n')
            if (BluetoothDiagnostics.suspectsStalePairing(checks)) append('\n').append(getString(R.string.diag_report_stale)).append('\n')
            append('\n').append(getString(R.string.diag_report_events, events.size)).append('\n')
            if (events.isEmpty()) append(getString(R.string.diag_report_none)).append('\n')
            for (e in events) append(fmt.format(Instant.ofEpochMilli(e.timeMillis))).append("  ").append(e.text).append('\n')
        }
    }

    private fun copyDiagnostics() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
        val info = "MyMouse $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        val text = buildReport(
            transport.facts(hasPermissions()), transport.events.entries(),
            "$info\n${statsLine()}\n" + getString(R.string.diag_report_interval, transport.intervalMs),
        )
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.diag_clip_label), text))
        Toast.makeText(this, getString(R.string.diag_copied), Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && hasPermissions()) transport.start()
        refresh()
    }

    private fun hasPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val HOLD_PAIRING_MS = 180_000L

        /** Abre la pantalla directamente en la sección "Diagnóstico" (toque en el punto de estado del trackpad). */
        const val EXTRA_SHOW_DIAGNOSTICS = "show_diagnostics"

        val PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                emptyArray()
            }

        fun hasPermissions(activity: Activity): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                PERMISSIONS.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
