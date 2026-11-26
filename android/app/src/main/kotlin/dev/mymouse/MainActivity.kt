package dev.mymouse

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.mymouse.core.gesture.GestureInterpreter
import dev.mymouse.core.gesture.TrackpadConfig
import dev.mymouse.core.protocol.Protocol

/**
 * Pantalla única de la app. Dos modos (docs/spec-v0.md §2.1):
 * - **Bloqueado:** trackpad a pantalla completa, sin barras y con la app fijada (lock task).
 * - **Ventana:** trackpad con margen, barras visibles y botones. Se alterna con Volumen − dos veces.
 */
class MainActivity : Activity() {

    private enum class Mode { LOCKED, WINDOW }

    private lateinit var connection: HostConnection
    private lateinit var trackpad: TrackpadView
    private lateinit var root: FrameLayout
    private lateinit var controls: LinearLayout

    private var mode = Mode.LOCKED
    private var lastVolumeDownAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val autoRelock = Runnable { if (mode == Mode.WINDOW) setMode(Mode.LOCKED) }
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminReceiver.applyKioskPolicies(this)

        // TODO (M4): cargar TrackpadConfig desde ajustes persistidos.
        val interpreter = GestureInterpreter(TrackpadConfig())
        connection = HostConnection(port = Protocol.DEFAULT_PORT)
        trackpad = TrackpadView(this, interpreter, connection::send)
        connection.onConnectionChanged = { connected -> trackpad.post { trackpad.connected = connected } }

        controls = buildControls()
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(trackpad, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(controls, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        }
        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mode = if (savedInstanceState?.getBoolean(KEY_WINDOW) == true) Mode.WINDOW else Mode.LOCKED
        applyLayout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WINDOW, mode == Mode.WINDOW)
    }

    override fun onResume() {
        super.onResume()
        connection.start()
        applyLayout()
        // Fijar la app cuando ya está en primer plano (llamarlo antes puede fallar).
        if (mode == Mode.LOCKED) root.post { startLock() } else scheduleAutoRelock()
    }

    override fun onPause() {
        handler.removeCallbacks(autoRelock)
        connection.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars() // el diálogo de "fijar app" u otros pueden mostrar las barras
    }

    // ---- Modos ----

    private fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        applyLayout()
        if (mode == Mode.LOCKED) {
            handler.removeCallbacks(autoRelock)
            startLock()
        } else {
            stopLock()
            scheduleAutoRelock()
        }
    }

    private fun applyLayout() {
        val window = mode == Mode.WINDOW
        controls.visibility = if (window) View.VISIBLE else View.GONE
        root.setBackgroundColor(if (window) Color.rgb(32, 32, 32) else Color.BLACK)
        val m = if (window) (16 * density).toInt() else 0
        (trackpad.layoutParams as FrameLayout.LayoutParams).setMargins(m, if (window) (56 * density).toInt() else 0, m, m)
        trackpad.requestLayout()
        trackpad.framed = window
        trackpad.cheatSheet.rows = CheatSheet.DEFAULT_ROWS +
            CheatSheet.Row(CheatSheet.Icon.VOLUME, if (window) "Volver a bloquear" else "Salir del bloqueo")
        trackpad.invalidate()
        applySystemBars()
    }

    private fun applySystemBars() {
        val window = mode == Mode.WINDOW
        WindowCompat.setDecorFitsSystemWindows(this.window, window)
        WindowInsetsControllerCompat(this.window, this.window.decorView).apply {
            if (window) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun startLock() {
        if (mode != Mode.LOCKED || isLockTaskActive()) return
        try {
            // Sin ser propietaria del dispositivo, Android pide confirmación ("Fijar aplicación").
            startLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo fijar la app: ${e.message}")
        }
    }

    private fun stopLock() {
        if (!isLockTaskActive()) return
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo soltar la app: ${e.message}")
        }
    }

    private fun isLockTaskActive(): Boolean =
        getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    private fun scheduleAutoRelock() {
        handler.removeCallbacks(autoRelock)
        if (mode == Mode.WINDOW) handler.postDelayed(autoRelock, AUTO_RELOCK_MS)
    }

    // ---- Teclas y toques ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.dispatchKeyEvent(event)
        }
        // Las teclas de volumen se consumen siempre: no cambian el volumen del móvil.
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = SystemClock.uptimeMillis()
            if (now - lastVolumeDownAt <= DOUBLE_PRESS_MS) {
                lastVolumeDownAt = 0
                setMode(if (mode == Mode.LOCKED) Mode.WINDOW else Mode.LOCKED)
            } else {
                lastVolumeDownAt = now
            }
        }
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) scheduleAutoRelock()
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // En modo bloqueado, Atrás no hace nada (cerraría la app). En modo ventana, sale.
        if (mode == Mode.WINDOW) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ---- Botones del modo ventana ----

    private fun buildControls(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val p = (8 * density).toInt()
        setPadding(p, p / 2, p, p / 2)
        addView(button("Bloquear") { setMode(Mode.LOCKED) })
        addView(button("Salir") {
            stopLock()
            finishAndRemoveTask()
        })
        if (AdminReceiver.isDeviceOwner(this@MainActivity)) {
            addView(button("Quitar propietario") { confirmReleaseOwner() })
        }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(220, 220, 220))
        setBackgroundColor(Color.rgb(60, 60, 60))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = (8 * density).toInt() }
    }

    private fun confirmReleaseOwner() {
        AlertDialog.Builder(this)
            .setTitle("Quitar propietario del dispositivo")
            .setMessage("MyMouse dejará de bloquear el móvil por completo y volverá la pantalla de bloqueo. Se puede volver a activar desde el PC con adb.")
            .setPositiveButton("Quitar") { _, _ ->
                AdminReceiver.releaseDeviceOwner(this)
                recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private companion object {
        const val TAG = "MyMouse"
        const val KEY_WINDOW = "window_mode"
        const val DOUBLE_PRESS_MS = 500L
        const val AUTO_RELOCK_MS = 30_000L
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
