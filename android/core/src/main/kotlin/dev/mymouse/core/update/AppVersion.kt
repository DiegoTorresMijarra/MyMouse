package dev.mymouse.core.update

/**
 * Versión de MyMouse (docs/spec-v5.1-actualizaciones.md §3): `X.Y` o `X.Y.Z`. Las releases usan `7.0`, `7.0.1`, `7.1` (etiqueta `v7.1`) y las
 * compilaciones de rama `0.0-dev.N`, que son versiones "dev": no avisan nunca. Se comparan los números, con el parche ausente como 0
 * (`5.0` == `5.0.0`); por eso es una clase normal y no una `data class`: la marca [isDev] no entra en la igualdad.
 */
class AppVersion(val major: Int, val minor: Int, val patch: Int, val isDev: Boolean) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    /** Mismos números (`5.0` y `5.0.0` son iguales). */
    fun sameNumbers(other: AppVersion) = compareTo(other) == 0

    /** Texto para mostrar: `5.1`, o `5.0.1` si hay parche. */
    val display: String get() = if (patch == 0) "$major.$minor" else "$major.$minor.$patch"

    override fun equals(other: Any?) = other is AppVersion && sameNumbers(other)
    override fun hashCode() = (major * 31 + minor) * 31 + patch
    override fun toString() = display

    companion object {
        // v?X.Y[.Z][.W] [-sufijo] [+metadatos]
        private val LOCAL = Regex("""^\s*[vV]?(\d{1,6})\.(\d{1,6})(?:\.(\d{1,6}))?(?:\.\d{1,6})?(?:-([0-9A-Za-z.\-]+))?(?:\+[0-9A-Za-z.\-]+)?\s*$""")
        private val TAG = Regex("""^v(\d{1,6})\.(\d{1,6})(?:\.(\d{1,6}))?$""")

        /**
         * Versión propia o del otro dispositivo: admite `v` inicial, sufijo (`-dev.12`) y metadatos (`+hash`). Es "dev" si lleva un sufijo con
         * "dev" o es la `0.0`. Devuelve null si no se entiende.
         */
        fun parse(text: String?): AppVersion? {
            if (text.isNullOrBlank()) return null
            val m = LOCAL.matchEntire(text) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: return null
            val minor = m.groupValues[2].toIntOrNull() ?: return null
            val patch = if (m.groupValues[3].isEmpty()) 0 else m.groupValues[3].toIntOrNull() ?: return null
            val suffix = m.groupValues[4]
            val dev = suffix.contains("dev", ignoreCase = true) || (major == 0 && minor == 0)
            return AppVersion(major, minor, patch, dev)
        }

        /** Etiqueta de una release de GitHub: solo `vX.Y` o `vX.Y.Z`. Lo demás (`nightly`, `v5.1-rc1`) es null. */
        fun parseTag(tag: String?): AppVersion? {
            if (tag.isNullOrEmpty()) return null
            val m = TAG.matchEntire(tag) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: return null
            val minor = m.groupValues[2].toIntOrNull() ?: return null
            val patch = if (m.groupValues[3].isEmpty()) 0 else m.groupValues[3].toIntOrNull() ?: return null
            return AppVersion(major, minor, patch, false)
        }
    }
}
