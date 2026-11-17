package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.model.Author
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Rules added in v0.23: test builds, releases and announcements (#41), download counts (#42), the profile photo
 * (#46), pictures (#43), and your feeds (#40).
 */
class RulesV23Test {

    private fun repoFile(path: String): File = listOf(File("../$path"), File(path)).first { it.exists() }

    private fun source(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    // --- Test builds and releases (#41), download counts (#42) ---

    private fun release(tag: String, build: Int, downloads: Int = 0, name: String = "Stips+ 0.23.$build") =
        """{"tag_name":"$tag","name":"$name","draft":false,"html_url":"https://github.com/EyalShimshi/stips-plus/releases/tag/$tag",""" +
            """"assets":[{"name":"stipsplus-$build.apk","size":4000000,"download_count":$downloads,""" +
            """"browser_download_url":"https://github.com/EyalShimshi/stips-plus/releases/download/$tag/stipsplus-$build.apk"}]}"""

    @Test
    fun testBuilds_reachOnlyAppsThatAskForThem() {
        val json = "[" + listOf(release("dev-40", 40, name = "Stips+ 0.23.40 (test)"), release("build-38", 38), release("dev-38", 38)).joinToString(",") + "]"
        val forEveryone = UpdateRules.newest(json)!!
        assertEquals("apps without Test builds see only releases", 38, forEveryone.build)
        assertFalse(forEveryone.test)
        val forTesters = UpdateRules.newest(json, includeTests = true)!!
        assertEquals(40, forTesters.build)
        assertTrue(forTesters.test)
        assertEquals("the app names it a test build itself", "0.23.40", forTesters.versionName)
        val released = "[" + release("dev-41", 41) + "," + release("build-41", 41) + "]"
        assertFalse("a build released to everyone isn't a test any more", UpdateRules.newest(released, includeTests = true)!!.test)
        assertEquals(12, UpdateRules.testBuildNumber("dev-12"))
        assertNull("0.18–0.22 read only build-N, so they never see a test build", UpdateRules.buildNumber("dev-12"))
        assertNull(UpdateRules.testBuildNumber("build-12"))
    }

    @Test
    fun downloads_countEveryApk() {
        val json = "[" + listOf(release("dev-40", 40, 5), release("build-38", 38, 17), release("build-37", 37, 9), release("v1", 1, 100)).joinToString(",") + "]"
        val counts = UpdateRules.downloads(json)!!
        assertEquals("only Stips+'s own builds count", 31, counts.total)
        assertEquals(38, counts.newestBuild)
        assertEquals(17, counts.newestCount)
        assertNull(UpdateRules.downloads("not json"))
    }

    @Test
    fun ci_publishesTestBuildsAndReleasesOnlyByHand() {
        val build = repoFile(".github/workflows/build.yml").readText()
        assertTrue(build.contains("tag_name: dev-\${{ github.run_number }}"))
        assertFalse("every push used to be offered to everyone", build.contains("tag_name: build-"))
        assertFalse("test builds don't wait for approval", build.contains("environment: release"))
        assertTrue("posting an announcement builds nothing", Regex("paths-ignore:\\s*\\n\\s*- announcements\\.json").containsMatchIn(build))
        assertTrue("old test builds go, so 0.18–0.22 (10 releases) still see the newest release", build.contains("gh release delete"))
        val release = repoFile(".github/workflows/release.yml").readText()
        assertTrue(release.contains("workflow_dispatch"))
        assertTrue(release.contains("gh release create \"build-\$BUILD\""))
        assertFalse("the release ships the file that was tested", release.contains("gradle"))
        // The file every app reads: valid JSON, with its list.
        val file = repoFile("announcements.json").readText()
        assertNotNull(Json.parseToJsonElement(file))
        assertTrue(file.contains("\"announcements\""))
    }

    // --- Announcements (#41) ---

    @Test
    fun announcements_readFromTheFile() {
        val text = """{"announcements":[
            {"id":"a","title":{"he":"שלום","en":"Hello"},"text":"Both","link":"https://example.com/x","linkText":{"en":"Go"}},
            {"id":"b","text":{"he":"רק עברית"},"link":"http://insecure.example/"},
            {"text":"no id"},
            {"id":"c"},
            {"id":"a","text":"the same id again"}
        ],"newField":true}"""
        val list = AnnouncementRules.parse(text)
        assertEquals(listOf("a", "b"), list.map { it.id })
        val a = list[0]
        assertEquals("שלום", a.title?.pick(hebrew = true))
        assertEquals("Hello", a.title?.pick(hebrew = false))
        assertEquals("a plain text is both languages", "Both", a.text.pick(hebrew = true))
        assertEquals("https://example.com/x", a.link)
        assertEquals("Go", a.linkText?.pick(hebrew = true))
        assertNull("https links only", list[1].link)
        assertEquals("the other language when one is missing", "רק עברית", list[1].text.pick(hebrew = false))
        assertTrue(AnnouncementRules.parse("not json").isEmpty())
        assertTrue(AnnouncementRules.parse(null).isEmpty())
        assertTrue(AnnouncementRules.parse(repoFile("announcements.json").readText()).isEmpty())
        // An empty list is a file read: announcements taken down reach every app. Only a file that isn't one fails.
        assertEquals(emptyList<AnnouncementRules.Announcement>(), AnnouncementRules.parseOrNull(repoFile("announcements.json").readText()))
        assertEquals(emptyList<AnnouncementRules.Announcement>(), AnnouncementRules.parseOrNull("""{"about":"x","announcements":[]}"""))
        assertNull(AnnouncementRules.parseOrNull("{}"))
        assertNull(AnnouncementRules.parseOrNull("<html>Rate limit</html>"))
    }

    private fun announcement(
        id: String,
        from: String? = null,
        until: String? = null,
        minBuild: Long? = null,
        maxBuild: Long? = null,
        audience: AnnouncementRules.Audience = AnnouncementRules.Audience.All,
    ) = AnnouncementRules.Announcement(
        id = id,
        title = null,
        text = AnnouncementRules.Text("טקסט", "Text"),
        link = null,
        linkText = null,
        from = from?.let(LocalDate::parse),
        until = until?.let(LocalDate::parse),
        minBuild = minBuild,
        maxBuild = maxBuild,
        audience = audience,
    )

    @Test
    fun announcements_reachTheAppsTheyreFor() {
        val day = LocalDate.parse("2026-11-25")
        val a = announcement("a", from = "2026-11-20", until = "2026-11-30", minBuild = 38, maxBuild = 50, audience = AnnouncementRules.Audience.Release)
        assertTrue(AnnouncementRules.appliesTo(a, build = 40, testBuilds = false, today = day))
        assertTrue("both days count", AnnouncementRules.appliesTo(a, 40, false, LocalDate.parse("2026-11-30")))
        assertFalse(AnnouncementRules.appliesTo(a, 40, false, LocalDate.parse("2026-11-19")))
        assertFalse(AnnouncementRules.appliesTo(a, 40, false, LocalDate.parse("2026-12-01")))
        assertFalse(AnnouncementRules.appliesTo(a, 37, false, day))
        assertFalse(AnnouncementRules.appliesTo(a, 51, false, day))
        assertFalse("for releases: not for apps taking test builds", AnnouncementRules.appliesTo(a, 40, true, day))
        val forTesters = announcement("t", audience = AnnouncementRules.Audience.Test)
        assertTrue(AnnouncementRules.appliesTo(forTesters, 40, true, day))
        assertFalse(AnnouncementRules.appliesTo(forTesters, 40, false, day))
    }

    @Test
    fun announcements_eachPopsUpOnce() {
        val day = LocalDate.parse("2026-11-25")
        val list = listOf(announcement("new"), announcement("old"))
        assertEquals("the newest first", "new", AnnouncementRules.toShow(list, emptySet(), 40, false, day)?.id)
        assertEquals("old", AnnouncementRules.toShow(list, setOf("new"), 40, false, day)?.id)
        assertNull(AnnouncementRules.toShow(list, setOf("new", "old"), 40, false, day))
        assertEquals("Settings shows the latest, seen or not", "new", AnnouncementRules.latest(list, 40, false, day)?.id)
        val now = 10L * 24 * 60 * 60 * 1000
        val hour = 60L * 60 * 1000
        assertTrue(AnnouncementRules.dueForCheck(0L, now))
        assertFalse(AnnouncementRules.dueForCheck(now - hour, now))
        assertTrue(AnnouncementRules.dueForCheck(now - 4 * hour, now))
        assertFalse("a while after a failed read", AnnouncementRules.dueForCheck(now - 4 * hour, now, lastFailureMs = now - 10 * 60 * 1000))
    }

    // --- The profile photo (#46) ---

    @Test
    fun photo_squareMeasuredOnStipsOwnCopy() {
        assertEquals(listOf(0, 300, 0, 300), PhotoRules.thumbnailBox(720, PhotoRules.Square(0, 0, 720)).toList())
        assertEquals(listOf(0, 300, 0, 300), PhotoRules.thumbnailBox(PhotoRules.UPLOAD_SIDE, PhotoRules.Square(0, 0, PhotoRules.UPLOAD_SIDE)).toList())
        assertEquals("a small photo isn't scaled up", listOf(0, 200, 0, 200), PhotoRules.thumbnailBox(200, PhotoRules.Square(0, 0, 200)).toList())
        assertEquals(listOf(75, 225, 0, 150), PhotoRules.thumbnailBox(1200, PhotoRules.Square(300, 0, 600)).toList())
    }

    @Test
    fun photo_theCircleStaysOnThePhoto() {
        assertEquals(0.8f, PhotoRules.coverScale(1000, 500, 400f), 0.0001f)
        assertEquals("centered", PhotoRules.Square(250, 0, 500), PhotoRules.cropSquare(1000, 500, 0.8f, 0f, 0f, 400f))
        assertEquals("dragged as far as it goes", PhotoRules.Square(500, 0, 500), PhotoRules.cropSquare(1000, 500, 0.8f, -200f, 0f, 400f))
        assertEquals(200f, PhotoRules.clampPan(1000f, 1000, 0.8f, 400f), 0.0001f)
        assertEquals("exactly as tall as the circle: no room to move", 0f, PhotoRules.clampPan(-50f, 500, 0.8f, 400f), 0.0001f)
        val zoomed = PhotoRules.cropSquare(1000, 500, 1.6f, 0f, 0f, 400f)
        assertEquals("twice the zoom, half the square", 250, zoomed.side)
    }

    // --- Pictures (#43) ---

    @Test
    fun images_knownByTheirFirstBytes() {
        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        assertEquals("image/jpeg", ImageFiles.mimeOf(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", ImageFiles.mimeOf(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A)))
        assertEquals("image/gif", ImageFiles.mimeOf("GIF89a".toByteArray()))
        assertEquals("image/webp", ImageFiles.mimeOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertNull(ImageFiles.mimeOf("<html>".toByteArray()))
        assertEquals("png", ImageFiles.extensionOf("image/png"))
        assertEquals("jpg", ImageFiles.extensionOf("image/jpeg"))
    }

    // --- Your feeds (#40) ---

    private fun q(id: Long, vararg tags: String, answers: Int = 0, pins: Int = 0) = Question(
        id = id,
        title = "Question $id",
        body = "",
        topics = tags.toList(),
        postedAt = Instant.parse("2026-11-17T12:00:00Z"),
        answerCount = answers,
        pinCount = pins,
        channelId = 0,
        author = Author("asker", 1, null, anonymous = false, online = false),
        cover = null,
    )

    private fun feed(order: FeedOrder, vararg tags: String, excluded: List<String> = emptyList(), unanswered: Boolean = false) =
        TagFeed(id = "f", name = "Feed", tags = tags.toList(), excluded = excluded, order = order, unansweredOnly = unanswered)

    private fun stream(tag: String, vararg pages: List<Question>, ended: Boolean = false) = TagFeedRules.Stream(tag, pages.toList(), ended)

    @Test
    fun feeds_tagsCleanedAndLimited() {
        assertEquals("זוגיות", TagFeedRules.cleanTag("  #זוגיות "))
        assertEquals("מערכת יחסים", TagFeedRules.cleanTag("מערכת   יחסים"))
        assertNull(TagFeedRules.cleanTag("  # "))
        assertNull(TagFeedRules.cleanTag("א".repeat(TagFeedRules.MAX_TAG + 1)))
        val many = TagFeedRules.cleanTags(listOf("a", "A", "b") + (1..20).map { "t$it" })
        assertEquals("15 tags at most (the owner's pick)", 15, TagFeedRules.MAX_TAGS)
        assertEquals(TagFeedRules.MAX_TAGS, many.size)
        assertEquals(listOf("a", "b"), many.take(2))
        assertEquals("never half an emoji", "❤️🪖🚗", TagFeedRules.cleanEmoji("❤️🪖🚗💰📚"))
        assertFalse(TagFeedRules.valid(feed(FeedOrder.New)))
        assertFalse(TagFeedRules.valid(feed(FeedOrder.New, "a").copy(name = " ")))
        assertTrue(TagFeedRules.valid(feed(FeedOrder.New, "a")))
    }

    @Test
    fun feeds_keptOnThePhone() {
        val feeds = listOf(
            TagFeed("1", "זוגיות", "❤️", listOf("זוגיות", "צה\"ל"), listOf("מין"), FeedOrder.Hot, HotWindow.Month, unansweredOnly = true),
            TagFeed("2", "Other", tags = listOf("x")),
        )
        assertEquals(feeds, TagFeedRules.fromJson(TagFeedRules.toJson(feeds)))
        assertTrue(TagFeedRules.fromJson("garbage").isEmpty())
        assertTrue(TagFeedRules.fromJson(null).isEmpty())
        val withBroken = """{"v":1,"feeds":[{"id":"1","name":"No tags","tags":[]},{"id":"2","name":"Fine","tags":["a"],"order":"Sideways"}]}"""
        val read = TagFeedRules.fromJson(withBroken)
        assertEquals("a feed without tags is dropped", listOf("2"), read.map { it.id })
        assertEquals("an unknown order reads as New", FeedOrder.New, read[0].order)
    }

    @Test
    fun feeds_newestFirstWithoutGaps() {
        val f = feed(FeedOrder.New, "A", "B")
        var a = stream("A", listOf(q(100, "A"), q(90, "A"), q(80, "A")))
        val b = stream("B", listOf(q(95, "B"), q(90, "A", "B"), q(50, "B")))
        var merge = TagFeedRules.merge(f, listOf(a, b))
        assertEquals("only as far back as every open tag reaches; once each", listOf(100L, 95L, 90L, 80L), merge.items.map { it.id })
        assertEquals("the tag holding it back is asked next", listOf("A"), merge.more)
        assertFalse(merge.ended)
        a = stream("A", listOf(q(100, "A"), q(90, "A"), q(80, "A")), listOf(q(70, "A"), q(60, "A")))
        merge = TagFeedRules.merge(f, listOf(a, b))
        assertEquals(listOf(100L, 95L, 90L, 80L, 70L, 60L), merge.items.map { it.id })
        merge = TagFeedRules.merge(f, listOf(a.copy(ended = true), b))
        assertEquals(50L, merge.items.last().id)
        assertEquals(listOf("B"), merge.more)
        assertTrue(TagFeedRules.merge(f, listOf(a.copy(ended = true), b.copy(ended = true))).ended)
        val waiting = TagFeedRules.merge(f, listOf(a, stream("B")))
        assertTrue("nothing shows before every tag answered", waiting.items.isEmpty())
        assertEquals(listOf("B"), waiting.more)
    }

    @Test
    fun feeds_aPageThatFailedNeverEndsTheFeed() {
        val f = feed(FeedOrder.New, "A", "B")
        val a = stream("A", listOf(q(100, "A"), q(80, "A")))
        val b = stream("B", listOf(q(95, "B"), q(50, "B")))
        // A's next page couldn't be read (offline): A stays open, so the feed waits for it instead of skipping its
        // questions between 80 and 50, or ending.
        assertEquals(a, TagFeedRules.afterFailedRead(a))
        val merge = TagFeedRules.merge(f, listOf(TagFeedRules.afterFailedRead(a), b))
        assertEquals(listOf(100L, 95L, 80L), merge.items.map { it.id })
        assertEquals(listOf("A"), merge.more)
        assertFalse(merge.ended)
        // A tag whose first page couldn't be read is set aside, so the others still show.
        val aside = TagFeedRules.afterFailedRead(stream("C"))
        assertTrue(aside.ended)
        val withC = TagFeedRules.merge(feed(FeedOrder.New, "A", "B", "C"), listOf(a, b, aside))
        assertEquals(listOf(100L, 95L, 80L), withC.items.map { it.id })
    }

    @Test
    fun feeds_hotRoundByRound() {
        val f = feed(FeedOrder.Hot, "A", "B")
        val a = stream("A", listOf(q(1, answers = 2), q(2, answers = 9)), listOf(q(4, answers = 50)))
        val b = stream("B", listOf(q(3, answers = 5)))
        var merge = TagFeedRules.merge(f, listOf(a, b))
        assertEquals("a round shows once every tag has its page", listOf(2L, 3L, 1L), merge.items.map { it.id })
        assertEquals(listOf("B"), merge.more)
        merge = TagFeedRules.merge(f, listOf(a, b.copy(ended = true)))
        assertEquals("the next round comes after, so nothing moves", listOf(2L, 3L, 1L, 4L), merge.items.map { it.id })
    }

    @Test
    fun feeds_mixedTakesTurns() {
        val f = feed(FeedOrder.Mixed, "A", "B", "C")
        val a = stream("A", listOf(q(1, "A"), q(2, "A"), q(3, "A")))
        val b = stream("B", listOf(q(1, "A", "B"), q(4, "B"), q(5, "B")))
        val c = stream("C", listOf(q(6, "C")), ended = true)
        val merge = TagFeedRules.merge(f, listOf(a, b, c))
        assertEquals("one from each tag in turn, each question once", listOf(1L, 4L, 6L, 2L, 5L, 3L), merge.items.map { it.id })
        assertEquals("the tag that ran out waits for its next page", listOf("B"), merge.more)
    }

    @Test
    fun feeds_leaveOutWhatTheyShould() {
        val f = feed(FeedOrder.New, "A", excluded = listOf("ספוילר"), unanswered = true)
        assertTrue(TagFeedRules.accepts(f, q(1, "A")))
        assertFalse(TagFeedRules.accepts(f, q(2, "A", "ספוילר")))
        assertFalse(TagFeedRules.accepts(f, q(3, "A", answers = 1)))
    }

    @Test
    fun feeds_sharedAsALink() {
        val shared = TagFeed("mine", "זוגיות & אהבה", "❤️", listOf("זוגיות", "צה\"ל", "C++"), listOf("מין"), FeedOrder.Hot, HotWindow.Month, unansweredOnly = true)
        val link = TagFeedRules.shareLink(shared)
        assertTrue(link.startsWith("https://www.shimshii.com/stips-plus/feed/?"))
        assertEquals(shared.copy(id = "x"), TagFeedRules.fromShareLink(link, "x"))
        val opened = Links.deepLink(link)
        assertTrue(opened is DeepLink.Feed)
        assertEquals(shared.tags, (opened as DeepLink.Feed).feed.tags)
        assertNull("another site", TagFeedRules.fromShareLink("https://evil.example/stips-plus/feed/?n=a&t=b", "x"))
        assertNull("not https", TagFeedRules.fromShareLink("http://www.shimshii.com/stips-plus/feed/?n=a&t=b", "x"))
        assertNull("no tags", TagFeedRules.fromShareLink("https://www.shimshii.com/stips-plus/feed/?n=a", "x"))
        assertNull("another page", TagFeedRules.fromShareLink("https://www.shimshii.com/stips-plus/?n=a&t=b", "x"))
        val manifest = source("AndroidManifest.xml").readText()
        assertTrue("Android opens the link in Stips+ without asking", Regex("autoVerify=\"true\"[\\s\\S]*?www\\.shimshii\\.com[\\s\\S]*?/stips-plus/feed").containsMatchIn(manifest))
    }

    @Test
    fun feeds_readyMadeOnesAreFeeds() {
        assertTrue(TagFeedRules.STARTERS.size >= 3)
        TagFeedRules.STARTERS.forEach { starter ->
            val made = starter.feed("id", hebrew = true)
            assertTrue(starter.nameEn, TagFeedRules.valid(TagFeedRules.cleaned(made)))
            assertEquals(starter.nameEn, starter.tags, TagFeedRules.cleanTags(starter.tags))
        }
    }

    // --- The owner's words, in both languages ---

    @Test
    fun strings_inBothLanguages() {
        fun strings(folder: String): Map<String, String> {
            val file = source("res/$folder/strings.xml")
            val list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
            return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        }
        val en = strings("values")
        val iw = strings("values-iw")
        assertEquals("the owner's exact words (#44)", "חמות, ואז נושאים (כמו באתר)", iw["layout_website"])
        for (key in listOf("tab_feeds", "home_view_new", "home_view_topics", "copy_answer", "settings_test_builds", "photo_crop_hint", "image_copy")) {
            assertTrue("$key in English", !en[key].isNullOrBlank())
            assertTrue("$key in Hebrew", !iw[key].isNullOrBlank())
        }
    }
}
