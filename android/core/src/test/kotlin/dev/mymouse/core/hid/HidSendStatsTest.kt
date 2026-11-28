package dev.mymouse.core.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HidSendStatsTest {
    @Test
    fun emptyIsIdle() {
        val s = HidSendStats().snapshot(5_000)
        assertEquals(0, s.reportsPerSecond)
        assertNull(s.avgIntervalMs)
        assertNull(s.avgSendMs)
    }

    @Test
    fun steadyEightMillisecondReports() {
        val stats = HidSendStats()
        for (i in 0 until 125) stats.record(10_000L + i * 8, sendMicros = 400)
        val s = stats.snapshot(10_000L + 124 * 8)
        assertEquals(125, s.reportsPerSecond)
        assertEquals(8.0, s.avgIntervalMs!!, 0.01)
        assertEquals(0.4, s.avgSendMs!!, 0.001)
    }

    @Test
    fun oldSamplesLeaveTheWindow() {
        val stats = HidSendStats()
        for (i in 0 until 50) stats.record(i * 10L, 100)
        // 2 s después solo cuenta lo reciente
        stats.record(2_500, 100)
        stats.record(2_510, 100)
        val s = stats.snapshot(2_520)
        assertEquals(2, s.reportsPerSecond)
        assertEquals(10.0, s.avgIntervalMs!!, 0.01)
        assertEquals(0, stats.snapshot(10_000).reportsPerSecond)
    }

    @Test
    fun singleReportHasNoInterval() {
        val stats = HidSendStats()
        stats.record(100, 250)
        assertEquals(1, stats.snapshot(150).reportsPerSecond)
        assertNull(stats.snapshot(150).avgIntervalMs)
    }

    @Test
    fun failuresAreCounted() {
        val stats = HidSendStats()
        stats.record(0, 100, ok = true)
        stats.record(10, 100, ok = false)
        val s = stats.snapshot(20)
        assertEquals(1, s.failures)
        assertEquals(10.0, s.avgIntervalMs!!, 0.01)
    }
}
