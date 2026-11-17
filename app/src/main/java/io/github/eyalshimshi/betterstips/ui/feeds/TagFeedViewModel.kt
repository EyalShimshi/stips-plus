package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eyalshimshi.betterstips.data.StipsRepository
import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.FeedQuery
import io.github.eyalshimshi.betterstips.data.model.FeedSort
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.components.asApiException
import io.github.eyalshimshi.betterstips.ui.feed.FeedUiState
import io.github.eyalshimshi.betterstips.util.AutoRefresh
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One of your feeds (#40): each of its tags asked from Stips on its own (`ask.new` or `ask.hot` with `topic`, as the
 * site's tag pages), only once the feed is on screen ([ensureLoaded]), the next pages as it's scrolled, all made into
 * one list by [TagFeedRules.merge].
 */
class TagFeedViewModel(private val repository: StipsRepository, initial: TagFeed) : ViewModel() {
    private var feed = initial

    private val _state = MutableStateFlow(FeedUiState(loading = true))
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** While a feed's tags are first asked: how many answered, of how many. */
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private var streams: List<TagFeedRules.Stream> = emptyList()
    private var job: Job? = null
    private var started = false
    private var pollNext = 0
    private var pageOneAt = 0L

    /** A tag's next page read into its stream, or why it couldn't be (the stream as it was, then). */
    private class Read(val stream: TagFeedRules.Stream, val error: ApiException?)

    /** The feed was edited: a different list starts over, a new name or emoji just shows. */
    fun update(next: TagFeed) {
        val changed = next.listKey != feed.listKey
        feed = next
        if (changed && started) load(reset = true, refreshing = false)
    }

    /** Loads the feed unless it already did (or is). */
    fun ensureLoaded() {
        if (!started) load(reset = true, refreshing = false)
    }

    fun refresh() = load(reset = true, refreshing = true)

    fun retry() {
        if (_state.value.items.isEmpty()) load(reset = true, refreshing = false) else loadMore(force = true)
    }

    fun loadMore(force: Boolean = false) {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || s.endReached) return
        if (s.error != null && !force) return
        load(reset = false, refreshing = false)
    }

    /** Whether coming back to the feed should read it again: not when it was read moments ago. */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = AutoRefresh.due(pageOneAt, now)

    private fun query(tag: String) = FeedQuery(
        sort = if (feed.order == FeedOrder.Hot) FeedSort.Hot else FeedSort.New,
        window = feed.window,
        topic = tag,
    ).normalized()

    private fun load(reset: Boolean, refreshing: Boolean) {
        job?.cancel()
        started = true
        val shown = feed
        job = viewModelScope.launch {
            if (reset) {
                _state.update {
                    it.copy(
                        loading = !refreshing,
                        refreshing = refreshing,
                        loadingMore = false,
                        endReached = false,
                        error = null,
                        items = if (refreshing) it.items else emptyList(),
                    )
                }
            } else {
                _state.update { it.copy(loadingMore = true, error = null) }
            }
            try {
                var current = if (reset) shown.tags.map { TagFeedRules.Stream(it) } else streams
                var merge = TagFeedRules.merge(shown, current)
                val before = if (reset) 0 else _state.value.items.size
                var rounds = 0
                var lastError: ApiException? = null
                var read = 0
                var failed = 0
                // Filters (left-out tags, unanswered only) can leave little of a page: a few rounds more, at most.
                while (merge.more.isNotEmpty() && rounds < TagFeedRules.MAX_ROUNDS) {
                    val ask = merge.more
                    val first = reset && rounds == 0
                    if (first) _progress.value = 0 to ask.size
                    val results = readNext(current, ask) { done -> if (first) _progress.value = done to ask.size }
                    var stuck = false
                    current = current.map { stream ->
                        val result = results[stream.tag] ?: return@map stream
                        read++
                        val failure = result.error ?: return@map result.stream
                        failed++
                        lastError = failure
                        // A later page that failed holds the list where it is (TagFeedRules.afterFailedRead).
                        if (stream.pages.isNotEmpty()) stuck = true
                        TagFeedRules.afterFailedRead(stream)
                    }
                    merge = TagFeedRules.merge(shown, current)
                    rounds++
                    if (stuck || merge.ended || merge.items.size - before >= TagFeedRules.MIN_NEW_ITEMS) break
                }
                _progress.value = null
                val nothingRead = read > 0 && failed == read
                if (reset && refreshing && nothingRead) {
                    // Pulled to refresh, and not one tag could be read: the list stays as it was, with the error.
                    _state.update { it.copy(refreshing = false, error = lastError) }
                    return@launch
                }
                streams = current
                if (reset && !nothingRead) pageOneAt = System.currentTimeMillis()
                // Nothing new because a tag couldn't be read: the error and Retry, never "the end of the list".
                val error = if (failed > 0 && merge.items.size <= before) lastError else null
                _state.update {
                    it.copy(
                        items = merge.items,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        endReached = merge.ended && error == null,
                        error = error,
                        swapped = reset && refreshing,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _progress.value = null
                _state.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = e.asApiException()) }
            }
        }
    }

    /** The next page of each of [tags], a few at a time; Stips' request limit stops the lot. */
    private suspend fun readNext(current: List<TagFeedRules.Stream>, tags: List<String>, onDone: (Int) -> Unit): Map<String, Read> =
        coroutineScope {
            val gate = Semaphore(TagFeedRules.PARALLEL)
            var done = 0
            tags.mapNotNull { tag -> current.firstOrNull { it.tag == tag } }.map { stream ->
                async {
                    gate.withPermit {
                        val read = readStream(stream)
                        // These run on the main thread one at a time, so the count needs no lock.
                        done++
                        onDone(done)
                        stream.tag to read
                    }
                }
            }.awaitAll().toMap()
        }

    private suspend fun readStream(stream: TagFeedRules.Stream): Read = try {
        val list = repository.feedPage(query(stream.tag), stream.pages.size + 1)
        Read(if (list.isEmpty()) stream.copy(ended = true) else stream.copy(pages = stream.pages + listOf(list)), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = e.asApiException()
        if (error.code == ApiException.RATE_LIMITED || error.code == ApiException.BLOCKED) throw error
        // The stream as it was: load decides whether the tag is set aside or asked again next time.
        Read(stream, error)
    }

    /**
     * Live in a feed (#40): one tag's newest page per call, the tags in turn, so a feed of 15 tags costs no more than
     * Home's Live. Newest-first feeds only. Returns how many questions came in on top, or -1 when the check failed.
     */
    suspend fun pollOne(): Int {
        val s = _state.value
        if (feed.order != FeedOrder.New || job?.isActive == true || s.loading || s.refreshing || s.items.isEmpty()) return 0
        val open = streams.filter { it.pages.isNotEmpty() }
        if (open.isEmpty()) return 0
        val tag = open[pollNext++ % open.size].tag
        val fresh = try {
            repository.feedPage(query(tag), 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return -1
        }
        // A load may have started meanwhile; it brings these too.
        if (job?.isActive == true) return 0
        val stream = streams.firstOrNull { it.tag == tag && it.pages.isNotEmpty() } ?: return 0
        val newest = stream.items.maxOfOrNull { it.id } ?: 0L
        val known = stream.items.mapTo(HashSet()) { it.id }
        val newer = fresh.filter { it.id > newest && it.id !in known }
        if (newer.isEmpty()) return 0
        val updated = stream.copy(pages = listOf(newer + stream.pages.first()) + stream.pages.drop(1))
        streams = streams.map { if (it.tag == tag) updated else it }
        pageOneAt = System.currentTimeMillis()
        val before = _state.value.items.mapTo(HashSet()) { it.id }
        val merge = TagFeedRules.merge(feed, streams)
        _state.update { it.copy(items = merge.items, swapped = false) }
        return merge.items.count { it.id !in before }
    }
}
