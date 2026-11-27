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
        screen = GlassTheme.Screen(this, "Bluetooth")
        statusBody = body(screen.card("Estado"))
        devicesBody = body(screen.card("Dispositivos emparejados"))
        diagCard = screen.card("Diagnóstico")
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

    private fun statsLine(): String = "Envío: " + transport.stats.snapshot(android.os.SystemClock.uptimeMillis()).detail()

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
        statusBody.addView(GlassTheme.text(this, if (granted) transport.status else "Falta el permiso de Bluetooth", 16f, GlassTheme.GREEN))
        if (!granted) {
            statusBody.addView(GlassTheme.button(this, "Dar permiso de Bluetooth", accent = true) {
                requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
            })
            return
        }
        statusBody.addView(GlassTheme.note(
            this,
            "Para emparejar por primera vez:\n" +
                "1. Pulsa \"Hacer visible\" y deja esta pantalla abierta.\n" +
                "2. En Windows: Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth, y elige este móvil.\n" +
                "3. Acepta el código en los dos lados.\n" +
                "Después, el móvil se conecta solo a ese PC al abrir MyMouse.\n\n" +
                BluetoothDiagnostics.REPAIR_FINE_SCROLL,
        ))
        statusBody.addView(GlassTheme.button(this, "Hacer visible (3 min)") {
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
            devicesBody.addView(GlassTheme.note(this, "Da el permiso de Bluetooth para ver los dispositivos."))
            return
        }
        val devices = transport.bondedDevices()
        if (devices.isEmpty()) devicesBody.addView(GlassTheme.note(this, "Ninguno todavía."))
        val connected = transport.connectedHost?.address
        for (d in devices) {
            devicesBody.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(GlassTheme.text(this@BluetoothActivity, transport.nameOf(d) + if (d.address == connected) "  · conectado" else "", 15f),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (d.address != connected) addView(GlassTheme.button(this@BluetoothActivity, "Usar este") { transport.connect(d) })
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
                addView(GlassTheme.text(this@BluetoothActivity, if (c.detail.isEmpty()) c.label else "${c.label}: ${c.detail}", 14f),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        if (BluetoothDiagnostics.suspectsStalePairing(checks)) {
            diagBody.addView(GlassTheme.text(
                this, "Todo está bien en el móvil pero Windows no abre la conexión: probablemente se emparejó antes de activar el modo Bluetooth.",
                13f, GlassTheme.AMBER,
            ).apply { setPadding(0, (8 * density).toInt(), 0, 0) })
        }

        diagBody.addView(GlassTheme.text(this, BluetoothDiagnostics.fineScrollLine(facts), 13f, GlassTheme.TEXT_SECONDARY).apply {
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        // Luces del teclado (modo teclado, v6): si no llegan, el teclado las supone y aquí se sugiere volver a emparejar.
        diagBody.addView(GlassTheme.text(
            this, BluetoothDiagnostics.keyboardLedsLine(facts), 13f,
            if (facts.keyboardLedsReceived) GlassTheme.TEXT_SECONDARY else GlassTheme.AMBER,
        ).apply { setPadding(0, (8 * density).toInt(), 0, 0) })
        statsText = GlassTheme.text(this, statsLine(), 12f, GlassTheme.TEXT_SECONDARY).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        diagBody.addView(statsText)
        diagBody.addView(GlassTheme.note(
            this,
            "El puntero va tan fluido como los informes por segundo: el intervalo real entre informes (no es un ping: " +
                "Bluetooth HID no confirma lo que envía). Ajustes → Intervalo Bluetooth cambia la cadencia (6–20 ms).",
        ))
        diagBody.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(GlassTheme.button(this@BluetoothActivity, "Reintentar conexión") { transport.retryConnection() })
            addView(GlassTheme.button(this@BluetoothActivity, "Volver a registrar") { transport.reregister() })
        })
        diagBody.addView(GlassTheme.button(this, "Copiar diagnóstico") { copyDiagnostics() })

        diagBody.addView(GlassTheme.note(this, BluetoothDiagnostics.REPAIR_HELP))
        diagBody.addView(GlassTheme.note(this, BluetoothDiagnostics.PAIRING_NOTE))
        diagBody.addView(GlassTheme.note(this, BluetoothDiagnostics.REPAIR_FINE_SCROLL))

        diagBody.addView(GlassTheme.cardTitle(this, "Últimos eventos").apply { setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt()) })
        val events = transport.events.entries()
        if (events.isEmpty()) diagBody.addView(GlassTheme.note(this, "Todavía no hay eventos."))
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        for (e in events) {
            diagBody.addView(GlassTheme.text(this, "${fmt.format(Instant.ofEpochMilli(e.timeMillis))}  ${e.text}", 11f, GlassTheme.TEXT_SECONDARY).apply {
                typeface = Typeface.MONOSPACE
            })
        }
    }

    private fun copyDiagnostics() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
        val info = "MyMouse $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        val text = BluetoothDiagnostics.report(
            transport.facts(hasPermissions()), transport.events.entries(),
            "$info\n${statsLine()}\nIntervalo de envío configurado: ${transport.intervalMs} ms",
        )
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Diagnóstico de MyMouse", text))
        Toast.makeText(this, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
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
