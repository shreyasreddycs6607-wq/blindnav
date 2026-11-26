package com.blindnav.core

/** Picks the TTS language for text read from a page, from the dominant Unicode script. */
object Script {
    fun languageTag(text: String): String {
        var dev = 0; var kan = 0; var tel = 0; var lat = 0
        for (c in text) when (c.code) {
            in 0x0900..0x097F -> dev++
            in 0x0C80..0x0CFF -> kan++
            in 0x0C00..0x0C7F -> tel++
            in 0x41..0x5A, in 0x61..0x7A -> lat++
        }
        val max = maxOf(dev, kan, tel, lat)
        if (max == 0) return "en-IN"
        return when (max) {
            kan -> "kn-IN"
            tel -> "te-IN"
            dev -> "hi-IN"
            else -> "en-IN"
        }
    }
}
