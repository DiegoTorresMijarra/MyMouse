package dev.mymouse.core.keyboard

/** Distribución elegida en Ajustes → Teclado (docs/spec-v6-teclado.md §6). */
enum class KeyboardLayoutSetting(val id: String) {
    AUTO("auto"),
    ES("es"),
    US("us");

    companion object {
        fun fromId(id: String?): KeyboardLayoutSetting = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Tamaño de las teclas: multiplica el alto de las filas. */
enum class KeySize(val id: String, val heightFactor: Float) {
    SMALL("small", 0.8f),
    MEDIUM("medium", 1.0f),
    LARGE("large", 1.25f);

    companion object {
        fun fromId(id: String?): KeySize = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

object KeyboardSettings {
    /** Alto base de una fila (tamaño mediano), en dp. */
    const val BASE_ROW_DP = 46f

    /**
     * La distribución que se dibuja. Explícita: la elegida. Automática: por cable, la del PC (`es-ES` → `es`, cualquier otra → `us`;
     * mientras no llegue ninguna, la de Bluetooth/ajustes); por Bluetooth, el ajuste de distribución para escribir texto.
     */
    fun resolveLayout(setting: KeyboardLayoutSetting, bluetooth: Boolean, hostLayoutId: String?, textLayoutId: String): String = when (setting) {
        KeyboardLayoutSetting.ES -> "es"
        KeyboardLayoutSetting.US -> "us"
        KeyboardLayoutSetting.AUTO -> if (bluetooth || hostLayoutId == null) textLayoutId else if (hostLayoutId == "es") "es" else "us"
    }
}
