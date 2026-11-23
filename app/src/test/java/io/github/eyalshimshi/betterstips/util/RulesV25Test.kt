package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.SpecialAccounts
import io.github.eyalshimshi.betterstips.data.ChatLock
import io.github.eyalshimshi.betterstips.data.TokenVault
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.25: the privacy screen and the chats lock (D-175, D-176), the sealed token (D-177), Fallen's thanks (D-179), how
 * Stips+ treats your account and how to check an APK (D-180, D-181), the honest status line (D-182), feed cards and
 * previews (D-183), and Vocaroo voice notes (D-184).
 */
class RulesV25Test {
    private fun repoFile(path: String): File = listOf(File("../$path"), File(path)).first { it.exists() }

    private fun source(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    // --- Vocaroo voice notes (D-184) ---

    @Test
    fun vocaroo_recordingLinks() {
        assertEquals("1lVEipfjSTbW", Vocaroo.recording("https://voca.ro/1lVEipfjSTbW")?.id)
        assertEquals("1lVEipfjSTbW", Vocaroo.recording("https://vocaroo.com/1lVEipfjSTbW")?.id)
        assertEquals("1lVEipfjSTbW", Vocaroo.recording("http://www.vocaroo.com/1lVEipfjSTbW/")?.id)
        assertEquals("old links", "s0Hz6qyfR7aX", Vocaroo.recording("https://vocaroo.com/i/s0Hz6qyfR7aX")?.id)
        assertEquals("1lVEipfjSTbW", Vocaroo.recording("https://vocaroo.com/embed/1lVEipfjSTbW?autoplay=0")?.id)
        assertEquals("the link as written", "https://voca.ro/1lVEipfjSTbW", Vocaroo.recording("  https://voca.ro/1lVEipfjSTbW ")?.link)
        assertNull("Vocaroo's own pages", Vocaroo.recording("https://vocaroo.com/upload"))
        assertNull(Vocaroo.recording("https://voca.ro/abc"))
        assertNull("another site", Vocaroo.recording("https://voca.ro.example.com/1lVEipfjSTbW"))
        assertNull(Vocaroo.recording("https://vocaroo.com/1lVEipfjSTbW/more"))
        assertNull(Vocaroo.recording(null))
    }

    @Test
    fun vocaroo_theServerItsOwnPagePicks() {
        // The example from the owner's ask (question 19778371): 12 letters starting with "1".
        assertEquals(
            listOf("https://media1.vocaroo.com/mp3/1lVEipfjSTbW", "https://media.vocaroo.com/mp3/1lVEipfjSTbW"),
            Vocaroo.mediaUrls("1lVEipfjSTbW"),
        )
        assertEquals("https://media.vocaroo.com/mp3/abcdefghijk", Vocaroo.mediaUrls("abcdefghijk").first())
        assertEquals("https://media1.vocaroo.com/mp3/abcdefghij", Vocaroo.mediaUrls("abcdefghij").first())
        assertEquals("12, not starting with 1", "https://media.vocaroo.com/mp3/s0Hz6qyfR7aX", Vocaroo.mediaUrls("s0Hz6qyfR7aX").first())
        assertEquals("its media server answers only its pages", "https://vocaroo.com/", Vocaroo.REFERER)
    }

    @Test
    fun vocaroo_rowsUnderTheText() {
        val text = "תקשיבו: https://voca.ro/1lVEipfjSTbW וגם https://vocaroo.com/1lVEipfjSTbW, https://voca.ro/1aaaaaaaaaaa"
        assertEquals("each recording once", listOf("1lVEipfjSTbW", "1aaaaaaaaaaa"), Vocaroo.find(text).map { it.id })
        assertFalse(Vocaroo.onlyRecordings(text))
        assertTrue("only links: the rows stand for the text", Vocaroo.onlyRecordings(" https://voca.ro/1lVEipfjSTbW\nhttps://voca.ro/1aaaaaaaaaaa "))
        assertFalse(Vocaroo.onlyRecordings("no links here"))
        assertFalse("other links aren't recordings", Vocaroo.onlyRecordings("https://stips.co.il/ask/1"))
        val many = (1..5).joinToString(" ") { "https://voca.ro/1aaaaaaaaa${it}b" }
        assertEquals(Vocaroo.MAX_PER_TEXT, Vocaroo.find(many).size)
        assertFalse("more recordings than rows: the text stays, so none goes missing", Vocaroo.onlyRecordings(many))
        assertTrue("one recording linked again is still one row", Vocaroo.onlyRecordings("https://voca.ro/1lVEipfjSTbW ".repeat(4)))
        assertEquals("0:00", Vocaroo.clock(0))
        assertEquals("1:05", Vocaroo.clock(65_000))
        assertEquals("1:02:03", Vocaroo.clock(3_723_000))
    }

    @Test
    fun vocaroo_playsInTheApp() {
        val player = source("java/io/github/eyalshimshi/betterstips/data/VoicePlayer.kt").readText()
        assertTrue("streams with Vocaroo's Referer", player.contains("\"Referer\" to Vocaroo.REFERER"))
        val root = source("java/io/github/eyalshimshi/betterstips/ui/AppRoot.kt").readText()
        assertTrue("a tap on the link plays it in its row", root.contains("voicePlayer.toggleLink"))
        val attached = source("java/io/github/eyalshimshi/betterstips/ui/components/AttachedLink.kt").readText()
        assertTrue("an attached recording is its row", attached.contains("VoiceNoteRow("))
    }

    // --- Shared feeds as cards and previews (D-183) ---

    @Test
    fun feedLinks_cardsAndPreviews() {
        val feed = TagFeed(id = "x", name = "צבא", emoji = "🪖", tags = listOf("צבא", "צו ראשון"), order = FeedOrder.Hot)
        val link = TagFeedRules.shareLink(feed)
        val target = ChatLinks.target(link)
        assertTrue(target is ChatLinks.Target.Feed)
        val card = (target as ChatLinks.Target.Feed).feed
        assertEquals("צבא", card.name)
        assertEquals("🪖", card.emoji)
        assertEquals(listOf("צבא", "צו ראשון"), card.tags)
        assertEquals(FeedOrder.Hot, card.order)
        // On a line of its own, the card takes its place, as for questions.
        val read = ChatLinks.read("תראה את הפיד הזה\n$link")
        assertTrue(read.target is ChatLinks.Target.Feed)
        assertEquals("תראה את הפיד הזה", read.body)
        assertTrue(ChatLinks.target("https://stips.co.il/ask/5") is ChatLinks.Target.Question)
        assertNull("the site's page isn't a feed", ChatLinks.target("https://www.shimshii.com/stips-plus/"))
        assertTrue("any feed link opens its own screen", Links.deepLink(link) is DeepLink.Feed)
    }

    @Test
    fun feedLinks_oneYouHaveIsYours() {
        val mine = TagFeed(id = "a", name = "A", tags = listOf("צבא", "Gius"), excluded = listOf("x"))
        val shared = mine.copy(id = "b", name = "B", emoji = "🪖", tags = listOf(" gius ", "צבא"), order = FeedOrder.Mixed)
        assertTrue("names, emoji and order aside", TagFeedRules.sameLists(mine, shared))
        assertFalse(TagFeedRules.sameLists(mine, shared.copy(unansweredOnly = true)))
        assertFalse(TagFeedRules.sameLists(mine, shared.copy(tags = listOf("צבא"))))
        assertFalse(TagFeedRules.sameLists(mine, shared.copy(excluded = emptyList())))
    }

    // --- The privacy screen and the chats lock (D-175, D-176) ---

    @Test
    fun privacyScreen_theWindow() {
        // Android 13 and later: the two switches apart.
        assertEquals(PrivacyRules.Window(secure = false, recentsPreview = true), PrivacyRules.window(34, false, false, false))
        assertEquals(PrivacyRules.Window(secure = false, recentsPreview = false), PrivacyRules.window(34, true, false, false))
        assertEquals(PrivacyRules.Window(secure = true, recentsPreview = true), PrivacyRules.window(34, false, true, false))
        assertEquals("locked chats stay out of recent apps", PrivacyRules.Window(false, false), PrivacyRules.window(33, false, false, true))
        // Before 13 only the secure flag hides the card, and it blocks screenshots too.
        assertEquals(PrivacyRules.Window(secure = true, recentsPreview = true), PrivacyRules.window(31, true, false, false))
        assertEquals("not for the chats lock alone", PrivacyRules.Window(false, true), PrivacyRules.window(31, false, false, true))
    }

    @Test
    fun chatsLock_locksAgainAfterTheMinutesAway() {
        val left = 1_000_000L
        assertFalse("not left since unlocking", PrivacyRules.lockAgain(0L, left, 1))
        assertTrue("at once", PrivacyRules.lockAgain(left, left, 0))
        assertFalse(PrivacyRules.lockAgain(left, left + 59_999, 1))
        assertTrue(PrivacyRules.lockAgain(left, left + 60_000, 1))
        assertTrue("a clock that went back", PrivacyRules.lockAgain(left, left - 1, 30))
        assertEquals(listOf(0, 1, 5, 30), PrivacyRules.LOCK_MINUTES)

        val lock = ChatLock()
        assertFalse("every start of the app finds chats locked", lock.unlocked.value)
        lock.unlock()
        lock.left(left)
        lock.cameBack(left + 30_000, 1)
        assertTrue("back within the minute", lock.unlocked.value)
        lock.left(left + 40_000)
        lock.cameBack(left + 100_000, 1)
        assertFalse(lock.unlocked.value)
        lock.unlock()
        lock.cameBack(left + 999_999, 0)
        assertTrue("unlocked while away counts from the next leaving", lock.unlocked.value)
    }

    @Test
    fun chatsLock_coversEveryChatScreen() {
        val manifest = source("AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.USE_BIOMETRIC"))
        val root = source("java/io/github/eyalshimshi/betterstips/ui/AppRoot.kt").readText()
        assertEquals("the Chats tab, every chat and sending to chats", 3, Regex("ChatLockGate\\(onBack").findAll(root).count())
    }

