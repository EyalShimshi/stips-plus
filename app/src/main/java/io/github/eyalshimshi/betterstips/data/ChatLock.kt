package io.github.eyalshimshi.betterstips.data

import io.github.eyalshimshi.betterstips.util.PrivacyRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether chats are open now (v0.25, D-176). With the chats lock on, they open with the fingerprint, face or screen
 * lock, and stay open until the app has been away for the minutes chosen. Kept in memory only: every new start of the
 * app finds them locked.
 */
class ChatLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile
    private var leftAt = 0L

    fun unlock() {
        leftAt = 0L
        _unlocked.value = true
    }

    fun lock() {
        leftAt = 0L
        _unlocked.value = false
    }

    /** The app left the screen at [now]. */
    fun left(now: Long) {
        if (_unlocked.value) leftAt = now
    }

    /** The app is back on screen at [now]: chats lock again when it was away [minutes] or longer. */
    fun cameBack(now: Long, minutes: Int) {
        if (_unlocked.value && PrivacyRules.lockAgain(leftAt, now, minutes)) _unlocked.value = false
        leftAt = 0L
    }
}
