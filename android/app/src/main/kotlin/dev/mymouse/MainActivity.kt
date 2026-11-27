package dev.mymouse

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.mymouse.core.theme.ThemeChoice
import dev.mymouse.core.debug.WidgetDescriber
import dev.mymouse.core.gesture.TrackpadEngine
import dev.mymouse.core.protocol.Message
import dev.mymouse.core.protocol.Protocol
import dev.mymouse.core.protocol.ShapeStatus
import dev.mymouse.core.widgets.PanelReveal

/**
 * Pantalla única de la app. Dos modos (docs/spec-v0.md §2.1):
 * - **Bloqueado:** trackpad a pantalla completa, sin barras y con la app fijada (lock task).
 * - **Ventana:** trackpad con margen, barras visibles y botones. Se alterna con Volumen − dos veces.
 * La app arranca en modo ventana: el usuario configura y bloquea cuando quiere.
 */
class MainActivity : Activity() {

    private enum class Mode { LOCKED, WINDOW }

    private lateinit var connection: Transport

    /** Tipo de conexión con el que se creó [connection]; si cambia en Ajustes, se recrea la pantalla. */
    private var connectionType = AppSettings.Connection.USB
    private lateinit var trackpad: TrackpadView
    private lateinit var panel: WidgetPanelView
    private lateinit var root: FrameLayout
    private lateinit var controls: LinearLayout

    private var mode = Mode.WINDOW

    /** Hueco de las barras del sistema y del notch, para no dibujar los botones debajo. */
    private var systemInsets = Insets.NONE
    private var lastVolumeDownAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val autoRelock = Runnable { if (mode == Mode.WINDOW) setMode(Mode.LOCKED) }
    private var settings = AppSettings()

    /** Estado deseado del panel de widgets (se guarda en los ajustes) y cuánto está desplegado ahora (0 a 1). */
    private var panelShown = true
    private var panelProgress = 1f
    private var panelAnimator: ValueAnimator? = null

    /** Tamaño del contenedor con el que se calcularon los márgenes; al girar la pantalla hay que recalcularlos. */
    private var laidOutSize = 0 to 0
    private lateinit var shapes: ShapeStore

    /** Última lista de formas enviada al PC, para reenviarla solo si cambia (al volver de grabar o borrar formas). */
    private var lastSyncedShapes: List<Message>? = null
    private var dimmed = false
    private val dim = Runnable { setDimmed(true) }
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminReceiver.applyKioskPolicies(this)

        settings = AppSettings.load(this)
        GlassTheme.applyTheme(settings.theme) // antes de crear ninguna vista
        appliedTheme = settings.theme.normalized()
        shapes = ShapeStore(this)
        panelShown = settings.showWidgets
        panelProgress = if (panelShown) 1f else 0f
        connectionType = settings.connection
        connection = when (connectionType) {
            AppSettings.Connection.USB -> HostConnection(port = Protocol.DEFAULT_PORT)
            AppSettings.Connection.BLUETOOTH ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BluetoothHidTransport.shared(this)
                else HostConnection(port = Protocol.DEFAULT_PORT) // Android 8: sin Bluetooth HID
        }
        trackpad = TrackpadView(this, buildEngine(), connection::send)
        trackpad.shapeStore = shapes
        trackpad.onPanelGesture = ::onPanelGesture
        trackpad.systemGesturesAvailable = { connection.supportsSystemGestures }
        trackpad.toolboxAvailable = { connection.supportsToolbox }
        trackpad.toolboxUnavailableText = { connection.toolboxUnavailableText }
        // En modo Bluetooth, un toque en el punto de estado abre el diagnóstico (docs/spec-v4.5-estilo.md §4).
        if (connectionType == AppSettings.Connection.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            trackpad.linkInfo = { (connection as? BluetoothHidTransport)?.linkInfo() }
            trackpad.unassignedText = "sin acción (Ajustes → Acciones por Bluetooth)"
            trackpad.hostGestureList = phoneGestureList()
            trackpad.onStatusDotTap = {
                startActivity(Intent(this, BluetoothActivity::class.java).putExtra(BluetoothActivity.EXTRA_SHOW_DIAGNOSTICS, true))
            }
        }
        panel = WidgetPanelView(this, connection::sendWidget, trackpad::noteDebug)
        panel.backdrop = GlassTheme.BackdropBlur(trackpad::drawBackdrop)
        trackpad.cheatSheet.backdrop = GlassTheme.BackdropBlur(trackpad::drawBackdrop)
        connection.onConnectionChanged = { connected ->
            trackpad.post {
                trackpad.connected = connected
                if (!connected) {
                    trackpad.hostGestureList = phoneGestureList() // por Bluetooth, las acciones son del móvil y se ven sin conexión
                    lastSyncedShapes = null
                    panel.clear()
                    applyMargins()
                } else {
                    syncShapes() // el PC (v5) recibe la lista de formas propias nada más conectar
                }
            }
        }
        connection.onWidgetLayout = { layout ->
            panel.post {
                panel.setEntries(layout.entries)
                applyMargins() // el panel aparece (o desaparece) y el trackpad cede o recupera su sitio
            }
        }
        connection.onVolumeState = { state -> panel.post { panel.onVolumeState(state) } }
        connection.onMediaState = { state -> panel.post { panel.onMediaState(state) } }
        connection.onWidgetResult = { result ->
            panel.post {
                val entry = panel.entries.firstOrNull { it.id == result.id }
                WidgetDescriber.resultNotice(entry, result.status)?.let(trackpad::showNotice)
            }
        }
        connection.onShapeAdd = { add -> handler.post { applyShapeRequest(add) } }
        connection.onShapeDelete = { delete -> handler.post { applyShapeRequest(delete) } }
        connection.onGestureList = { list -> trackpad.post { trackpad.hostGestureList = list } }
        connection.onGestureResult = { status -> trackpad.post { trackpad.showGestureStatus(status) } }

