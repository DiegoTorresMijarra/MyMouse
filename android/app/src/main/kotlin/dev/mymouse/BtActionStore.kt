package dev.mymouse

import android.content.Context
import android.util.Log
import dev.mymouse.core.btactions.BtGestureConfig
import dev.mymouse.core.btactions.BtWidgetConfig
import java.io.File

/**
 * Acciones por Bluetooth guardadas en el móvil (docs/spec-v5-bluetooth-completo.md §2.2 y §3): `bt-gestures.json` (forma → acción) y
 * `bt-widgets.json` (botones propios del panel). La lectura y la escritura del contenido son de `core` ([BtGestureConfig],
 * [BtWidgetConfig]); aquí solo está el fichero. Sin fichero (la primera vez) o con uno ilegible, valen los valores por defecto; no se
 * escribe nada hasta que el usuario guarda un cambio.
 */
class BtActionStore(context: Context) {
    private val gesturesFile = File(context.filesDir, GESTURES_FILE)
    private val widgetsFile = File(context.filesDir, WIDGETS_FILE)

    fun gestures(): BtGestureConfig = read(gesturesFile, BtGestureConfig.DEFAULT) { BtGestureConfig.parse(it).config }

    fun saveGestures(config: BtGestureConfig) = write(gesturesFile, config.toJson())

    /** Vuelve a los valores por defecto (borra el fichero). */
    fun resetGestures() {
        gesturesFile.delete()
    }

    fun widgets(): BtWidgetConfig = read(widgetsFile, BtWidgetConfig.DEFAULT) { BtWidgetConfig.parse(it).config }

    fun saveWidgets(config: BtWidgetConfig) = write(widgetsFile, config.toJson())

    fun resetWidgets() {
        widgetsFile.delete()
    }

    private fun <T> read(file: File, default: T, parse: (String) -> T): T {
        if (!file.exists()) return default
        return try {
            parse(file.readText())
        } catch (e: Exception) {
            Log.w("MyMouse", "${file.name} ilegible, se usan los valores por defecto: ${e.message}")
            default
        }
    }

    /** Escribe en un fichero temporal y lo renombra, para no dejar el fichero a medias si se corta. */
    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private companion object {
        const val GESTURES_FILE = "bt-gestures.json"
        const val WIDGETS_FILE = "bt-widgets.json"
    }
}
