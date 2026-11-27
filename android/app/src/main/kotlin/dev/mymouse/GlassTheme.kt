package dev.mymouse

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.mymouse.core.theme.ThemeChoice
import dev.mymouse.core.theme.Themes

/**
 * La capa de estilo "glass" (docs/spec-v4.5-estilo.md §1 y §3): los tokens en un único sitio, más los dibujables y las
 * vistas pequeñas que se repiten en todas las pantallas. Sin Material Components ni Compose: solo vistas de la plataforma.
 */
object GlassTheme {
    // ---- Tokens (docs/spec-v4.7-universo.md §3.1; los que dependen del tema, docs/spec-v4.8-temas.md §4) ----
    /** El tema y el acento elegidos en Ajustes. Lo fija [applyTheme]; las pantallas lo cargan con [refresh] antes de crear sus vistas. */
    var choice: ThemeChoice = ThemeChoice()
        private set

    /** Cristal: panel sin desenfoque: el color medio del tema, aclarado, al 92 %. */
    var panelSolid: Int = 0
        private set

    /** Seleccionado: acento al 22 % con borde de acento al 45 %. */
    var selectedFill: Int = 0
        private set
    var selectedBorder: Int = 0
        private set

    /** Pista de un interruptor activado: acento al 45 %. */
    private var trackOn: Int = 0

    /** Acento del tema (o el elegido). Los botones primarios llevan el degradado de [accentStart] a [accentEnd]. */
    var accent: Int = 0
        private set
    var accentStart: Int = 0
        private set
    var accentEnd: Int = 0
        private set

    /** Color medio de la base del tema (relleno de los pomos de los deslizadores del icono). */
    private var baseMid: Int = 0

    /** Cristal: panel (blanco al 7 %) sobre el fondo; con desenfoque disponible. */
    val PANEL_FILL: Int = Color.parseColor("#12FFFFFF")

    /** Cristal: tarjeta (blanco al 7 %), borde (blanco al 16 %) y brillo superior (blanco del 10 % al 0 % en el 35 % de arriba). */
    val CARD_FILL: Int = Color.parseColor("#12FFFFFF")
    val BORDER: Int = Color.parseColor("#29FFFFFF")
    val SHINE: Int = Color.parseColor("#1AFFFFFF")

    /** Cristal: elemento elevado / hover (blanco al 12 %). */
    val ELEVATED: Int = Color.parseColor("#1FFFFFFF")

    private fun argb(rgb: Int, alpha: Int = 0xFF): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    /** Aplica un tema: recalcula los tokens que dependen de él. Solo cambia valores; las vistas ya creadas no se enteran. */
    fun applyTheme(newChoice: ThemeChoice) {
        val c = newChoice.normalized()
        choice = c
        val rgb = c.accent
        accent = argb(rgb)
        selectedFill = argb(rgb, 0x38)
        selectedBorder = argb(rgb, 0x73)
        trackOn = argb(rgb, 0x73)
        accentStart = argb(Themes.accentStart(rgb))
        accentEnd = argb(Themes.accentEnd(rgb))
        panelSolid = argb(Themes.surfaceSolid(c.theme), 0xEB)
        baseMid = argb(c.theme.baseMid)
    }

    /** Carga el tema guardado en los ajustes. */
    fun refresh(context: Context) = applyTheme(AppSettings.load(context).theme)

    init {
        applyTheme(ThemeChoice())
    }

    val GREEN: Int = Color.parseColor("#5FB865")
    val AMBER: Int = Color.parseColor("#E5A50A")
    val RED: Int = Color.parseColor("#E5534B")

    val TEXT: Int = Color.parseColor("#F4F5FA")

    /** Texto secundario: contraste mínimo de 4,5:1 sobre la tarjeta. */
    val TEXT_SECONDARY: Int = Color.parseColor("#B9BDD0")
    val TEXT_TERTIARY: Int = Color.parseColor("#8C90A8")

    // Radios en dp: panel 16, tarjeta 12, fila o botón 8, chip 6.
    const val RADIUS_PANEL = 16f
    const val RADIUS_CARD = 12f
    const val RADIUS_ROW = 8f
    const val RADIUS_CHIP = 6f

    /** Tamaño del texto de los títulos de tarjeta, en sp. */
    const val CARD_TITLE_SP = 11f

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density
    fun dpi(context: Context, value: Float): Int = dp(context, value).toInt()

    /**
     * El fondo "universo" del tema elegido (degradado, nebulosas y estrellas). [trackpad]: es el del trackpad, donde las estrellas
     * solo parpadean si se ha activado (por defecto no); en el resto de pantallas, si está activada la animación.
     */
    fun universe(context: Context, trackpad: Boolean = false): Drawable =
        UniverseDrawable(context.resources.displayMetrics.density, choice.theme, choice.showStars, choice.twinkles(trackpad))

