package io.github.eyalshimshi.betterstips.util

/**
 * The privacy screen and the chats lock (v0.25). Pure: the activity applies [window], and the chats lock asks
 * [lockAgain] when the app comes back.
 */
object PrivacyRules {
    /**
     * What the app's window does now. [secure] is Android's secure flag: no screenshots or screen recordings, and a
     * blank card in recent apps. [recentsPreview] false hides only that card, and only from Android 13.
     */
    data class Window(val secure: Boolean, val recentsPreview: Boolean)

    /** Android 13: the first that hides the card in recent apps without blocking screenshots. */
    const val SEPARATE_RECENTS_SDK = 33

    /**
     * For Android [sdk]: [hideRecents] and [blockScreenshots] are the two switches; [onLockedChats], a chat screen while
     * the chats lock is on, which keeps chats out of recent apps from 13 (before 13 that would block screenshots too).
     */
    fun window(sdk: Int, hideRecents: Boolean, blockScreenshots: Boolean, onLockedChats: Boolean): Window {
        val separate = sdk >= SEPARATE_RECENTS_SDK
        return Window(
            secure = blockScreenshots || (hideRecents && !separate),
            recentsPreview = !(separate && (hideRecents || onLockedChats)),
        )
    }

    /** How long chats stay unlocked after leaving the app, in minutes: 0 is "at once". */
    val LOCK_MINUTES = listOf(0, 1, 5, 30)

    const val DEFAULT_LOCK_MINUTES = 1

    /**
     * Whether chats lock again as the app comes back at [now], having been left at [leftAt] (epoch ms; 0: not left
     * since they were unlocked). A clock that went back locks too.
     */
    fun lockAgain(leftAt: Long, now: Long, minutes: Int): Boolean {
        if (leftAt <= 0L) return false
        if (now < leftAt) return true
        return now - leftAt >= minutes.coerceAtLeast(0) * 60_000L
    }
}
