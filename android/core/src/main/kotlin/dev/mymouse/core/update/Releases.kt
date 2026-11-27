package dev.mymouse.core.update

import dev.mymouse.core.json.MiniJson

/** Un fichero de una release de GitHub. [sha256] viene del campo `digest` de la API cuando existe. */
data class ReleaseAsset(val name: String, val size: Long, val downloadUrl: String, val sha256: String?)

/** Una release de GitHub, ya validada: etiqueta `vX.Y[.Z]` y no borrador. */
data class ReleaseInfo(
    val tag: String,
    val version: AppVersion,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

enum class AssetKind { WINDOWS_INSTALLER, WINDOWS_ZIP, ANDROID_APK }

/** Lógica pura sobre la respuesta de `GET /repos/…/releases`: parseo, elección de la más alta y elección del fichero. */
object Releases {
    const val REPOSITORY = "DiegoTorresMijarra/MyMouse"
    const val LIST_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=10"
    const val USER_AGENT = "MyMouse-Updater"

    /**
     * Convierte el JSON de la API en releases válidas. Se ignoran los borradores y las etiquetas que no son `vX.Y[.Z]`. Un JSON que no es una lista
     * (p. ej. el error de límite de peticiones, un objeto) o no es JSON lanza [IllegalArgumentException].
     */
    fun parse(json: String): List<ReleaseInfo> {
        val root = MiniJson.parse(json) as? List<*> ?: throw IllegalArgumentException("La respuesta de GitHub no es una lista de releases")
        val result = mutableListOf<ReleaseInfo>()
        for (item in root) {
            val map = item as? Map<*, *> ?: continue
            if (map["draft"] == true) continue
            val tag = map["tag_name"] as? String ?: continue
            val version = AppVersion.parseTag(tag) ?: continue
            val title = (map["name"] as? String).orEmpty()
            result += ReleaseInfo(
                tag = tag,
                version = version,
                title = title.ifBlank { tag },
                notes = (map["body"] as? String).orEmpty(),
                pageUrl = (map["html_url"] as? String).orEmpty(),
                prerelease = map["prerelease"] == true,
                assets = parseAssets(map["assets"]),
            )
        }
        return result
    }

    private fun parseAssets(value: Any?): List<ReleaseAsset> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { a ->
            val map = a as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val url = map["browser_download_url"] as? String ?: return@mapNotNull null
            if (name.isEmpty() || url.isEmpty()) return@mapNotNull null
            val size = (map["size"] as? Number)?.toLong() ?: 0L
            val digest = map["digest"] as? String
            val sha = if (digest != null && digest.startsWith("sha256:", ignoreCase = true) && digest.length == 7 + 64) digest.substring(7).lowercase() else null
            ReleaseAsset(name, size, url, sha)
        }
    }

    /** La release de versión más alta (no se usa `/releases/latest`: todas son prerelease). Null si no hay ninguna. */
    fun highest(releases: List<ReleaseInfo>): ReleaseInfo? = releases.maxByOrNull { it.version }

    /** La release de una versión concreta (por números: `5.0` = `5.0.0`), o null. */
    fun find(releases: List<ReleaseInfo>, version: AppVersion): ReleaseInfo? = releases.firstOrNull { it.version.sameNumbers(version) }

    /** El fichero de la release para ese destino, o null si no está. Entre varios, el primero. */
    fun pickAsset(release: ReleaseInfo, kind: AssetKind): ReleaseAsset? = release.assets.firstOrNull { matches(it.name, kind) }

    fun matches(name: String, kind: AssetKind): Boolean = when (kind) {
        AssetKind.WINDOWS_INSTALLER -> name.startsWith("MyMouse-Setup-", ignoreCase = true) && name.endsWith(".exe", ignoreCase = true)
        AssetKind.WINDOWS_ZIP -> name.startsWith("MyMouse-Windows-", ignoreCase = true) && name.endsWith(".zip", ignoreCase = true)
        AssetKind.ANDROID_APK -> name.startsWith("MyMouse-", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)
    }

    /** Solo se descarga de GitHub por HTTPS (el enlace de la API es de `github.com`; GitHub redirige luego a su CDN). */
    fun isTrustedDownloadUrl(url: String): Boolean {
        val m = Regex("""^https://([^/:?#]+)(?::\d+)?(?:[/?#].*)?$""", RegexOption.IGNORE_CASE).matchEntire(url) ?: return false
        return m.groupValues[1].equals("github.com", ignoreCase = true)
    }
}
