package dev.mymouse.core.shapes

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compara [ShapeCatalog] con `shape-catalog.txt`, el mismo fichero que lee el test de C# (`ShapeCatalogTests`):
 * así el PC dibuja las miniaturas con las mismas coordenadas que el móvil (docs/spec-v4-toolbox.md §6).
 */
class ShapeCatalogFixtureTest {
    @Test
    fun catalogMatchesTheSharedFixture() {
        val text = assertNotNull(javaClass.getResourceAsStream("/shape-catalog.txt")).bufferedReader(Charsets.UTF_8).readText()
        val rows = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(ShapeCatalog.ALL.map { it.id }, rows.map { it.substringBefore('|') })
        for ((shape, row) in ShapeCatalog.ALL.zip(rows)) {
            val (id, closed, points) = row.split('|')
            assertEquals(closed == "1", shape.closed, id)
            val expected = points.split(';').map { it.split(',').let { (x, y) -> x.toFloat() to y.toFloat() } }
            assertEquals(expected.size, shape.points.size, id)
            for ((e, p) in expected.zip(shape.points)) {
                assertTrue(abs(e.first - p.x) < 1e-6f && abs(e.second - p.y) < 1e-6f, "$id: $e vs $p")
            }
        }
    }
}
