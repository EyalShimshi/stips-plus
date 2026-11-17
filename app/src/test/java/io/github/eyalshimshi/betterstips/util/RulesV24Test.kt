package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.api.RateGuard
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.data.parse.Parsers
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** v0.24: the feeds' cache and preloading (D-167, D-168), the emoji picker (D-170), and Live in feeds gone (D-169). */
class RulesV24Test {
    private fun source(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val stipsTime = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    private val start = LocalDateTime.of(2026, 11, 1, 12, 0)

    /** Epoch ms of [minutes] after the test's start, as the phone's clock. */
    private fun at(minutes: Long): Long = start.plusMinutes(minutes).atZone(zone).toInstant().toEpochMilli()

    /** A question as Stips sends it (so it keeps its JSON), posted [minutes] after the start. */
    private fun q(id: Long, vararg tags: String, minutes: Long = id, answers: Int = 0, pins: Int = 0, pinned: Boolean = false): Question {
        val time = start.plusMinutes(minutes).format(stipsTime)
        val tagList = tags.joinToString(",") { "\"$it\"" }
        val raw = """{"objType":"ask","data":{"id":$id,"q":"Question $id","tagslist":[$tagList],"time":"$time",""" +
            """"answer_count":$answers,"pin_count":$pins,"user_pinned":${if (pinned) 1 else 0}},"extra":{},"meta":{}}"""
        return Parsers.question(Json.parseToJsonElement(raw)) ?: error("not a question: $raw")
    }

    /** Questions [from] down to [to], newest first. */
    private fun qs(from: Long, to: Long, vararg tags: String): List<Question> = (from downTo to).map { q(it, *tags) }

    private fun ids(entry: TagListRules.Entry?) = entry?.items?.map { it.id }

    private val newKind = TagListRules.Kind.New

    // --- The feeds' cache (D-167) ---

    @Test
    fun cache_newestTopJoinsWhatsKept() {
        val kept = TagListRules.fold(null, newKind, 1, qs(100, 71), at(0))
        assertEquals((100L downTo 71L).toList(), ids(kept))
        assertFalse("a full page: more below", kept.ended)
        assertEquals(2, TagListRules.nextPage(kept, newKind))
        // 5 new on top: the page covers 105..76 and joins the rest; 98 was deleted, so it goes.
        val fresh = qs(105, 99) + qs(97, 75)
        val joined = TagListRules.fold(kept, newKind, 1, fresh, at(60))
        assertEquals(105L, joined.items.first().id)
        assertEquals(71L, joined.items.last().id)
        assertFalse(98L in ids(joined)!!)
        assertEquals(at(60), joined.fetchedAt)
        assertEquals("Stips' pages moved by the new ones: the next one repeats a few, never skips", 2, TagListRules.nextPage(joined, newKind))
        // What's below the top is kept for 6 hours since it was read; after that the top stands alone, the rest read again.
        val stale = TagListRules.fold(joined, newKind, 1, qs(110, 81), at(8L * 60))
        assertEquals((110L downTo 81L).toList(), ids(stale))
        assertFalse(stale.ended)
        // More came than a page holds: no way to join, so it starts over from the top.
        val gap = TagListRules.fold(kept, newKind, 1, qs(200, 171), at(90))
        assertEquals((200L downTo 171L).toList(), ids(gap))
        // A short first page is everything the tag has.
        val small = TagListRules.fold(null, newKind, 1, qs(18, 1), at(0))
        assertTrue(small.ended)
        val none = TagListRules.fold(null, newKind, 1, emptyList(), at(0))
        assertTrue("a tag with no questions is read, and ended", none.read && none.ended && none.items.isEmpty())
    }

    @Test
    fun cache_deeperPagesFollowOn() {
        val one = TagListRules.fold(null, newKind, 1, qs(100, 71), at(0))
        val two = TagListRules.fold(one, newKind, 2, qs(70, 41), at(5))
        assertEquals(60, two.items.size)
        assertEquals(3, TagListRules.nextPage(two, newKind))
        assertEquals("a page that isn't next any more changes nothing", two, TagListRules.fold(two, newKind, 5, qs(30, 1), at(6)))
        val repeats = TagListRules.fold(two, newKind, 3, qs(70, 41), at(7))
        assertEquals("a page of repeats looks one further next time", 4, TagListRules.nextPage(repeats, newKind))
        val last = TagListRules.fold(two, newKind, 3, qs(40, 31), at(8))
        assertTrue("a short page is the last", last.ended)
        assertTrue(TagListRules.fold(two, newKind, 3, emptyList(), at(8)).ended)
    }

    @Test
    fun cache_hotKeepsItsRounds() {
        val hot = TagListRules.Kind.HotWeek
        val one = TagListRules.fold(null, hot, 1, qs(100, 71), at(0))
        val two = TagListRules.fold(one, hot, 2, qs(70, 41), at(1))
        assertEquals(2, two.pages.size)
        val again = TagListRules.fold(two, hot, 1, qs(130, 101), at(30))
        assertEquals("a new first round, the later ones kept", listOf(130L, 70L), again.pages.map { it.first().id })
        assertFalse(again.ended)
        val later = TagListRules.fold(two, hot, 1, qs(130, 101), at(2L * 60))
        assertEquals("an hour on, the later rounds are read again", 1, later.pages.size)
        val short = TagListRules.fold(two, hot, 1, qs(110, 100), at(40))
        assertEquals(1, short.pages.size)
        assertTrue(short.ended)
    }

    @Test
    fun cache_aTagStipsFailsOnWaitsItsTurn() {
        val kept = TagListRules.fold(null, newKind, 1, qs(100, 71), at(0))
        assertEquals("the connection's failure isn't the tag's", kept, TagListRules.failed(kept, ApiException.NETWORK, at(1)))
        val once = TagListRules.failed(null, ApiException.SERVER, at(0))!!
        assertFalse("never read, and nothing to show", once.read)
        assertTrue(TagListRules.unavailable(once, at(29)))
        assertFalse(TagListRules.unavailable(once, at(31)))
        assertFalse("not asked while it waits", TagListRules.due(once, newKind, at(10)))
        assertTrue(TagListRules.due(once, newKind, at(31)))
        val often = (1..6).fold(once) { e, n -> TagListRules.failed(e, ApiException.SERVER, at(n.toLong()))!! }
        assertEquals("the wait grows to a day at most", 24L * 60 * 60 * 1000, TagListRules.backoffMs(often.failures))
        val failedWithList = TagListRules.failed(kept, ApiException.SERVER, at(2))!!
        assertEquals("what's kept still shows", kept.items, failedWithList.items)
        assertFalse("and it isn't ended", failedWithList.ended)
        assertEquals("a good read clears it", 0, TagListRules.fold(failedWithList, newKind, 1, qs(100, 71), at(100)).failures)
    }

    @Test
    fun cache_eachTagAtItsOwnPace() {
        // 30 questions in the 10 days before the read: about every 4 hours.
        val quiet = TagListRules.fold(null, newKind, 1, (30L downTo 1L).map { q(it, minutes = it * 480) }, at(30L * 480 + 60))
        val every = TagListRules.refreshEvery(quiet, newKind)
        assertTrue("every $every ms", every in 3L * 60 * 60 * 1000..5L * 60 * 60 * 1000)
        assertFalse(TagListRules.due(quiet, newKind, quiet.fetchedAt + 60 * 60 * 1000))
        assertTrue(TagListRules.due(quiet, newKind, quiet.fetchedAt + every))
        // 30 in the last half hour: at most every 3 minutes.
        val busy = TagListRules.fold(null, newKind, 1, (30L downTo 1L).map { q(it, minutes = 1000 + it) }, at(1031))
        assertEquals(TagListRules.MIN_REFRESH_MS, TagListRules.refreshEvery(busy, newKind))
        // A few questions in years: twice a day.
        val rare = TagListRules.fold(null, newKind, 1, (3L downTo 1L).map { q(it, minutes = -it * 200_000) }, at(0))
        assertEquals(TagListRules.MAX_REFRESH_MS, TagListRules.refreshEvery(rare, newKind))
        assertEquals(TagListRules.HOT_REFRESH_MS, TagListRules.refreshEvery(busy, TagListRules.Kind.HotMonth))
        assertTrue("never read: due", TagListRules.due(null, newKind, at(0)))
    }

    @Test
    fun cache_homesNewQuestionsComeIn() {
        val kept = TagListRules.fold(null, newKind, 1, qs(100, 71, "A"), at(0))
        // Home's page reaches back to 99, so every newer question tagged A is on it.
        val home = listOf(q(110, "a "), q(108, "B"), q(105, "A", "B"), q(101, "B"), q(100, "A", answers = 7), q(99, "A"))
        val after = TagListRules.absorb(kept, "A", home)!!
        assertEquals(listOf(110L, 105L, 100L), ids(after)!!.take(3))
        assertEquals("the ones it had come fresher", 7, after.items.first { it.id == 100L }.answerCount)
        assertEquals(kept.fetchedAt, after.fetchedAt)
        assertNull("a page that doesn't reach back may miss some: nothing taken", TagListRules.absorb(kept, "A", listOf(q(130, "A"), q(120, "A"))))
        assertNull(TagListRules.absorb(kept, "A", listOf(q(90, "A"))))
    }

    @Test
    fun cache_keptOnThePhone() {
        val entry = TagListRules.fold(null, newKind, 1, qs(100, 71, "A"), at(0))
        val failed = TagListRules.failed(entry, ApiException.SERVER, at(1))!!
        val text = TagListRules.toJson("1:0|new|a", newKind, failed)
        assertEquals(failed, TagListRules.fromJson(text, "1:0|new|a"))
        assertNull("another list's file", TagListRules.fromJson(text, "1:0|new|b"))
        assertNull(TagListRules.fromJson("{}", "1:0|new|a"))
        val deep = TagListRules.fold(entry, newKind, 2, qs(70, 41), at(2))
        assertEquals(deep, TagListRules.fromJson(TagListRules.toJson("d", newKind, deep), "d"))
        val long = TagListRules.fold(entry, newKind, 2, qs(70, 41), at(2))
            .let { TagListRules.fold(it, newKind, 3, qs(40, 11), at(3)) }
            .let { TagListRules.fold(it, newKind, 4, qs(10, 1) + qs(-1, -20), at(4)) }
            .let { TagListRules.fold(it, newKind, 5, qs(-21, -50), at(5)) }
            .let { TagListRules.fold(it, newKind, 6, qs(-51, -80), at(6)) }
        val stored = TagListRules.fromJson(TagListRules.toJson("k", newKind, long), "k")!!
        assertEquals(TagListRules.KEPT_ITEMS, stored.items.size)
        assertFalse(stored.ended)
        assertEquals(6, TagListRules.nextPage(stored, newKind))
    }

    @Test
    fun cache_yourPinStaysAfterARestart() {
        val question = q(5, "A", pins = 3)
        val pinned = TagListRules.withPin(question, true)
        assertTrue(pinned.userPinned)
        assertEquals(4, pinned.pinCount)
        val reread = Parsers.question(Json.parseToJsonElement(pinned.raw!!))!!
        assertTrue(reread.userPinned)
        assertEquals(4, reread.pinCount)
        assertEquals(question.copy(raw = null), TagListRules.withPin(pinned, false).copy(raw = null))
    }

    @Test
    fun cache_theLeastUsedGoFirst() {
        val files = listOf(TagListRules.Kept("a", 10, 1), TagListRules.Kept("b", 10, 3), TagListRules.Kept("c", 10, 2))
        assertEquals(listOf("a", "c"), TagListRules.evict(files, maxBytes = 15, maxFiles = 10))
        assertEquals(listOf("a"), TagListRules.evict(files, maxBytes = 100, maxFiles = 2))
        assertTrue(TagListRules.evict(files, maxBytes = 100, maxFiles = 10).isEmpty())
    }

    @Test
    fun cache_whatsOnScreenNeverGoesAway() {
        val shown = TagListRules.fold(null, newKind, 1, qs(100, 71), at(0)).let { TagListRules.fold(it, newKind, 2, qs(70, 41), at(1)) }
        val startedOver = TagListRules.fold(shown, newKind, 1, qs(200, 171), at(2))
        val keep = TagListRules.keepShowing(shown, startedOver, newKind)
        assertEquals(90, keep.items.size)
        assertEquals(200L, keep.items.first().id)
        val grown = TagListRules.fold(shown, newKind, 3, qs(40, 11), at(3))
        assertEquals(grown, TagListRules.keepShowing(shown, grown, newKind))
        val hot = TagListRules.Kind.HotWeek
        val hotShown = TagListRules.fold(TagListRules.fold(null, hot, 1, qs(100, 71), at(0)), hot, 2, qs(70, 41), at(1))
        val hotShort = TagListRules.fold(hotShown, hot, 1, qs(10, 1), at(2))
        assertEquals(hotShown, TagListRules.keepShowing(hotShown, hotShort, hot))
    }

    // --- A list being read keeps still ---

    private fun feed(order: FeedOrder, vararg tags: String) = TagFeed(id = "f", name = "Feed", tags = tags.toList(), order = order)

    private fun stream(tag: String, vararg pages: List<Question>, ended: Boolean = false) = TagFeedRules.Stream(tag, pages.toList(), ended)

    @Test
    fun feeds_keepStillWhileRead() {
        val a = stream("A", listOf(q(100, "A"), q(90, "A"), q(80, "A")))
        val late = stream("L", listOf(q(99, "L"), q(95, "L")))
        val shown = TagFeedRules.merge(feed(FeedOrder.New, "A", "L"), listOf(a, stream("L", ended = true)))
        assertEquals(listOf(100L, 90L, 80L), shown.items.map { it.id })
        // A tag whose list came late is newer at its bottom (95): laid out anew, the list would end at 95.
        val fresh = TagFeedRules.merge(feed(FeedOrder.New, "A", "L"), listOf(a, late))
        assertEquals(listOf(100L, 99L, 95L), fresh.items.map { it.id })
        val held = TagFeedRules.merge(feed(FeedOrder.New, "A", "L"), listOf(a, late), TagFeedRules.Held(floor = 80L, base = mapOf("A" to 100L)))
        assertEquals("nothing on screen goes away", listOf(100L, 99L, 95L, 90L, 80L), held.items.map { it.id })
        assertEquals(listOf("L"), held.more)
        // Mixed: what came on top, and tags that came late, wait for the top.
        val mixedA = stream("A", listOf(q(120, "A"), q(100, "A"), q(90, "A")))
        val b = stream("B", listOf(q(98, "B"), q(97, "B")))
        val mixed = TagFeedRules.merge(
            feed(FeedOrder.Mixed, "A", "B", "L"),
            listOf(mixedA, b, late),
            TagFeedRules.Held(floor = null, base = mapOf("A" to 100L, "B" to 98L)),
        )
        assertEquals(listOf(100L, 98L, 90L, 97L), mixed.items.map { it.id })
        val hot = TagFeedRules.merge(feed(FeedOrder.Hot, "A", "L"), listOf(a, late), TagFeedRules.Held(floor = null, base = mapOf("A" to 100L)))
        assertEquals(listOf(100L, 90L, 80L), hot.items.map { it.id })
    }

    @Test
    fun feeds_newAndMixedReadTheSameLists() {
        assertEquals(TagListRules.Kind.New, TagListRules.kindOf(FeedOrder.New, HotWindow.Week))
        assertEquals(TagListRules.Kind.New, TagListRules.kindOf(FeedOrder.Mixed, HotWindow.Month))
        assertEquals(TagListRules.Kind.HotWeek, TagListRules.kindOf(FeedOrder.Hot, HotWindow.Week))
        assertEquals(TagListRules.Kind.HotMonth, TagListRules.kindOf(FeedOrder.Hot, HotWindow.Month))
        val feeds = listOf(
            TagFeed("1", "One", tags = listOf("A", "B")),
            TagFeed("2", "Two", tags = listOf("b ", "C"), order = FeedOrder.Mixed),
            TagFeed("3", "Three", tags = listOf("A"), order = FeedOrder.Hot),
        )
        assertEquals(
            "each list once, in your feeds' order",
            listOf("A" to TagListRules.Kind.New, "B" to TagListRules.Kind.New, "C" to TagListRules.Kind.New, "A" to TagListRules.Kind.HotWeek),
            TagListRules.warmKeys(feeds),
        )
    }

    @Test
    fun warmup_onlyWithBudgetToSpare() {
        val now = 1_000_000L
        RateGuard.reset(now)
        try {
            assertTrue(RateGuard.allowWarmup(now))
            repeat(11) { RateGuard.acquire(now) }
            assertFalse("preloading stops first", RateGuard.allowWarmup(now))
            assertTrue("the automatic checks still run", RateGuard.allowBackground(now))
        } finally {
            RateGuard.reset(now)
        }
    }

    // --- The emoji picker (D-170) ---

    @Test
    fun emoji_oneWholeEmoji() {
        assertEquals("👨‍👩‍👧‍👦", TagFeedRules.cleanEmoji("👨‍👩‍👧‍👦x"))
        assertEquals("🇮🇱", TagFeedRules.cleanEmoji("🇮🇱🇺🇸"))
        assertEquals("1️⃣", TagFeedRules.cleanEmoji("1️⃣2"))
        assertEquals("👍🏽", TagFeedRules.cleanEmoji(" 👍🏽! "))
        assertEquals("", TagFeedRules.cleanEmoji("  "))
    }

    @Test
    fun emoji_everyOneThePickerOffers() {
        val groups = source("assets/${EmojiCatalog.ASSET}").useLines { EmojiCatalog.parse(it) }
        assertEquals("Unicode's groups, in order", EmojiCatalog.GROUPS, groups.map { it.key })
        val all = groups.flatMap { it.emoji }
        assertTrue("about 1,900 emoji (${all.size})", all.size > 1500)
        assertEquals("each once", all.size, all.toSet().size)
        assertTrue("no skin tones", all.none { e -> e.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
        all.forEach { assertEquals("$it stays whole", it, TagFeedRules.cleanEmoji(it)) }
        assertEquals(listOf(EmojiCatalog.Group("people", listOf("🧑", "🧑‍💻"))), EmojiCatalog.parse(sequenceOf("# note", "", "people\t🧑 🧑‍💻", "unknown\t😀")))
        assertEquals(listOf("🎮", "❤️", "🪖"), EmojiCatalog.recent("🎮", listOf("❤️", "🎮", "🪖")))
        assertEquals(EmojiCatalog.MAX_RECENT, EmojiCatalog.recent("x", (1..40).map { "e$it" }).size)
    }

    // --- Live in feeds is gone (D-169), and the new words in both languages ---

    @Test
    fun strings_liveInFeedsGoneAndNewWordsInBoth() {
        fun strings(folder: String): Map<String, String> {
            val list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source("res/$folder/strings.xml"))
                .documentElement.getElementsByTagName("string")
            return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        }
        val en = strings("values")
        val iw = strings("values-iw")
        for (gone in listOf("settings_feeds_live", "settings_feeds_live_summary", "feed_no_emoji")) {
            assertFalse(gone, gone in en || gone in iw)
        }
        assertFalse(source("java/io/github/eyalshimshi/betterstips/data/SettingsStore.kt").readText().contains("feedsLive"))
        val keys = listOf("feed_unreadable", "feed_emoji_add", "feed_emoji_change", "emoji_pick_title", "emoji_none", "emoji_group_recent") +
            EmojiCatalog.GROUPS.map { "emoji_group_$it" }
        for (key in keys) {
            assertNotNull("$key in English", en[key]?.takeIf { it.isNotBlank() })
            assertNotNull("$key in Hebrew", iw[key]?.takeIf { it.isNotBlank() })
        }
    }
}
