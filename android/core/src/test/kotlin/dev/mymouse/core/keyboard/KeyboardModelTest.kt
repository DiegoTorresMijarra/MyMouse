package dev.mymouse.core.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardModelTest {
    private val combos = buildList {
        for (bits in 0 until 32) {
            add(KeyboardCombo(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0, bits and 16 != 0))
        }
    }

    private fun every(block: (String, KeyboardCombo, Boolean, KeyboardGeometry) -> Unit) {
        for (layout in listOf("es", "us")) for (landscape in listOf(false, true)) for (c in combos) {
            block(layout, c, landscape, KeyboardModel.build(layout, c, landscape))
        }
    }

    private fun f(x: Float) = Math.round(x * 100)

    /** Ancho cubierto por las teclas de una zona en cada fila (las altas cuentan en todas las filas que ocupan). */
    private fun rowWidths(keys: List<KeyDef>): Map<Int, Int> {
        val rows = sortedSetOf<Int>()
        keys.forEach { k -> var y = Math.round(k.y * 100); while (y < Math.round(k.bottom * 100)) { rows += y; y += 100 } }
        return rows.associateWith { row -> keys.filter { f(it.y) <= row && row < f(it.bottom) }.sumOf { f(it.w) } }
    }

    @Test
    fun everyRowOfABlockAddsUpToTheSameWidth() {
        every { layout, combo, landscape, g ->
            val c = combo.normalized(landscape)
            // Las teclas fijas son parte de la fila inferior de la base (o la columna del solo numpad, que se comprueba aparte).
            val zones = g.keys.groupBy { if (it.zone == KeyZone.FIXED && !c.numpadOnly) KeyZone.BASE else it.zone }
            for ((zone, keys) in zones) {
                if (zone == KeyZone.FIXED) continue
                val widths = rowWidths(keys).values.toSet()
                assertEquals(1, widths.size, "zona $zone con $layout, $c, horizontal=$landscape: $widths")
            }
            // Base y Fn, 16 u; flechas y navegación, 3; numpad, 4 (en el solo numpad, 4 más la columna fija).
            zones[KeyZone.BASE]?.let { assertEquals(1600, rowWidths(it).values.first() ) }
            zones[KeyZone.FN]?.let { assertEquals(1600, rowWidths(it).values.first()) }
            zones[KeyZone.NAV]?.let { assertEquals(300, rowWidths(it).values.first()) }
            zones[KeyZone.ARROWS]?.let { assertEquals(300, rowWidths(it).values.first()) }
            zones[KeyZone.NUMPAD]?.let { assertEquals(400, rowWidths(it).values.first()) }
            zones[KeyZone.NUMPAD_ONLY]?.let { assertEquals(400, rowWidths(it).values.first()) }
        }
    }

    @Test
    fun baseHasFiveRowsAndTheBlocksStayInsideTheGeometry() {
        every { _, _, _, g ->
            for (k in g.keys) {
                assertTrue(k.x >= 0f && k.right <= g.widthUnits + 0.001f, "$k fuera del ancho ${g.widthUnits}")
                assertTrue(k.y >= 0f && k.bottom <= g.heightUnits + 0.001f, "$k fuera del alto ${g.heightUnits}")
            }
        }
        assertEquals(5f, KeyboardModel.build("es", KeyboardCombo(), false).heightUnits)
        assertEquals(6f, KeyboardModel.build("es", KeyboardCombo(fn = true), false).heightUnits)
        assertEquals(16f, KeyboardModel.build("us", KeyboardCombo(), false).widthUnits)
        assertEquals(16f + 0.5f + 3f + 0.5f + 4f, KeyboardModel.build("us", KeyboardCombo(nav = true, numpad = true), true).widthUnits)
    }

    @Test
    fun noUsageIsRepeatedAndKeypadEnterHasItsOwn() {
        every { layout, combo, landscape, g ->
            val usages = g.keys.filter { it.usage != 0 }.map { it.usage }
            assertEquals(usages.size, usages.toSet().size, "usos repetidos con $layout, $combo, horizontal=$landscape: ${usages.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
        val all = KeyboardModel.build("es", KeyboardCombo(fn = true, numpad = true), true)
        assertEquals("Intro", all.keys.single { it.usage == 0x58 }.label)
        assertEquals("Intro", all.keys.single { it.usage == 0x28 }.label)
    }

    @Test
    fun baseHasEveryKeyOfAFullKeyboard() {
        for (layout in listOf("es", "us")) {
            val g = KeyboardModel.build(layout, KeyboardCombo(), false)
            val usages = g.keys.map { it.usage }.toSet()
            for (letter in 0x04..0x1D) assertTrue(letter in usages, "falta la letra $letter")
            for (digit in 0x1E..0x27) assertTrue(digit in usages, "falta el número $digit")
            for (u in listOf(0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x39, 0x65, 0xE0, 0xE1, 0xE2, 0xE3, 0xE5, 0xE6)) assertTrue(u in usages, "falta 0x%02X".format(u))
        }
        // La ISO tiene la tecla <>; la ANSI tiene la barra invertida en su sitio.
        assertTrue(KeyboardModel.build("es", KeyboardCombo(), false).keys.any { it.usage == 0x64 })
        assertFalse(KeyboardModel.build("us", KeyboardCombo(), false).keys.any { it.usage == 0x64 })
        assertTrue(KeyboardModel.build("us", KeyboardCombo(), false).keys.any { it.usage == 0x31 })
    }

    @Test
    fun spanishAndEnglishLabelsOnSymbolKeys() {
        fun key(layout: String, usage: Int) = KeyboardModel.build(layout, KeyboardCombo(), false).keys.single { it.usage == usage }
        // La tecla 2: " arriba y @ abajo en ES; @ arriba en US.
        val es2 = key("es", 0x1F)
        assertEquals(Triple("2", "\"", "@"), Triple(es2.label, es2.shiftLabel, es2.altGrLabel))
        val us2 = key("us", 0x1F)
        assertEquals(Triple("2", "@", null), Triple(us2.label, us2.shiftLabel, us2.altGrLabel))
        assertEquals("ª", key("es", 0x35).shiftLabel)
        assertEquals("\\", key("es", 0x35).altGrLabel)
        assertEquals("~", key("us", 0x35).shiftLabel)
        assertEquals("Ñ", key("es", 0x33).label)
        assertEquals(";", key("us", 0x33).label)
        assertEquals("€", key("es", 0x22).altGrLabel)
        assertEquals("{", key("es", 0x34).altGrLabel)
        assertEquals("}", key("es", 0x32).altGrLabel)
        assertEquals("Alt Gr", key("es", 0xE6).label)
        assertEquals("Alt", key("us", 0xE6).label)
        assertEquals("/", key("us", 0x38).label)
        assertEquals("-", key("es", 0x38).label)
        assertEquals("Bloq Mayús", key("es", 0x39).label)
        assertEquals("Caps Lock", key("us", 0x39).label)
        // Las letras llevan la mayúscula como etiqueta, sin etiqueta de Shift.
        val a = key("es", 0x04)
        assertTrue(a.letter)
        assertEquals("A", a.label)
        assertNull(a.shiftLabel)
        assertFalse(key("es", 0x1E).letter)
    }

    @Test
    fun unknownLayoutFallsBackToSpanish() {
        assertEquals("es", KeyboardModel.layout("zz").id)
        assertEquals("us", KeyboardModel.layout("us").id)
        assertEquals(KeyboardModel.build("es", KeyboardCombo(), false), KeyboardModel.build(null, KeyboardCombo(), false))
    }

    @Test
    fun fixedKeysAreInEveryCombination() {
        every { layout, _, _, g ->
            val mode = g.keys.filter { it.kind == KeyKind.MODE }
            val mouse = g.keys.filter { it.kind == KeyKind.MOUSE }
            assertEquals(1, mode.size, layout)
            assertEquals(1, mouse.size, layout)
            assertTrue(mode[0].usage == 0 && mouse[0].usage == 0)
        }
        // En la base van a la izquierda de la fila inferior, antes de Ctrl.
        val g = KeyboardModel.build("es", KeyboardCombo(), false)
        val bottom = g.keys.filter { it.y == 4f }.sortedBy { it.x }
        assertEquals(listOf(KeyKind.MODE, KeyKind.MOUSE), bottom.take(2).map { it.kind })
        assertEquals(0xE0, bottom[2].usage)
        // En el solo numpad, una columna a la izquierda.
        val n = KeyboardModel.build("es", KeyboardCombo(numpadOnly = true), false)
        val fixed = n.keys.filter { it.zone == KeyZone.FIXED }
        assertEquals(2, fixed.size)
        assertTrue(fixed.all { it.x == 0f && it.right == KeyboardModel.FIXED_COLUMN_WIDTH })
        assertEquals(5f, fixed.sumOf { it.h.toDouble() }.toFloat())
    }

    @Test
    fun numpadOnlyHasNoBase() {
        val g = KeyboardModel.build("es", KeyboardCombo(numpadOnly = true, fn = true, arrows = true), true)
        assertTrue(g.keys.none { it.zone == KeyZone.BASE || it.zone == KeyZone.FN || it.zone == KeyZone.ARROWS })
        assertEquals(KeyboardModel.FIXED_COLUMN_WIDTH + 4f, g.widthUnits)
        assertEquals(setOf(0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x60, 0x61, 0x62, 0x63),
            g.keys.filter { it.zone == KeyZone.NUMPAD_ONLY }.map { it.usage }.toSet())
    }

    @Test
    fun navigationIncludesTheArrows() {
        val g = KeyboardModel.build("es", KeyboardCombo(nav = true, arrows = true), true)
        val nav = g.keys.filter { it.zone == KeyZone.NAV }.map { it.usage }.toSet()
        assertTrue(setOf(0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52).all { it in nav })
        assertTrue(g.keys.none { it.zone == KeyZone.ARROWS })
    }

    @Test
    fun arrowsAreAnInvertedT() {
        val g = KeyboardModel.build("us", KeyboardCombo(arrows = true), true)
        val a = g.keys.filter { it.zone == KeyZone.ARROWS && it.kind == KeyKind.NORMAL }.associateBy { it.usage }
        val up = a.getValue(0x52); val down = a.getValue(0x51); val left = a.getValue(0x50); val right = a.getValue(0x4F)
        assertEquals(down.x, up.x)
        assertEquals(down.y, left.y)
        assertTrue(left.x < down.x && down.x < right.x)
        assertTrue(up.y < down.y)
    }

    @Test
    fun hitTestingFindsKeysAndIgnoresSpacers() {
        val g = KeyboardModel.build("es", KeyboardCombo(arrows = true), true)
        assertEquals(0x04, g.keyAt(2.5f, 2.5f)?.usage) // A
        assertEquals(KeyKind.MODE, g.keyAt(0.5f, 4.5f)?.kind)
        assertNull(g.keyAt(16.2f, 0.5f)) // el hueco entre bloques
        assertNull(g.keyAt(17.0f, 3.5f)) // el hueco sobre la flecha izquierda... es un espaciador
        assertNotNull(g.keyAt(18.0f, 3.5f)) // ▲
        assertEquals(0x28, g.keyAt(14.5f, 1.5f)?.usage) // Intro ISO alta
        assertEquals(0x28, g.keyAt(14.5f, 2.5f)?.usage)
    }

    @Test
    fun repeatableKeysAreTheOnesInTheSpec() {
        val g = KeyboardModel.build("es", KeyboardCombo(nav = true, numpad = true), true)
        fun rep(u: Int) = g.keys.single { it.usage == u }.repeatable
        for (u in listOf(0x04, 0x1E, 0x27, 0x2A, 0x4C, 0x4F, 0x52, 0x2C, 0x28, 0x58, 0x5F)) assertTrue(rep(u), "0x%02X".format(u))
        for (u in listOf(0xE1, 0x39, 0x53, 0x29, 0x2B, 0x4A, 0x4B)) assertFalse(rep(u), "0x%02X".format(u))
        assertTrue(g.keys.filter { it.kind == KeyKind.MODE || it.kind == KeyKind.MOUSE }.none { it.repeatable })
    }

    // ---- Combinaciones ----

    @Test
    fun navAndArrowsExcludeEachOther() {
        val nav = KeyboardCombo().toggled(KeyboardBlock.NAV, true)
        assertTrue(nav.nav && !nav.arrows)
        val arrows = nav.toggled(KeyboardBlock.ARROWS, true)
        assertTrue(arrows.arrows && !arrows.nav)
        assertTrue(arrows.toggled(KeyboardBlock.NAV, true).let { it.nav && !it.arrows })
    }

    @Test
    fun landscapeAddsLateralBlocks() {
        var c = KeyboardCombo()
        c = c.toggled(KeyboardBlock.FN, true).toggled(KeyboardBlock.ARROWS, true).toggled(KeyboardBlock.NUMPAD, true)
        assertEquals(KeyboardCombo(fn = true, arrows = true, numpad = true), c)
        assertEquals(KeyboardCombo(fn = true, nav = true, numpad = true), c.toggled(KeyboardBlock.NAV, true))
    }

    @Test
    fun portraitOnlyFitsOneLateralBlock() {
        var c = KeyboardCombo().toggled(KeyboardBlock.ARROWS, false)
        assertEquals(KeyboardCombo(arrows = true), c)
        c = c.toggled(KeyboardBlock.NUMPAD, false)
        assertEquals(KeyboardCombo(numpad = true), c)
        c = c.toggled(KeyboardBlock.NAV, false)
        assertEquals(KeyboardCombo(nav = true), c)
        // Fn sí se combina siempre.
        assertEquals(KeyboardCombo(fn = true, nav = true), c.toggled(KeyboardBlock.FN, false))
        // Apagar el único bloque lateral no enciende otro.
        assertEquals(KeyboardCombo(), c.toggled(KeyboardBlock.NAV, false))
    }

    @Test
    fun numpadOnlyIsExclusive() {
        val c = KeyboardCombo(fn = true, nav = true, numpad = true).toggled(KeyboardBlock.NUMPAD_ONLY, true)
        assertEquals(KeyboardCombo(numpadOnly = true), c)
        // Encender cualquier otro bloque saca del solo numpad.
        assertEquals(KeyboardCombo(fn = true), c.toggled(KeyboardBlock.FN, true))
        assertEquals(KeyboardCombo(arrows = true), c.toggled(KeyboardBlock.ARROWS, true))
        assertEquals(KeyboardCombo(numpad = true), c.toggled(KeyboardBlock.NUMPAD, false))
        // Apagarlo vuelve a la base.
        assertEquals(KeyboardCombo(), c.toggled(KeyboardBlock.NUMPAD_ONLY, true))
    }

    @Test
    fun normalizeFixesStoredCombinations() {
        assertEquals(KeyboardCombo(numpadOnly = true), KeyboardCombo(fn = true, numpad = true, numpadOnly = true).normalized(true))
        assertEquals(KeyboardCombo(nav = true), KeyboardCombo(nav = true, arrows = true).normalized(true))
        assertEquals(KeyboardCombo(numpad = true), KeyboardCombo(nav = true, arrows = true, numpad = true).normalized(false))
        assertEquals(KeyboardCombo(nav = true, numpad = true), KeyboardCombo(nav = true, arrows = true, numpad = true).normalized(true))
    }

    @Test
    fun noValidCombinationAfterAnyToggleBreaksTheRules() {
        for (landscape in listOf(false, true)) {
            var c = KeyboardCombo.default(landscape)
            val blocks = KeyboardBlock.entries
            // Todas las secuencias de tres toques.
            for (a in blocks) for (b in blocks) for (d in blocks) {
                c = KeyboardCombo.default(landscape).toggled(a, landscape).toggled(b, landscape).toggled(d, landscape)
                assertEquals(c, c.normalized(landscape), "$a $b $d horizontal=$landscape")
                if (c.numpadOnly) assertEquals(KeyboardCombo(numpadOnly = true), c)
                if (!landscape) assertTrue(listOf(c.arrows, c.nav, c.numpad).count { it } <= 1)
                assertFalse(c.nav && c.arrows)
            }
        }
    }

    @Test
    fun combinationsRoundTripThroughSettingsText() {
        for (landscape in listOf(false, true)) for (c in combos.map { it.normalized(landscape) }.toSet()) {
            assertEquals(c, KeyboardCombo.decode(c.encode(), landscape))
        }
        assertEquals(KeyboardCombo(), KeyboardCombo.decode("", false))
        assertEquals(KeyboardCombo(arrows = true), KeyboardCombo.decode(null, true))
        assertEquals(KeyboardCombo(), KeyboardCombo.decode(null, false))
        assertEquals(listOf("Fn", "Nav", "Num"), KeyboardCombo(fn = true, nav = true, numpad = true).labels())
    }

    @Test
    fun swappingBetweenTheLastTwoCombinations() {
        val h = ComboHistory(KeyboardCombo(numpad = true))
        assertFalse(h.swap())
        h.change(KeyboardCombo(numpadOnly = true))
        assertEquals(KeyboardCombo(numpadOnly = true), h.current)
        assertTrue(h.swap())
        assertEquals(KeyboardCombo(numpad = true), h.current)
        assertTrue(h.swap())
        assertEquals(KeyboardCombo(numpadOnly = true), h.current)
        // Un cambio que no cambia nada no toca el historial.
        h.change(h.current)
        assertTrue(h.swap())
        assertEquals(KeyboardCombo(numpad = true), h.current)
    }
}
