package io.github.eyalshimshi.betterstips.data

import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.api.RateGuard
import io.github.eyalshimshi.betterstips.util.TagListRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Preloading your feeds (v0.24, D-168): while the app is on screen, the lists your feeds read are brought up to date in
 * the background, each as often as its tag's pace says (TagListRules.due), one request at a time and only with request
 * budget to spare ([RateGuard.allowWarmup]), so a feed opens at once. Soon after the app comes up, every few minutes,
 * and when your feeds change. Lists Stips failed on wait their turn (TagListRules.unavailable).
 */
class FeedWarmer(
    private val feeds: TagFeedStore,
    private val lists: TagLists,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** The app is on screen. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(START_DELAY_MS)
            while (true) {
                warm()
                // The next round: in a few minutes, or at once when a feed is added or changed.
                withTimeoutOrNull(PERIOD_MS) { feeds.feeds.drop(1).first() }
            }
        }
    }

    /** The app left the screen. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun warm() {
        for ((tag, kind) in TagListRules.warmKeys(feeds.feeds.value)) {
            val key = TagLists.Key(tag, kind)
            // Reading what's kept also brings it into memory, so the feed shows without waiting for the storage.
            if (!TagListRules.due(lists.known(key), kind, System.currentTimeMillis())) continue
            while (!RateGuard.allowWarmup()) delay(BUDGET_WAIT_MS)
            try {
                lists.read(key, 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Stips paused the phone: this round ends, the next one tries again.
                return
            }
            delay(GAP_MS)
        }
    }

    private companion object {
        /** After the app comes up, Home's own requests go first. */
        const val START_DELAY_MS = 6_000L
        const val PERIOD_MS = 5 * 60_000L
        const val GAP_MS = 1_500L
        const val BUDGET_WAIT_MS = 5_000L
    }
}
