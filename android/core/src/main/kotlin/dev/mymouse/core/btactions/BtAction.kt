package dev.mymouse.core.btactions

import dev.mymouse.core.hid.ConsumerKey
import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.json.MiniJson

/**
 * Acción que el móvil puede ejecutar por Bluetooth, solo "tecleando" (docs/spec-v5-bluetooth-completo.md §2.1):
 * atajo, tecla multimedia o texto. Lo demás (abrir apps, pausar MyMouse) necesita el programa del PC y es solo del USB.
 */
sealed interface BtAction {
    /**
     * Atajo: [modifiers] (bits de [HidKeys], `MOD_*`) y [key] (uso HID; 0 = solo modificadores, como "Win").
     * Siempre una sola combinación: las secuencias (`Ctrl+K, Ctrl+C`) son del PC.
     */
    data class Keys(val modifiers: Int, val key: Int) : BtAction

    data class Media(val key: ConsumerKey) : BtAction

    /** Texto tecleado según la distribución del teclado del PC. */
    data class Text(val text: String) : BtAction

    /** Abre el teclado del móvil (docs/spec-v6-teclado.md §3). No envía nada: lo resuelve la app. */
    data object Keyboard : BtAction

    companion object {
        /** Tope del texto de una acción (cada carácter son unos 2 informes HID). */
        const val MAX_TEXT = 300
    }
}

/** Lectura, escritura y descripción de [BtAction] (lógica pura, con tests). */
object BtActions {
    private val MODIFIER_NAMES: Map<String, Int> = mapOf(
        "ctrl" to HidKeys.MOD_CTRL, "control" to HidKeys.MOD_CTRL, "ctl" to HidKeys.MOD_CTRL,
        "shift" to HidKeys.MOD_SHIFT, "mayús" to HidKeys.MOD_SHIFT, "mayus" to HidKeys.MOD_SHIFT,
        "alt" to HidKeys.MOD_ALT,
        "win" to HidKeys.MOD_GUI, "windows" to HidKeys.MOD_GUI, "meta" to HidKeys.MOD_GUI,
    )

    /** Atajos: `Ctrl+Shift+T`, `Win+D`, `F5`, `Win`. Lanza [IllegalArgumentException] si no se entiende. */
    fun parseKeys(text: String): BtAction.Keys {
        if (text.isBlank()) throw IllegalArgumentException("Atajo vacío")
        if (text.contains(',')) throw IllegalArgumentException("Por Bluetooth solo hay atajos de una combinación: '$text'")
        var modifiers = 0
        var key = 0
        // "Ctrl++" = Ctrl y la tecla "+" (que no está en el selector): se rechaza con el mismo mensaje que cualquier tecla desconocida.
        val tokens = text.split('+').map { it.trim() }
        for (token in tokens) {
            if (token.isEmpty()) throw IllegalArgumentException("Atajo mal escrito: '$text'")
            val mod = MODIFIER_NAMES[token.lowercase()]
            if (mod != null) {
                if (key != 0) throw IllegalArgumentException("El modificador '$token' va antes de la tecla en '$text'")
                modifiers = modifiers or mod
                continue
            }
            if (key != 0) throw IllegalArgumentException("Más de una tecla en '$text'")
            key = HidKeys.byName(token)?.usage ?: throw IllegalArgumentException("Tecla desconocida: '$token'")
        }
        return BtAction.Keys(modifiers, key)
    }

    /** Escribe un atajo en el formato del fichero: `Ctrl+Shift+T` (orden fijo Ctrl, Shift, Alt, Win). */
    fun formatKeys(keys: BtAction.Keys): String = keyParts(keys, display = false).joinToString("+")

    /** Como [formatKeys], pero con las etiquetas del selector (`Ctrl+Inicio`). */
    fun displayKeys(keys: BtAction.Keys): String = keyParts(keys, display = true).joinToString("+")

