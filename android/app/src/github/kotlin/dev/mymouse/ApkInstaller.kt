package dev.mymouse

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.mymouse.core.update.ReleaseAsset
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Descarga e instalación del APK de una release (docs/spec-v5.1-actualizaciones.md §5): `DownloadManager` a la carpeta privada de la app
 * (`getExternalFilesDir(Download)`, sin permisos de almacenamiento) y el instalador de paquetes del sistema con un `content://` de [FileProvider]
 * (`<paquete>.updates`, ver `res/xml/file_paths.xml`).
 */
object ApkInstaller {
    const val MIME = "application/vnd.android.package-archive"

    private fun authority(context: Context) = "${context.packageName}.updates"

    /** Carpeta de descargas de la app, o null si no hay almacenamiento externo (entonces `DownloadManager` no puede escribir). */
    private fun directory(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    /** El fichero de [asset] en la carpeta de la app (exista o no). Null si no hay carpeta o el nombre no es un nombre de fichero sin más. */
    fun targetFile(context: Context, asset: ReleaseAsset): File? {
        if (asset.name.contains('/') || asset.name.contains('\\') || asset.name.startsWith(".")) return null
        return directory(context)?.let { File(it, asset.name) }
    }

    /** Ya descargado y entero (el tamaño coincide con el de la API)? Se instala sin volver a bajarlo. */
    fun downloaded(context: Context, asset: ReleaseAsset): File? =
        targetFile(context, asset)?.takeIf { it.isFile && (asset.size <= 0 || it.length() == asset.size) }

    /** Borra los APK de descargas anteriores (el sistema renombraría el nuevo con un -1 si existiera). */
    fun cleanup(context: Context) {
        directory(context)?.listFiles { f -> f.name.endsWith(".apk", ignoreCase = true) }?.forEach { it.delete() }
    }

    /** Encola la descarga. Devuelve el id de `DownloadManager`. Lanza [IOException] si no se puede. */
    fun enqueue(context: Context, asset: ReleaseAsset, title: String): Long {
        val target = targetFile(context, asset) ?: throw IOException("No hay carpeta de descargas")
        target.delete()
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle(title)
            .setMimeType(MIME)
            .addRequestHeader("User-Agent", dev.mymouse.core.update.Releases.USER_AGENT)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, target.name)
        val manager = context.getSystemService(DownloadManager::class.java) ?: throw IOException("Sin DownloadManager")
        try {
            return manager.enqueue(request)
        } catch (e: RuntimeException) {
            throw IOException(e.message, e) // p. ej. el Gestor de descargas está desactivado
        }
    }

    /** Estado de una descarga. [progress] de 0 a 1 (0 si aún no se sabe el total). */
    data class Progress(val status: Int, val progress: Float)

    /** Consulta una descarga; null si ya no existe (el usuario la canceló desde la notificación). */
    fun query(context: Context, id: Long): Progress? {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return Progress(status, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f)
        }
        return null
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(DownloadManager::class.java)?.remove(id)
    }

    /** Comprueba el tamaño y, si la API lo dio, el SHA-256. Lee el fichero entero: llamar fuera del hilo de la interfaz. */
    fun verify(file: File, asset: ReleaseAsset): Boolean {
        if (!file.isFile) return false
        if (asset.size > 0 && file.length() != asset.size) return false
        val expected = asset.sha256 ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }

    /** ¿Puede esta app pedir la instalación de paquetes? Desde Android 8 es un permiso por app ("Instalar apps desconocidas"). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** La pantalla del sistema "Instalar apps desconocidas" de MyMouse (Android 8 o posterior). */
    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Abre el instalador de paquetes del sistema con el APK. Lanza [IOException] si el fichero no es servible o no hay instalador. */
    fun install(context: Context, file: File) {
        val uri = try {
            FileProvider.getUriForFile(context, authority(context), file)
        } catch (e: IllegalArgumentException) {
            throw IOException("El fichero no está en una carpeta compartida: ${e.message}", e)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            throw IOException("No hay instalador de paquetes", e)
        }
    }
}
