package io.github.eyalshimshi.betterstips.data

import android.content.Context
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Your feeds (#40), in your order, kept on this phone (backups keep them across a reinstall). */
class TagFeedStore(context: Context) {
    private val prefs = context.getSharedPreferences("tag_feeds", Context.MODE_PRIVATE)
    private val _feeds = MutableStateFlow(TagFeedRules.fromJson(prefs.getString(KEY, null)))
    val feeds: StateFlow<List<TagFeed>> = _feeds.asStateFlow()

    private val _opening = MutableStateFlow<String?>(null)

    /** A feed the Feeds tab should show next (just added, or picked on Home), until it has. */
    val opening: StateFlow<String?> = _opening.asStateFlow()

    fun open(id: String) {
        _opening.value = id
    }

    fun opened() {
        _opening.value = null
    }

    /** A new feed's id. */
    fun newId(): String = UUID.randomUUID().toString()

    fun find(id: String?): TagFeed? = _feeds.value.firstOrNull { it.id == id }

    /** Adds [feed], or replaces the one with its id where it is. False when it isn't valid, or there are too many. */
    @Synchronized
    fun save(feed: TagFeed): Boolean {
        val clean = TagFeedRules.cleaned(feed)
        if (!TagFeedRules.valid(clean)) return false
        val list = _feeds.value
        val index = list.indexOfFirst { it.id == clean.id }
        val next = when {
            index >= 0 -> list.toMutableList().also { it[index] = clean }
            list.size >= TagFeedRules.MAX_FEEDS -> return false
            else -> list + clean
        }
        write(next)
        return true
    }

    @Synchronized
    fun delete(id: String) {
        write(_feeds.value.filterNot { it.id == id })
    }

    /** Moves a feed one place earlier ([by] -1) or later (1). */
    @Synchronized
    fun move(id: String, by: Int) {
        val list = _feeds.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        write(list)
    }

    private fun write(list: List<TagFeed>) {
        _feeds.value = list
        prefs.edit().putString(KEY, TagFeedRules.toJson(list)).apply()
    }

    private companion object {
        const val KEY = "feeds"
    }
}
