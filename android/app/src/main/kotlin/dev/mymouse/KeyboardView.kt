package dev.mymouse

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import dev.mymouse.core.gesture.OutputAction
import dev.mymouse.core.gesture.TouchInput
import dev.mymouse.core.gesture.TrackpadConfig
import dev.mymouse.core.gesture.TrackpadEngine
import dev.mymouse.core.keyboard.ComboHistory
import dev.mymouse.core.keyboard.KeyDef
import dev.mymouse.core.keyboard.KeyEvent
import dev.mymouse.core.keyboard.KeyKind
import dev.mymouse.core.keyboard.KeySize
import dev.mymouse.core.keyboard.KeyUsages
import dev.mymouse.core.keyboard.KeyboardBlock
import dev.mymouse.core.keyboard.KeyboardCombo
import dev.mymouse.core.keyboard.KeyboardController
import dev.mymouse.core.keyboard.KeyboardGeometry
import dev.mymouse.core.keyboard.KeyboardLayoutSetting
import dev.mymouse.core.keyboard.KeyboardModel
import dev.mymouse.core.keyboard.KeyboardSettings
import dev.mymouse.core.keyboard.ModifierState
import kotlin.math.abs
import kotlin.math.min

/**
 * El teclado del modo teclado (docs/spec-v6-teclado.md §4 y §5): una sola vista con multitáctil que dibuja las teclas con los tokens
 * del tema y envía **teclas físicas** (usos HID), no texto. La lógica (qué teclas hay, modificadores, repetición, luces) es de `core`
 * y está probada ([KeyboardModel], [KeyboardController]); aquí solo se dibuja y se traducen los toques.
 *
 * Arriba queda el espacio libre con la barra de estado (y, si se ha activado, una barra de trackpad); abajo, el teclado; encima del
 * teclado, la barra de chips que abre la tecla `⌨`.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    // ---- Lo que rodea a la vista (lo rellena MainActivity) ----

    /** Una tecla que enviar al PC (pulsar o soltar). */
    var onKey: (KeyEvent) -> Unit = {}

    /** Movimiento, clic y scroll de la barra de trackpad. */
    var onPointer: (OutputAction) -> Unit = {}

    /** Se ha tocado `🖱`: volver al trackpad. */
    var onExit: () -> Unit = {}

    /** Cambió la combinación de bloques de alguna orientación (texto para los ajustes: vertical, horizontal). */
    var onCombosChanged: (String, String) -> Unit = { _, _ -> }

    /** Texto del enlace ("USB", "Bluetooth · 120 inf/s · 8 ms"), o null. */
    var linkInfo: () -> String? = { null }

    var connected = false
        set(value) {
            field = value
            invalidate()
        }

    /** Modo Bluetooth: Windows repite las teclas y no informa de su distribución. */
    var bluetooth = false
        set(value) {
            field = value
            updateRepeat()
            relayout(animate = false)
        }

    /** Ajuste de texto de Bluetooth, que usa la distribución "Automática" por Bluetooth. */
    var textLayoutId = "es"
        set(value) {
            field = value
            relayout(animate = false)
        }

    /** Distribución del PC (`KEYBOARD_LAYOUT`), solo por USB; null mientras no llegue. */
    var hostLayoutId: String? = null
        set(value) {
            field = value
            relayout(animate = true)
        }

    var layoutSetting = KeyboardLayoutSetting.AUTO
        set(value) {
            field = value
            layoutOverride = null
            relayout(animate = false)
        }

    /** Cambio temporal de distribución con el chip ES/US (no se guarda). */
    private var layoutOverride: String? = null

    var keySize = KeySize.MEDIUM
        set(value) {
            field = value
            relayout(animate = false)
        }

    var vibrate = true

    var repeatSetting = true
        set(value) {
            field = value
            updateRepeat()
        }

    var trackpadBar = false
        set(value) {
            field = value
            invalidate()
        }

    /** Modo ventana: esquinas redondeadas; en modo bloqueado, a pantalla completa. */
    var framed = false
        set(value) {
            field = value
            invalidate()
        }

    // ---- Estado ----

    private val density = resources.displayMetrics.density
    private val controller = KeyboardController(repeatEnabled = true)
    private val comboPortrait = ComboHistory(KeyboardCombo.default(landscape = false))
    private val comboLandscape = ComboHistory(KeyboardCombo.default(landscape = true))
    private var landscape = false
    private var chipsOpen = false
    private var chipsProgress = 0f

    /** Teclas con un dedo encima ahora mismo (incluidas `⌨` y `🖱`, que no pasan por el controlador). */
    private val held = HashSet<KeyDef>()

    private class Placement(val geometry: KeyboardGeometry, val unitW: Float, val rowH: Float, val top: Float, val left: Float)

    private var current: Placement? = null
    private var previous: Placement? = null
    private var blend = 1f
    private var blendAnimator: ValueAnimator? = null
    private var chipsAnimator: ValueAnimator? = null

    fun setCombos(portrait: String?, landscape: String?) {
        comboPortrait.current = KeyboardCombo.decode(portrait, landscape = false)
        comboPortrait.previous = null
        comboLandscape.current = KeyboardCombo.decode(landscape, landscape = true)
        comboLandscape.previous = null
        relayout(animate = false)
    }

    private val history get() = if (landscape) comboLandscape else comboPortrait

    fun currentLayoutId(): String =
        layoutOverride ?: KeyboardSettings.resolveLayout(layoutSetting, bluetooth, hostLayoutId, textLayoutId)

    // ---- Luces ----

    /** Las luces del PC (`KEYBOARD_LEDS`, o el informe de salida de Bluetooth). */
    fun onHostLeds(bits: Int) {
        controller.leds.onHostLeds(bits)
        invalidate()
    }

    /** Sin conexión: las luces vuelven a suponerse y nada queda pulsado (el PC suelta lo suyo). */
    fun onDisconnected() {
        controller.releaseAll()
        held.clear()
        controller.leds.reset()
        hostLayoutId = null
        removeCallbacks(repeatTick)
        invalidate()
    }

    private fun updateRepeat() {
        // Por Bluetooth la repetición la hace Windows: el móvil nunca repite.
        controller.repeatEnabled = repeatSetting && !bluetooth
    }

    // ---- Barra de trackpad (docs/spec-v6-teclado.md §4) ----

    private var barEngine = buildBarEngine(TrackpadConfig())
    private val barPointers = HashSet<Int>()

    private fun buildBarEngine(config: TrackpadConfig) =
        // Sin gestos de 3 o 4 dedos: solo mover, clic, scroll y zoom.
        TrackpadEngine(config.copy(gestureFingers = 0, windowsGestures = false), null)

    fun configureTrackpad(config: TrackpadConfig) {
        sendBar(barEngine.onTouch(TouchInput.Cancel(SystemClock.uptimeMillis())))
        barPointers.clear()
        barEngine = buildBarEngine(config)
    }

    private val barTick = Runnable {
        sendBar(barEngine.onTick(SystemClock.uptimeMillis()))
        scheduleBarTick()
    }

    private fun scheduleBarTick() {
        removeCallbacks(barTick)
        val deadline = barEngine.nextDeadlineMs ?: return
        postDelayed(barTick, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    private fun sendBar(actions: List<OutputAction>) {
        for (a in actions) {
            when (a) {
                is OutputAction.PointerMove, is OutputAction.Scroll, is OutputAction.Zoom -> onPointer(a)
                is OutputAction.Button -> {
                    // Un clic cuenta como una tecla: los modificadores en espera (Ctrl, Shift…) se aplican y luego se sueltan.
                    if (a.pressed) controller.onPointerClickStart()
                    onPointer(a)
                    if (!a.pressed) emit(controller.afterPointerClick())
                }
                else -> Unit // los gestos de varios dedos no existen en la barra
            }
        }
    }

    // ---- Salida ----

    private fun emit(events: List<KeyEvent>) {
        if (events.isEmpty()) return
        for (e in events) onKey(e)
        invalidate()
    }

    /** Suelta todo lo que esté pulsado: al salir, al cambiar de bloques, al pasar a segundo plano o al perder la conexión (§5.5). */
    fun releaseAll() {
        emit(controller.releaseAll())
        held.clear()
        removeCallbacks(repeatTick)
        if (barPointers.isNotEmpty()) {
            sendBar(barEngine.onTouch(TouchInput.Cancel(SystemClock.uptimeMillis())))
            barPointers.clear()
        }
        invalidate()
    }

    private val repeatTick = Runnable {
        emit(controller.tick(SystemClock.uptimeMillis()))
        scheduleRepeat()
    }

    private fun scheduleRepeat() {
        removeCallbacks(repeatTick)
        val deadline = controller.nextDeadlineMs ?: return
        postDelayed(repeatTick, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    private fun vibrateTap() {
        if (!vibrate) return
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(TAP_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: SecurityException) {
            // Sin permiso de vibración: no es imprescindible.
        }
    }

    // ---- Distribución en pantalla ----

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseAll() // un giro con teclas pulsadas no debe dejarlas abajo
        relayout(animate = false)
    }

    /** Recalcula las teclas y su sitio; con [animate], las que existen antes y después se desplazan y el resto se funde (150 ms). */
    private fun relayout(animate: Boolean) {
        if (width == 0 || height == 0) return
        val newLandscape = width > height
        if (newLandscape != landscape) {
            landscape = newLandscape
            releaseAll()
        }
        val combo = history.current.normalized(landscape)
        val geometry = KeyboardModel.build(currentLayoutId(), combo, landscape)
        val old = current
        val unitW = width.toFloat() / geometry.widthUnits
        val wanted = KeyboardSettings.BASE_ROW_DP * density * keySize.heightFactor
        val rowH = min(wanted, height * MAX_KEYBOARD_FRACTION / geometry.heightUnits)
        val next = Placement(geometry, unitW, rowH, height - rowH * geometry.heightUnits - BOTTOM_PAD_DP * density, 0f)
        if (old != null && old.geometry == next.geometry && old.unitW == next.unitW && old.rowH == next.rowH) {
            current = next
            invalidate()
            return
        }
        current = next
        blendAnimator?.cancel()
        if (animate && old != null) {
            previous = old
            blend = 0f
            blendAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SWITCH_MS
                addUpdateListener {
                    blend = it.animatedValue as Float
                    if (blend >= 1f) previous = null
                    invalidate()
                }
                start()
            }
        } else {
            previous = null
            blend = 1f
        }
        invalidate()
    }

    private fun rect(p: Placement, k: KeyDef, out: RectF): RectF {
        val gap = KEY_GAP_DP * density / 2
        out.set(p.left + k.x * p.unitW + gap, p.top + k.y * p.rowH + gap, p.left + k.right * p.unitW - gap, p.top + k.bottom * p.rowH - gap)
        return out
    }

    private fun keyAtPoint(x: Float, y: Float): KeyDef? {
        val p = current ?: return null
        return p.geometry.keyAt((x - p.left) / p.unitW, (y - p.top) / p.rowH)
    }

    // ---- Barra de chips (docs/spec-v6-teclado.md §4.2) ----

    private class Chip(val label: String, val block: KeyboardBlock?)

    private fun chips(): List<Chip> = listOf(
        Chip(UiText.keyboardBlock(context, "fn"), KeyboardBlock.FN), Chip(UiText.keyboardBlock(context, "arrows"), KeyboardBlock.ARROWS),
        Chip(UiText.keyboardBlock(context, "nav"), KeyboardBlock.NAV), Chip(UiText.keyboardBlock(context, "num"), KeyboardBlock.NUMPAD),
        Chip(UiText.keyboardBlock(context, "numonly"), KeyboardBlock.NUMPAD_ONLY), Chip(currentLayoutId().uppercase(), null),
    )

    private fun chipsTop(): Float = (current?.top ?: height.toFloat()) - CHIPS_HEIGHT_DP * density * chipsProgress

    private fun chipRect(index: Int, out: RectF): RectF {
        val n = chips().size
        val margin = 8 * density
        val gap = 6 * density
        val w = (width - 2 * margin - gap * (n - 1)) / n
        val top = (current?.top ?: height.toFloat()) - CHIPS_HEIGHT_DP * density + 6 * density
        out.set(margin + index * (w + gap), top, margin + index * (w + gap) + w, top + (CHIPS_HEIGHT_DP - 12) * density)
        return out
    }

    private fun chipAt(x: Float, y: Float): Int? {
        if (chipsProgress < 1f) return null
        val r = RectF()
        for (i in chips().indices) if (chipRect(i, r).contains(x, y)) return i
        return null
    }

    private fun setChips(open: Boolean) {
        if (chipsOpen == open) return
        chipsOpen = open
        chipsAnimator?.cancel()
        chipsAnimator = ValueAnimator.ofFloat(chipsProgress, if (open) 1f else 0f).apply {
            duration = CHIPS_MS
            addUpdateListener {
                chipsProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun toggleChip(index: Int) {
        val chip = chips()[index]
        val block = chip.block
        if (block == null) {
            // ES ⇄ US, solo mientras dure esta sesión (el ajuste de siempre está en Ajustes → Teclado).
            releaseAll()
            layoutOverride = if (currentLayoutId() == "es") "us" else "es"
            relayout(animate = true)
            return
        }
        applyCombo(history.current.toggled(block, landscape))
    }

    private fun applyCombo(next: KeyboardCombo) {
        releaseAll() // al cambiar de bloques con teclas pulsadas, se sueltan todas
        history.change(next)
        relayout(animate = true)
        onCombosChanged(comboPortrait.current.encode(), comboLandscape.current.encode())
    }

    /** Deslizar sobre `⌨`: como Alt+Tab entre las dos últimas combinaciones. */
    private fun swapCombos() {
        releaseAll()
        if (history.swap()) {
            relayout(animate = true)
            onCombosChanged(comboPortrait.current.encode(), comboLandscape.current.encode())
        }
    }

    // ---- Toques ----

    private sealed interface Owner {
        class KeyOwner(val key: KeyDef, val downX: Float, val downT: Long) : Owner {
            var swiped = false
        }

        class ChipOwner(val index: Int) : Owner
        data object Bar : Owner
        data object None : Owner
    }

    private val owners = SparseArray<Owner>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val i = event.actionIndex
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(event.getPointerId(i), event.getX(i), event.getY(i), event.eventTime)
            MotionEvent.ACTION_MOVE -> {
                var lastBar = -1
                for (p in 0 until event.pointerCount) if (owners[event.getPointerId(p)] === Owner.Bar) lastBar = p
                for (p in 0 until event.pointerCount) {
                    val id = event.getPointerId(p)
                    when (val o = owners[id]) {
                        is Owner.KeyOwner -> moveKey(o, event.getX(p))
                        Owner.Bar -> {
                            val (bx, by) = barOrigin()
                            sendBar(barEngine.onTouch(TouchInput.Move(id, event.getX(p) - bx, event.getY(p) - by, event.eventTime, p == lastBar)))
                        }
                        else -> Unit
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                up(event.getPointerId(i), event.getX(i), event.getY(i), event.eventTime, cancelled = false)
            MotionEvent.ACTION_CANCEL -> {
                for (p in 0 until event.pointerCount) up(event.getPointerId(p), event.getX(p), event.getY(p), event.eventTime, cancelled = true)
            }
        }
        scheduleRepeat()
        scheduleBarTick()
        invalidate()
        return true
    }

    private fun down(id: Int, x: Float, y: Float, time: Long) {
        // 1. La barra de chips (si está desplegada del todo).
        chipAt(x, y)?.let {
            owners.put(id, Owner.ChipOwner(it))
            vibrateTap()
            return
        }
        // 2. El teclado.
        val key = keyAtPoint(x, y)
        if (key != null && key.kind != KeyKind.SPACER) {
            owners.put(id, Owner.KeyOwner(key, x, time))
            held += key
            vibrateTap()
            // Pulsar cualquier tecla (menos `⌨`) cierra la barra de chips.
            if (key.kind != KeyKind.MODE && chipsOpen) setChips(false)
            val events = controller.press(id, key, time)
            emit(events)
            return
        }
        // 3. La barra de trackpad, si está activada y el dedo cae en el espacio libre de arriba.
        if (trackpadBar && barRect().contains(x, y)) {
            owners.put(id, Owner.Bar)
            barPointers += id
            val (bx, by) = barOrigin()
            sendBar(barEngine.onTouch(TouchInput.Down(id, x - bx, y - by, time)))
            return
        }
        owners.put(id, Owner.None)
    }

    private fun moveKey(o: Owner.KeyOwner, x: Float) {
        if (o.key.kind != KeyKind.MODE || o.swiped) return
        if (abs(x - o.downX) > SWIPE_DP * density) {
            o.swiped = true
            swapCombos()
        }
    }

    private fun up(id: Int, x: Float, y: Float, time: Long, cancelled: Boolean) {
        val owner = owners[id] ?: return
        owners.remove(id)
        when (owner) {
            is Owner.KeyOwner -> {
                held -= owner.key
                emit(controller.release(id, time))
                if (cancelled || owner.swiped) return
                val inside = keyAtPoint(x, y) == owner.key
                when (owner.key.kind) {
                    KeyKind.MODE -> if (abs(x - owner.downX) < TAP_SLOP_DP * density) setChips(!chipsOpen)
                    KeyKind.MOUSE -> if (inside) onExit()
                    else -> Unit
                }
            }
            is Owner.ChipOwner -> if (!cancelled && chipAt(x, y) == owner.index) toggleChip(owner.index)
            Owner.Bar -> {
                barPointers -= id
                sendBar(barEngine.onTouch(if (cancelled) TouchInput.Cancel(time) else {
                    val (bx, by) = barOrigin()
                    TouchInput.Up(id, x - bx, y - by, time)
                }))
            }
            Owner.None -> Unit
        }
    }

    /** El hueco libre de arriba donde va la barra de trackpad (debajo de la barra de estado y encima de los chips). */
    private fun barRect(): RectF {
        val top = STATUS_HEIGHT_DP * density
        val bottom = chipsTop() - 8 * density
        val m = 8 * density
        return RectF(m, top, width - m, maxOf(top, bottom))
    }

    private fun barOrigin(): Pair<Float, Float> = barRect().let { it.left to it.top }

    // ---- Dibujo ----

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * density }
    private val tmp = RectF()
    private val tmp2 = RectF()
    private val lockPath = Path()

    private fun keyId(k: KeyDef): Int = if (k.usage != 0) k.usage else -1 - k.kind.ordinal

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cur = current ?: return
        drawBackground(canvas)
        drawStatus(canvas)
        if (trackpadBar) drawBar(canvas)

        val prev = previous
        val shiftOn = controller.modifiers.isDown(KeyUsages.SHIFT) || controller.modifiers.isDown(KeyUsages.SHIFT_RIGHT)
        val altGrOn = controller.modifiers.isDown(KeyUsages.ALT_GR) ||
            (controller.modifiers.isDown(KeyUsages.CTRL) && controller.modifiers.isDown(KeyUsages.ALT))
        val capsOn = controller.leds.caps
        val old = prev?.geometry?.keys?.filter { it.kind != KeyKind.SPACER }?.associateBy(::keyId).orEmpty()

        if (prev != null) {
            val now = cur.geometry.keys.map(::keyId).toSet()
            for ((id, k) in old) if (id !in now) drawKey(canvas, k, rect(prev, k, tmp), 1f - blend, shiftOn, altGrOn, capsOn)
        }
        for (k in cur.geometry.keys) {
            if (k.kind == KeyKind.SPACER) continue
            val target = rect(cur, k, tmp)
            val from = old[keyId(k)]
            if (prev != null && from != null) {
                val a = rect(prev, from, tmp2)
                target.set(lerp(a.left, target.left), lerp(a.top, target.top), lerp(a.right, target.right), lerp(a.bottom, target.bottom))
                drawKey(canvas, k, target, 1f, shiftOn, altGrOn, capsOn)
            } else {
                drawKey(canvas, k, target, if (prev != null) blend else 1f, shiftOn, altGrOn, capsOn)
            }
        }
        if (chipsProgress > 0f) drawChips(canvas)
    }

    private fun lerp(a: Float, b: Float) = a + (b - a) * blend

    private fun drawBackground(canvas: Canvas) {
        // Cristal bajo el teclado, para que se lea sobre cualquier fondo.
        val cur = current ?: return
        val r = if (framed) GlassTheme.dp(context, GlassTheme.RADIUS_PANEL) else 0f
        fillPaint.color = GlassTheme.PANEL_FILL
        fillPaint.style = Paint.Style.FILL
        tmp.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(tmp, r, r, fillPaint)
        fillPaint.color = GlassTheme.panelSolid
        val top = cur.top - 6 * density
        tmp.set(0f, top, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(tmp, r, r, fillPaint)
        strokePaint.color = GlassTheme.BORDER
        canvas.drawLine(0f, top, width.toFloat(), top, strokePaint)
        if (framed) {
            val inset = strokePaint.strokeWidth / 2
            tmp.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(tmp, r, r, strokePaint)
        }
    }

    /** Conexión, distribución, luces y bloques encendidos en la parte libre de arriba. */
    private fun drawStatus(canvas: Canvas) {
        val cur = current ?: return
        if (cur.top - chipsProgress * CHIPS_HEIGHT_DP * density < STATUS_HEIGHT_DP * density) return // no cabe
        val d = density
        var x = 14 * d
        val y = 22 * d
        statusPaint.textAlign = Paint.Align.LEFT
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = if (connected) GlassTheme.GREEN else GlassTheme.TEXT_TERTIARY
        canvas.drawCircle(x + 4 * d, y - 4 * d, 4 * d, fillPaint)
        x += 14 * d
        statusPaint.color = GlassTheme.TEXT_SECONDARY
        val link = if (connected) linkInfo() ?: context.getString(R.string.kb_connected) else context.getString(R.string.kb_not_connected)
        canvas.drawText(link, x, y, statusPaint)
        x += statusPaint.measureText(link) + 14 * d

        statusPaint.color = GlassTheme.TEXT
        val parts = listOf(currentLayoutId().uppercase()) + history.current.normalized(landscape).blocks().map { UiText.keyboardBlock(context, it) }
        val blocks = parts.joinToString(" · ")
        canvas.drawText(blocks, x, y, statusPaint)
        x += statusPaint.measureText(blocks) + 14 * d

        // Luces: verde si las manda el PC; gris (supuestas) si el móvil las conmuta él mismo.
        val leds = controller.leds
        for ((name, on) in listOf(context.getString(R.string.kb_led_caps) to leds.caps, context.getString(R.string.kb_led_num) to leds.num)) {
            val cx = x + 4 * d
            fillPaint.color = if (!on) GlassTheme.TEXT_TERTIARY else if (leds.known) GlassTheme.GREEN else GlassTheme.TEXT_SECONDARY
            if (on) {
                canvas.drawCircle(cx, y - 4 * d, 4 * d, fillPaint)
            } else {
                strokePaint.color = GlassTheme.TEXT_TERTIARY
                canvas.drawCircle(cx, y - 4 * d, 3.5f * d, strokePaint)
            }
            statusPaint.color = if (on) GlassTheme.TEXT else GlassTheme.TEXT_TERTIARY
            canvas.drawText(name, x + 12 * d, y, statusPaint)
            x += 12 * d + statusPaint.measureText(name) + 12 * d
        }
    }

    private fun drawBar(canvas: Canvas) {
        val r = barRect()
        if (r.height() < 24 * density) return
        val rad = GlassTheme.dp(context, GlassTheme.RADIUS_CARD)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = GlassTheme.CARD_FILL
        canvas.drawRoundRect(r, rad, rad, fillPaint)
        strokePaint.color = GlassTheme.BORDER
        canvas.drawRoundRect(r, rad, rad, strokePaint)
        statusPaint.color = GlassTheme.TEXT_TERTIARY
        statusPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(context.getString(R.string.kb_trackpad_bar), r.centerX(), r.centerY() + statusPaint.textSize / 3, statusPaint)
        statusPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawChips(canvas: Canvas) {
        val list = chips()
        val combo = history.current.normalized(landscape)
        val alpha = chipsProgress
        val offset = (1f - chipsProgress) * CHIPS_HEIGHT_DP * density
        val r = RectF()
        for ((i, chip) in list.withIndex()) {
            chipRect(i, r)
            r.offset(0f, offset)
            val on = chip.block != null && combo.isOn(chip.block)
            val rad = GlassTheme.dp(context, GlassTheme.RADIUS_ROW)
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = withAlpha(if (on) GlassTheme.selectedFill else GlassTheme.ELEVATED, alpha)
            canvas.drawRoundRect(r, rad, rad, fillPaint)
            strokePaint.color = withAlpha(if (on) GlassTheme.selectedBorder else GlassTheme.BORDER, alpha)
            canvas.drawRoundRect(r, rad, rad, strokePaint)
            textPaint.color = withAlpha(if (on) GlassTheme.TEXT else GlassTheme.TEXT_SECONDARY, alpha)
            textPaint.isFakeBoldText = on
            textPaint.textSize = fitText(chip.label, 13 * density, r.width() - 6 * density, textPaint)
            canvas.drawText(chip.label, r.centerX(), r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            textPaint.isFakeBoldText = false
        }
    }

    private fun withAlpha(color: Int, a: Float): Int =
        ((((color ushr 24) and 0xFF) * a).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    /** Reduce el tamaño del texto hasta que quepa en [maxWidth]. */
    private fun fitText(text: String, size: Float, maxWidth: Float, paint: Paint): Float {
        paint.textSize = size
        val w = paint.measureText(text)
        return if (w <= maxWidth || w == 0f) size else size * maxWidth / w
    }

    private fun drawKey(canvas: Canvas, key: KeyDef, r: RectF, alpha: Float, shiftOn: Boolean, altGrOn: Boolean, capsOn: Boolean) {
        val d = density
        val radius = GlassTheme.dp(context, GlassTheme.RADIUS_CHIP + 1)
        val mode = if (key.kind == KeyKind.MODIFIER) controller.modifiers.mode(key.usage) else ModifierState.Mode.OFF
        val pressed = key in held && key.kind != KeyKind.MODIFIER
        val fill = when {
            pressed -> GlassTheme.accent
            mode == ModifierState.Mode.LOCKED -> GlassTheme.accent
            mode == ModifierState.Mode.HELD -> GlassTheme.selectedBorder
            mode == ModifierState.Mode.PENDING -> GlassTheme.selectedFill
            key.kind == KeyKind.MODIFIER || key.kind == KeyKind.MODE || key.kind == KeyKind.MOUSE -> GlassTheme.ELEVATED
            else -> NORMAL_FILL
        }
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = withAlpha(fill, alpha)
        canvas.drawRoundRect(r, radius, radius, fillPaint)
        strokePaint.color = withAlpha(if (mode == ModifierState.Mode.PENDING || mode == ModifierState.Mode.HELD) GlassTheme.selectedBorder else GlassTheme.BORDER, alpha)
        canvas.drawRoundRect(r, radius, radius, strokePaint)

        val strong = pressed || mode == ModifierState.Mode.LOCKED
        val mainColor = if (strong) 0xFF101018.toInt() else GlassTheme.TEXT
        val dimColor = if (strong) 0xFF202030.toInt() else GlassTheme.TEXT_TERTIARY
        val hasCorners = key.shiftLabel != null || key.altGrLabel != null
        val shiftActive = shiftOn xor (capsOn && key.letter)

        val base = min(r.height() * 0.38f, r.width() * 0.62f).coerceIn(10 * d, 20 * d)
        val small = (base * 0.62f).coerceAtLeast(7 * d)

        // Etiqueta principal. En las letras, la mayúscula se resalta con Shift o Bloq Mayús; en el resto, la de Shift pasa a ser la fuerte.
        val label = key.label
        if (label.isNotEmpty()) {
            val mainDim = key.kind == KeyKind.NORMAL && key.shiftLabel != null && shiftOn
            val highlighted = key.letter && shiftActive && !strong
            textPaint.color = withAlpha(if (mainDim) dimColor else if (highlighted) GlassTheme.accent else mainColor, alpha)
            textPaint.isFakeBoldText = key.letter && shiftActive
            val maxW = r.width() - 4 * d
            textPaint.textSize = fitText(label, if (label.length > 1 && key.kind != KeyKind.MODE && key.kind != KeyKind.MOUSE) base * 0.8f else base, maxW, textPaint)
            val cx = if (hasCorners) r.left + r.width() * 0.4f else r.centerX()
            val cy = (if (hasCorners) r.centerY() + r.height() * 0.12f else r.centerY()) - (textPaint.ascent() + textPaint.descent()) / 2
            canvas.drawText(label, cx, cy, textPaint)
            textPaint.isFakeBoldText = false
        }
        key.shiftLabel?.let {
            val on = shiftOn && key.kind == KeyKind.NORMAL
            smallPaint.color = withAlpha(if (on) (if (strong) mainColor else GlassTheme.accent) else dimColor, alpha)
            smallPaint.isFakeBoldText = on
            smallPaint.textSize = fitText(it, small, r.width() * 0.45f, smallPaint)
            smallPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(it, r.right - 3 * d, r.top + smallPaint.textSize + 2 * d, smallPaint)
        }
        key.altGrLabel?.let {
            smallPaint.color = withAlpha(if (altGrOn) (if (strong) mainColor else GlassTheme.accent) else dimColor, alpha)
            smallPaint.isFakeBoldText = altGrOn
            smallPaint.textSize = fitText(it, small, r.width() * 0.45f, smallPaint)
            smallPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(it, r.right - 3 * d, r.bottom - 3 * d, smallPaint)
        }
        smallPaint.isFakeBoldText = false

        if (mode == ModifierState.Mode.LOCKED) drawLock(canvas, r, alpha)
        if (key.kind == KeyKind.LOCK && (key.usage == KeyUsages.CAPS_LOCK || key.usage == KeyUsages.NUM_LOCK)) drawLed(canvas, key, r, alpha)
    }

    /** El punto de una tecla de bloqueo: verde si el PC lo ha dicho, gris si es una suposición del móvil. */
    private fun drawLed(canvas: Canvas, key: KeyDef, r: RectF, alpha: Float) {
        val leds = controller.leds
        val on = if (key.usage == KeyUsages.CAPS_LOCK) leds.caps else leds.num
        val cx = r.left + 7 * density
        val cy = r.top + 7 * density
        if (on) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = withAlpha(if (leds.known) GlassTheme.GREEN else GlassTheme.TEXT_SECONDARY, alpha)
            canvas.drawCircle(cx, cy, 3.5f * density, fillPaint)
        } else {
            strokePaint.color = withAlpha(GlassTheme.TEXT_TERTIARY, alpha)
            canvas.drawCircle(cx, cy, 3f * density, strokePaint)
        }
    }

    /** Candado pequeño (modificador fijado), dibujado con trazos. */
    private fun drawLock(canvas: Canvas, r: RectF, alpha: Float) {
        val d = density
        val cx = r.left + 8 * d
        val cy = r.top + 8 * d
        strokePaint.color = withAlpha(0xFF101018.toInt(), alpha)
        strokePaint.strokeWidth = 1.5f * d
        lockPath.reset()
        lockPath.addArc(cx - 3 * d, cy - 6 * d, cx + 3 * d, cy, 180f, 180f)
        canvas.drawPath(lockPath, strokePaint)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = withAlpha(0xFF101018.toInt(), alpha)
        canvas.drawRoundRect(cx - 4.5f * d, cy - 2 * d, cx + 4.5f * d, cy + 4 * d, 1.5f * d, 1.5f * d, fillPaint)
        strokePaint.strokeWidth = d
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeatTick)
        removeCallbacks(barTick)
        blendAnimator?.cancel()
        chipsAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Relleno de una tecla normal: blanco al 8 %, un poco más tenue que las teclas de control. */
        val NORMAL_FILL: Int = android.graphics.Color.parseColor("#14FFFFFF")
        const val TAP_VIBRATION_MS = 20L
        const val SWITCH_MS = 150L
        const val CHIPS_MS = 150L
        const val CHIPS_HEIGHT_DP = 44f
        const val STATUS_HEIGHT_DP = 34f
        const val KEY_GAP_DP = 3f
        const val BOTTOM_PAD_DP = 8f
        const val SWIPE_DP = 40f
        const val TAP_SLOP_DP = 24f

        /** El teclado nunca ocupa más de esto del alto (queda sitio para el estado y la barra de trackpad). */
        const val MAX_KEYBOARD_FRACTION = 0.78f
    }
}
