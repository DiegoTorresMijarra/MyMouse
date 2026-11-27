package dev.mymouse.core.theme

/**
 * Temas de MyMouse (docs/spec-v4.8-temas.md). Lógica pura, sin Android: las definiciones, la resolución del acento y los colores
 * derivados. Los colores son enteros `0xRRGGBB` (sin canal alfa); la app les añade el alfa al dibujar.
 *
 * Las definiciones están replicadas a mano en [Themes.ALL]; un test ([ThemesFixtureTest]) las compara con `shared/themes.json`, el
 * fichero que el PC incrusta como recurso. Así "Universo" es idéntico en los dos lados aunque cada uno elija el suyo.
 */

/** Una nebulosa: degradado radial con centro (x, y) relativo al ancho y al alto, y radio [r] relativo al ancho. */
data class Nebula(val color: Int, val opacity: Float, val x: Float, val y: Float, val r: Float)

/**
 * Un tema. [base] son 1–3 colores del degradado diagonal; [stars]: si el tema tiene estrellas; [starDensity]: multiplicador del
 * número de estrellas; [twinkleDepth]: cuánto parpadean (0 = nada, 1 = lo normal); [accent]: el acento por defecto.
 */
data class ThemeDef(
    val id: String,
    val name: String,
    val base: List<Int>,
    val nebulas: List<Nebula>,
    val stars: Boolean,
    val starDensity: Float,
    val twinkleDepth: Float,
    val accent: Int,
) {
    /** El color medio de la base: el de los fondos sólidos (diálogos, menús). */
    val baseMid: Int get() = base[base.size / 2]
}

/** Un acento que se puede elegir en lugar del del tema. */
data class AccentOption(val id: String, val name: String, val color: Int)

/** Lo que el usuario ha elegido en una plataforma: tema, acento y estrellas. Se guarda por separado en el móvil y en el PC. */
data class ThemeChoice(
    val themeId: String = Themes.DEFAULT_ID,
    val accentId: String = Themes.ACCENT_AUTO,
    /** "Estrellas" (solo cuenta en los temas que las tienen). */
    val stars: Boolean = true,
    /** "Animación de estrellas" en las pantallas que no son el trackpad. */
    val twinkle: Boolean = true,
    /** La animación en el trackpad: desactivada por defecto, como en la v4.7. */
    val twinkleTrackpad: Boolean = false,
) {
    /** El tema elegido; un id desconocido da el de por defecto. */
    val theme: ThemeDef get() = Themes.resolve(themeId)

    /** El color de acento resuelto: el elegido o, en Automático, el del tema. */
    val accent: Int get() = Themes.accentColor(theme, accentId)

    /** ¿Se dibujan estrellas? Hace falta que el tema las tenga y que la casilla esté marcada. */
    val showStars: Boolean get() = theme.stars && stars

    /** ¿Parpadean las estrellas en el trackpad ([trackpad]) o en el resto de pantallas? */
    fun twinkles(trackpad: Boolean): Boolean =
        showStars && theme.twinkleDepth > 0f && (if (trackpad) twinkleTrackpad else twinkle)

    /** La misma elección con el tema y el acento reconocidos: lo desconocido pasa a Universo y a Automático. */
    fun normalized(): ThemeChoice = copy(
        themeId = Themes.byId(themeId)?.id ?: Themes.DEFAULT_ID,
        accentId = if (Themes.accentById(accentId) != null) accentId else Themes.ACCENT_AUTO,
    )
}

object Themes {
    const val DEFAULT_ID = "universe"

    /** Id del "acento automático": el del tema. */
    const val ACCENT_AUTO = "auto"

    val ACCENTS: List<AccentOption> = listOf(
        AccentOption("steel", "Azul acero", 0x7AA2F7),
        AccentOption("lavender", "Lavanda", 0x7C8CFF),
        AccentOption("turquoise", "Turquesa", 0x4FD1C5),
        AccentOption("green", "Verde", 0x6EE7A8),
        AccentOption("amber", "Ámbar", 0xF5B94A),
        AccentOption("pink", "Rosa", 0xF472B6),
        AccentOption("white", "Blanco", 0xE5E7EB),
    )

