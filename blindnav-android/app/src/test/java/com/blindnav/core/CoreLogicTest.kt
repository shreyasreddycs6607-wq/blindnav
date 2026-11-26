package com.blindnav.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    // ~111 m per 0.001 deg latitude
    private fun p(dLatM: Double, dLonM: Double = 0.0) = LatLon(12.9716 + dLatM / 111_000.0, 77.5946 + dLonM / 108_000.0)

    private val route = Route(
        steps = listOf(Step("Turn left onto Main Road", p(100.0)), Step("Arrive at destination", p(200.0))),
        path = listOf(p(0.0), p(100.0), p(200.0)),
    )

    @Test fun geoDistanceAndBearing() {
        assertEquals(111.0, Geo.distanceM(p(0.0), p(111.0)), 2.0)
        assertEquals(0.0, Geo.bearing(p(0.0), p(100.0)), 1.0)
        assertEquals(10.0, Geo.distanceToPathM(p(50.0, 10.0), route.path), 1.0)
    }

    @Test fun announcesOnceThenAgainCloser() {
        val t = RouteTracker(route)
        assertNull(t.update(Fix(p(0.0), 5f)))                    // 100 m? exactly on threshold edge handled below
        val far = t.update(Fix(p(20.0), 5f))                     // 80 m from step -> 100 m threshold
        assertNotNull(far); assertTrue(far!!.startsWith("In 80 metres, turn left"))
        assertNull(t.update(Fix(p(25.0), 5f)))                   // same threshold, no repeat
        assertNotNull(t.update(Fix(p(75.0), 5f)))                // 25 m -> 30 m threshold
    }

    @Test fun weakGpsWarnsOnceNoDirections() {
        val t = RouteTracker(route)
        assertTrue(t.update(Fix(p(20.0), 80f))!!.contains("GPS signal is weak"))
        assertNull(t.update(Fix(p(21.0), 80f)))
    }

    @Test fun offRouteAfterThreeFixes() {
        val t = RouteTracker(route)
        assertNull(t.update(Fix(p(50.0, 80.0), 5f)))
        assertNull(t.update(Fix(p(50.0, 80.0), 5f)))
        assertTrue(t.update(Fix(p(50.0, 80.0), 5f))!!.contains("off the route"))
    }

    @Test fun arrivalDetected() {
        val t = RouteTracker(route)
        assertTrue(t.update(Fix(p(195.0), 5f))!!.contains("reached your destination"))
        assertTrue(t.arrived)
        assertNull(t.update(Fix(p(195.0), 5f)))
    }

    @Test fun commandsSafetyFirst() {
        assertEquals(Command.Sos, CommandParser.parse("SOS"))
        assertEquals(Command.Sos, CommandParser.parse("मदद"))
        assertEquals(Command.CancelSos, CommandParser.parse("cancel"))
        assertEquals(Command.CancelSos, CommandParser.parse("cancel sos"))
        assertEquals(Command.StopGuidance, CommandParser.parse("stop"))
        assertEquals(Command.Read, CommandParser.parse("read this"))
        assertEquals(Command.Currency, CommandParser.parse("what note is this"))
        assertEquals(Command.Navigate("city market"), CommandParser.parse("Take me to City Market"))
        assertEquals(Command.Describe, CommandParser.parse("what's ahead"))
        assertEquals(Command.Ask("is the shop open"), CommandParser.parse("is the shop open"))
        assertEquals(Command.Ask("is it possible to cross"), CommandParser.parse("is it possible to cross"))
    }

    @Test fun scriptPicksVoiceLanguage() {
        assertEquals("kn-IN", Script.languageTag("ಬೆಂಗಳೂರು ರಸ್ತೆ"))
        assertEquals("te-IN", Script.languageTag("హైదరాబాద్ రోడ్"))
        assertEquals("hi-IN", Script.languageTag("दवा की दुकान"))
        assertEquals("en-IN", Script.languageTag("Pharmacy"))
        assertEquals("en-IN", Script.languageTag("1234"))
    }

    @Test fun sosCountdownFiresAndCancels() {
        val m = SosMachine(5)
        assertTrue(m.trigger() is SosMachine.Out.Say)
        assertEquals(SosMachine.Out.None, m.tick())              // 4
        assertEquals(SosMachine.Out.Say("3"), m.tick())          // 3
        m.tick(); m.tick()                                       // 2,1
        assertEquals(SosMachine.Out.Fire, m.tick())              // 0
        assertFalse(m.active)

        val c = SosMachine(5)
        c.trigger(); c.tick()
        assertEquals(SosMachine.Out.Say("S O S cancelled."), c.cancel())
        assertEquals(SosMachine.Out.None, c.tick())
    }

    @Test fun triplePressWithinWindow() {
        val mp = MultiPress(3, 1500)
        assertFalse(mp.press(0)); assertFalse(mp.press(500)); assertTrue(mp.press(900))
        assertFalse(mp.press(5000)); assertFalse(mp.press(7000)); assertFalse(mp.press(9000))
    }
}
