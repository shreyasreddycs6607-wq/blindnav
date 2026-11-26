package com.blindnav.core

data class Step(val instruction: String, val at: LatLon)

data class Route(val steps: List<Step>, val path: List<LatLon>)

/** A GPS fix. accuracyM is the phone's own 68% radius estimate. */
data class Fix(val pos: LatLon, val accuracyM: Float)

/**
 * Turns GPS fixes into spoken guidance. GPS is typically 3-10 m in the open and far worse between buildings,
 * so this never says "you are at the door"; it gives distances and flags weak signal.
 * Visual obstacle avoidance is separate and never merged into these messages.
 */
class RouteTracker(
    private val route: Route,
    private val offRouteM: Double = 30.0,
    private val offRouteFixes: Int = 3,
    private val weakAccuracyM: Float = 40f,
    private val arriveM: Double = 15.0,
) {
    private var idx = 0
    private var offCount = 0
    private var weakAnnounced = false
    private var announced = mutableSetOf<Pair<Int, Int>>() // (step, threshold) already spoken
    var arrived = false
        private set

    /** Returns text to speak, or null. Call once per fix. */
    fun update(fix: Fix): String? {
        if (arrived || route.steps.isEmpty()) return null

        if (fix.accuracyM > weakAccuracyM) {
            if (weakAnnounced) return null
            weakAnnounced = true
            return "GPS signal is weak. Directions may be wrong. Use caution."
        }
        weakAnnounced = false

        val last = route.steps.last().at
        if (Geo.distanceM(fix.pos, last) <= arriveM) {
            arrived = true
            return "You have reached your destination, within about ${arriveM.toInt()} metres."
        }

        if (Geo.distanceToPathM(fix.pos, route.path) > offRouteM) {
            offCount++
            if (offCount == offRouteFixes) return "You may be off the route. Stop and wait for new directions."
            return null
        }
        offCount = 0

        // advance past steps whose point we've passed (within 10 m)
        while (idx < route.steps.size - 1 && Geo.distanceM(fix.pos, route.steps[idx].at) < 10) idx++

        val step = route.steps[idx]
        val d = Geo.distanceM(fix.pos, step.at)
        // speak the tightest applicable threshold once per step
        val tightest = THRESHOLDS.filter { d <= it }.minOrNull() ?: return null
        if (!announced.add(idx to tightest)) return null
        return "In ${roundM(d)} metres, ${step.instruction.replaceFirstChar { it.lowercase() }}"
    }

    private fun roundM(d: Double) = if (d >= 50) (d / 10).toInt() * 10 else (d / 5).toInt().coerceAtLeast(1) * 5

    companion object {
        val THRESHOLDS = listOf(100, 30)
    }
}
