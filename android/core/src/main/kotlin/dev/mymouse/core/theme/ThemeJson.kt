package dev.mymouse.core.theme

import dev.mymouse.core.json.MiniJson

/** Lo que dice `shared/themes.json`: acentos, tema por defecto y temas. */
data class ThemeFile(val defaultTheme: String, val accents: List<AccentOption>, val themes: List<ThemeDef>)

/**
 * Lectura y validación de `shared/themes.json` (docs/spec-v4.8-temas.md §4), con el lector de JSON mínimo [MiniJson] (el módulo `core` no
 * tiene dependencias). Lanza [IllegalArgumentException] con un mensaje en español si el fichero no es válido.
 */
object ThemeJson {
    fun parse(text: String): ThemeFile {
        val root = (try { MiniJson.parse(text) } catch (e: IllegalArgumentException) { fail(e.message ?: "JSON no válido") }) as? Map<*, *>
            ?: fail("la raíz debe ser un objeto")
        val accents = list(root["accents"], "accents").map { a ->
            val o = obj(a, "accent")
            AccentOption(string(o, "id"), string(o, "name"), color(o, "color"))
        }
        val themes = list(root["themes"], "themes").map { t ->
            val o = obj(t, "theme")
            ThemeDef(
                id = string(o, "id"),
                name = string(o, "name"),
                base = list(o["base"], "base").map { c -> Themes.parseHex(c as? String) ?: fail("color base no válido: $c") },
                nebulas = list(o["nebulas"], "nebulas").map { n ->
                    val no = obj(n, "nebula")
                    Nebula(color(no, "color"), number(no, "opacity"), number(no, "x"), number(no, "y"), number(no, "r"))
                },
                stars = o["stars"] as? Boolean ?: fail("falta stars"),
                starDensity = number(o, "starDensity"),
                twinkleDepth = number(o, "twinkleDepth"),
                accent = color(o, "accent"),
            )
        }
        val file = ThemeFile(string(root, "defaultTheme"), accents, themes)
        validate(file)
        return file
    }

    /** Reglas: ids únicos y no vacíos, 1–3 colores de base, 0–3 nebulosas con opacidad 0–1 y radio > 0, el tema por defecto existe. */
    fun validate(file: ThemeFile) {
        if (file.themes.isEmpty()) fail("no hay temas")
        if (file.themes.map { it.id }.toSet().size != file.themes.size) fail("ids de tema repetidos")
        if (file.themes.any { it.id.isBlank() }) fail("hay un tema sin id")
        if (file.themes.none { it.id == file.defaultTheme }) fail("el tema por defecto '${file.defaultTheme}' no existe")
        val accentIds = file.accents.map { it.id }
        if (accentIds.toSet().size != accentIds.size || Themes.ACCENT_AUTO in accentIds) fail("ids de acento repetidos o reservados")
        for (t in file.themes) {
            if (t.base.size !in 1..3) fail("${t.id}: la base lleva de 1 a 3 colores")
            if (t.nebulas.size > 3) fail("${t.id}: como mucho 3 nebulosas")
            for (n in t.nebulas) {
                if (n.opacity !in 0f..1f) fail("${t.id}: opacidad de nebulosa fuera de 0–1")
                if (n.r <= 0f) fail("${t.id}: radio de nebulosa no positivo")
            }
            if (t.starDensity < 0f || t.twinkleDepth !in 0f..1f) fail("${t.id}: estrellas no válidas")
            if (t.stars && t.starDensity <= 0f) fail("${t.id}: tiene estrellas pero densidad 0")
        }
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException("themes.json: $message")

    private fun obj(v: Any?, what: String): Map<*, *> = v as? Map<*, *> ?: fail("$what debe ser un objeto")
    private fun list(v: Any?, what: String): List<*> = v as? List<*> ?: fail("$what debe ser una lista")
    private fun string(o: Map<*, *>, key: String): String = o[key] as? String ?: fail("falta '$key'")
    private fun number(o: Map<*, *>, key: String): Float = (o[key] as? Double)?.toFloat() ?: fail("falta el número '$key'")
    private fun color(o: Map<*, *>, key: String): Int = Themes.parseHex(o[key] as? String) ?: fail("color no válido en '$key'")
}
