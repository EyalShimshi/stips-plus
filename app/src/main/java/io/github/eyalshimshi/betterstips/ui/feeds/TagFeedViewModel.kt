package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eyalshimshi.betterstips.data.StipsRepository
import io.github.eyalshimshi.betterstips.data.TagLists
import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.components.asApiException
import io.github.eyalshimshi.betterstips.ui.feed.FeedUiState
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import io.github.eyalshimshi.betterstips.util.TagListRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One of your feeds (#40) from the feeds' cache (v0.24, D-167): what the phone kept shows at once, and only the lists
 * due for it are read again, in the background. A tag that's slow, or that Stips fails on, is never waited for. What's
 * on screen changes only when that doesn't move what's being read: opening the feed, its top, pulling it down, and the
 * next page (which keeps the rest still). New and Mixed read the same lists, so switching between them asks nothing.
 */
class TagFeedViewModel(
    private val lists: TagLists,
    private val repository: StipsRepository,
    initial: TagFeed,
) : ViewModel() {
    private var feed = initial

    private val _state = MutableStateFlow(FeedUiState(loading = true))
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** While a feed with nothing kept is first read: how many tags answered, of how many. */
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _unreadable = MutableStateFlow<List<String>>(emptyList())

    /** Tags Stips failed on lately with nothing kept to show: the feed says so. */
    val unreadable: StateFlow<List<String>> = _unreadable.asStateFlow()

    /** Each tag's list as the screen shows it. */
    private var shown: Map<String, TagListRules.Entry> = emptyMap()

    /** The tags laid out, each with its newest question then (TagFeedRules.Held). */
    private var base: Map<String, Long> = emptyMap()

    /** Set while the list is being read past its layout: what's on screen keeps still. */
    private var held: TagFeedRules.Held? = null

    private var job: Job? = null
    private var settle: Job? = null
    private var started = false
    private var atTop = true

    /** The cache has newer lists than the screen: they come in at the top. */
    private var waiting = false
    private var generation = 0

    init {
        viewModelScope.launch {
            lists.changed.collect { key ->
                if (key.kind == kind() && feed.tags.any { TagListRules.sameTag(it, key.tag) }) onListChanged()
            }
        }
    }

    private fun kind(): TagListRules.Kind = TagListRules.kindOf(feed.order, feed.window)

    /** What the cache keeps for the feed's tags, by tag. */
    private suspend fun kept(): Map<String, TagListRules.Entry> {
        val kind = kind()
        return lists.known(feed.tags.map { TagLists.Key(it, kind) }).mapKeys { it.key.tag }
    }

    /** The feed was edited, or its order changed: the other list comes from what's kept; only new tags are asked. */
    fun update(next: TagFeed) {
        val changed = next.listKey != feed.listKey
        feed = next
        if (changed && started) open()
    }

    /** Shows the feed unless it already did (or is). */
    fun ensureLoaded() {
        if (!started) open()
    }

    /** The feed came on screen again (the tab, the app, its page): what came meanwhile, and the lists due. */
    fun onEnter() {
        if (!started) {
            open()
            return
        }
        if (atTop && job?.isActive != true) adoptLatest()
        refreshDue()
    }

    /** Pulling down, or tapping the tab again: every list's top read again, except those Stips fails on. */
    fun refresh() {
        job?.cancel()
        started = true
        val gen = ++generation
        _state.update { it.copy(refreshing = true, loadingMore = false, error = null) }
        job = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val before = kept()
                val tags = feed.tags.filter { !TagListRules.unavailable(before[it], now) }
                val tops = readTops(tags, PULL_WAIT_MS, gen, showProgress = false, needOne = false)
                if (tops.read == 0 && tops.errors.isNotEmpty() && _state.value.items.isNotEmpty()) {
                    // Not one list could be read: what's on screen stays, with the error.
                    _state.update { it.copy(refreshing = false, error = tops.errors.last()) }
                    return@launch
                }
                layOut(kept(), error = if (tops.read == 0) tops.errors.lastOrNull() else null, swapped = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) open(force = true) else loadMore(force = true)
    }

    /** Whether the list's top is on screen: the cache's newer lists come in only then. */
    fun setAtTop(value: Boolean) {
        atTop = value
        if (value && waiting && job?.isActive != true) adoptLatest()
    }

    /**
     * Lays the feed out from what's kept, at once. Tags never read are asked first, waiting a moment for them (with
     * nothing to show, for the first that answers); slower ones come in later. Then the lists due, in the background.
     */
    private fun open(force: Boolean = false) {
        job?.cancel()
        started = true
        val gen = ++generation
        job = viewModelScope.launch {
            try {
                var kept = kept()
                val now = System.currentTimeMillis()
                val missing = feed.tags.filter { tag ->
                    kept[tag]?.read != true && (force || !TagListRules.unavailable(kept[tag], now))
                }
                var errors = emptyList<ApiException>()
                if (missing.isNotEmpty()) {
                    val anything = kept.values.any { it.items.isNotEmpty() }
                    if (!anything) _state.update { it.copy(loading = true, error = null, endReached = false, items = emptyList()) }
                    val tops = readTops(missing, if (anything) SOFT_WAIT_MS else FIRST_WAIT_MS, gen, showProgress = !anything, needOne = !anything)
                    errors = tops.errors
                    kept = kept()
                }
                // Nothing to show: why (a read's error, or tags Stips fails on), with Retry, rather than "no questions".
                val nothing = kept.values.none { it.read }
                val error = when {
                    !nothing -> null
                    errors.isNotEmpty() -> errors.last()
                    feed.tags.any { TagListRules.unavailable(kept[it], now) } -> ApiException(ApiException.SERVER)
                    else -> null
                }
                val merge = layOut(kept, error = error, swapped = true)
                // Filters (left-out tags, unanswered only) can leave little: a few pages more at once.
                if (merge.items.size < TagFeedRules.MIN_NEW_ITEMS && merge.more.isNotEmpty() && kept.values.any { it.read }) {
                    _state.update { it.copy(loadingMore = true) }
                    val failure = more(merge.items.size)
                    publish(error = failure)
                }
                refreshDue(skip = missing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    fun loadMore(force: Boolean = false) {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || s.endReached) return
        if (s.error != null && !force) return
        if (job?.isActive == true) return
        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            try {
                publish(error = more(s.items.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    /**
     * Reads on below what's shown: kept pages come first; the tags holding the list back are asked for their next page,
     * a few rounds when filters leave little. A page that couldn't be read stops it there (going on would skip its
     * questions), and when nothing new came, says why.
     */
    private suspend fun more(before: Int): ApiException? {
        val kind = kind()
        var kept = kept()
        adoptHeld(kept)
        var merge = merged()
        var rounds = 0
        var failure: ApiException? = null
        while (merge.more.isNotEmpty() && merge.items.size - before < TagFeedRules.MIN_NEW_ITEMS && rounds < TagFeedRules.MAX_ROUNDS) {
            val outcomes = coroutineScope {
                merge.more.map { tag ->
                    async { lists.read(TagLists.Key(tag, kind), TagListRules.nextPage(kept[tag], kind)) }
                }.awaitAll()
            }
            failure = outcomes.firstNotNullOfOrNull { it.error }
            kept = kept()
            adoptHeld(kept)
            merge = merged()
            rounds++
            if (failure != null || merge.ended) break
        }
        return if (failure != null && merge.items.size <= before) failure else null
    }

    /** What reading the tops brought: how many tags answered, and why the others didn't. */
    private class Tops(val read: Int, val errors: List<ApiException>)

    /**
     * Reads the tops of [tags], waiting up to [waitMs] for them all; with [needOne], also for the first that answers.
     * Reads that take longer go on, into the cache, and come in later.
     */
    private suspend fun readTops(tags: List<String>, waitMs: Long, gen: Int, showProgress: Boolean, needOne: Boolean): Tops {
        if (tags.isEmpty()) return Tops(0, emptyList())
        val kind = kind()
        val errors = ArrayList<ApiException>()
        var read = 0
        var done = 0
        val first = CompletableDeferred<Unit>()
        val all = CompletableDeferred<Unit>()
        // The count shows only while this waits; reads that finish later don't bring it back.
        var waitingHere = true
        if (showProgress) _progress.value = 0 to tags.size
        tags.forEach { tag ->
            // Not this load's: a read it stops waiting for still lands in the cache.
            viewModelScope.launch {
                val error = try {
                    lists.read(TagLists.Key(tag, kind), 1).error
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.asApiException()
                }
                done++
                if (error == null) read++ else errors += error
                if (showProgress && waitingHere && gen == generation) _progress.value = done to tags.size
                if (error == null || done == tags.size) first.complete(Unit)
                if (done == tags.size) all.complete(Unit)
            }
        }
        try {
            withTimeoutOrNull(waitMs) { all.await() }
            if (needOne && read == 0) first.await()
        } finally {
            waitingHere = false
            if (gen == generation) _progress.value = null
        }
        return Tops(read, errors.toList())
    }

    /** Reads the tops of the lists due (by their tag's pace) in the background; they come in at the list's top. */
    private fun refreshDue(skip: Collection<String> = emptyList()) {
        viewModelScope.launch {
            val kind = kind()
            val kept = kept()
            val now = System.currentTimeMillis()
            for (tag in feed.tags) {
                if (tag in skip || !TagListRules.due(kept[tag], kind, now)) continue
                launch {
                    try {
                        lists.read(TagLists.Key(tag, kind), 1)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Stips paused the phone: the list comes up to date another time.
                    }
                }
            }
        }
    }

    /** The cache read something for this feed: in at once at the list's top, otherwise when it's back there. */
    private fun onListChanged() {
        if (!started) return
        if (!atTop || job?.isActive == true) {
            waiting = true
            return
        }
        if (settle?.isActive == true) return
        // A few lists usually come in together: one change for them all.
        settle = viewModelScope.launch {
            delay(SETTLE_MS)
            if (atTop && job?.isActive != true) adoptLatest() else waiting = true
        }
    }

    private fun adoptLatest() {
        val gen = generation
        viewModelScope.launch {
            val kept = kept()
            if (gen == generation && job?.isActive != true) layOut(kept) else waiting = true
        }
    }

    /** The list laid out anew from [kept]: every tag from its newest. */
    private fun layOut(kept: Map<String, TagListRules.Entry>, error: ApiException? = null, swapped: Boolean = false): TagFeedRules.Merge {
        shown = kept.filterValues { it.read }
        base = shown.mapValues { (_, entry) -> entry.items.maxOfOrNull { it.id } ?: Long.MIN_VALUE }
        held = null
        waiting = false
        val now = System.currentTimeMillis()
        _unreadable.value = feed.tags.filter { tag ->
            val entry = kept[tag]
            entry?.read != true && TagListRules.unavailable(entry, now)
        }
        return publish(error, swapped)
    }

    /** The cache's lists taken in below what's shown, which keeps still (TagFeedRules.Held). */
    private fun adoptHeld(kept: Map<String, TagListRules.Entry>) {
        val kind = kind()
        shown = feed.tags.mapNotNull { tag ->
            val entry = kept[tag]?.takeIf { it.read }?.let { TagListRules.keepShowing(shown[tag], it, kind) } ?: shown[tag]
            entry?.let { tag to it }
        }.toMap()
        held = TagFeedRules.Held(floor = _state.value.items.minOfOrNull { it.id }, base = base)
    }

    private fun merged(): TagFeedRules.Merge {
        val streams = feed.tags.map { tag ->
            val entry = shown[tag]
            if (entry == null) TagFeedRules.Stream(tag, ended = true) else TagFeedRules.Stream(tag, entry.pages, entry.ended)
        }
        return TagFeedRules.merge(feed, streams, held)
    }

    private fun publish(error: ApiException? = null, swapped: Boolean = false): TagFeedRules.Merge {
        val merge = merged()
        // The first rows open at once when tapped, from what's kept.
        repository.keepShown(merge.items.take(KEEP_SHOWN))
        _state.update {
            it.copy(
                items = merge.items,
                loading = false,
                refreshing = false,
                loadingMore = false,
                endReached = merge.ended && error == null,
                error = error,
                swapped = swapped,
            )
        }
        return merge
    }

    private fun failed(e: Exception) {
        _progress.value = null
        _state.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = e.asApiException()) }
    }

    private companion object {
        /** With nothing kept, how long a new feed waits for all its tags before showing those that answered. */
        const val FIRST_WAIT_MS = 1_200L

        /** Tags added to a feed that shows already: a moment, so they're in its first layout. */
        const val SOFT_WAIT_MS = 800L

        /** Pulling down: the spinner waits this long at most; slower lists come in later. */
        const val PULL_WAIT_MS = 3_000L
        const val SETTLE_MS = 400L
        const val KEEP_SHOWN = 60
    }
}
