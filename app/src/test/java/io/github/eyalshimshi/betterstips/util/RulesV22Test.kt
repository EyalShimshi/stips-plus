package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.model.Author
import io.github.eyalshimshi.betterstips.data.model.FeedQuery
import io.github.eyalshimshi.betterstips.data.model.FeedSort
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.widget.HotWidgetFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/** Rules added in v0.22: Home's "Unanswered" category (#37), New's questions that no one answered yet. */
class RulesV22Test {

    private val unanswered = FeedQuery(unanswered = true)
    private val new = FeedQuery()

    private fun question(id: Long, answers: Int) = Question(
        id = id,
        title = "Question $id",
        body = "",
        topics = emptyList(),
        postedAt = Instant.parse("2026-11-15T12:00:00Z"),
        answerCount = answers,
        pinCount = 0,
        channelId = 0,
        author = Author("asker", 1, null, anonymous = false, online = false),
        cover = null,
    )

    @Test
    fun unanswered_keepsOnlyQuestionsWithoutAnswers() {
        val page = listOf(question(1, 0), question(2, 3), question(3, 0), question(4, 1))
        assertEquals(listOf(1L, 3L), page.filter { FeedRules.shows(unanswered, it) }.map { it.id })
        assertEquals("New keeps them all", page, page.filter { FeedRules.shows(new, it) })
        assertTrue(FeedRules.shows(FeedQuery(sort = FeedSort.Hot), question(5, 9)))
    }

    @Test
    fun unanswered_looksAFewPagesFurtherWhenOneHasNone() {
        val most = FeedRules.UNANSWERED_PAGES_PER_LOAD
        assertTrue(most in 2..6)
        assertTrue("nothing to add yet: the next page", FeedRules.keepLooking(unanswered, found = 0, lastPageEmpty = false, pagesLooked = 1))
        assertFalse("found one: enough for this load", FeedRules.keepLooking(unanswered, found = 1, lastPageEmpty = false, pagesLooked = 1))
        assertFalse("Stips has no more pages", FeedRules.keepLooking(unanswered, found = 0, lastPageEmpty = true, pagesLooked = 1))
        assertFalse("looked far enough", FeedRules.keepLooking(unanswered, found = 0, lastPageEmpty = false, pagesLooked = most))
        assertTrue(FeedRules.keepLooking(unanswered, found = 0, lastPageEmpty = false, pagesLooked = most - 1))
        assertFalse("other lists read one page at a time", FeedRules.keepLooking(new, found = 0, lastPageEmpty = false, pagesLooked = 1))
    }

    @Test
    fun unanswered_endsWhenItFindsNoMore() {
        assertTrue(FeedRules.ended(new, found = 0, lastPageEmpty = true))
        assertFalse("a page of questions already shown isn't the end of New", FeedRules.ended(new, found = 0, lastPageEmpty = false))
        assertTrue("its pages looked through, none unanswered", FeedRules.ended(unanswered, found = 0, lastPageEmpty = false))
        assertFalse(FeedRules.ended(unanswered, found = 2, lastPageEmpty = false))
        assertTrue(FeedRules.ended(unanswered, found = 2, lastPageEmpty = true))
    }

    @Test
    fun unanswered_isAListOfItsOwn() {
        assertEquals("New's cached first page keeps its key", "New||||", new.cacheKey)
        assertNotEquals(new.cacheKey, unanswered.cacheKey)
        assertNotEquals(new, unanswered)
        assertNotEquals("the hot widget never takes it for its list", HotWidgetFeed.QUERY, unanswered)
        assertEquals(unanswered, unanswered.normalized())
    }

    @Test
    fun unanswered_namedInBothLanguages() {
        fun strings(folder: String): Map<String, String> {
            val file = listOf(File("src/main/res/$folder/strings.xml"), File("app/src/main/res/$folder/strings.xml")).first { it.exists() }
            val list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
            return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        }
        val en = strings("values")
        val iw = strings("values-iw")
        assertEquals("Unanswered", en["channel_unanswered"])
        assertEquals("ללא תשובות", iw["channel_unanswered"])
        for (key in listOf("unanswered_questions", "empty_unanswered")) {
            assertTrue("$key in English", !en[key].isNullOrBlank())
            assertTrue("$key in Hebrew", !iw[key].isNullOrBlank())
        }
    }
}
