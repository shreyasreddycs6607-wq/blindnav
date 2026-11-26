package com.blindnav

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.blindnav.core.LatLon
import com.blindnav.core.Route
import com.blindnav.core.Step
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class BackendException(message: String) : Exception(message)

/** Runs blocking work off the main thread and delivers the result back on it. */
object Bg {
    private val ex = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        ex.execute { val r = runCatching(work); main.post { done(r) } }
    }
}

/** Advisory cloud features only. Obstacle warnings never depend on this. */
class Backend(private val s: Settings) {
    private fun base(): String =
        s.backendUrl.trimEnd('/').ifEmpty { throw BackendException("The server address is not set. Add it in Settings.") }

    private fun http(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, retry: Boolean = true): JSONObject {
        try {
            val c = (URL(base() + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
                if (auth) setRequestProperty("Authorization", "Bearer " + token())
                if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code == 401 && auth && retry) { s.token = ""; return http(method, path, body, auth, false) }
            if (code >= 400) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("Server error $code.")
                throw BackendException(msg)
            }
            return JSONObject(text)
        } catch (e: IOException) {
            throw BackendException("Cannot reach the server. Check the internet and the server address.")
        }
    }

    private fun token(): String {
        if (s.token.isEmpty()) {
            val c = (URL(base() + "/v1/auth/register").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (s.registerKey.isNotEmpty()) setRequestProperty("X-Register-Key", s.registerKey)
                outputStream.use { it.write("{}".toByteArray()) }
            }
            val text = (if (c.responseCode < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (c.responseCode >= 400) throw BackendException("Could not register with the server. Check the server key in Settings.")
            s.token = JSONObject(text).getString("token")
        }
        return s.token
    }

    private fun jpeg64(bmp: Bitmap): String {
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun code(tag: String) = tag.substringBefore('-')

    fun scene(bmp: Bitmap, lang: String): String =
        http("POST", "/v1/vision/scene", JSONObject().put("image", jpeg64(bmp)).put("language", code(lang))).getString("description")

    fun ask(bmp: Bitmap, question: String, lang: String, history: List<Pair<String, String>> = emptyList()): String {
        val h = JSONArray(history.takeLast(6).map { JSONObject().put("role", it.first).put("content", it.second) })
        return http("POST", "/v1/vision/ask", JSONObject().put("image", jpeg64(bmp)).put("question", question)
            .put("language", code(lang)).put("history", h)).getString("answer")
    }

    fun ocr(bmp: Bitmap, lang: String): String =
        http("POST", "/v1/ocr/read", JSONObject().put("image", jpeg64(bmp)).put("language", code(lang))).getString("text")

    fun search(q: String, near: LatLon?): List<Place> {
        val extra = if (near != null) "&lat=${near.lat}&lon=${near.lon}" else ""
        val arr = http("GET", "/v1/nav/search?q=" + URLEncoder.encode(q, "UTF-8") + extra).getJSONArray("results")
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Place(o.getString("name"), LatLon(o.getDouble("lat"), o.getDouble("lon"))) } }
    }

    /** Returns the route plus its length in metres. */
    fun route(from: LatLon, to: LatLon): Pair<Route, Int> {
        val j = http("POST", "/v1/nav/route", JSONObject()
            .put("from", JSONObject().put("lat", from.lat).put("lon", from.lon))
            .put("to", JSONObject().put("lat", to.lat).put("lon", to.lon)))
        val steps = j.getJSONArray("steps").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { Step(it.getString("instruction"), LatLon(it.getDouble("lat"), it.getDouble("lon"))) }
        val path = j.getJSONArray("path").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> LatLon(p.getDouble(0), p.getDouble(1)) } } }
        return Route(steps, path) to j.getInt("distance_m")
    }

    /** Returns the numbers that were sent. */
    fun notifySos(numbers: List<String>, at: LatLon?, message: String): List<String> {
        val body = JSONObject().put("contacts", JSONArray(numbers)).put("message", message)
        if (at != null) body.put("location", JSONObject().put("lat", at.lat).put("lon", at.lon))
        val sent = http("POST", "/v1/emergency/notify", body).getJSONArray("sent")
        return (0 until sent.length()).map { sent.getString(it) }
    }
}
