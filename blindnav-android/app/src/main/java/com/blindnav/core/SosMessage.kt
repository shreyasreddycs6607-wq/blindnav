package com.blindnav.core

import java.net.URLEncoder
import java.util.Locale

object SosMessage {
    /** The text a contact receives. includeLocation=false when the server adds the map link itself (SMS). */
    fun build(at: LatLon?, liveUrl: String?, hours: Int, includeLocation: Boolean = true): String = buildString {
        append("SOS! I need help. Sent from BlindNav.")
        if (includeLocation) {
            if (at != null) append(" My location: https://maps.google.com/?q=${"%.6f".format(Locale.US, at.lat)},${"%.6f".format(Locale.US, at.lon)}")
            else append(" My location is not available yet.")
        }
        if (liveUrl != null) append(" Live location for the next $hours hours: $liveUrl")
    }
}

object Whatsapp {
    /** wa.me wants digits only (country code included, no plus). The message is URL-encoded. */
    fun url(number: String, text: String): String =
        "https://wa.me/${number.filter { it.isDigit() }}?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
