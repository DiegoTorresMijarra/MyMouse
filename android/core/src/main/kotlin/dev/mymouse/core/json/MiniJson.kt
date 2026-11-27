package dev.mymouse.core.json

/**
 * JSON mínimo para `core`, que no tiene dependencias (el móvil usa los ficheros `bt-gestures.json` y `bt-widgets.json`; los temas, `shared/themes.json`).
 * [parse] devuelve Map, List, String, Double, Boolean o null; [write] hace el camino de vuelta con sangrado de dos espacios.
 * Los errores son [IllegalArgumentException] con un mensaje en español.
 */
object MiniJson {
    fun parse(text: String): Any? = try {
        Reader(text).readDocument()
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: RuntimeException) {
        fail("JSON no válido (${e.javaClass.simpleName})")
    }

    /** Escribe [value] (Map, List, String, Número, Boolean o null) como JSON con sangrado. */
    fun write(value: Any?): String = StringBuilder().also { write(it, value, 0) }.append('\n').toString()

    private fun write(sb: StringBuilder, value: Any?, level: Int) {
        val pad = "  ".repeat(level + 1)
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long -> sb.append(value)
            is Number -> sb.append(value.toDouble().let { if (it == Math.rint(it) && Math.abs(it) < 1e15) it.toLong().toString() else it.toString() })
            is Map<*, *> -> if (value.isEmpty()) sb.append("{}") else {
                sb.append("{\n")
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append(pad)
                    quote(sb, k.toString())
                    sb.append(": ")
                    write(sb, v, level + 1)
                }
                sb.append('\n').append("  ".repeat(level)).append('}')
            }
            is List<*> -> if (value.isEmpty()) sb.append("[]") else {
                sb.append("[\n")
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(",\n")
                    sb.append(pad)
                    write(sb, v, level + 1)
                }
                sb.append('\n').append("  ".repeat(level)).append(']')
            }
            else -> throw IllegalArgumentException("No se puede escribir ${value.javaClass.simpleName} como JSON")
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

    private class Reader(private val s: String) {
        private var i = 0

        fun readDocument(): Any? {
            val v = value()
            ws()
            if (i != s.length) fail("texto sobrante en la posición $i")
            return v
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun value(): Any? {
            ws()
            if (i >= s.length) fail("fin inesperado")
            return when (val c = s[i]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) numberValue() else fail("carácter inesperado '$c' en $i")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("se esperaba $word en $i")
            i += word.length
            return v
        }

        private fun numberValue(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("número no válido en $start")
        }

        private fun stringValue(): String {
            i++ // comilla de apertura
            val sb = StringBuilder()
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s.getOrNull(i) ?: fail("cadena sin cerrar")) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            if (i >= s.length) fail("cadena sin cerrar")
            i++ // comilla de cierre
            return sb.toString()
        }

        private fun arrayValue(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (s.getOrNull(i) == ']') {
                i++
                return out
            }
            while (true) {
                out.add(value())
                ws()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    ']' -> return out
                    else -> fail("se esperaba , o ] en ${i - 1}")
                }
            }
        }

        private fun objectValue(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (s.getOrNull(i) == '}') {
                i++
                return out
            }
            while (true) {
                ws()
                if (s.getOrNull(i) != '"') fail("se esperaba una clave en $i")
                val key = stringValue()
                ws()
                if (s.getOrNull(i++) != ':') fail("se esperaba : en ${i - 1}")
                out[key] = value()
                ws()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    '}' -> return out
                    else -> fail("se esperaba , o } en ${i - 1}")
                }
            }
        }
    }
}
