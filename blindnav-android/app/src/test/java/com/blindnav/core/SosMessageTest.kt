package com.blindnav.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosMessageTest {
    private val here = LatLon(12.9716, 77.5946)

    @Test fun messageHasLocationAndLiveLink() {
        val m = SosMessage.build(here, "https://x.test/t/abc", 8)
        assertTrue(m.contains("https://maps.google.com/?q=12.971600,77.594600"))
        assertTrue(m.contains("Live location for the next 8 hours: https://x.test/t/abc"))
    }

    @Test fun messageWithoutFixOrLink() {
        val m = SosMessage.build(null, null, 8)
        assertTrue(m.contains("not available yet"))
        assertFalse(m.contains("Live location"))
    }

    @Test fun serverVariantOmitsMapLink() {
        assertFalse(SosMessage.build(here, null, 8, includeLocation = false).contains("maps.google.com"))
    }

    @Test fun whatsappUrlStripsPlusAndEncodes() {
        val u = Whatsapp.url("+91 98765-43210", "SOS! Help me & call")
        assertTrue(u.startsWith("https://wa.me/919876543210?text="))
        assertEquals("SOS%21%20Help%20me%20%26%20call", u.substringAfter("text="))
    }

    @Test fun stopSharingCommand() {
        assertEquals(Command.StopSharing, CommandParser.parse("stop sharing my location"))
        assertEquals(Command.StopGuidance, CommandParser.parse("stop"))
        assertEquals(Command.CancelSos, CommandParser.parse("stop sos"))
    }
}
