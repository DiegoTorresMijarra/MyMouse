package dev.mymouse

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.mymouse.core.gesture.GestureInterpreter
import dev.mymouse.core.protocol.Protocol

/**
 * Pantalla única de la app. Dos modos (docs/spec-v0.md §2.1):
 * - **Bloqueado:** trackpad a pantalla completa, sin barras y con la app fijada (lock task).
 * - **Ventana:** trackpad con margen, barras visibles y botones. Se alterna con Volumen − dos veces.
 * La app arranca en modo ventana: el usuario configura y bloquea cuando quiere.
 */
class MainActivity : Activity() {

    private enum class Mode { LOCKED, WINDOW }

    private lateinit var connection: HostConnection
    private lateinit var trackpad: TrackpadView
    private lateinit var root: FrameLayout
    private lateinit var controls: LinearLayout

    private var mode = Mode.WINDOW

    /** Hueco de las barras del sistema y del notch, para no dibujar los botones debajo. */
    private var systemInsets = Insets.NONE
    private var lastVolumeDownAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val autoRelock = Runnable { if (mode == Mode.WINDOW) setMode(Mode.LOCKED) }
    private var settings = AppSettings()
    private var dimmed = false
    private val dim = Runnable { setDimmed(true) }
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminReceiver.applyKioskPolicies(this)

        settings = AppSettings.load(this)
        val interpreter = GestureInterpreter(settings.trackpadConfig())
        connection = HostConnection(port = Protocol.DEFAULT_PORT)
        trackpad = TrackpadView(this, interpreter, connection::send)
        connection.onConnectionChanged = { connected -> trackpad.post { trackpad.connected = connected } }

        controls = buildControls()
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(trackpad, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(controls, FrameLayout.LayoutParams(MATCH, (CONTROLS_HEIGHT_DP * density).toInt(), Gravity.TOP))
        }
        // Desde Android 15 la app siempre se dibuja bajo las barras: se aplican los márgenes a mano.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            applyMargins() // sin tocar las barras: mostrarlas u ocultarlas vuelve a disparar este listener
            insets
        }
        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mode = if (savedInstanceState?.getBoolean(KEY_WINDOW, true) == false) Mode.LOCKED else Mode.WINDOW
        applyLayout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WINDOW, mode == Mode.WINDOW)
    }

    override fun onResume() {
        super.onResume()
        connection.start()
        applySettings()
        applyLayout()
        // Fijar la app cuando ya está en primer plano (llamarlo antes puede fallar).
        if (mode == Mode.LOCKED) root.post { startLock() } else scheduleAutoRelock()
    }

    override fun onPause() {
        handler.removeCallbacks(autoRelock)
        handler.removeCallbacks(dim)
        setDimmed(false)
        connection.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars() // el diálogo de "fijar app" u otros pueden mostrar las barras
    }

    // ---- Ajustes y atenuación ----

    private fun applySettings() {
        val loaded = AppSettings.load(this)
        if (loaded.trackpadConfig() != settings.trackpadConfig()) {
            trackpad.interpreter = GestureInterpreter(loaded.trackpadConfig())
        }
        settings = loaded
        trackpad.showCheatSheet = settings.showCheatSheet
        if (settings.debug != (trackpad.debugOverlay != null)) {
            trackpad.debugOverlay = if (settings.debug) DebugOverlay(density) else null
        }
        requestedOrientation = when (settings.orientation) {
            AppSettings.Orientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            AppSettings.Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            AppSettings.Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        scheduleDim()
    }

    private fun scheduleDim() {
        handler.removeCallbacks(dim)
        if (settings.dimAfterSeconds > 0) handler.postDelayed(dim, settings.dimAfterSeconds * 1000L)
    }

    /** Atenuar no apaga la pantalla: baja el brillo de esta ventana al mínimo. */
    private fun setDimmed(value: Boolean) {
        if (dimmed == value) return
        dimmed = value
        window.attributes = window.attributes.apply {
            screenBrightness = if (value) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
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
        applyMargins()
        trackpad.framed = window
        trackpad.cheatSheet.rows = CheatSheet.DEFAULT_ROWS +
            CheatSheet.Row(CheatSheet.Icon.VOLUME, if (window) "Bloquear / desbloquear" else "Salir del bloqueo")
        trackpad.invalidate()
        applySystemBars()
    }

    private fun applyMargins() {
        val window = mode == Mode.WINDOW
        val i = systemInsets
        (controls.layoutParams as FrameLayout.LayoutParams).setMargins(i.left, i.top, i.right, 0)
        controls.requestLayout()
        val lp = trackpad.layoutParams as FrameLayout.LayoutParams
        if (window) {
            val m = (WINDOW_MARGIN_DP * density).toInt()
            lp.setMargins(i.left + m, i.top + (CONTROLS_HEIGHT_DP * density).toInt(), i.right + m, i.bottom + m)
        } else {
            lp.setMargins(0, 0, 0, 0) // barras ocultas: toda la pantalla
        }
        trackpad.requestLayout()
    }

    private fun applySystemBars() {
        val window = mode == Mode.WINDOW
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
        if (mode != Mode.LOCKED || !settings.pinApp || isLockTaskActive()) return
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
        if (mode == Mode.WINDOW && settings.autoRelock) handler.postDelayed(autoRelock, AUTO_RELOCK_MS)
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
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            scheduleAutoRelock()
            setDimmed(false)
            scheduleDim()
        }
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
        addView(button("Ajustes") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
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
        const val CONTROLS_HEIGHT_DP = 64
        const val WINDOW_MARGIN_DP = 24
        const val DIM_BRIGHTNESS = 0.02f
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
