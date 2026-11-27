package dev.mymouse.core.btactions

import dev.mymouse.core.hid.HidKeys
import dev.mymouse.core.json.MiniJson
import dev.mymouse.core.protocol.WidgetEntry
import dev.mymouse.core.protocol.WidgetKind

/** Un botón propio del panel de Bluetooth: etiqueta, icono (un emoji o 1–3 letras) y acción. */
data class BtButton(val label: String, val icon: String, val action: BtAction)

/**
 * Botones propios del panel de widgets por Bluetooth (docs/spec-v5-bluetooth-completo.md §3), guardados en `bt-widgets.json`.
 * Como mucho [MAX_BUTTONS]. Inmutable.
 */
class BtWidgetConfig(buttons: List<BtButton>) {
    val buttons: List<BtButton> = buttons.take(MAX_BUTTONS)

    val isFull: Boolean get() = buttons.size >= MAX_BUTTONS

    fun add(button: BtButton): BtWidgetConfig = if (isFull) this else BtWidgetConfig(buttons + button)

    fun replace(index: Int, button: BtButton): BtWidgetConfig =
        if (index !in buttons.indices) this else BtWidgetConfig(buttons.toMutableList().also { it[index] = button })

    fun remove(index: Int): BtWidgetConfig =
        if (index !in buttons.indices) this else BtWidgetConfig(buttons.filterIndexed { i, _ -> i != index })

    /** Sube (delta −1) o baja (delta +1) un botón de sitio. Fuera de los extremos no hace nada. */
    fun move(index: Int, delta: Int): BtWidgetConfig {
        val to = index + delta
        if (index !in buttons.indices || to !in buttons.indices) return this
        return BtWidgetConfig(buttons.toMutableList().also { val b = it.removeAt(index); it.add(to, b) })
    }

    /** Los widgets del panel: multimedia, volumen (Vol −, Vol +, Silencio) y después los botones propios. */
    fun entries(): List<WidgetEntry> = buildList {
        add(WidgetEntry(ID_MEDIA, WidgetKind.MEDIA, "", ""))
        add(WidgetEntry(ID_VOLUME_DOWN, WidgetKind.BUTTON, "Vol −", "🔉"))
        add(WidgetEntry(ID_VOLUME_UP, WidgetKind.BUTTON, "Vol +", "🔊"))
        add(WidgetEntry(ID_MUTE, WidgetKind.BUTTON, "Silencio", "🔇"))
        buttons.forEachIndexed { i, b -> add(WidgetEntry(FIRST_CUSTOM_ID + i, WidgetKind.BUTTON, b.label, b.icon)) }
    }

    /** El botón propio de un id de widget, o null si no es de los propios. */
    fun buttonFor(widgetId: Int): BtButton? = buttons.getOrNull(widgetId - FIRST_CUSTOM_ID)?.takeIf { widgetId >= FIRST_CUSTOM_ID }

    fun toJson(): String = MiniJson.write(
        linkedMapOf(
            "version" to 1,
            "buttons" to buttons.map { b ->
                linkedMapOf("label" to b.label, "icon" to b.icon, "action" to BtActions.toJson(b.action))
            },
        ),
    )

    override fun equals(other: Any?) = other is BtWidgetConfig && buttons == other.buttons
    override fun hashCode() = buttons.hashCode()

    companion object {
        const val MAX_BUTTONS = 16
        const val MAX_LABEL = 20

        /** Ids de los widgets fijos del panel y del primer botón propio. */
        const val ID_MEDIA = 0
        const val ID_VOLUME_DOWN = 1
        const val ID_VOLUME_UP = 2
        const val ID_MUTE = 3
        const val FIRST_CUSTOM_ID = 10

        private fun keys(modifiers: Int, key: String) = BtAction.Keys(modifiers, HidKeys.byName(key)!!.usage)

        val DEFAULT = BtWidgetConfig(listOf(
            BtButton("Copiar", "📋", keys(HidKeys.MOD_CTRL, "C")),
            BtButton("Pegar", "📄", keys(HidKeys.MOD_CTRL, "V")),
            BtButton("Captura", "✂️", keys(HidKeys.MOD_GUI or HidKeys.MOD_SHIFT, "S")),
            BtButton("Deshacer", "↩️", keys(HidKeys.MOD_CTRL, "Z")),
            BtButton("Bloquear", "🔒", keys(HidKeys.MOD_GUI, "L")),
            BtButton("Teclado", "⌨", BtAction.Keyboard),
        ))

        /** El icono es un emoji o 1–3 letras: como mucho 4 puntos de código (un emoji puede llevar un selector de variante). */
        fun validIcon(icon: String): Boolean = icon.isNotBlank() && icon.codePointCount(0, icon.length) <= 4

        fun validLabel(label: String): Boolean = label.isNotBlank() && label.length <= MAX_LABEL

        /** Lee el fichero. Los botones no válidos se descartan con un aviso; con más de 16 sobran los últimos. */
        fun parse(text: String): BtParseResult<BtWidgetConfig> {
            val root = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("bt-widgets.json: la raíz debe ser un objeto")
            val list = root["buttons"] as? List<*> ?: throw IllegalArgumentException("bt-widgets.json: falta la lista 'buttons'")
            val warnings = mutableListOf<String>()
            val buttons = mutableListOf<BtButton>()
            list.forEachIndexed { i, item ->
                try {
                    val o = item as? Map<*, *> ?: throw IllegalArgumentException("el botón debe ser un objeto")
                    val label = (o["label"] as? String)?.trim().orEmpty()
                    if (!validLabel(label)) throw IllegalArgumentException("etiqueta vacía o de más de $MAX_LABEL caracteres")
                    val icon = (o["icon"] as? String)?.trim().orEmpty()
                    if (!validIcon(icon)) throw IllegalArgumentException("el icono es un emoji o de 1 a 3 letras")
                    if (buttons.size >= MAX_BUTTONS) throw IllegalArgumentException("sobra: el máximo es $MAX_BUTTONS botones")
                    buttons += BtButton(label, icon, BtActions.fromJson(o["action"]))
                } catch (e: IllegalArgumentException) {
                    warnings += "Botón ${i + 1}: ${e.message}"
                }
            }
            return BtParseResult(BtWidgetConfig(buttons), warnings)
        }
    }
}
