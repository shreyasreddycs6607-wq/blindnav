package com.blindnav.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardEngineTest {
    private var now = 0L
    private val engine = HazardEngine(clock = { now })

    // box centred at cx with the given area fraction (square-ish in normalised units)
    private fun det(label: String, cx: Float, area: Float): Detection {
        val half = kotlin.math.sqrt(area) / 2
        return Detection(label, Box(cx - half, 0.5f - half, cx + half, 0.5f + half), 0.9f)
    }

    @Test fun largeObjectAheadIsUrgent() {
        val a = engine.update(listOf(det("person", 0.5f, 0.3f))).single()
        assertEquals(Level.URGENT, a.level)
        assertEquals("Stop. person ahead", a.text)
    }

    @Test fun noRepeatWithinCooldownThenRepeats() {
        engine.update(listOf(det("person", 0.5f, 0.3f)))
        now += 500
        assertTrue(engine.update(listOf(det("person", 0.5f, 0.3f))).isEmpty())
        now += 2000
        assertEquals(1, engine.update(listOf(det("person", 0.5f, 0.3f))).size)
    }

    @Test fun smallDistantObjectIgnored() {
        assertTrue(engine.update(listOf(det("person", 0.5f, 0.02f))).isEmpty())
    }

    @Test fun objectOutsideCorridorIgnored() {
        assertTrue(engine.update(listOf(det("car", 0.08f, 0.01f).copy(box = Box(0f, 0.3f, 0.2f, 0.7f)))).isEmpty())
    }

    @Test fun nonHazardLabelIgnored() {
        assertTrue(engine.update(listOf(det("kite", 0.5f, 0.4f))).isEmpty())
    }

    @Test fun approachingObjectEscalatesFromCautionToUrgent() {
        val first = engine.update(listOf(det("person", 0.5f, 0.10f))).single()
        assertEquals(Level.CAUTION, first.level)
        now += 100
        engine.update(listOf(det("person", 0.5f, 0.15f)))
        now += 100
        val last = engine.update(listOf(det("person", 0.5f, 0.45f))).single()
        assertEquals(Level.URGENT, last.level)
    }

    @Test fun trackLostThenReturnsAlertsAgain() {
        engine.update(listOf(det("person", 0.5f, 0.3f)))
        repeat(7) { now += 100; engine.update(emptyList()) }
        val a = engine.update(listOf(det("person", 0.5f, 0.3f)))
        assertEquals(1, a.size)
    }
}
