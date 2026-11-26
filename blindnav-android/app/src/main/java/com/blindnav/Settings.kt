package com.blindnav

import android.content.Context
import com.blindnav.core.LatLon

data class Contact(val name: String, val number: String)

data class Place(val name: String, val pos: LatLon)

/** Everything stays on the phone. Cloud features default to OFF. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("blindnav", Context.MODE_PRIVATE)

    var language: String
        get() = p.getString("language", "en-IN")!!
        set(v) = p.edit().putString("language", v).apply()

    var speechRate: Float
        get() = p.getFloat("rate", 1.0f)
        set(v) = p.edit().putFloat("rate", v.coerceIn(0.5f, 2.0f)).apply()

    var cloudEnabled: Boolean
        get() = p.getBoolean("cloud", false)
        set(v) = p.edit().putBoolean("cloud", v).apply()

    var backendUrl: String
        get() = p.getString("backend", "")!!
        set(v) = p.edit().putString("backend", v.trim()).apply()

    var registerKey: String
        get() = p.getString("regkey", "")!!
        set(v) = p.edit().putString("regkey", v.trim()).apply()

    var token: String
        get() = p.getString("token", "")!!
        set(v) = p.edit().putString("token", v).apply()

    var contacts: List<Contact>
        get() = p.getString("contacts", "")!!.lines().filter { it.contains('|') }
            .map { it.split('|', limit = 2).let { (n, num) -> Contact(n, num) } }
        set(v) = p.edit().putString("contacts", v.joinToString("\n") { "${it.name.replace('|', ' ')}|${it.number.trim()}" }).apply()

    fun clearAll() = p.edit().clear().apply()

    fun langCode() = language.substringBefore('-')

    companion object {
        val LANGUAGES = listOf("en-IN" to "English", "hi-IN" to "Hindi", "kn-IN" to "Kannada", "te-IN" to "Telugu")
        fun nameOf(tag: String) = LANGUAGES.firstOrNull { it.first == tag }?.second ?: tag
    }
}