    private fun keyParts(keys: BtAction.Keys, display: Boolean): List<String> = buildList {
        if (keys.modifiers and HidKeys.MOD_CTRL != 0) add("Ctrl")
        if (keys.modifiers and HidKeys.MOD_SHIFT != 0) add("Shift")
        if (keys.modifiers and HidKeys.MOD_ALT != 0) add("Alt")
        if (keys.modifiers and HidKeys.MOD_GUI != 0) add("Win")
        if (keys.key != 0) add((if (display) HidKeys.labelOf(keys.key) else HidKeys.nameOf(keys.key)) ?: "0x%02X".format(keys.key))
    }

    /** Las seis teclas multimedia del panel de Bluetooth, con su nombre en el fichero (la app pone la etiqueta). */
    data class MediaOption(val name: String, val key: ConsumerKey)

    val MEDIA: List<MediaOption> = listOf(
        MediaOption("PlayPause", ConsumerKey.PLAY_PAUSE),
        MediaOption("Next", ConsumerKey.NEXT),
        MediaOption("Previous", ConsumerKey.PREVIOUS),
        MediaOption("VolumeUp", ConsumerKey.VOLUME_UP),
        MediaOption("VolumeDown", ConsumerKey.VOLUME_DOWN),
        MediaOption("Mute", ConsumerKey.MUTE),
    )

    fun mediaOption(key: ConsumerKey): MediaOption = MEDIA.first { it.key == key }

    fun parseMedia(name: String): ConsumerKey {
        val n = name.trim().lowercase()
        val aliases = mapOf("mediaplaypause" to "playpause", "medianext" to "next", "nexttrack" to "next", "prev" to "previous",
            "mediaprevious" to "previous", "previoustrack" to "previous", "volumemute" to "mute")
        return MEDIA.firstOrNull { it.name.lowercase() == (aliases[n] ?: n) }?.key
            ?: throw IllegalArgumentException("Tecla multimedia desconocida: '$name'")
    }

    /** Texto neutro (sin traducir) de la acción, para el registro de eventos. La pantalla usa el de la app, en el idioma del móvil. */
    fun logText(action: BtAction): String = when (action) {
        is BtAction.Keys -> displayKeys(action)
        is BtAction.Media -> "media " + mediaOption(action.key).name
        is BtAction.Text -> "text \"" + action.text.let { if (it.length > 20) it.take(20) + "…" else it }.replace('\n', ' ') + "\""
        BtAction.Keyboard -> "keyboard"
    }

    /** Tipo en el fichero. */
    fun typeOf(action: BtAction): String = when (action) {
        is BtAction.Keys -> "keys"
        is BtAction.Media -> "media"
        is BtAction.Text -> "text"
        BtAction.Keyboard -> "keyboard"
    }

    /** Objeto JSON de la acción: `{"type":"keys","keys":"Ctrl+Z"}`. */
    fun toJson(action: BtAction): Map<String, Any?> = when (action) {
        is BtAction.Keys -> linkedMapOf("type" to "keys", "keys" to formatKeys(action))
        is BtAction.Media -> linkedMapOf("type" to "media", "key" to mediaOption(action.key).name)
        is BtAction.Text -> linkedMapOf("type" to "text", "text" to action.text)
        BtAction.Keyboard -> linkedMapOf("type" to "keyboard")
    }

    /** Lee un objeto JSON de acción (ya analizado con [MiniJson]). Lanza [IllegalArgumentException] si no es válido. */
    fun fromJson(value: Any?): BtAction {
        val o = value as? Map<*, *> ?: throw IllegalArgumentException("la acción debe ser un objeto")
        return when (val type = o["type"] as? String) {
            "keys" -> parseKeys(o["keys"] as? String ?: throw IllegalArgumentException("falta 'keys'"))
            "media" -> BtAction.Media(parseMedia(o["key"] as? String ?: throw IllegalArgumentException("falta 'key'")))
            "text" -> {
                val text = o["text"] as? String ?: throw IllegalArgumentException("falta 'text'")
                if (text.isEmpty()) throw IllegalArgumentException("texto vacío")
                BtAction.Text(text.take(BtAction.MAX_TEXT))
            }
            "keyboard" -> BtAction.Keyboard
            null -> throw IllegalArgumentException("falta 'type'")
            else -> throw IllegalArgumentException("tipo '$type' no disponible por Bluetooth (solo keys, media, text y keyboard)")
        }
    }
}
