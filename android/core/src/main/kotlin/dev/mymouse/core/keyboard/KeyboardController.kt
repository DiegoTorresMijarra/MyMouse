package dev.mymouse.core.keyboard

/** Luces del teclado del PC: Bloq Mayús, Bloq Num y Bloq Despl. [known] = true si el PC ha informado de ellas alguna vez. */
class KeyboardLeds {
    var caps = false
        private set
    var num = false
        private set
    var scroll = false
        private set

    /** false mientras el estado es una suposición (el móvil lo conmuta al pulsar las teclas de bloqueo): se dibuja con un punto gris. */
    var known = false
        private set

    /** El PC informa de las luces (`KEYBOARD_LEDS`, o el informe de salida del teclado Bluetooth). Devuelve true si algo cambió. */
    fun onHostLeds(bits: Int): Boolean {
        val c = bits and LED_CAPS != 0
        val n = bits and LED_NUM != 0
        val s = bits and LED_SCROLL != 0
        val changed = c != caps || n != num || s != scroll || !known
        caps = c
        num = n
        scroll = s
        known = true
        return changed
    }

    /** Se ha pulsado una tecla de bloqueo: mientras el PC no informe, se supone que conmuta. */
    fun onLockKey(usage: Int): Boolean {
        if (known) return false
        when (usage) {
            KeyUsages.CAPS_LOCK -> caps = !caps
            KeyUsages.NUM_LOCK -> num = !num
            KeyUsages.SCROLL_LOCK -> scroll = !scroll
            else -> return false
        }
        return true
    }

    /** Vuelve a lo desconocido (al cambiar de conexión). */
    fun reset() {
        caps = false
        num = false
        scroll = false
        known = false
    }

    val bits: Int get() = (if (caps) LED_CAPS else 0) or (if (num) LED_NUM else 0) or (if (scroll) LED_SCROLL else 0)

    companion object {
        const val LED_CAPS = 1
        const val LED_NUM = 2
        const val LED_SCROLL = 4
    }
}

/**
 * La lógica del teclado sin pantalla ni red (docs/spec-v6-teclado.md §5): recibe pulsaciones por dedo (multitáctil), aplica los
 * modificadores inteligentes y la repetición, y devuelve los [KeyEvent] que hay que enviar. Quien la usa los manda por cable
 * (`KEY`) o por Bluetooth (informe HID) y llama a [tick] cuando llega [nextDeadlineMs].
 */
class KeyboardController(
    /** Repetir al mantener (solo por cable; por Bluetooth lo hace Windows). */
    var repeatEnabled: Boolean = true,
    val modifiers: ModifierState = ModifierState(),
    private val repeat: KeyRepeat = KeyRepeat(),
) {
    val leds = KeyboardLeds()

    /** Qué tecla tiene cada dedo. */
    private val pressed = LinkedHashMap<Int, KeyDef>()

    /** Las teclas normales que están bajadas ahora mismo. */
    val pressedKeys: List<KeyDef> get() = pressed.values.toList()

    fun isPressed(key: KeyDef): Boolean = pressed.values.any { it === key || (it.usage == key.usage && it.kind == key.kind && key.usage != 0) }

    /**
     * Un dedo baja sobre [key]. Devuelve lo que hay que enviar. Las teclas sin uso ([KeyKind.MODE], [KeyKind.MOUSE]) no envían nada:
     * las trata quien dibuja.
     */
    fun press(pointerId: Int, key: KeyDef, nowMs: Long): List<KeyEvent> {
        // Un dedo que baja sobre otra tecla sin haberse levantado (evento perdido): primero se suelta la anterior.
        val previous = if (pointerId in pressed) release(pointerId, nowMs) else emptyList()
        return previous + pressKey(pointerId, key, nowMs)
    }

    private fun pressKey(pointerId: Int, key: KeyDef, nowMs: Long): List<KeyEvent> {
        if (key.kind == KeyKind.SPACER || key.kind == KeyKind.MODE || key.kind == KeyKind.MOUSE) return emptyList()
        pressed[pointerId] = key
        if (key.kind == KeyKind.MODIFIER) return modifiers.onModifierDown(key.usage, nowMs)
        modifiers.onNormalKeyDown()
        if (key.kind == KeyKind.LOCK) leds.onLockKey(key.usage)
        if (repeatEnabled && key.repeatable) repeat.start(key.usage, nowMs)
        return listOf(KeyEvent(key.usage, true))
    }

    /** El dedo [pointerId] se levanta (o se cancela). */
    fun release(pointerId: Int, nowMs: Long): List<KeyEvent> {
        val key = pressed.remove(pointerId) ?: return emptyList()
        if (key.kind == KeyKind.MODIFIER) return modifiers.onModifierUp(key.usage, nowMs)
        repeat.stop(key.usage)
        return listOf(KeyEvent(key.usage, false)) + modifiers.onNormalKeyUp()
    }

    /** Un clic de la barra de trackpad ha terminado: se sueltan los modificadores en espera (Ctrl+clic). */
    fun afterPointerClick(): List<KeyEvent> = modifiers.onNormalKeyUp()

    /** Marca los modificadores con el dedo encima como usados (un clic de la barra de trackpad cuenta como tecla). */
    fun onPointerClickStart() = modifiers.onNormalKeyDown()

    /** Instante en que hay que llamar a [tick], o null. */
    val nextDeadlineMs: Long? get() = repeat.nextDeadlineMs

    /** Repeticiones que tocan: un "pulsar" por cada una, sin "soltar" en medio, como un teclado real. */
    fun tick(nowMs: Long): List<KeyEvent> = repeat.onTick(nowMs).map { KeyEvent(it, true) }

    /** Suelta todo lo que esté bajado: teclas normales, modificadores en espera y fijados (docs/spec-v6-teclado.md §5.5). */
    fun releaseAll(): List<KeyEvent> {
        val out = pressed.values.filter { it.kind != KeyKind.MODIFIER }.map { KeyEvent(it.usage, false) }.distinct().toMutableList()
        pressed.clear()
        repeat.stopAll()
        out += modifiers.releaseAll()
        return out
    }
}
