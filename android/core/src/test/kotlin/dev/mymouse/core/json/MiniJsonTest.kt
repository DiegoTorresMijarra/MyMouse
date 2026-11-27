package dev.mymouse.core.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MiniJsonTest {
    @Test
    fun writesAndReadsBack() {
        val value = linkedMapOf(
            "a" to 1,
            "texto" to "línea 1\nlínea \"2\"\t\\ ñ 😀",
            "lista" to listOf(true, null, "x", linkedMapOf("k" to 2.5)),
            "vacio" to emptyList<Any>(),
            "obj" to emptyMap<String, Any>(),
        )
        val parsed = MiniJson.parse(MiniJson.write(value)) as Map<*, *>
        assertEquals(1.0, parsed["a"])
        assertEquals("línea 1\nlínea \"2\"\t\\ ñ 😀", parsed["texto"])
        assertEquals(listOf(true, null, "x", mapOf("k" to 2.5)), parsed["lista"])
        assertEquals(emptyList<Any>(), parsed["vacio"])
        assertEquals(emptyMap<String, Any>(), parsed["obj"])
    }

    @Test
    fun controlCharactersAreEscaped() {
        val text = MiniJson.write(mapOf("t" to "a\u0001b\r"))
        assertEquals("a\u0001b\r", (MiniJson.parse(text) as Map<*, *>)["t"])
    }

    @Test
    fun invalidJsonIsAnIllegalArgument() {
        for (bad in listOf("", "no es json", "{", "{\"a\":}", "[1,]", "\"abc", "{\"a\":1} x", "\"\\u12\"")) {
            assertFailsWith<IllegalArgumentException>(bad) { MiniJson.parse(bad) }
        }
    }
}