        controls = buildControls()
        root = FrameLayout(this).apply {
            background = GlassTheme.universe(this@MainActivity, trackpad = true) // universo (estático salvo que se active) detrás del trackpad (también en modo bloqueado)
            addView(trackpad, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(panel, FrameLayout.LayoutParams(MATCH, MATCH)) // el tamaño y el sitio los fija applyMargins
            // Un toque que empieza en el panel nunca llega al trackpad, ni al revés: todos los dedos van a la vista
            // que recibió el primero (sin esto, un segundo dedo sobre la otra vista le llegaría a ella).
            isMotionEventSplittingEnabled = false
            addView(controls, FrameLayout.LayoutParams(MATCH, (CONTROLS_HEIGHT_DP * density).toInt(), Gravity.TOP))
        }
        // Desde Android 15 la app siempre se dibuja bajo las barras: se aplican los márgenes a mano.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            applyMargins() // sin tocar las barras: mostrarlas u ocultarlas vuelve a disparar este listener
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            // Al girar el móvil el panel cambia de lado y de tamaño.
            val size = (right - left) to (bottom - top)
            if (size != laidOutSize) {
                laidOutSize = size
                applyMargins()
            }
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
        if (AppSettings.load(this).connection != connectionType) {
            recreate() // se ha cambiado entre USB y Bluetooth en Ajustes
            return
        }
        connection.start()
        applySettings()
        applyLayout()
        // Fijar la app cuando ya está en primer plano (llamarlo antes puede fallar).
        if (mode == Mode.LOCKED) root.post { startLock() } else scheduleAutoRelock()
    }

