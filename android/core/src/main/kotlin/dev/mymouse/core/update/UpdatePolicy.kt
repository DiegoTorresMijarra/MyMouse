package dev.mymouse.core.update

enum class VersionSide { PC, PHONE }

enum class CrossDeviceKind {
    /** Sin aviso: misma versión, desconocida o alguna de desarrollo. */
    NONE,

    /** El otro dispositivo tiene una versión más alta: se puede actualizar este. */
    OTHER_NEWER,

    /** El otro dispositivo tiene una versión más baja: solo se informa, se actualiza desde el otro. */
    OTHER_OLDER,
}

/** Resultado de comparar la versión de este dispositivo con la del otro (docs/spec-v5.1-actualizaciones.md §6). */
data class CrossDeviceNotice(val kind: CrossDeviceKind, val mineVersion: AppVersion?, val otherVersion: AppVersion?) {
    companion object {
        val NONE = CrossDeviceNotice(CrossDeviceKind.NONE, null, null)
    }
}

enum class BannerKind { NEW_VERSION, PC_NEWER, PC_OLDER }

/**
 * Un aviso. [release] es lo que instalaría "Descargar e instalar" (null si no se encontró en GitHub). El texto lo compone la app según
 * [kind], para que salga en el idioma del móvil: [version] es la que se nombra (la nueva, o la del PC) y [mineVersion] la de este móvil.
 */
data class UpdateBanner(
    val kind: BannerKind,
    val version: AppVersion,
    val mineVersion: AppVersion?,
    val release: ReleaseInfo?,
    val canInstall: Boolean,
    val canSkip: Boolean,
)

/** Decisiones puras de las actualizaciones: si avisar y qué decir. */
object UpdatePolicy {
    /**
     * ¿Se ofrece [latest] a quien tiene [local]? No si la versión propia es "dev" o no se entiende, si la release no es más alta, o si el usuario la
     * omitió ([skipped]). Una release posterior a la omitida sí se ofrece.
     */
    fun shouldOffer(local: AppVersion?, latest: ReleaseInfo?, skipped: String?): Boolean {
        if (latest == null || local == null || local.isDev) return false
        if (latest.version <= local) return false
        return !isSkipped(latest.version, skipped)
    }

    /** ¿Es [version] la versión que el usuario decidió omitir? (Se guarda como texto: `5.2`.) */
    fun isSkipped(version: AppVersion, skipped: String?): Boolean = AppVersion.parse(skipped)?.sameNumbers(version) == true

    /**
     * Aviso cruzado. [mine] es la versión de este dispositivo y [other] la que dijo el otro por USB. No hay aviso si alguna se desconoce o es
     * "dev", ni si son iguales.
     */
    fun crossDevice(side: VersionSide, mine: String?, other: String?): CrossDeviceNotice {
        val a = AppVersion.parse(mine)?.takeUnless { it.isDev } ?: return CrossDeviceNotice.NONE
        val b = AppVersion.parse(other)?.takeUnless { it.isDev } ?: return CrossDeviceNotice.NONE
        val cmp = b.compareTo(a)
        if (cmp == 0) return CrossDeviceNotice.NONE

        return CrossDeviceNotice(if (cmp > 0) CrossDeviceKind.OTHER_NEWER else CrossDeviceKind.OTHER_OLDER, a, b)
    }

    /**
     * Los avisos de este móvil (el de versión nueva primero; como mucho dos). Si ya se ofrece una release (al menos tan alta como la del PC), el
     * aviso cruzado de "el PC es más nuevo" no se repite.
     */
    fun banners(local: AppVersion?, localText: String?, releases: List<ReleaseInfo>, skipped: String?, pcVersion: String?): List<UpdateBanner> {
        val result = mutableListOf<UpdateBanner>()
        val offer = highestOffer(local, releases, skipped)
        if (offer != null) {
            result += UpdateBanner(BannerKind.NEW_VERSION, offer.version, local, offer, canInstall = Releases.pickAsset(offer, AssetKind.ANDROID_APK) != null, canSkip = true)
        }

        val cross = crossDevice(VersionSide.PHONE, local?.display ?: localText, pcVersion)
        when (cross.kind) {
            CrossDeviceKind.OTHER_NEWER -> if (offer == null) {
                val target = Releases.find(releases, cross.otherVersion!!)
                result += UpdateBanner(BannerKind.PC_NEWER, cross.otherVersion!!, cross.mineVersion, target, canInstall = target != null && Releases.pickAsset(target, AssetKind.ANDROID_APK) != null, canSkip = false)
            }
            CrossDeviceKind.OTHER_OLDER -> result += UpdateBanner(BannerKind.PC_OLDER, cross.otherVersion!!, cross.mineVersion, null, canInstall = false, canSkip = false)
            CrossDeviceKind.NONE -> Unit
        }
        return result
    }

    /** La release más alta si hay que ofrecerla (ver [shouldOffer]), o null. */
    fun highestOffer(local: AppVersion?, releases: List<ReleaseInfo>, skipped: String?): ReleaseInfo? =
        Releases.highest(releases)?.takeIf { shouldOffer(local, it, skipped) }

    /** Notas de la release recortadas para una hoja: sin líneas en blanco de más y como mucho [max] caracteres. */
    fun trimNotes(notes: String, max: Int = 600): String {
        var text = notes.replace("\r", "").split('\n').joinToString("\n") { it.trimEnd() }.trim()
        while (text.contains("\n\n\n")) text = text.replace("\n\n\n", "\n\n")
        if (text.length <= max) return text
        var cut = text.substring(0, max)
        val lastBreak = cut.lastIndexOfAny(charArrayOf('\n', ' '))
        if (lastBreak > max / 2) cut = cut.substring(0, lastBreak)
        return cut.trimEnd() + "…"
    }
}
