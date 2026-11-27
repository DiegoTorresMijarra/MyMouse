package dev.mymouse.core.theme

import dev.mymouse.core.protocol.Message

/**
 * Lo que se sincroniza del tema entre móvil y PC (docs/spec-v6.1-tema-sincronizado.md): tema, acento, estrellas y
 * cuándo se eligieron (ms desde 1970, UTC; 0 = nunca). La animación de las estrellas es de cada lado y no viaja.
 */
data class SyncedTheme(val themeId: String, val accentId: String, val stars: Boolean, val changedAtMs: Long) {
    fun sameLook(other: SyncedTheme) = themeId == other.themeId && accentId == other.accentId && stars == other.stars

    /** El mensaje `THEME` del móvil. */
    fun toMessage() = Message.Theme(themeId, accentId, stars, changedAtMs)

    /** La misma elección de [choice] con el tema, el acento y las estrellas de aquí (la animación se queda como estaba). */
    fun applyTo(choice: ThemeChoice) = choice.copy(themeId = themeId, accentId = accentId, stars = stars).normalized()

    companion object {
        fun of(choice: ThemeChoice, changedAtMs: Long) = SyncedTheme(choice.themeId, choice.accentId, choice.stars, changedAtMs)

        fun of(message: Message.Theme) = SyncedTheme(message.themeId, message.accentId, message.stars, message.changedAtMs)
    }
}

/** La regla de la sincronización. Lógica pura, la misma que `ThemeSync` en `MyMouse.HostCore`. */
object ThemeSync {
    /**
     * Llega el tema del otro lado: devuelve lo que este lado debe guardar, o null si se queda con el suyo.
     * - Gana el cambio más reciente: se adopta el remoto entero, con su instante.
     * - Mismo instante y distinto aspecto (por ejemplo, los dos sin tocar nunca): gana el móvil.
     * - Mismo aspecto: solo se adopta el instante si es más reciente, para no volver a mandarlo.
     */
    fun resolve(local: SyncedTheme, remote: SyncedTheme, localIsPhone: Boolean): SyncedTheme? = when {
        remote.changedAtMs > local.changedAtMs -> remote
        remote.changedAtMs < local.changedAtMs || local.sameLook(remote) -> null
        localIsPhone -> null
        else -> remote
    }
}
