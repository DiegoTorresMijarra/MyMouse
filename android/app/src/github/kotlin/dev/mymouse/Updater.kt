package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.mymouse.core.update.AppVersion
import dev.mymouse.core.update.AssetKind
import dev.mymouse.core.update.ReleaseInfo
import dev.mymouse.core.update.Releases
import dev.mymouse.core.update.UpdateBanner
import dev.mymouse.core.update.UpdatePolicy
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Avisos de versión nueva del móvil (docs/spec-v5.1-actualizaciones.md §3, §5 y §6). Un único objeto para todo el proceso, que comparten la pantalla
 * principal y Ajustes. Todo el estado se toca desde el hilo de la interfaz; la red va en un hilo aparte (`HttpURLConnection`) y vuelve con un `Handler`.
 */
object Updater {
    enum class Outcome { AVAILABLE, UP_TO_DATE, SKIPPED, DEV_BUILD, FAILED }

    /** Descarga en curso del APK de [tag] (`DownloadManager`); [failed] = no se pudo y se explica en [message]. */
    data class Download(val tag: String, val progress: Float, val failed: Boolean = false, val message: String? = null)

    private const val TAG = "MyMouse"
    private const val FIRST_CHECK_DELAY_MS = 10_000L
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val POLL_MS = 500L
    private const val PREFS = "updates"

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    private var appContext: Context? = null
    private var releases: List<ReleaseInfo> = emptyList()
    private var started = false
    private var checking = false
    private var foreground: WeakReference<Activity>? = null

    /** Versión del PC por USB (`APP_VERSION` 0x8E), o null sin PC conectado. */
    var pcVersion: String? = null
        private set

    /** Resultado de la última comprobación (null si aún no hubo ninguna con éxito o falló la red). */
    var lastOutcome: Outcome? = null
        private set

    /** Descarga en curso o fallida; null si no hay. */
    var download: Download? = null
        private set

    private var downloadId = -1L
    private var downloadAsset: dev.mymouse.core.update.ReleaseAsset? = null
    private var downloadRelease: ReleaseInfo? = null

    /** APK ya descargado y comprobado que espera a instalarse (la app estaba en segundo plano o falta el permiso). */
    private var pendingInstall: File? = null
    private var askedPermission = false

    // ---- Estado ----

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheck(context: Context): Boolean = prefs(context).getBoolean("autoCheck", true)

    fun setAutoCheck(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("autoCheck", value).apply()
        if (value) schedule(FIRST_CHECK_DELAY_MS)
    }

    private fun skipped(context: Context): String? = prefs(context).getString("skipped", null)

    /** `versionName` de esta app: `5.0` en una release, `0.0-dev.N` en las demás. */
    fun localVersionText(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0-dev"
        } catch (e: Exception) {
            "0.0-dev"
        }

    private fun local(context: Context) = AppVersion.parse(localVersionText(context))

    /** Una versión de desarrollo (o ilegible) no avisa nunca. */
    fun isDev(context: Context) = local(context)?.isDev != false

    /** Los avisos que hay que mostrar ahora (en modo ventana). */
    fun banners(context: Context): List<UpdateBanner> =
        UpdatePolicy.banners(local(context), localVersionText(context), releases, skipped(context), pcVersion)

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() = listeners.toList().forEach { it() }

    /** Lo llama la conexión USB al recibir (o perder) `APP_VERSION` del PC. Puede venir de otro hilo. */
    fun setPcVersion(version: String?) {
        handler.post {
            if (pcVersion == version) return@post
            pcVersion = version
            changed()
        }
    }

    // ---- Comprobación ----

