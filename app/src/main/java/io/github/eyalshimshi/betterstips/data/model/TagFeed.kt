package io.github.eyalshimshi.betterstips.data.model

/** How a feed (#40) orders its questions. */
enum class FeedOrder {
    /** Newest first, across all its tags. */
    New,

    /** Each tag's hot questions (this week or month), the most answered first. */
    Hot,

    /** One question from each tag in turn, whatever their times: a mix of everything in the feed. */
    Mixed,
}

/**
 * One of your feeds (#40): questions with any of its [tags], asked from Stips tag by tag, kept on this phone. Stips
 * has no such thing: its categories are a fixed list of channels, but every tag has its own lists of new and hot
 * questions, which a feed brings together.
 */
data class TagFeed(
    /** Made on this phone; a feed from a shared link gets a new one. */
    val id: String,
    val name: String,
    /** Shown before the name; may be empty. */
    val emoji: String = "",
    /** 1 to 15 tags, as Stips writes them (no "#"). */
    val tags: List<String>,
    /** Questions with any of these tags are left out. */
    val excluded: List<String> = emptyList(),
    val order: FeedOrder = FeedOrder.New,
    /** Hot's window: Stips offers a week or a month for a tag. */
    val window: HotWindow = HotWindow.Week,
    /** Only questions no one answered yet. */
    val unansweredOnly: Boolean = false,
) {
    /** The name with its emoji, as tabs and chips show it. */
    val label: String get() = if (emoji.isBlank()) name else "$emoji $name"

    /** What its list depends on: a feed edited into another list starts over. */
    val listKey: String get() = listOf(tags.joinToString("\u0001"), excluded.joinToString("\u0001"), order.name, window.name, unansweredOnly.toString()).joinToString("|")
}
