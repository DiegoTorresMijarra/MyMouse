package dev.mymouse

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Emparejar el móvil como ratón Bluetooth (docs/spec-v3-bluetooth.md §2.1). */
@RequiresApi(Build.VERSION_CODES.P)
class BluetoothActivity : Activity() {

    private lateinit var transport: BluetoothHidTransport
    private lateinit var list: LinearLayout
    private lateinit var statusText: TextView
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transport = BluetoothHidTransport.shared(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * density).toInt()
            setPadding(p, p, p, p)
        }
        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(120, 220, 120))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(list)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        transport.statusListener = { s -> runOnUiThread { statusText.text = s; build() } }
        if (hasPermissions()) transport.start()
        build()
    }

    override fun onPause() {
        transport.statusListener = {}
        if (hasPermissions()) transport.stop()
        super.onPause()
    }

    private fun build() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "Bluetooth"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        statusText.text = if (hasPermissions()) transport.status else "Falta el permiso de Bluetooth"
        (statusText.parent as? ViewGroup)?.removeView(statusText)
        list.addView(statusText)

        if (!hasPermissions()) {
            list.addView(button("Dar permiso de Bluetooth") { requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS) })
            return
        }

        list.addView(note(
            "Para emparejar por primera vez:\n" +
                "1. Pulsa \"Hacer visible\".\n" +
                "2. En Windows: Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth, y elige este móvil.\n" +
                "3. Acepta el código en los dos lados.\n" +
                "Después, el móvil se conecta solo a ese PC al abrir MyMouse.",
        ))
        list.addView(button("Hacer visible (2 min)") {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
            )
        })

        list.addView(header("Dispositivos emparejados"))
        val devices = transport.bondedDevices()
        if (devices.isEmpty()) list.addView(note("Ninguno todavía."))
        val connected = transport.connectedHost?.address
        for (d in devices) {
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@BluetoothActivity).apply {
                    text = transport.nameOf(d) + if (d.address == connected) "  · conectado" else ""
                    textSize = 16f
                    setTextColor(Color.rgb(230, 230, 230))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (d.address != connected) addView(button("Usar este") { transport.connect(d) })
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && hasPermissions()) transport.start()
        build()
    }

    private fun hasPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(100, 180, 255))
        setPadding(0, (20 * density).toInt(), 0, (6 * density).toInt())
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(170, 170, 170))
        setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1

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
