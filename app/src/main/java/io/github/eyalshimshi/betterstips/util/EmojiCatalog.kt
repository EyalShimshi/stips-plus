package io.github.eyalshimshi.betterstips.util

/**
 * The feed emoji picker's list (v0.24, D-170): Unicode's emoji in its groups, from the app's file `emoji_picker.txt`
 * (tools/gen_emoji_picker.js makes it from Unicode's emoji-test.txt). The picker shows only those the phone can draw.
 * Pure.
 */
object EmojiCatalog {
    const val ASSET = "emoji_picker.txt"

    /** The groups the file has, in its order: the picker's tabs. */
    val GROUPS = listOf("smileys", "people", "animals", "food", "travel", "activities", "objects", "symbols", "flags")

    /** The picker's first tab: emoji picked lately. */
    const val RECENT = "recent"

    /** Emoji picked lately that the picker remembers. */
    const val MAX_RECENT = 24

    data class Group(val key: String, val emoji: List<String>)

    /** The groups in [lines] ("key", a tab, then emoji separated by spaces); comments (#) and unknown groups left out. */
    fun parse(lines: Sequence<String>): List<Group> = lines.mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val key = line.substringBefore('\t', "").trim()
        val emoji = line.substringAfter('\t', "").split(' ').filter { it.isNotBlank() }
        if (key !in GROUPS || emoji.isEmpty()) null else Group(key, emoji)
    }.toList()

    /** [picked] first, then the ones picked before it, each once, at most [MAX_RECENT]. */
    fun recent(picked: String, before: List<String>): List<String> =
        (listOf(picked) + before).filter { it.isNotBlank() }.distinct().take(MAX_RECENT)
}
