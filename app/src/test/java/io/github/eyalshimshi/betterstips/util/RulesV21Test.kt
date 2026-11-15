package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.AnswerSwipe
import io.github.eyalshimshi.betterstips.data.model.Author
import io.github.eyalshimshi.betterstips.data.model.Conversation
import io.github.eyalshimshi.betterstips.ui.chats.ChatRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Rules added in v0.21: the app's language, first in the intro (#32); pinned chats (#33); no "delete" swipe on your own
 * answers (#34); what isn't there, named (#36).
 */
class RulesV21Test {

    private fun file(path: String): File =
        listOf(File(path), File("app/$path"), File("../$path"), File(path.removePrefix("app/"))).first { it.exists() }

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file("src/main/res/$folder/strings.xml")).documentElement
        val list = root.getElementsByTagName("string")
        return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    // --- The app's language (#32) ---

    @Test
    fun language_tagsAndChoices() {
        assertEquals(AppLanguage.Choice.Hebrew, AppLanguage.choiceOf("he"))
        assertEquals("Android's old code for Hebrew", AppLanguage.Choice.Hebrew, AppLanguage.choiceOf("iw"))
        assertEquals(AppLanguage.Choice.Hebrew, AppLanguage.choiceOf("he-IL"))
        assertEquals(AppLanguage.Choice.Hebrew, AppLanguage.choiceOf("iw_IL"))
        assertEquals(AppLanguage.Choice.English, AppLanguage.choiceOf("en-US"))
        assertEquals(AppLanguage.Choice.English, AppLanguage.choiceOf("EN"))
        assertEquals(AppLanguage.Choice.System, AppLanguage.choiceOf(null))
        assertEquals(AppLanguage.Choice.System, AppLanguage.choiceOf(""))
        assertEquals("a language Stips+ doesn't speak is the phone's", AppLanguage.Choice.System, AppLanguage.choiceOf("fr"))
        assertNull(AppLanguage.Choice.System.tag)
        assertEquals("he", AppLanguage.Choice.Hebrew.tag)
        assertEquals("en", AppLanguage.Choice.English.tag)
        assertEquals("עברית", AppLanguage.Choice.Hebrew.nativeName)
        assertEquals("English", AppLanguage.Choice.English.nativeName)
        // Each choice's own tag reads back as itself.
        for (choice in AppLanguage.Choice.entries) assertEquals(choice, AppLanguage.choiceOf(choice.tag))
    }

    @Test
    fun language_androidOffersTheSameTwo() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
        val config = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file("src/main/res/xml/locales_config.xml")).documentElement
        val locales = config.getElementsByTagName("locale")
        val tags = (0 until locales.length).map { (locales.item(it) as Element).getAttribute("android:name") }.toSet()
        assertEquals(setOf("en", "he"), tags)
        assertEquals(tags, AppLanguage.Choice.entries.mapNotNull { it.tag }.toSet())
    }

    @Test
    fun language_isTheIntrosFirstScreenOnTheLaunchTest() {
        val title = strings("values")["intro_language_title"]
        assertEquals("Choose a language", title)
        // The launch test checks a fresh install opens on it, by these words.
        assertTrue(file("tools/launch_test.sh").readText().contains("expect_text fresh-install \"$title\""))
        // Each language's title shows in the other too, so a reader of either finds the way.
        val en = strings("values")
        val iw = strings("values-iw")
        assertEquals(iw["intro_language_title"], en["intro_language_other"])
        assertEquals(en["intro_language_title"], iw["intro_language_other"])
    }

    // --- Pinned chats (#33) ---

    private val t0: Instant = Instant.parse("2026-11-13T20:00:00Z")

    private fun chat(id: Long, last: String = "m$id", unread: Int = 0) =
        Conversation(Author("user$id", id, null, anonymous = false, online = false), last, t0.minusSeconds(id * 60), null, unread, false)

    @Test
    fun pins_atMostFiveTheLatestOnTop() {
        assertEquals(5, ChatRules.MAX_PINNED)
        var pinned = emptyList<Conversation>()
        for (id in 1L..5L) pinned = ChatRules.pin(pinned, chat(id))!!
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), pinned.map { it.other.userId })
        assertNull("a sixth is refused", ChatRules.pin(pinned, chat(6)))
        assertSame("pinning a pinned chat changes nothing", pinned, ChatRules.pin(pinned, chat(3)))
        val fewer = ChatRules.unpin(pinned, 3)
        assertEquals(listOf(5L, 4L, 2L, 1L), fewer.map { it.other.userId })
        assertEquals("then there's room", listOf(6L, 5L, 4L, 2L, 1L), ChatRules.pin(fewer, chat(6))!!.map { it.other.userId })
        assertEquals(fewer, ChatRules.unpin(fewer, 99))
    }

    @Test
    fun pins_onTopOfTheList() {
        val list = listOf(chat(1), chat(2), chat(3), chat(4))
        assertSame("nothing pinned, nothing moves", list, ChatRules.withPinned(list, emptyList()))
        // Pinned 3 then 9: 9 is the latest pin, and isn't on the pages loaded so far.
        val pinned = listOf(chat(9, last = "old"), chat(3, last = "old"))
        val shown = ChatRules.withPinned(list, pinned)
        assertEquals(listOf(9L, 3L, 1L, 2L, 4L), shown.map { it.other.userId })
        assertEquals("a loaded chat shows as the list has it", "m3", shown[1].lastMessage)
        assertEquals("one not loaded yet shows as it last looked", "old", shown[0].lastMessage)
        assertEquals("each chat once", shown.size, shown.map { it.other.userId }.toSet().size)
        val fresh = ChatRules.freshPins(pinned, listOf(chat(3, last = "new", unread = 2)))
        assertEquals(listOf("old", "new"), fresh.map { it.lastMessage })
        assertEquals(2, fresh[1].unread)
    }

    // --- No "delete" swipe on your own answers (#34) ---

    @Test
    fun mySwipe_noDelete() {
        assertFalse("Stips lets you detach your answer, not delete it", AnswerSwipe.entries.any { it.name == "Delete" })
        assertEquals(listOf(AnswerSwipe.None, AnswerSwipe.Edit, AnswerSwipe.Detach, AnswerSwipe.History), SwipeRules.MY_ANSWER_CHOICES)
    }

    // --- What isn't there, named (#36) ---

    @Test
    fun notFound_namesWhatsMissing() {
        for (folder in listOf("values", "values-iw")) {
            val s = strings(folder)
            for (key in listOf("error_not_found", "error_not_found_question", "error_not_found_user", "error_not_found_answer")) {
                assertNotNull("$folder: $key", s[key])
            }
            assertEquals("$folder: four different texts", 4, setOf(s["error_not_found"], s["error_not_found_question"], s["error_not_found_user"], s["error_not_found_answer"]).size)
        }
        val en = strings("values")
        assertFalse("a missing user or page isn't a question", en.getValue("error_not_found").contains("question"))
        assertTrue(en.getValue("error_not_found_user").contains("user"))
        assertTrue(strings("values-iw").getValue("error_not_found_user").contains("משתמש"))
    }
}