    /** Rectángulo redondeado con relleno y borde de 1 dp opcional. */
    fun rounded(context: Context, fill: Int, radiusDp: Float, border: Int = 0): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, radiusDp)
        setColor(fill)
        if (border != 0) setStroke(maxOf(1, dpi(context, 1f)), border)
    }

    /** Tarjeta de cristal: blanco al 7 %, borde al 16 % y brillo superior (blanco del 10 % al 0 % en el 35 % de arriba). Sin sombra. */
    fun cardDrawable(context: Context, radiusDp: Float = RADIUS_CARD): Drawable =
        GlassCardDrawable(context.resources.displayMetrics.density, radiusDp)

    /**
     * La tarjeta de cristal, dibujada a mano: el brillo se desvanece exactamente en el 35 % de arriba, algo que un `GradientDrawable`
     * no permite antes de la API 29.
     */
    class GlassCardDrawable(private val density: Float, radiusDp: Float) : Drawable() {
        private val radius = radiusDp * density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_FILL }
        private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = BORDER
            strokeWidth = maxOf(1f, density)
        }
        private val rect = RectF()

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            shine.shader = LinearGradient(0f, 0f, 0f, bounds.height() * 0.35f, SHINE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            rect.set(b)
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            rect.set(0f, 0f, b.width().toFloat(), b.height().toFloat())
            canvas.drawRoundRect(rect, radius, radius, shine)
            val half = border.strokeWidth / 2
            rect.inset(half, half)
            canvas.drawRoundRect(rect, radius - half, radius - half, border)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Fondo de los botones primarios: degradado del acento oscurecido ([accentStart] a [accentEnd]), para que el texto blanco se lea. */
    fun accentBackground(context: Context, radiusDp: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accentStart, accentEnd)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp)
        }

    /** Fondo con ondulación al pulsar, recortada con el mismo radio. */
    fun ripple(context: Context, content: Drawable, radiusDp: Float): Drawable {
        val mask = rounded(context, Color.WHITE, radiusDp)
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, mask)
    }

    // ---- Vistas ----

    fun text(context: Context, text: CharSequence, sp: Float = 16f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    /** Título de tarjeta: mayúsculas pequeñas (11 sp), espaciado de letras +0,06 em y color secundario. */
    fun cardTitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text.uppercase()
        textSize = CARD_TITLE_SP
        letterSpacing = 0.06f
        setTextColor(TEXT_SECONDARY)
        setTypeface(typeface, Typeface.BOLD)
    }

    fun note(context: Context, text: CharSequence): TextView = text(context, text, 13f, TEXT_SECONDARY).apply {
        setPadding(0, dpi(context, 6f), 0, dpi(context, 6f))
    }

    /** Botón de cristal elevado (radio 8). Con [accent], relleno de acento. */
    fun button(context: Context, label: String, accent: Boolean = false, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        stateListAnimator = null
        minHeight = 0
        minimumHeight = dpi(context, 40f)
        setTextColor(if (accent) Color.WHITE else TEXT)
        setPadding(dpi(context, 14f), dpi(context, 6f), dpi(context, 14f), dpi(context, 6f))
        background = ripple(context, if (accent) accentBackground(context, RADIUS_ROW) else rounded(context, ELEVATED, RADIUS_ROW), RADIUS_ROW)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpi(context, 8f)
            marginEnd = dpi(context, 8f)
        }
    }

    // ---- Controles teñidos con el acento ----

    private fun checkedList(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(on, off),
    )

    @Suppress("UseSwitchCompatOrMaterialCode")
    fun tint(view: Switch) {
        view.thumbTintList = checkedList(accent, Color.parseColor("#B0B5BD"))
        view.trackTintList = checkedList(trackOn, Color.parseColor("#26FFFFFF"))
    }

    fun tint(view: SeekBar) {
        view.progressTintList = ColorStateList.valueOf(accent)
        view.thumbTintList = ColorStateList.valueOf(accent)
        view.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
    }

    fun tint(view: RadioButton) {
        view.buttonTintList = checkedList(accent, TEXT_SECONDARY)
    }

    // ---- Desenfoque del fondo (Android 12+) ----

    /**
     * Desenfoca lo que hay detrás de un panel (docs/spec-v4.5-estilo.md §3). El fondo se vuelve a dibujar en un [RenderNode] con
     * `RenderEffect.createBlurEffect` y se pinta recortado con el panel. Solo existe desde Android 12; por debajo, o si el lienzo no
     * es acelerado, [draw] devuelve false y quien llama usa el color translúcido sin desenfoque ([panelSolid]).
     * [drawBackdrop] dibuja lo que hay detrás, en coordenadas del padre y con su ancho y alto.
     */
    class BackdropBlur(private val drawBackdrop: (Canvas, Int, Int) -> Unit) {
        private var node: Any? = null

        /**
         * Pinta el fondo desenfocado en [rect] (coordenadas del lienzo actual) con esquinas de [radius] px. [offsetX] y [offsetY]
         * son la posición de este lienzo dentro del padre; [parentWidth] y [parentHeight], su tamaño.
         */
        fun draw(canvas: Canvas, rect: RectF, radius: Float, offsetX: Float, offsetY: Float, parentWidth: Int, parentHeight: Int): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || parentWidth <= 0 || parentHeight <= 0) return false
            return try {
                drawBlurred(canvas, rect, radius, offsetX, offsetY, parentWidth, parentHeight)
            } catch (e: RuntimeException) {
                false // lienzo sin aceleración o efecto no admitido
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun drawBlurred(canvas: Canvas, rect: RectF, radius: Float, offsetX: Float, offsetY: Float, pw: Int, ph: Int): Boolean {
            if (!canvas.isHardwareAccelerated) return false
            val n = (node as? RenderNode) ?: RenderNode("mymouse-glass").also {
                it.setRenderEffect(RenderEffect.createBlurEffect(BLUR_PX, BLUR_PX, Shader.TileMode.CLAMP))
                node = it
            }
            n.setPosition(0, 0, pw, ph)
            val recording = n.beginRecording(pw, ph)
            try {
                drawBackdrop(recording, pw, ph)
            } finally {
                n.endRecording()
            }
            val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.translate(-offsetX, -offsetY)
            canvas.drawRenderNode(n)
            canvas.restore()
            return true
        }

        private companion object {
            const val BLUR_PX = 24f
        }
    }

    // ---- Pantallas con barra superior propia ----

    /**
     * Esqueleto de las pantallas de ajustes: fondo universo, barra superior propia (sin ActionBar) con la flecha de volver y el
     * título, y las secciones como tarjetas de cristal. Respeta las barras del sistema.
     */
    class Screen(private val activity: Activity, title: String, scrollable: Boolean = true, onBack: () -> Unit = { activity.finish() }) {
        /** Donde se añaden las tarjetas. */
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val side = dpi(activity, 16f)
            setPadding(side, 0, side, dpi(activity, 24f))
        }

        val view: View

        /** El ScrollView de la pantalla (null si no es desplazable), para llevar la vista a una tarjeta. */
        var scrollView: ScrollView? = null
            private set

        init {
            refresh(activity) // el tema guardado, antes de crear ninguna vista
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val back = IconButton(activity, IconButton.Kind.BACK, circle = true)
            back.contentDescription = "Volver"
            back.setOnClickListener { onBack() }
            val bar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(activity, 12f), dpi(activity, 8f), dpi(activity, 16f), dpi(activity, 8f))
                addView(back, LinearLayout.LayoutParams(dpi(activity, 44f), dpi(activity, 44f)))
                addView(
                    text(activity, title, 20f, TEXT, bold = true).apply { setPadding(dpi(activity, 12f), 0, 0, 0) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                // El icono de la app, el mismo dibujo del lanzador (docs/spec-v4.7-universo.md §3.3).
                addView(
                    ImageView(activity).apply {
                        setImageResource(R.drawable.ic_app_logo)
                        contentDescription = "MyMouse"
                    },
                    LinearLayout.LayoutParams(dpi(activity, 28f), dpi(activity, 28f)),
                )
            }
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = universe(activity) // universo del tema, con estrellas que parpadean si está activado (el trackpad es estático)
                addView(bar)
                if (scrollable) {
                    val scroll = ScrollView(activity).apply { addView(content) }
                    scrollView = scroll
                    addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                } else {
                    addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                }
            }
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            view = root
        }

        /** Una tarjeta de cristal con título en mayúsculas pequeñas (sin título si es null). */
        fun card(title: String?): LinearLayout {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = cardDrawable(activity)
                val p = dpi(activity, 16f)
                setPadding(p, p, p, p)
            }
            if (title != null) {
                card.addView(cardTitle(activity, title).apply { setPadding(0, 0, 0, dpi(activity, 4f)) })
            }
            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(activity, 12f)
            })
            return card
        }

        fun setContentView() = activity.setContentView(view)
    }

    // ---- Iconos dibujados con trazos ----

    /**
     * Botón de icono dibujado con trazos (sin fuentes ni recursos): círculo de cristal de 44 dp por defecto.
     * Con [label], es una píldora de cristal con el icono a la izquierda y el texto a la derecha.
     */
    @SuppressLint("ViewConstructor")
    class IconButton(
        context: Context,
        private val kind: Kind,
        private val circle: Boolean = true,
        private val label: String? = null,
        /** Borde con el acento del tema (el aviso de actualización). */
        private val highlight: Boolean = false,
    ) : View(context) {
        enum class Kind { BACK, SLIDERS, LOCK, POWER, CLOSE, UPDATE }

        private val d = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ELEVATED }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = BORDER
            strokeWidth = d
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = TEXT
            strokeWidth = 2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val rect = RectF()
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 14 * d
        }

        init {
            isClickable = true
            isFocusable = true
        }

        /** Ancho de la píldora con texto: margen, icono, hueco, texto, margen. */
        private fun pillWidth(): Int = label?.let { (14 * d + 20 * d + 8 * d + textPaint.measureText(it) + 16 * d).toInt() } ?: (44 * d).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = (44 * d).toInt()
            setMeasuredDimension(resolveSize(pillWidth(), widthMeasureSpec), resolveSize(size, heightMeasureSpec))
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val s: Float
            val x0: Float
            ring.color = if (highlight) accent else BORDER
            if (label != null) {
                fill.color = if (isPressed) Color.parseColor("#33FFFFFF") else ELEVATED
                rect.set(d, d, w - d, h - d)
                canvas.drawRoundRect(rect, h / 2, h / 2, fill)
                canvas.drawRoundRect(rect, h / 2, h / 2, ring)
                s = 20 * d
                x0 = 14 * d
                val baseline = h / 2 - (textPaint.ascent() + textPaint.descent()) / 2
                canvas.drawText(label, x0 + s + 8 * d, baseline, textPaint)
            } else {
                if (circle) {
                    fill.color = if (isPressed) Color.parseColor("#33FFFFFF") else ELEVATED
                    canvas.drawCircle(w / 2, h / 2, w / 2 - d, fill)
                    canvas.drawCircle(w / 2, h / 2, w / 2 - d, ring)
                }
                s = minOf(w, h) * 0.5f // el icono ocupa la mitad del botón
                x0 = (w - s) / 2
            }
            val y0 = (h - s) / 2
            fun x(f: Float) = x0 + f * s
            fun y(f: Float) = y0 + f * s
            when (kind) {
                Kind.BACK -> {
                    canvas.drawLine(x(0.95f), y(0.5f), x(0.05f), y(0.5f), line)
                    canvas.drawLine(x(0.45f), y(0.08f), x(0.05f), y(0.5f), line)
                    canvas.drawLine(x(0.05f), y(0.5f), x(0.45f), y(0.92f), line)
                }
                Kind.SLIDERS -> {
                    for ((fy, knob) in listOf(0.15f to 0.7f, 0.5f to 0.3f, 0.85f to 0.6f)) {
                        canvas.drawLine(x(0f), y(fy), x(1f), y(fy), line)
                        fill.color = baseMid
                        canvas.drawCircle(x(knob), y(fy), 0.13f * s, fill)
                        canvas.drawCircle(x(knob), y(fy), 0.13f * s, line)
                    }
                }
                Kind.LOCK -> {
                    rect.set(x(0.1f), y(0.45f), x(0.9f), y(1f))
                    canvas.drawRoundRect(rect, 0.14f * s, 0.14f * s, line)
                    rect.set(x(0.27f), y(0f), x(0.73f), y(0.9f))
                    canvas.drawArc(rect, 180f, 180f, false, line) // el arco del candado: solo la mitad de arriba se ve sobre el cuerpo
                }
                Kind.POWER -> {
                    rect.set(x(0.08f), y(0.12f), x(0.92f), y(0.96f))
                    canvas.drawArc(rect, -55f, 290f, false, line)
                    canvas.drawLine(x(0.5f), y(0f), x(0.5f), y(0.5f), line)
                }
                Kind.CLOSE -> {
                    canvas.drawLine(x(0.1f), y(0.1f), x(0.9f), y(0.9f), line)
                    canvas.drawLine(x(0.9f), y(0.1f), x(0.1f), y(0.9f), line)
                }
                Kind.UPDATE -> { // flecha hacia abajo sobre una base
                    canvas.drawLine(x(0.5f), y(0f), x(0.5f), y(0.75f), line)
                    canvas.drawLine(x(0.15f), y(0.4f), x(0.5f), y(0.75f), line)
                    canvas.drawLine(x(0.85f), y(0.4f), x(0.5f), y(0.75f), line)
                    canvas.drawLine(x(0.05f), y(1f), x(0.95f), y(1f), line)
                }
            }
        }
    }
}