    // --- The sealed token (D-177) ---

    @Test
    fun token_sealed() {
        val sealed = SealedText.join(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7, 6))
        assertTrue(SealedText.isSealed(sealed))
        val parts = SealedText.split(sealed)
        assertNotNull(parts)
        assertArrayEquals(byteArrayOf(1, 2, 3), parts!!.first)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), parts.second)
        assertFalse("a token kept before v0.25", SealedText.isSealed("abc123"))
        assertNull(SealedText.split("abc123"))
        assertNull("damaged", SealedText.split(SealedText.PREFIX + "%%%:abc"))
        assertNull(SealedText.split(SealedText.PREFIX + "onlyone"))
        val session = source("java/io/github/eyalshimshi/betterstips/data/SessionStore.kt").readText()
        assertTrue("saved sealed", session.contains("TokenVault.seal"))
        assertFalse("never the token as it came", session.contains(".putString(\"token\", session.token)"))
    }

    @Test
    fun token_signsOutOnlyWhenLost() {
        assertEquals("damaged: it can never open", TokenVault.Opened.Lost, TokenVault.open(SealedText.PREFIX + "onlyone"))
        // The JVM has no Android keystore, as on a phone whose keystore doesn't answer: kept for the next start.
        assertEquals(TokenVault.Opened.Unavailable, TokenVault.open(SealedText.join(ByteArray(12), byteArrayOf(1, 2, 3))))
    }

    // --- Thanks, docs and the status line (D-179 to D-182) ---

    @Test
    fun thanks_fallen() {
        val entry = SpecialAccounts.badge(SpecialAccounts.FALLEN_ID)
        assertNotNull(entry)
        assertTrue("on his profile only", entry!!.profileOnly)
        assertNull(SpecialAccounts.contentBadge(SpecialAccounts.FALLEN_ID))
        // Only his profile shows it: every other screen asks for contentBadge, which leaves it out (D-185).
        val code = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val fullBadge = code.walk().filter { it.extension == "kt" && it.readText().contains("SpecialAccounts.badge(") }.map { it.name }.toList()
        assertEquals("only the profile asks for a profile-only badge", listOf("UserProfileScreen.kt"), fullBadge)
        assertTrue("the + stays after Stips in Hebrew", entry.badgeHe!!.endsWith("\u200E"))
        assertTrue(SpecialAccounts.THANKS.any { it.userId == 457393L })
    }

    @Test
    fun docs_sayHowToCheck() {
        val security = repoFile("SECURITY.md").readText()
        assertTrue(security.contains("https://stips.co.il/api"))
        assertTrue(security.contains("gh attestation verify"))
        val build = repoFile(".github/workflows/build.yml").readText()
        assertTrue("every APK gets its provenance", build.contains("actions/attest-build-provenance@"))
        assertTrue(build.contains("id-token: write"))
        assertTrue(build.contains("attestations: write"))
        val readme = repoFile("README.md").readText()
        assertFalse("the status line says what's true", readme.contains("isn't published anywhere yet"))
        assertTrue("the signing certificate, to compare", readme.contains("c0118f1a84a763a7bc13ae6f9c89803c49978745d74bb931b0bb9e16521e352c"))
        assertTrue(readme.contains("SECURITY.md"))
    }
}
