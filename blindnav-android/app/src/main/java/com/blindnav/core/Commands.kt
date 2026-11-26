package com.blindnav.core

sealed interface Command {
    data object StartGuidance : Command
    data object StopGuidance : Command
    data object Read : Command
    data object Currency : Command
    data object Describe : Command
    data object Sos : Command
    data object CancelSos : Command
    data object Repeat : Command
    data class Navigate(val destination: String) : Command
    data class Ask(val question: String) : Command
}

/**
 * Maps recognised speech to a command. Fixed phrases only for the safety-relevant ones (stop, SOS, cancel),
 * matched in English, Hindi, Kannada and Telugu (romanised and native script). Anything else is a free question.
 * Latin keywords match whole words only, so "possible" can never trigger SOS.
 * ponytail: keyword lists, not NLU. Extend per language after testing with real speakers.
 */
object CommandParser {
    private val sos = listOf("sos", "emergency", "help me", "call for help", "मदद", "बचाओ", "सहायता", "ಸಹಾಯ", "సహాయం", "madad", "bachao", "sahaya")
    private val cancel = listOf("cancel", "stop sos", "i am fine", "i'm fine", "रद्द", "ರದ್ದು", "రద్దు", "radd")
    private val stop = listOf("stop", "pause", "रुको", "बंद", "ನಿಲ್ಲಿಸು", "ఆపు", "ruko", "nillisu", "aapu")
    private val start = listOf("start", "begin guidance", "guide me", "शुरू", "ಪ್ರಾರಂಭ", "ప్రారంభం", "shuru")
    private val read = listOf("read", "what does it say", "पढ़ो", "पढ़", "ಓದು", "చదువు", "padho", "odu", "chaduvu")
    private val currency = listOf("currency", "note", "money", "rupee", "rupees", "नोट", "पैसे", "ನೋಟು", "నోటు", "paise")
    private val describe = listOf("what's ahead", "what is ahead", "what's in front", "what is in front", "describe", "around me",
        "सामने क्या", "वर्णन", "ಮುಂದೆ ಏನು", "ముందు ఏముంది", "samne kya")
    private val repeat = listOf("repeat", "say again", "फिर से", "ಮತ್ತೆ", "మళ్ళీ")
    private val navigate = listOf("navigate to ", "take me to ", "go to ", "directions to ", "walk to ")

    private fun has(t: String, k: String): Boolean =
        if (k.all { it.code < 128 }) Regex("(?<![a-z])" + Regex.escape(k) + "(?![a-z])").containsMatchIn(t) else t.contains(k)

    private fun any(t: String, keys: List<String>) = keys.any { has(t, it) }

    fun parse(raw: String): Command {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return Command.Repeat
        // safety first; "cancel" / "cancel sos" must beat both "stop" and "sos"
        if (any(t, cancel)) return Command.CancelSos
        if (any(t, sos)) return Command.Sos
        navigate.firstOrNull { t.startsWith(it) }?.let { return Command.Navigate(t.removePrefix(it).trim()) }
        if (any(t, stop)) return Command.StopGuidance
        if (any(t, start)) return Command.StartGuidance
        if (any(t, currency)) return Command.Currency
        if (any(t, read)) return Command.Read
        if (any(t, describe)) return Command.Describe
        if (any(t, repeat)) return Command.Repeat
        return Command.Ask(raw.trim())
    }
}
