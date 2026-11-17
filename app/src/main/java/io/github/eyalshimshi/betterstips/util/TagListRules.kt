package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.data.parse.Parsers
import io.github.eyalshimshi.betterstips.data.parse.arr
import io.github.eyalshimshi.betterstips.data.parse.asObject
import io.github.eyalshimshi.betterstips.data.parse.bool
import io.github.eyalshimshi.betterstips.data.parse.int
import io.github.eyalshimshi.betterstips.data.parse.long
import io.github.eyalshimshi.betterstips.data.parse.obj
import io.github.eyalshimshi.betterstips.data.parse.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The feeds' cache (v0.24, D-167): each tag's list from Stips, kept on the phone as far as it was read, so a feed shows
 * at once and asks Stips only for what's new. A newest-first list is kept as one list, newest first, and Stips' next
 * page follows from how many it holds (Stips' pages are full but for the last; measured). A hot list keeps its pages,
 * a feed's rounds. Pure.
 */
object TagListRules {
    /** Stips' page: 30 questions. A shorter one is the last (the safe filter applies before paging; measured). */
    const val PAGE_SIZE = 30

    /** A busy tag is read again at most this often... */
    const val MIN_REFRESH_MS = 3L * 60 * 1000

    /** ...and a quiet one at least this often. */
    const val MAX_REFRESH_MS = 12L * 60 * 60 * 1000

    /** A tag's hot list changes with its answers. */
    const val HOT_REFRESH_MS = 30L * 60 * 1000

    /**
     * How long what's below a list's first page is kept once its top is read again: questions further down change
     * too (answers come, so "unanswered only" would show answered ones; hot rounds move). Older than this, it goes,
     * and is read again as a feed is scrolled.
     */
    const val DEEP_KEEP_MS = 6L * 60 * 60 * 1000
    const val HOT_DEEP_KEEP_MS = 60L * 60 * 1000

    /** What the phone's storage keeps of a list: its newest 150 questions, or a hot list's first 5 pages. */
    const val KEPT_ITEMS = 150
    const val KEPT_HOT_PAGES = 5

    /** After Stips failed on a tag once, twice, three times, more: how long until it's asked again. */
    private val BACKOFF_MS = longArrayOf(30L * 60 * 1000, 2L * 60 * 60 * 1000, 8L * 60 * 60 * 1000, 24L * 60 * 60 * 1000)

    /** Failures that are the tag's (Stips couldn't answer for it), not the connection's or the request budget's. */
    private val TAG_FAILURES = setOf(ApiException.SERVER, ApiException.BAD_RESPONSE, ApiException.HTTP, ApiException.GENERAL)

    /** Which of a tag's lists: its newest (New and Mixed feeds read the same one), or its hot this week or month. */
    enum class Kind(val key: String) { New("new"), HotWeek("hot-week"), HotMonth("hot-month") }

    fun kindOf(order: FeedOrder, window: HotWindow): Kind = when (order) {
        FeedOrder.Hot -> if (window == HotWindow.Month) Kind.HotMonth else Kind.HotWeek
        FeedOrder.New, FeedOrder.Mixed -> Kind.New
    }

    fun kindOf(key: String): Kind? = Kind.entries.firstOrNull { it.key == key }

    /** A tag the same whatever its spaces or case. */
    fun normalTag(tag: String): String = tag.replace(Regex("\\s+"), " ").trim().lowercase()

    fun sameTag(a: String, b: String): Boolean = normalTag(a) == normalTag(b)

    /**
     * A tag's list as kept: [pages] (one page, newest first, for [Kind.New]; Stips' own pages for hot lists), whether
     * Stips has no more, when its top was last read ([fetchedAt]; 0: never), and the failed reads in a row.
     */
    data class Entry(
        val pages: List<List<Question>> = emptyList(),
        val ended: Boolean = false,
        val fetchedAt: Long = 0L,
        /** Pages that brought nothing new below, in a row: the next read looks that much further on. */
        val skip: Int = 0,
        val failures: Int = 0,
        val failedAt: Long = 0L,
        /** When the oldest-read part below the first page was read (0: there's none): see [DEEP_KEEP_MS]. */
        val deepAt: Long = 0L,
    ) {
        val items: List<Question> get() = pages.flatten()

        /** Stips answered for the tag at least once: it has questions, or it has none at all. */
        val read: Boolean get() = pages.isNotEmpty() || ended
    }

    /** Stips' page to ask for more of [entry]. */
    fun nextPage(entry: Entry?, kind: Kind): Int = when {
        entry == null -> 1
        kind == Kind.New -> entry.items.size / PAGE_SIZE + 1 + entry.skip
        else -> entry.pages.size + 1
    }

    /** [entry] with Stips' page [page] ([list], read at [now]) folded in. */
    fun fold(entry: Entry?, kind: Kind, page: Int, list: List<Question>, now: Long): Entry =
        if (kind == Kind.New) foldNew(entry ?: Entry(), page, list, now) else foldHot(entry ?: Entry(), page, list, now)

    private fun foldNew(entry: Entry, page: Int, list: List<Question>, now: Long): Entry {
        val sorted = list.distinctBy { it.id }.sortedByDescending { it.id }
        val kept = entry.items
        if (page <= 1) {
            // A short first page is all the tag has. A full one replaces the top it covers and joins what's kept
            // below when it reaches it (while that's fresh enough); when it doesn't, more came than a page holds, and
            // the list starts over.
            if (sorted.size < PAGE_SIZE) return Entry(pages = if (sorted.isEmpty()) emptyList() else listOf(sorted), ended = true, fetchedAt = now)
            val ids = sorted.mapTo(HashSet()) { it.id }
            if (kept.none { it.id in ids }) return Entry(pages = listOf(sorted), fetchedAt = now)
            val oldestFresh = sorted.last().id
            val below = kept.filter { it.id < oldestFresh }
            val since = if (entry.deepAt > 0L) minOf(entry.deepAt, entry.fetchedAt) else entry.fetchedAt
            val keep = below.isNotEmpty() && now - since in 0 until DEEP_KEEP_MS
            return entry.copy(
                pages = listOf(if (keep) sorted + below else sorted),
                ended = if (keep || below.isEmpty()) entry.ended else false,
                fetchedAt = now,
                skip = 0,
                failures = 0,
                failedAt = 0L,
                deepAt = if (keep) since else 0L,
            )
        }
        // A page further down, if it's still the one that comes next (a refresh may have moved the list meanwhile):
        // the questions older than the oldest kept; the ones it repeats come fresher.
        if (page != nextPage(entry, Kind.New)) return entry
        if (sorted.isEmpty()) return entry.copy(ended = true, failures = 0, failedAt = 0L)
        val fresh = sorted.associateBy { it.id }
        val oldest = kept.minOfOrNull { it.id } ?: Long.MAX_VALUE
        val older = sorted.filter { it.id < oldest }
        return entry.copy(
            pages = listOf(kept.map { fresh[it.id] ?: it } + older),
            ended = sorted.size < PAGE_SIZE,
            skip = if (older.isEmpty() && sorted.size >= PAGE_SIZE) entry.skip + 1 else 0,
            failures = 0,
            failedAt = 0L,
            deepAt = deepSince(entry, now),
        )
    }

    /** When the part below the first page was read, now that a deeper page joins it: the oldest of it counts. */
    private fun deepSince(entry: Entry, now: Long): Long = when {
        entry.deepAt > 0L -> entry.deepAt
        entry.fetchedAt > 0L -> minOf(entry.fetchedAt, now)
        else -> now
    }

    private fun foldHot(entry: Entry, page: Int, list: List<Question>, now: Long): Entry {
        if (page <= 1) {
            // A short first page is the whole hot list. A full one replaces the first round and keeps the later ones
            // while they're fresh enough, so a feed being read keeps its rounds (a question that moved shows once).
            if (list.size < PAGE_SIZE) return Entry(pages = if (list.isEmpty()) emptyList() else listOf(list), ended = true, fetchedAt = now)
            val keep = entry.pages.size > 1 && entry.deepAt > 0L && now - entry.deepAt in 0 until HOT_DEEP_KEEP_MS
            return Entry(
                pages = listOf(list) + if (keep) entry.pages.drop(1) else emptyList(),
                ended = keep && entry.ended,
                fetchedAt = now,
                deepAt = if (keep) entry.deepAt else 0L,
            )
        }
        if (page != entry.pages.size + 1) return entry
        return entry.copy(
            pages = if (list.isEmpty()) entry.pages else entry.pages + listOf(list),
            ended = list.size < PAGE_SIZE,
            failures = 0,
            failedAt = 0L,
            // The later rounds are as old as the first of them read.
            deepAt = if (list.isEmpty() || entry.deepAt > 0L) entry.deepAt else now,
        )
    }

    /**
     * The list a feed being read takes for a tag: the cache's ([kept]), but never with fewer questions than [shown]
     * (a refresh that started a tag over, or a shorter hot list), so nothing on screen goes away. The next visit lays
     * the cache's out as it is.
     */
    fun keepShowing(shown: Entry?, kept: Entry, kind: Kind): Entry {
        if (shown == null) return kept
        if (kind != Kind.New) return if (kept.pages.size < shown.pages.size) shown else kept
        val ids = kept.items.mapTo(HashSet()) { it.id }
        val gone = shown.items.filter { it.id !in ids }
        if (gone.isEmpty()) return kept
        return kept.copy(pages = listOf((kept.items + gone).sortedByDescending { it.id }))
    }

    /** [entry] after a read failed with [code] at [now]: Stips' own failures count against the tag, the connection's don't. */
    fun failed(entry: Entry?, code: String, now: Long): Entry? {
        if (code !in TAG_FAILURES) return entry
        val e = entry ?: Entry()
        return e.copy(failures = e.failures + 1, failedAt = now)
    }

    fun backoffMs(failures: Int): Long = if (failures <= 0) 0L else BACKOFF_MS[(failures - 1).coerceAtMost(BACKOFF_MS.size - 1)]

    /** Stips failed on the tag lately: it isn't asked again until its wait is over (what's kept still shows). */
    fun unavailable(entry: Entry?, now: Long): Boolean {
        if (entry == null || entry.failures <= 0) return false
        return now - entry.failedAt in 0 until backoffMs(entry.failures)
    }

    /** Time to read [entry]'s top again: never read, or as long ago as its tag's pace allows. */
    fun due(entry: Entry?, kind: Kind, now: Long): Boolean {
        if (entry == null) return true
        if (unavailable(entry, now)) return false
        if (!entry.read) return true
        val age = now - entry.fetchedAt
        return age < 0 || age >= refreshEvery(entry, kind)
    }

    /**
     * How long a tag's list stays fresh: half the time each of its newest questions took to come (30 in the last 10
     * days: about every 4 hours), from 3 minutes to 12 hours. A hot list: every 30 minutes.
     */
    fun refreshEvery(entry: Entry, kind: Kind): Long {
        if (kind != Kind.New) return HOT_REFRESH_MS
        val newest = entry.items.take(PAGE_SIZE)
        val since = newest.lastOrNull()?.postedAt?.toEpochMilli() ?: return MAX_REFRESH_MS
        if (newest.size < 2) return MAX_REFRESH_MS
        val span = entry.fetchedAt - since
        if (span <= 0L) return MIN_REFRESH_MS
        return (span / newest.size / 2).coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)
    }

    /**
     * Home's newest questions ([newest], all of Stips) brought into a tag's newest-first list without asking Stips:
     * when they reach back to the tag's newest kept question, every newer one with the tag is among them. The ones it
     * already has come fresher. Null when nothing changes.
     */
    fun absorb(entry: Entry?, tag: String, newest: List<Question>): Entry? {
        val kept = entry?.items.orEmpty()
        if (entry == null || kept.isEmpty() || newest.isEmpty()) return null
        val top = kept.maxOf { it.id }
        if (top < newest.minOf { it.id }) return null
        val known = kept.mapTo(HashSet()) { it.id }
        val fresh = newest.associateBy { it.id }
        val added = newest
            .filter { it.id > top && it.id !in known && it.topics.any { topic -> sameTag(topic, tag) } }
            .sortedByDescending { it.id }
        val updated = kept.map { fresh[it.id] ?: it }
        if (added.isEmpty() && updated == kept) return null
        return entry.copy(pages = listOf(added + updated))
    }

    /** [question] as pinned by you or not, its count and its kept JSON in step, so it stays so after a restart. */
    fun withPin(question: Question, pinned: Boolean): Question {
        if (question.userPinned == pinned) return question
        val count = (question.pinCount + if (pinned) 1 else -1).coerceAtLeast(0)
        val raw = question.raw?.let { patchPin(it, pinned, count) } ?: question.raw
        return question.copy(userPinned = pinned, pinCount = count, raw = raw)
    }

    private fun patchPin(raw: String, pinned: Boolean, count: Int): String? = runCatching {
        val item = Json.parseToJsonElement(raw).asObject() ?: return@runCatching null
        val data = item.obj("data") ?: return@runCatching null
        val patched = JsonObject(data + mapOf("user_pinned" to JsonPrimitive(if (pinned) 1 else 0), "pin_count" to JsonPrimitive(count)))
        JsonObject(item + ("data" to patched)).toString()
    }.getOrNull()

    /** What preloading reads, in order: each feed's tags in its own order's list, the feeds in yours, each list once. */
    fun warmKeys(feeds: List<TagFeed>): List<Pair<String, Kind>> =
        feeds.flatMap { feed -> feed.tags.map { it to kindOf(feed.order, feed.window) } }
            .distinctBy { (tag, kind) -> normalTag(tag) to kind }

    // --- On the phone's storage ---

    /** What the storage keeps of [entry]: its newest [KEPT_ITEMS], or its first [KEPT_HOT_PAGES] pages. */
    fun trimmed(entry: Entry, kind: Kind): Entry = when {
        kind == Kind.New && entry.items.size > KEPT_ITEMS -> entry.copy(pages = listOf(entry.items.take(KEPT_ITEMS)), ended = false, skip = 0)
        kind != Kind.New && entry.pages.size > KEPT_HOT_PAGES -> entry.copy(pages = entry.pages.take(KEPT_HOT_PAGES), ended = false)
        else -> entry
    }

    /** The file that keeps [entry] for [id] (checked when it's read): each question as Stips sent it. */
    fun toJson(id: String, kind: Kind, entry: Entry): String {
        val kept = trimmed(entry, kind)
        val pages = JsonArray(
            kept.pages.map { page -> JsonArray(page.mapNotNull { q -> q.raw?.let { raw -> runCatching { Json.parseToJsonElement(raw) }.getOrNull() } }) },
        )
        return buildJsonObject {
            put("v", 1)
            put("id", id)
            put("fetchedAt", kept.fetchedAt)
            put("ended", kept.ended)
            put("skip", kept.skip)
            put("failures", kept.failures)
            put("failedAt", kept.failedAt)
            put("deepAt", kept.deepAt)
            put("pages", pages)
        }.toString()
    }

    /** The list a file keeps for [id]; null when it isn't one, or it's another list's. */
    fun fromJson(text: String?, id: String): Entry? {
        if (text.isNullOrBlank()) return null
        val o = runCatching { Json.parseToJsonElement(text) }.getOrNull().asObject() ?: return null
        if (o.int("v") != 1 || o.str("id") != id) return null
        return Entry(
            pages = o.arr("pages").orEmpty().map { Parsers.questions(it) }.filter { it.isNotEmpty() },
            ended = o.bool("ended") == true,
            fetchedAt = o.long("fetchedAt") ?: 0L,
            skip = o.int("skip") ?: 0,
            failures = o.int("failures") ?: 0,
            failedAt = o.long("failedAt") ?: 0L,
            deepAt = o.long("deepAt") ?: 0L,
        )
    }

    /** A kept list's file: its name, its size, and when it was last used. */
    data class Kept(val name: String, val bytes: Long, val usedAt: Long)

    /** The files to delete, least recently used first, so what's kept fits [maxBytes] and [maxFiles]. */
    fun evict(files: List<Kept>, maxBytes: Long, maxFiles: Int): List<String> {
        var bytes = files.sumOf { it.bytes }
        var count = files.size
        val drop = ArrayList<String>()
        for (file in files.sortedBy { it.usedAt }) {
            if (bytes <= maxBytes && count <= maxFiles) break
            drop += file.name
            bytes -= file.bytes
            count--
        }
        return drop
    }
}