    override fun onPause() {
        finishPanelAnimation()
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

    private fun buildEngine(): TrackpadEngine {
        // Por Bluetooth las acciones de las formas son del móvil (bt-gestures.json): el modo gestos funciona igual.
        val config = settings.trackpadConfig().let { if (connection.supportsGestures) it else it.copy(gestureFingers = 0) }
        val recognizer = if (config.gestureFingers > 0) shapes.recognizer(minSizePx = 40 * density) else null
        return TrackpadEngine(config, recognizer)
    }

    /** Por Bluetooth, las formas con acción de `bt-gestures.json` para la hoja del modo gestos; por USB, null (la manda el PC). */
    private fun phoneGestureList(): Message.GestureList? {
        if (connectionType != AppSettings.Connection.BLUETOOTH || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val transport = connection as? BluetoothHidTransport ?: return null
        transport.reloadActions()
        return transport.gestureList(shapes.enabled)
    }

    /** Tema con el que está pintada esta pantalla (ver [applySettings]). */
    private var appliedTheme: ThemeChoice? = null

    private fun invalidateTree(view: View) {
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
    }

    private fun applySettings() {
        settings = AppSettings.load(this)
        // Se cambió el tema en Ajustes: fondo y acento nuevos (docs/spec-v4.8-temas.md §3). Se compara con el tema que
        // tiene pintado ESTA pantalla y no con GlassTheme.choice: Ajustes ya cambia GlassTheme al momento, así que esa
        // comparación nunca veía el cambio y el trackpad se quedaba con el tema viejo hasta reiniciar la app.
        val theme = settings.theme.normalized()
        if (theme != appliedTheme) {
            appliedTheme = theme
            GlassTheme.applyTheme(theme)
            root.background = GlassTheme.universe(this, trackpad = true)
            trackpad.onThemeChanged()
            invalidateTree(root) // botones, panel de widgets y demás vistas leen el acento al dibujarse
        }
        if (panelAnimator == null) {
            panelShown = settings.showWidgets
            panelProgress = if (panelShown) 1f else 0f
        }
        // Las formas o los ajustes pueden haber cambiado en otras pantallas: se recrea el motor.
        shapes = ShapeStore(this)
        trackpad.shapeStore = shapes
        trackpad.interpreter = buildEngine()
        trackpad.showCheatSheet = settings.showCheatSheet
        phoneGestureList()?.let { trackpad.hostGestureList = it } // el editor de "Acciones por Bluetooth" pudo cambiarlas
        if (connectionType == AppSettings.Connection.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (connection as? BluetoothHidTransport)?.intervalMs = settings.bluetoothIntervalMs.toLong()
        }
        applyMargins() // "Mostrar panel de widgets" y "Tamaño del panel" pueden haber cambiado
        if (settings.debug != (trackpad.debugOverlay != null)) {
            trackpad.debugOverlay = if (settings.debug) DebugOverlay(density) else null
        }
        requestedOrientation = when (settings.orientation) {
            AppSettings.Orientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            AppSettings.Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            AppSettings.Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        scheduleDim()
        syncShapes() // las formas pueden haber cambiado en ShapesActivity o RecordShapeActivity
    }

    // ---- Formas sincronizadas con el PC (docs/spec-v4-toolbox.md §5 y §7) ----

    /** Envía la lista de formas propias al PC si hay toolbox y es distinta de la última enviada. */
    private fun syncShapes() {
        if (!connection.supportsToolbox) return
        val messages = shapes.syncMessages()
        if (messages == lastSyncedShapes) return
        lastSyncedShapes = messages
        connection.sendShapes(messages)
    }

    /** `SHAPE_ADD` o `SHAPE_DELETE` del PC: se aplican sobre lo guardado, se contesta y, si cambió algo, se reenvía la lista. */
    private fun applyShapeRequest(request: Message) {
        val store = ShapeStore(this)
        val result = store.applyRequest(request)
        val replies = mutableListOf<Message>(result)
        if (result.status == ShapeStatus.SAVED || result.status == ShapeStatus.DELETED) {
            shapes = store
            trackpad.shapeStore = store
            trackpad.interpreter = buildEngine() // el reconocedor debe conocer la forma nueva (o dejar de conocer la borrada)
            val list = store.syncMessages()
            lastSyncedShapes = list
            replies += list
            ShapeStore.onExternalChange?.invoke()
        }
        connection.sendShapes(replies)
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
        applyMargins()
        trackpad.framed = window
        panel.framed = window // el panel de widgets: cristal con radio 16 en modo ventana, a pantalla completa sin radio en bloqueado
        trackpad.cheatSheet.rows = CheatSheet.rowsFor(
            windowsGestures = settings.windowsGestures,
            gestureMode = connection.supportsGestures && settings.gestureFingers == 4,
        ) + CheatSheet.Row(CheatSheet.Icon.VOLUME, if (window) "Bloquear / desbloquear" else "Salir del bloqueo")
        trackpad.invalidate()
        applySystemBars()
    }

    private fun applyMargins() {
        val window = mode == Mode.WINDOW
        val i = systemInsets
        (controls.layoutParams as FrameLayout.LayoutParams).setMargins(i.left, i.top, i.right, 0)
        controls.requestLayout()

        // Área del trackpad (y del panel): sin las barras del sistema y, en modo ventana, con margen y sin los botones.
        val m = if (window) (WINDOW_MARGIN_DP * density).toInt() else 0
        val left = if (window) i.left + m else 0
        val top = if (window) i.top + (CONTROLS_HEIGHT_DP * density).toInt() else 0
        val right = if (window) i.right + m else 0
        val bottom = if (window) i.bottom + m else 0 // barras ocultas en modo bloqueado: toda la pantalla

        // El panel va abajo en vertical y a la derecha en horizontal. Solo se ve si el PC ha enviado widgets.
        // Se coloca siempre con su tamaño completo y se desliza con una traslación (recortada al área del trackpad);
        // el trackpad cede el sitio en proporción a lo desplegado que esté ([panelProgress]).
        val landscape = root.width > root.height
        panel.landscape = landscape
        val hasPanel = panel.hasWidgets && root.width > 0 && root.height > 0
        val progress = panelProgress
        val showPanel = hasPanel && progress > 0f
        var panelExtent = 0
        if (hasPanel) {
            val panelLp = panel.layoutParams as FrameLayout.LayoutParams
            if (landscape) {
                panelExtent = settings.widgetPanelSize.extent(root.width - left - right)
                panelLp.width = panelExtent
                panelLp.height = MATCH
                panelLp.gravity = Gravity.END
                panelLp.setMargins(0, top, right, bottom)
            } else {
                panelExtent = settings.widgetPanelSize.extent(root.height - top - bottom)
                panelLp.width = MATCH
                panelLp.height = panelExtent
                panelLp.gravity = Gravity.BOTTOM
                panelLp.setMargins(left, 0, right, bottom)
            }
            panel.requestLayout()
        }
        panel.visibility = if (showPanel) View.VISIBLE else View.GONE
        val hidden = ((1f - progress) * panelExtent)
        panel.translationX = if (landscape) hidden else 0f
        panel.translationY = if (landscape) 0f else hidden
        panel.clipBounds = if (showPanel && progress < 1f) {
            if (landscape) Rect(0, 0, (panelExtent - hidden).toInt(), root.height)
            else Rect(0, 0, root.width, (panelExtent - hidden).toInt())
        } else {
            null
        }

        val used = (panelExtent * progress).toInt()
        val lp = trackpad.layoutParams as FrameLayout.LayoutParams
        lp.setMargins(
            left, top,
            right + if (landscape) used else 0,
            bottom + if (landscape) 0 else used,
        )
        trackpad.requestLayout()
    }

    // ---- Panel de widgets con 4 dedos (docs/spec-v3.1-ajustes.md §1.1) ----

    /** 4 dedos juntándose (show) o separándose. Con el panel vacío solo avisa. */
    private fun onPanelGesture(show: Boolean) {
        if (show && !panel.hasWidgets) {
            trackpad.showNotice("No hay widgets: configúralos en el PC")
            return
        }
        if (show == panelShown) return
        panelShown = show
        settings = settings.copy(showWidgets = show).also { it.save(this) }
        animatePanel(show)
    }

    private fun animatePanel(show: Boolean) {
        panelAnimator?.cancel()
        val from = panelProgress
        val to = if (show) 1f else 0f
        if (from == to || !panel.hasWidgets) {
            panelProgress = to
            applyMargins()
            return
        }
        val full = if (show) PanelReveal.SLIDE_IN_MS else PanelReveal.SLIDE_OUT_MS
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = (full * kotlin.math.abs(to - from)).toLong().coerceAtLeast(1L)
        // Entra decelerando y sale acelerando.
        animator.interpolator = if (show) DecelerateInterpolator(1.8f) else AccelerateInterpolator(1.5f)
        animator.addUpdateListener {
            panelProgress = it.animatedValue as Float
            applyMargins()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (panelAnimator === animation) {
                    panelAnimator = null
                    panel.inputBlocked = false
                }
                panelProgress = to
                applyMargins()
            }
        })
        panelAnimator = animator
        panel.inputBlocked = true
        if (show) panel.playReveal()
        animator.start()
    }

    /** Termina al instante una animación en curso (al pausar la app). */
    private fun finishPanelAnimation() {
        val a = panelAnimator ?: return
        panelAnimator = null
        a.cancel() // onAnimationEnd deja el panel en su estado final
        panel.inputBlocked = false
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

    /** Botones del modo ventana: píldoras de cristal de 44 dp con icono dibujado con trazos y texto. */
    private fun buildControls(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val p = (8 * density).toInt()
        setPadding(p, p / 2, p, p / 2)
        addView(iconButton(GlassTheme.IconButton.Kind.SLIDERS, "Ajustes") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
        addView(iconButton(GlassTheme.IconButton.Kind.LOCK, "Bloquear") { setMode(Mode.LOCKED) })
        addView(iconButton(GlassTheme.IconButton.Kind.POWER, "Salir") {
            stopLock()
            finishAndRemoveTask()
        })
        if (AdminReceiver.isDeviceOwner(this@MainActivity)) {
            addView(GlassTheme.button(this@MainActivity, "Quitar propietario") { confirmReleaseOwner() }.apply {
                (layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; marginStart = (8 * density).toInt(); marginEnd = 0 }
            })
        }
    }

    private fun iconButton(kind: GlassTheme.IconButton.Kind, description: String, onClick: () -> Unit) =
        GlassTheme.IconButton(this, kind, label = description).apply {
            contentDescription = description
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (44 * density).toInt()).apply { marginStart = (8 * density).toInt() }
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
