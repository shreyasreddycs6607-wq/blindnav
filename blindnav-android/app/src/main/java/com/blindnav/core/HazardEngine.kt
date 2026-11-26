package com.blindnav.core

import kotlin.math.max
import kotlin.math.min

/** Normalised box, 0..1 in the upright frame. */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val cx get() = (l + r) / 2
    val area get() = max(0f, r - l) * max(0f, b - t)

    fun iou(o: Box): Float {
        val w = min(r, o.r) - max(l, o.l)
        val h = min(b, o.b) - max(t, o.t)
        if (w <= 0 || h <= 0) return 0f
        val inter = w * h
        return inter / (area + o.area - inter)
    }
}

data class Detection(val label: String, val box: Box, val score: Float)

enum class Level { CAUTION, URGENT }

data class Alert(val level: Level, val text: String, val trackId: Int)

/**
 * All thresholds are uncalibrated guesses. "Proximity" is box area as a fraction of the frame:
 * a relative cue, NOT a distance in metres. Tune per phone / carry position (README).
 * ponytail: area-based proximity, upgrade to depth-model cue if field tests show it is too crude.
 */
data class HazardConfig(
    val corridorL: Float = 0.30f,
    val corridorR: Float = 0.70f,
    val urgentArea: Float = 0.25f,
    val cautionArea: Float = 0.08f,
    val approachGrowth: Float = 1.3f,
    val urgentCooldownMs: Long = 2000,
    val cautionCooldownMs: Long = 5000,
    val maxMissedFrames: Int = 5,
    val matchIou: Float = 0.3f,
    val hazardLabels: Set<String> = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "train", "dog", "cat", "horse", "cow",
        "bench", "chair", "couch", "dining table", "potted plant", "bed", "toilet", "fire hydrant",
        "stop sign", "parking meter", "suitcase", "backpack", "umbrella", "refrigerator", "tv",
    ),
)

private class Track(val id: Int, val label: String, var box: Box, var lastMs: Long) {
    var missed = 0
    var level: Level? = null
    var lastAlertMs = Long.MIN_VALUE
    val areas = ArrayDeque<Float>() // smoothed area history
}

/** Deterministic, no-ML-in-the-loop rules: detections in, at most one alert per frame out. */
class HazardEngine(
    private val cfg: HazardConfig = HazardConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    fun update(detections: List<Detection>): List<Alert> {
        val now = clock()
        val dets = detections.filter { it.label in cfg.hazardLabels }
        val unmatched = dets.toMutableList()

        for (t in tracks) {
            val best = unmatched.filter { it.label == t.label }.maxByOrNull { it.box.iou(t.box) }
            if (best != null && best.box.iou(t.box) >= cfg.matchIou) {
                unmatched.remove(best)
                t.box = best.box
                t.missed = 0
                val prev = t.areas.lastOrNull() ?: best.box.area
                t.areas.addLast(0.5f * prev + 0.5f * best.box.area)
                if (t.areas.size > 6) t.areas.removeFirst()
            } else {
                t.missed++
            }
        }
        tracks.removeAll { it.missed > cfg.maxMissedFrames }
        for (d in unmatched) {
            tracks += Track(nextId++, d.label, d.box, now).also { it.areas.addLast(d.box.area) }
        }

        val alerts = tracks.filter { it.missed == 0 }.mapNotNull { evaluate(it, now) }
        return alerts.sortedByDescending { it.level }.take(1)
    }

    private fun evaluate(t: Track, now: Long): Alert? {
        val inCorridor = t.box.r > cfg.corridorL && t.box.l < cfg.corridorR
        if (!inCorridor) { t.level = null; return null }

        val area = t.areas.last()
        val approaching = t.areas.size >= 3 && area > t.areas.first() * cfg.approachGrowth
        val level = when {
            area >= cfg.urgentArea -> Level.URGENT
            area >= cfg.cautionArea || (approaching && area >= cfg.cautionArea / 2) -> Level.CAUTION
            else -> null
        }
        if (level == null) { t.level = null; return null }

        val cooldown = if (level == Level.URGENT) cfg.urgentCooldownMs else cfg.cautionCooldownMs
        val escalated = t.level == null || level > t.level!!
        if (!escalated && now - t.lastAlertMs < cooldown) return null

        t.level = level
        t.lastAlertMs = now
        val side = when {
            t.box.cx < 0.4f -> "left"
            t.box.cx > 0.6f -> "right"
            else -> "ahead"
        }
        val text = if (level == Level.URGENT) "Stop. ${t.label} $side" else "${t.label} $side"
        return Alert(level, text, t.id)
    }
}
