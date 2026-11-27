package dev.mymouse.core.universe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarFieldTest {
    @Test
    fun sameSeedGivesSameStars() {
        assertEquals(StarField.generate(90), StarField.generate(90))
        assertTrue(StarField.generate(20, seed = 1) != StarField.generate(20, seed = 2))
    }

    @Test
    fun shorterFieldIsAPrefixSoResizingDoesNotMoveStars() {
        val big = StarField.generate(120)
        assertEquals(big.take(60), StarField.generate(60))
    }

    @Test
    fun matchesTheReferenceValuesOfTheWindowsSide() {
        // Los mismos valores están en StarFieldTests.cs (MyMouse.HostCore.Tests): los dos lados dibujan el mismo cielo.
        val s = StarField.generate(3)
        assertEquals(0.19447365f, s[0].x, 1e-6f)
        assertEquals(0.71386205f, s[0].y, 1e-6f)
        assertEquals(1.14943442f, s[0].radius, 1e-5f)
        assertEquals(0.56145599f, s[0].opacity, 1e-6f)
        assertEquals(4.5371262f, s[0].periodSeconds, 1e-5f)
        assertEquals(1.7658669f, s[0].phase, 1e-5f)
        assertEquals(0.06382243f, s[1].x, 1e-6f)
        assertEquals(0.76248036f, s[2].x, 1e-6f)
    }

    @Test
    fun valuesStayInTheirRanges() {
        val stars = StarField.generate(400)
        for (s in stars) {
            assertTrue(s.x in 0f..1f && s.y in 0f..1f)
            assertTrue(s.radius in 0.5f..1.6f, "radio ${s.radius}")
            assertTrue(s.opacity in 0.35f..1f, "opacidad ${s.opacity}")
            assertTrue(s.periodSeconds in 2f..5f)
            assertTrue(s.phase in 0f..(2 * Math.PI.toFloat()))
        }
    }

    @Test
    fun aboutTenPercentHaveAHaloAndAboutTwelveOfNinetyTwinkle() {
        val stars = StarField.generate(90)
        assertEquals(13, stars.count { it.twinkle })
        assertEquals(7, stars.count { it.halo })
        val many = StarField.generate(2000)
        assertTrue(many.count { it.halo } in 150..250, "halos ${many.count { it.halo }}")
    }

    @Test
    fun densityFollowsTheArea() {
        assertEquals(90, StarField.countFor(400f, 600f))
        assertEquals(45, StarField.countFor(300f, 400f))
        assertEquals(StarField.MIN_COUNT, StarField.countFor(0f, 0f))
        assertEquals(StarField.MIN_COUNT, StarField.countFor(40f, 40f))
        assertEquals(StarField.MAX_COUNT, StarField.countFor(4000f, 4000f))
        assertTrue(StarField.countFor(800f, 480f) > StarField.countFor(360f, 640f) - 20)
    }

    @Test
    fun steadyStarsDoNotTwinkle() {
        val s = StarField.generate(10).first { !it.twinkle }
        for (t in listOf(0.0, 1.3, 7.7)) assertEquals(s.opacity, StarField.opacityAt(s, t))
    }

    @Test
    fun twinklingStarsOscillateBetweenFloorAndTheirOpacity() {
        val s = StarField.generate(10).first { it.twinkle }
        val values = (0..2000).map { StarField.opacityAt(s, it / 100.0) }
        assertTrue(values.all { it in (s.opacity * StarField.TWINKLE_FLOOR - 1e-4f)..(s.opacity + 1e-4f) })
        assertTrue(values.max() > s.opacity * 0.99f)
        assertTrue(values.min() < s.opacity * (StarField.TWINKLE_FLOOR + 0.02f))
    }

    @Test
    fun twinkleIsPeriodic() {
        val s = StarField.generate(10).first { it.twinkle }
        for (t in listOf(0.0, 0.7, 3.1)) {
            assertEquals(StarField.opacityAt(s, t), StarField.opacityAt(s, t + s.periodSeconds), 1e-4f)
        }
    }

    @Test
    fun differentStarsHaveDifferentPhases() {
        val twinkling = StarField.generate(90).filter { it.twinkle }
        assertEquals(twinkling.size, twinkling.map { it.phase }.toSet().size)
        assertFalse(twinkling.map { StarField.opacityAt(it, 0.0) }.toSet().size == 1)
    }
}

class StarFieldThemeTest {
    @Test
    fun densityMultipliesTheCountAndKeepsTheLimits() {
        assertEquals(StarField.countFor(400f, 600f), StarField.countFor(400f, 600f, 1f))
        assertEquals(45, StarField.countFor(400f, 600f, 0.5f))
        assertEquals(72, StarField.countFor(400f, 600f, 0.8f))
        assertEquals(StarField.MIN_COUNT, StarField.countFor(72f, 96f, 0.5f))
        assertEquals(StarField.MAX_COUNT, StarField.countFor(4000f, 4000f, 1f))
        assertEquals(0, StarField.countFor(400f, 600f, 0f))
    }

    @Test
    fun twinkleDepthBlendsBetweenStaticAndFullTwinkle() {
        val star = StarField.generate(40).first { it.twinkle }
        val seconds = 1.3
        val full = StarField.opacityAt(star, seconds)
        assertEquals(full, StarField.opacityAt(star, seconds, 1f), 1e-6f)
        assertEquals(star.opacity, StarField.opacityAt(star, seconds, 0f), 1e-6f)
        assertEquals(star.opacity + (full - star.opacity) * 0.6f, StarField.opacityAt(star, seconds, 0.6f), 1e-6f)
        val still = StarField.generate(40).first { !it.twinkle }
        assertEquals(still.opacity, StarField.opacityAt(still, seconds, 0.6f), 1e-6f)
    }
}
