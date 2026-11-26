package com.blindnav.core

/**
 * SOS with a cancel window so a pocket press doesn't alert contacts. Drive with tick() once per second.
 * Pure logic: the caller speaks the returned text and, on Fire, sends the alert.
 */
class SosMachine(private val countdownSeconds: Int = 5) {
    sealed interface Out {
        data class Say(val text: String) : Out
        data object Fire : Out
        data object None : Out
    }

    private var remaining = 0
    val active get() = remaining > 0

    fun trigger(): Out {
        if (active) return Out.None
        remaining = countdownSeconds
        return Out.Say("S O S in $remaining seconds. Say cancel, or press any volume key, to stop.")
    }

    fun cancel(): Out {
        if (!active) return Out.None
        remaining = 0
        return Out.Say("S O S cancelled.")
    }

    fun tick(): Out {
        if (!active) return Out.None
        remaining--
        return when {
            remaining == 0 -> Out.Fire
            remaining <= 3 -> Out.Say(remaining.toString())
            else -> Out.None
        }
    }
}

/** Detects N presses of a key within a window (used for triple volume-down = SOS). */
class MultiPress(private val needed: Int = 3, private val windowMs: Long = 1500) {
    private val times = ArrayDeque<Long>()

    fun press(nowMs: Long): Boolean {
        times.addLast(nowMs)
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
        if (times.size >= needed) { times.clear(); return true }
        return false
    }
}
