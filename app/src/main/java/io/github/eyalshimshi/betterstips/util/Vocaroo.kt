package io.github.eyalshimshi.betterstips.util

/**
 * Vocaroo voice recordings (v0.25): links like `https://voca.ro/1lVEipfjSTbW` play in the app, in a voice-note row under
 * the text that has them. Nothing is read until ▶: then the MP3 streams from Vocaroo's media server, the one its own
 * page uses for that id, with its Referer, since the server answers nothing else (SPEC-04 §2.13). Pure.
 */
object Vocaroo {
    /** One recording: its [id], and the [link] as written. */
    data class Recording(val id: String, val link: String)

    /** Vocaroo's media server refuses requests without its page's Referer (403, measured Nov 2026); its player sends this. */
    const val REFERER = "https://vocaroo.com/"

    /** The most rows one text shows: a list of links shouldn't turn into a wall of players. */
    const val MAX_PER_TEXT = 3

    private const val MEDIA = "https://media.vocaroo.com/mp3/"
    private const val MEDIA1 = "https://media1.vocaroo.com/mp3/"

    // The short link and the page, old ("/i/") and embedded ones too. Ids are 10 to 12 letters and digits, the lengths
    // Vocaroo's own script knows; its pages ("/about", "/upload") are shorter.
    private val recordingLink = Regex(
        """^https?://(?:www\.)?(?:voca\.ro|vocaroo\.com)/(?:i/|embed/)?([A-Za-z0-9]{10,12})/?(?:[?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** The recording [link] leads to, or null for any other link. */
    fun recording(link: String?): Recording? {
        val trimmed = link?.trim().orEmpty()
        val id = recordingLink.matchEntire(trimmed)?.groupValues?.get(1) ?: return null
        return Recording(id, trimmed)
    }

    /** The recordings linked in [text], each once, in their order, at most [MAX_PER_TEXT]. */
    fun find(text: String): List<Recording> =
        Links.find(text).mapNotNull { recording(text.substring(it)) }.distinctBy { it.id }.take(MAX_PER_TEXT)

    /**
     * Whether [text] is nothing but recording links: then its rows stand for it, as a voice note does. Not when it has
     * more recordings than rows ([MAX_PER_TEXT]), so none of its links goes missing.
     */
    fun onlyRecordings(text: String): Boolean {
        val found = Links.find(text).mapNotNull { range -> recording(text.substring(range))?.let { range to it.id } }
        if (found.isEmpty() || found.distinctBy { it.second }.size > MAX_PER_TEXT) return false
        var rest = text
        found.map { it.first }.sortedByDescending { it.first }.forEach { rest = rest.removeRange(it) }
        return rest.isBlank()
    }

    /**
     * Where the recording's MP3 is: first the server Vocaroo's own page picks for the id (its script, Nov 2026: 11
     * letters on `media`, 12 starting with "1" and 10 on `media1`, anything else on `media`), then the other one.
     */
    fun mediaUrls(id: String): List<String> {
        val first = if ((id.length == 12 && id.startsWith("1")) || id.length == 10) MEDIA1 else MEDIA
        val second = if (first == MEDIA) MEDIA1 else MEDIA
        return listOf(first + id, second + id)
    }

    /** "1:05" for 65 s; "1:02:03" past an hour. */
    fun clock(ms: Int): String {
        val total = (ms.coerceAtLeast(0) + 500) / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = (total % 60).toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
    }
}
