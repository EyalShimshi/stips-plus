package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.parse.arr
import io.github.eyalshimshi.betterstips.data.parse.asArray
import io.github.eyalshimshi.betterstips.data.parse.asObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

/**
 * Announcements from Stips+ to everyone who has it (#41): `announcements.json` in the app's repository, read every few
 * hours by every version from 0.23 on. Each new one pops up once; the latest stays in Settings → About. The format only
 * ever gains optional fields, so a newer file keeps working in older versions (MAINTAINING.md says how to post one).
 * Pure.
 */
object AnnouncementRules {
    /** Where the file is: the repository's main branch, served without GitHub's API limit. Never move it. */
    const val URL = "https://raw.githubusercontent.com/EyalShimshi/stips-plus/main/announcements.json"

    /** How often the app reads the file, as it comes to the screen. */
    const val CHECK_EVERY_MS = 3L * 60 * 60 * 1000

    /** After a read that failed, how long the app waits before trying again. */
    const val RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000

    /** The file is small; anything far bigger isn't it. */
    const val MAX_BYTES = 64 * 1024

    private const val MAX_TITLE = 120
    private const val MAX_TEXT = 2000
    private const val MAX_LINK_TEXT = 40
    private const val MAX_ID = 80

    /** Which apps see an announcement: all of them, only those on releases, or only those taking test builds. */
    enum class Audience { All, Release, Test }

    /** A text in Hebrew and English; either may be missing, and the other shows instead. */
    data class Text(val he: String?, val en: String?) {
        fun pick(hebrew: Boolean): String = (if (hebrew) he ?: en else en ?: he).orEmpty()
    }

    data class Announcement(
        val id: String,
        val title: Text?,
        val text: Text,
        /** A web page to open, https only. */
        val link: String?,
        val linkText: Text?,
        /** Shown from this day and until that day, both included; either may be missing. */
        val from: LocalDate?,
        val until: LocalDate?,
        /** Only for these versions (version codes, the build numbers); either may be missing. */
        val minBuild: Long?,
        val maxBuild: Long?,
        val audience: Audience,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** The announcements in the file, in its order (newest first); anything unreadable is left out. */
    fun parse(text: String?): List<Announcement> {
        if (text.isNullOrBlank() || text.length > MAX_BYTES) return emptyList()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val list = root.asObject()?.arr("announcements") ?: root.asArray() ?: return emptyList()
        return list.mapNotNull { element -> element.asObject()?.let(::announcement) }.distinctBy { it.id }
    }

    private fun announcement(o: JsonObject): Announcement? {
        val id = o.plain("id")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ID } ?: return null
        val text = text(o["text"], MAX_TEXT) ?: return null
        val link = o.plain("link")?.trim()?.takeIf { isWebLink(it) }
        return Announcement(
            id = id,
            title = text(o["title"], MAX_TITLE),
            text = text,
            link = link,
            linkText = if (link != null) text(o["linkText"], MAX_LINK_TEXT) else null,
            from = date(o.plain("from")),
            until = date(o.plain("until")),
            minBuild = (o["minBuild"] as? JsonPrimitive)?.longOrNull,
            maxBuild = (o["maxBuild"] as? JsonPrimitive)?.longOrNull,
            audience = when (o.plain("channel")?.trim()?.lowercase()) {
                "release" -> Audience.Release
                "test" -> Audience.Test
                else -> Audience.All
            },
        )
    }

    /** A plain string, or {"he": …, "en": …}; null when there's no text at all. */
    private fun text(element: JsonElement?, max: Int): Text? {
        fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.take(max)
        val primitive = element as? JsonPrimitive
        if (primitive != null) {
            val value = clean(primitive.takeIf { it.isString }?.content) ?: return null
            return Text(value, value)
        }
        val o = element.asObject() ?: return null
        val he = clean(o.plain("he"))
        val en = clean(o.plain("en"))
        return if (he == null && en == null) null else Text(he, en)
    }

    private fun JsonObject.plain(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun date(value: String?): LocalDate? = value?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** An https address with a host: the only links an announcement may open. */
    fun isWebLink(link: String): Boolean {
        val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    /** Whether [announcement] is for this app ([build], taking test builds or not) on [today]. */
    fun appliesTo(announcement: Announcement, build: Long, testBuilds: Boolean, today: LocalDate): Boolean {
        val a = announcement
        if (a.from != null && today.isBefore(a.from)) return false
        if (a.until != null && today.isAfter(a.until)) return false
        if (a.minBuild != null && build < a.minBuild) return false
        if (a.maxBuild != null && build > a.maxBuild) return false
        return when (a.audience) {
            Audience.All -> true
            Audience.Release -> !testBuilds
            Audience.Test -> testBuilds
        }
    }

    /** The announcement to pop up now: the newest one for this app that hasn't been seen; null when there's none. */
    fun toShow(all: List<Announcement>, seen: Set<String>, build: Long, testBuilds: Boolean, today: LocalDate): Announcement? =
        all.firstOrNull { it.id !in seen && appliesTo(it, build, testBuilds, today) }

    /** The newest announcement for this app, seen or not: Settings → About shows it again. */
    fun latest(all: List<Announcement>, build: Long, testBuilds: Boolean, today: LocalDate): Announcement? =
        all.firstOrNull { appliesTo(it, build, testBuilds, today) }

    /** Time to read the file again: [CHECK_EVERY_MS] since the last read (or the clock went back), and a while after a failed one. */
    fun dueForCheck(lastCheckMs: Long, nowMs: Long, lastFailureMs: Long = 0L): Boolean =
        nowMs - lastCheckMs !in 0 until CHECK_EVERY_MS &&
            (lastFailureMs <= 0L || nowMs - lastFailureMs !in 0 until RETRY_AFTER_FAILURE_MS)
}