    val ALL: List<ThemeDef> = listOf(
        ThemeDef(
            id = "universe", name = "Universo",
            base = listOf(0x05070D, 0x0A0F1C, 0x0E1424),
            nebulas = listOf(Nebula(0x2B3A67, 0.22f, 0.10f, 0.06f, 0.70f), Nebula(0x1E3A5F, 0.20f, 0.90f, 0.95f, 0.60f)),
            stars = true, starDensity = 0.8f, twinkleDepth = 0.6f, accent = 0x7AA2F7,
        ),
        ThemeDef(
            id = "midnight", name = "Medianoche",
            base = listOf(0x0B0D12, 0x121620),
            nebulas = listOf(Nebula(0x1C2533, 0.25f, 0.50f, 0.50f, 0.80f)),
            stars = false, starDensity = 0f, twinkleDepth = 0f, accent = 0x5B8DEF,
        ),
        ThemeDef(
            id = "graphite", name = "Grafito",
            base = listOf(0x111214, 0x1B1C1F),
            nebulas = emptyList(),
            stars = false, starDensity = 0f, twinkleDepth = 0f, accent = 0x9AA4B2,
        ),
        ThemeDef(
            id = "ocean", name = "Océano",
            base = listOf(0x03090D, 0x071820),
            nebulas = listOf(Nebula(0x0F4C5C, 0.25f, 0.12f, 0.08f, 0.70f), Nebula(0x123A5A, 0.20f, 0.90f, 0.95f, 0.60f)),
            stars = true, starDensity = 0.5f, twinkleDepth = 0.8f, accent = 0x4FD1C5,
        ),
        ThemeDef(
            id = "aurora", name = "Aurora",
            base = listOf(0x05080A, 0x0B1412),
            nebulas = listOf(Nebula(0x14532D, 0.20f, 0.50f, 0.00f, 0.95f), Nebula(0x134E4A, 0.18f, 0.85f, 0.90f, 0.55f)),
            stars = true, starDensity = 0.6f, twinkleDepth = 0.8f, accent = 0x6EE7A8,
        ),
        ThemeDef(
            id = "oled", name = "Negro (OLED)",
            base = listOf(0x000000),
            nebulas = emptyList(),
            stars = false, starDensity = 0f, twinkleDepth = 0f, accent = 0xE5E7EB,
        ),
        ThemeDef(
            id = "nebula", name = "Nebulosa",
            base = listOf(0x070616, 0x120D2E, 0x1B0F36),
            nebulas = listOf(
                Nebula(0x7C3AED, 0.35f, 0.10f, 0.06f, 0.70f),
                Nebula(0x2563EB, 0.28f, 0.90f, 0.95f, 0.60f),
                Nebula(0xDB2777, 0.18f, 0.85f, 0.50f, 0.40f),
            ),
            stars = true, starDensity = 1f, twinkleDepth = 1f, accent = 0x7C8CFF,
        ),
    )

    fun byId(id: String?): ThemeDef? = ALL.firstOrNull { it.id == id }

    /** El tema de [id]; si no existe (o es null, como en quien venía de una versión sin temas), Universo. */
    fun resolve(id: String?): ThemeDef = byId(id) ?: byId(DEFAULT_ID)!!

    fun accentById(id: String?): AccentOption? = ACCENTS.firstOrNull { it.id == id }

    /** El acento de [accentId] sobre [theme]: "auto" (o un id desconocido) es el acento del tema. */
    fun accentColor(theme: ThemeDef, accentId: String?): Int = accentById(accentId)?.color ?: theme.accent

    // ---- Colores derivados (para que los tokens dinámicos salgan de una sola función en cada lado) ----

    /** Mezcla [a] con [b]: 0 = [a], 1 = [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Principio del degradado de los botones primarios: el acento oscurecido, para que el texto blanco se lea con cualquier acento. */
    fun accentStart(accent: Int): Int = mix(accent, 0x000000, 0.35f)

    /** Final del degradado de los botones primarios. */
    fun accentEnd(accent: Int): Int = mix(accent, 0x000000, 0.55f)

    /** Fondo sólido de menús y diálogos: el color medio de la base, aclarado un poco para que se distinga del fondo. */
    fun surfaceSolid(theme: ThemeDef): Int = mix(theme.baseMid, 0xFFFFFF, 0.07f)

    /** Cuántas estrellas pide [theme] en un área: las de referencia por el multiplicador del tema (0 si no tiene estrellas). */
    fun starCount(theme: ThemeDef, widthDp: Float, heightDp: Float): Int =
        if (!theme.stars) 0 else dev.mymouse.core.universe.StarField.countFor(widthDp, heightDp, theme.starDensity)

    /** `#RRGGBB` de un color `0xRRGGBB`. */
    fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    /** `#RRGGBB` → entero, o null si no es un color válido. */
    fun parseHex(text: String?): Int? {
        if (text == null || text.length != 7 || text[0] != '#') return null
        return text.substring(1).toIntOrNull(16)
    }
}
