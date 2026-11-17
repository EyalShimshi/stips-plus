package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.data.parse.arr
import io.github.eyalshimshi.betterstips.data.parse.asObject
import io.github.eyalshimshi.betterstips.data.parse.bool
import io.github.eyalshimshi.betterstips.data.parse.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Your feeds (#40): what a feed may hold, how it's kept, how each tag's lists from Stips become one list, and the link
 * that shares a feed. Stips is asked tag by tag, only while a feed is on screen, page after page as it's scrolled
 * (the owner's choices). Pure.
 */
object TagFeedRules {
    /** The most tags a feed asks Stips for, one request each (the owner's pick: 15, fixed). */
    const val MAX_TAGS = 15

    /** The most tags a feed leaves out; they cost no requests. */
    const val MAX_EXCLUDED = 15

    const val MAX_NAME = 30
    const val MAX_TAG = 40
    const val MAX_FEEDS = 30

    /** How many tags' pages are asked at the same time (TagLists, for all feeds and the preloading together). */
    const val PARALLEL = 6

    /** When a load added fewer than this (filters can leave little), the next pages are asked at once. */
    const val MIN_NEW_ITEMS = 8

    /** At most this many rounds of asking for one load, so a feed that finds nothing stops. */
    const val MAX_ROUNDS = 3

    /** "#זוגיות " → "זוגיות": as Stips writes a tag; null when nothing's left or it's too long. */
    fun cleanTag(raw: String?): String? {
        val tag = raw?.replace(Regex("\\s+"), " ")?.trim()?.trimStart('#')?.trim() ?: return null
        return tag.takeIf { it.isNotEmpty() && it.length <= MAX_TAG }
    }

    /** [raw] cleaned, each tag once, at most [max]. */
    fun cleanTags(raw: List<String>, max: Int = MAX_TAGS): List<String> =
        raw.mapNotNull(::cleanTag).distinctBy { it.lowercase() }.take(max)

    fun cleanName(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME)

    /**
     * One emoji, whole, never half of one (v0.24: the picker offers any): its variation selector, skin tone, joined
     * parts (👨‍👩‍👧), keycap or flag pair stay with it; anything after it goes.
     */
    fun cleanEmoji(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        fun after(index: Int) = index + Character.charCount(text.codePointAt(index))
        val first = text.codePointAt(0)
        var end = after(0)
        if (first in REGIONAL_INDICATORS) {
            if (end < text.length && text.codePointAt(end) in REGIONAL_INDICATORS) end = after(end)
            return text.substring(0, end)
        }
        var parts = 1
        while (end < text.length && parts < MAX_EMOJI_PARTS) {
            val next = text.codePointAt(end)
            end = when {
                next == 0xFE0F || next == 0xFE0E || next == 0x20E3 || next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F -> after(end)
                next == 0x200D && after(end) < text.length -> after(after(end))
                else -> break
            }
            parts++
        }
        return text.substring(0, end)
    }

    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
    private const val MAX_EMOJI_PARTS = 16

    /** A feed that can be saved: a name and at least one tag. */
    fun valid(feed: TagFeed): Boolean = feed.name.isNotBlank() && feed.tags.isNotEmpty()

    /** [feed] with everything in its limits. */
    fun cleaned(feed: TagFeed): TagFeed = feed.copy(
        name = cleanName(feed.name),
        emoji = cleanEmoji(feed.emoji),
        tags = cleanTags(feed.tags),
        excluded = cleanTags(feed.excluded, MAX_EXCLUDED),
        window = if (feed.window == HotWindow.Month) HotWindow.Month else HotWindow.Week,
    )

    // --- Kept on the phone ---

    fun toJson(feeds: List<TagFeed>): String = buildJsonObject {
        put("v", 1)
        putJsonArray("feeds") {
            feeds.forEach { feed ->
                add(
                    buildJsonObject {
                        put("id", feed.id)
                        put("name", feed.name)
                        put("emoji", feed.emoji)
                        put("tags", buildJsonArray { feed.tags.forEach { add(JsonPrimitive(it)) } })
                        put("excluded", buildJsonArray { feed.excluded.forEach { add(JsonPrimitive(it)) } })
                        put("order", feed.order.name)
                        put("window", feed.window.name)
                        put("unanswered", feed.unansweredOnly)
                    },
                )
            }
        }
    }.toString()

    /** The feeds kept in [text]; anything unreadable is left out. */
    fun fromJson(text: String?): List<TagFeed> {
        if (text.isNullOrBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull().asObject() ?: return emptyList()
        return root.arr("feeds").orEmpty().mapNotNull { element ->
            val o = element.asObject() ?: return@mapNotNull null
            val feed = TagFeed(
                id = o.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                name = o.str("name").orEmpty(),
                emoji = o.str("emoji").orEmpty(),
                tags = o.arr("tags").orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
                excluded = o.arr("excluded").orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
                order = FeedOrder.entries.firstOrNull { it.name == o.str("order") } ?: FeedOrder.New,
                window = HotWindow.entries.firstOrNull { it.name == o.str("window") } ?: HotWindow.Week,
                unansweredOnly = o.bool("unanswered") == true,
            )
            cleaned(feed).takeIf(::valid)
        }.distinctBy { it.id }.take(MAX_FEEDS)
    }

    // --- One list from each tag's lists ---

    /** One tag's list as far as it was read: its pages in order, and whether Stips has no more. */
    data class Stream(val tag: String, val pages: List<List<Question>> = emptyList(), val ended: Boolean = false) {
        val items: List<Question> get() = pages.flatten()
    }

    /** What a feed shows now, which tags to ask for their next page to show more, and whether there's no more at all. */
    data class Merge(val items: List<Question>, val more: List<String>, val ended: Boolean)

    /**
     * A list being read keeps still (v0.24) while the lists under it are brought up to date: New never shows less than
     * down to [floor] (the oldest question on screen); Hot and Mixed leave out tags that came in after the list was
     * laid out ([base] holds the ones it had, each with its newest question), and Mixed takes only up to that newest,
     * so new questions wait for the top of the list, or the next visit.
     */
    data class Held(val floor: Long?, val base: Map<String, Long>)

    /** A question the feed keeps: none of its left-out tags, and unanswered when it asks for that. */
    fun accepts(feed: TagFeed, question: Question): Boolean {
        if (feed.unansweredOnly && question.answerCount > 0) return false
        if (feed.excluded.isEmpty()) return true
        val left = feed.excluded.mapTo(HashSet()) { it.lowercase() }
        return question.topics.none { it.lowercase() in left }
    }

    /**
     * The feed's list from its tags' [streams], in the feed's order of tags. Each way only ever adds at the end as more
     * pages come, so what's on screen stays put; [held] keeps it so while the lists are brought up to date.
     */
    fun merge(feed: TagFeed, streams: List<Stream>, held: Held? = null): Merge {
        if (held == null) {
            return when (feed.order) {
                FeedOrder.New -> mergeNew(feed, streams, null)
                FeedOrder.Hot -> mergeHot(feed, streams)
                FeedOrder.Mixed -> mergeMixed(feed, streams)
            }
        }
        if (feed.order == FeedOrder.New) return mergeNew(feed, streams, held.floor)
        // Hot and Mixed: a tag that came in after the list was laid out waits for its top, or the next visit.
        val laidOut = streams.map { stream -> if (stream.tag in held.base) stream else Stream(stream.tag, ended = true) }
        if (feed.order == FeedOrder.Hot) return mergeHot(feed, laidOut)
        return mergeMixed(
            feed,
            laidOut.map { stream ->
                val base = held.base[stream.tag] ?: return@map stream
                stream.copy(pages = stream.pages.map { page -> page.filter { it.id <= base } })
            },
        )
    }

    /**
     * Newest first. A tag's next page can hold questions newer than another tag's oldest so far, so only questions at
     * least as new as every open tag's oldest one show (the frontier), or down to [floor] when that's further; the
     * tags holding the frontier back are asked next.
     */
    private fun mergeNew(feed: TagFeed, streams: List<Stream>, floor: Long?): Merge {
        val open = streams.filter { !it.ended }
        val waiting = open.filter { it.pages.isEmpty() }
        if (waiting.isNotEmpty()) return Merge(emptyList(), waiting.map { it.tag }, ended = false)
        val oldest = open.associate { s -> s.tag to (s.items.minOfOrNull { it.id } ?: Long.MAX_VALUE) }
        val frontier = oldest.values.maxOrNull()
        val down = if (frontier == null || floor == null) frontier else minOf(frontier, floor)
        val items = streams.flatMap { it.items }.distinctBy { it.id }
            .filter { (down == null || it.id >= down) && accepts(feed, it) }
            .sortedByDescending { it.id }
        val more = if (frontier == null) emptyList() else open.filter { oldest[it.tag] == frontier }.map { it.tag }
        return Merge(items, more, ended = open.isEmpty())
    }

    /**
     * Each tag's hot questions: round by round (page 1 of every tag, then page 2…), each round the most answered
     * first. A round shows once every open tag has its page.
     */
    private fun mergeHot(feed: TagFeed, streams: List<Stream>): Merge {
        val open = streams.filter { !it.ended }
        val rounds = open.minOfOrNull { it.pages.size } ?: (streams.maxOfOrNull { it.pages.size } ?: 0)
        val seen = HashSet<Long>()
        val items = mutableListOf<Question>()
        for (round in 0 until rounds) {
            val batch = streams.flatMap { it.pages.getOrNull(round).orEmpty() }.filter { seen.add(it.id) }
            items += batch.filter { accepts(feed, it) }
                .sortedWith(compareByDescending<Question> { it.answerCount }.thenByDescending { it.pinCount })
        }
        val more = open.filter { it.pages.size == rounds }.map { it.tag }
        return Merge(items, more, ended = open.isEmpty())
    }

    /**
     * One question from each tag in turn, whatever their times (the owner's "mixed"). A tag that has run out of read
     * questions stops the turn until its next page comes; a tag Stips has no more of is skipped.
     */
    private fun mergeMixed(feed: TagFeed, streams: List<Stream>): Merge {
        val lists = streams.map { it.items }
        val next = IntArray(streams.size)
        val seen = HashSet<Long>()
        val items = mutableListOf<Question>()
        var stoppedBy: String? = null
        turns@ while (true) {
            var tookAny = false
            for ((index, stream) in streams.withIndex()) {
                var took = false
                while (next[index] < lists[index].size) {
                    val question = lists[index][next[index]++]
                    if (seen.add(question.id)) {
                        if (accepts(feed, question)) items += question
                        took = true
                        break
                    }
                }
                if (took) {
                    tookAny = true
                } else if (!stream.ended) {
                    stoppedBy = stream.tag
                    break@turns
                }
            }
            if (!tookAny) break
        }
        val unread = streams.filter { !it.ended && it.pages.isEmpty() }.map { it.tag }
        val more = (unread + listOfNotNull(stoppedBy)).distinct()
        return Merge(items, more, ended = streams.all { it.ended })
    }

    // --- Shared as a link (#40) ---

    /** Where a shared feed's link points: the website's page for it, which Stips+ opens instead when it's installed. */
    const val SHARE_PREFIX = "https://www.shimshii.com/stips-plus/feed/"

    private val shareHosts = setOf("www.shimshii.com", "shimshii.com")

    /** The link that shares [feed]: its name, emoji, order and tags, nothing about whoever shares it. */
    fun shareLink(feed: TagFeed): String {
        val params = buildList {
            add("v" to "1")
            add("n" to feed.name)
            if (feed.emoji.isNotBlank()) add("e" to feed.emoji)
            add("s" to feed.order.name.lowercase())
            if (feed.order == FeedOrder.Hot) add("w" to feed.window.name.lowercase())
            if (feed.unansweredOnly) add("u" to "1")
            feed.tags.forEach { add("t" to it) }
            feed.excluded.forEach { add("x" to it) }
        }
        return SHARE_PREFIX + "?" + params.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    /** The feed a shared link describes, with [newId]; null for any other link, or one without a name or tags. */
    fun fromShareLink(link: String?, newId: String): TagFeed? {
        val uri = runCatching { URI(link?.trim().orEmpty()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || host !in shareHosts) return null
        if (!uri.rawPath.orEmpty().startsWith("/stips-plus/feed")) return null
        val params = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
        }
        fun one(key: String) = params.firstOrNull { it.first == key }?.second
        fun all(key: String) = params.filter { it.first == key }.map { it.second }
        val feed = TagFeed(
            id = newId,
            name = one("n").orEmpty(),
            emoji = one("e").orEmpty(),
            tags = all("t"),
            excluded = all("x"),
            order = FeedOrder.entries.firstOrNull { it.name.equals(one("s"), ignoreCase = true) } ?: FeedOrder.New,
            window = if (one("w").equals("month", ignoreCase = true)) HotWindow.Month else HotWindow.Week,
            unansweredOnly = one("u") == "1",
        )
        return cleaned(feed).takeIf(::valid)
    }

    // --- Ready-made feeds (#40) ---

    /** A ready-made feed, offered while you have none: its tags checked against Stips' own suggestions. */
    data class Starter(val emoji: String, val nameHe: String, val nameEn: String, val tags: List<String>) {
        fun feed(id: String, hebrew: Boolean): TagFeed = TagFeed(id = id, name = if (hebrew) nameHe else nameEn, emoji = emoji, tags = tags)
    }

    val STARTERS: List<Starter> = listOf(
        Starter(
            "❤️", "זוגיות ואהבה", "Love and relationships",
            listOf("זוגיות", "מערכת יחסים", "מערכות יחסים", "אהבה", "דייטים", "דייט", "דייט ראשון", "קראש", "התאהבות", "פרידה"),
        ),
        Starter(
            "🪖", "צבא", "The army",
            listOf("צבא", "צה\"ל", "גיוס", "חיילים", "תפקידים בצה\"ל", "מיונים", "צו ראשון", "טירונות", "צבא קבע"),
        ),
        Starter(
            "🚗", "נהיגה ורכב", "Driving and cars",
            listOf("נהיגה", "רשיון נהיגה", "רישיון נהיגה", "שיעורי נהיגה", "תאוריה", "טסט", "רכב", "קניית רכב"),
        ),
        Starter(
            "💰", "כסף ועבודה", "Money and work",
            listOf("כסף", "חסכון", "חיסכון כספי", "עבודה", "עבודה בחופש", "משכורת", "השקעות"),
        ),
        Starter(
            "📚", "לימודים ובגרויות", "School and exams",
            listOf("בגרות", "בגרויות", "מבחנים", "לימודים", "בית ספר", "שיעורי בית", "שיעורים פרטיים"),
        ),
    )
}