    /** Programa la primera comprobación (10 s tras arrancar) y luego cada 12 h mientras el proceso viva. Llamar desde la pantalla principal. */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (started) return
        started = true
        schedule(FIRST_CHECK_DELAY_MS)
    }

    private val scheduled = Runnable {
        val context = appContext ?: return@Runnable
        if (autoCheck(context)) checkNow(context, manual = false) {}
        schedule(CHECK_INTERVAL_MS)
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(scheduled)
        handler.postDelayed(scheduled, delayMs)
    }

    /**
     * Busca en GitHub (hilo aparte). Si falla la red, en silencio: se anota en el log y se devuelve [Outcome.FAILED].
     * [manual] ("Buscar ahora"): si la versión más alta es la omitida, se deja de omitir.
     */
    fun checkNow(context: Context, manual: Boolean, done: (Outcome) -> Unit) {
        val app = context.applicationContext
        appContext = app
        if (checking) {
            done(Outcome.FAILED)
            return
        }
        checking = true
        thread(name = "MyMouse-updates", isDaemon = true) {
            val result = try {
                Releases.parse(fetch())
            } catch (e: Exception) {
                Log.d(TAG, "No se pudo buscar actualizaciones: ${e.message}")
                null
            }
            handler.post {
                checking = false
                if (result == null) {
                    done(Outcome.FAILED)
                    return@post
                }
                releases = result
                val latest = Releases.highest(result)
                val local = local(app)
                if (manual && latest != null && UpdatePolicy.isSkipped(latest.version, skipped(app))) {
                    prefs(app).edit().remove("skipped").apply()
                }
                Log.i(TAG, "Buscar actualizaciones: instalada ${localVersionText(app)}, última ${latest?.version?.display ?: "ninguna"}")
                val outcome = when {
                    local == null || local.isDev -> Outcome.DEV_BUILD
                    UpdatePolicy.shouldOffer(local, latest, skipped(app)) -> Outcome.AVAILABLE
                    latest != null && latest.version > local -> Outcome.SKIPPED
                    else -> Outcome.UP_TO_DATE
                }
                lastOutcome = outcome
                changed()
                done(outcome)
            }
        }
    }

    private fun fetch(): String {
        val connection = URL(Releases.LIST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", Releases.USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_RESPONSE_BYTES) throw IOException("Respuesta demasiado grande")
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

    /** "Omitir esta versión": no vuelve a avisar de la que se ofrece ahora, sí de la siguiente. */
    fun skipOffered(context: Context) {
        val offer = banners(context).firstOrNull { it.canSkip }?.release ?: return
        prefs(context).edit().putString("skipped", offer.version.display).apply()
        changed()
    }

    // ---- Descarga e instalación ----

    /** Pantalla en primer plano (la usa para mostrar el diálogo del permiso y abrir el instalador al terminar). */
    fun onForeground(activity: Activity) {
        foreground = WeakReference(activity)
        appContext = activity.applicationContext
        if (pendingInstall != null) tryInstall(activity)
    }

    fun onBackground(activity: Activity) {
        if (foreground?.get() === activity) foreground = null
    }

    /** "Descargar e instalar": baja el APK de [release] (si no estaba ya) y abre el instalador del sistema. */
    fun downloadAndInstall(activity: Activity, release: ReleaseInfo) {
        if (download?.failed == false) return // ya está descargando
        val context = activity.applicationContext
        val asset = Releases.pickAsset(release, AssetKind.ANDROID_APK)
        if (asset == null || !Releases.isTrustedDownloadUrl(asset.downloadUrl) || ApkInstaller.targetFile(context, asset) == null) {
            openReleasePage(activity, release)
            return
        }
        askedPermission = false
        ApkInstaller.downloaded(context, asset)?.let { existing ->
            verifyThenInstall(existing, asset)
            return
        }
        try {
            ApkInstaller.cleanup(context)
            downloadId = ApkInstaller.enqueue(context, asset, "MyMouse ${release.version.display}")
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo empezar la descarga: ${e.message}")
            openReleasePage(activity, release)
            return
        }
        downloadAsset = asset
        downloadRelease = release
        download = Download(release.tag, 0f)
        changed()
        handler.postDelayed(poll, POLL_MS)
    }

    private val poll = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            val asset = downloadAsset ?: return
            val state = ApkInstaller.query(context, downloadId)
            when {
                state == null -> fail(context.getString(R.string.upd_fail_cancelled))
                state.status == DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = ApkInstaller.targetFile(context, asset)
                    val release = downloadRelease
                    clearDownload()
                    if (file == null || release == null) fail(context.getString(R.string.upd_fail_missing_file)) else verifyThenInstall(file, asset)
                }
                state.status == DownloadManager.STATUS_FAILED -> {
                    ApkInstaller.cancel(context, downloadId)
                    fail(context.getString(R.string.upd_fail_download))
                }
                else -> {
                    download = download?.copy(progress = state.progress)
                    changed()
                    handler.postDelayed(this, POLL_MS)
                }
            }
        }
    }

    private fun clearDownload() {
        download = null
        downloadAsset = null
        downloadRelease = null
        downloadId = -1L
        changed()
    }

    private fun fail(message: String) {
        val tag = download?.tag ?: downloadRelease?.tag ?: ""
        downloadAsset = null
        downloadRelease = null
        downloadId = -1L
        download = Download(tag, 0f, failed = true, message = message)
        changed()
    }

    /** Quita el aviso de descarga fallida (al tocarlo o al volver a intentarlo). */
    fun clearFailure() {
        if (download?.failed == true) {
            download = null
            changed()
        }
    }

    /** Comprueba tamaño y SHA-256 en un hilo aparte y, si está bien, lo instala (o lo deja pendiente si la app no está delante). */
    private fun verifyThenInstall(file: File, asset: dev.mymouse.core.update.ReleaseAsset) {
        thread(name = "MyMouse-verify", isDaemon = true) {
            val ok = ApkInstaller.verify(file, asset)
            handler.post {
                if (!ok) {
                    file.delete()
                    fail(appContext?.getString(R.string.upd_fail_invalid) ?: "")
                    return@post
                }
                pendingInstall = file
                // Sin la app delante no se puede abrir el instalador (Android 10 lo bloquea): se instala al volver (onForeground).
                foreground?.get()?.let { tryInstall(it) }
            }
        }
    }

    /** Abre el instalador; si falta el permiso de "Instalar apps desconocidas", lo explica y abre su pantalla del sistema. */
    private fun tryInstall(activity: Activity) {
        val file = pendingInstall ?: return
        if (!ApkInstaller.canInstall(activity)) {
            if (askedPermission) return
            askedPermission = true
            AlertDialog.Builder(activity)
                .setTitle(R.string.upd_permission_title)
                .setMessage(R.string.upd_permission_message)
                .setPositiveButton(R.string.upd_open_settings) { _, _ -> ApkInstaller.openUnknownSourcesSettings(activity) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        pendingInstall = null
        try {
            ApkInstaller.install(activity, file)
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo abrir el instalador: ${e.message}")
            fail(activity.getString(R.string.upd_fail_installer))
        }
    }

    /** Alternativa cuando no se puede descargar dentro de la app: la página de la release en el navegador. */
    fun openReleasePage(activity: Activity, release: ReleaseInfo) {
        val uri = Uri.parse(release.pageUrl)
        if (uri.scheme != "https") return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir ${release.pageUrl}: ${e.message}")
        }
    }
}
