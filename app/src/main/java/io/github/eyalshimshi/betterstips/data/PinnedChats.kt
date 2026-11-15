package io.github.eyalshimshi.betterstips.data

import android.content.Context
import io.github.eyalshimshi.betterstips.data.model.Author
import io.github.eyalshimshi.betterstips.data.model.Conversation
import io.github.eyalshimshi.betterstips.data.parse.asArray
import io.github.eyalshimshi.betterstips.data.parse.asObject
import io.github.eyalshimshi.betterstips.data.parse.bool
import io.github.eyalshimshi.betterstips.data.parse.long
import io.github.eyalshimshi.betterstips.data.parse.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Chats pinned on top of Chats (#33), per account, the latest pin first. Stips has no pins, so they're kept on this
 * phone, each with how the chat last looked: one on a page of chats that hasn't loaded yet still shows on top. Unread
 * counts and who's online aren't kept; they come with the list.
 */
class PinnedChats(context: Context) {
    private val prefs = context.getSharedPreferences("pinned_chats", Context.MODE_PRIVATE)

    fun read(account: Long): List<Conversation> = try {
        Json.parseToJsonElement(prefs.getString(key(account), null) ?: "[]").asArray()?.mapNotNull { element ->
            val o = element.asObject() ?: return@mapNotNull null
            val id = o.long("id") ?: return@mapNotNull null
            Conversation(
                other = Author(
                    nickname = o.str("nickname").orEmpty(),
                    userId = id,
                    photoStamp = o.str("photo"),
                    anonymous = false,
                    online = false,
                    branding = o.str("branding"),
                    highlightName = o.bool("highlight") == true,
                    platform = o.bool("platform") == true,
                ),
                lastMessage = o.str("last").orEmpty(),
                time = o.long("time")?.let { Instant.ofEpochMilli(it) },
                timeText = o.str("timeText"),
                unread = 0,
                lastFromMe = o.bool("fromMe") == true,
            )
        }.orEmpty().distinctBy { it.other.userId }
    } catch (e: Exception) {
        emptyList()
    }

    /** Keeps [pinned] for [account]; nothing is written when it's what's kept already. */
    fun write(account: Long, pinned: List<Conversation>) {
        val json = buildJsonArray {
            pinned.forEach { chat ->
                val id = chat.other.userId ?: return@forEach
                addJsonObject {
                    put("id", id)
                    put("nickname", chat.other.nickname)
                    put("photo", chat.other.photoStamp)
                    put("branding", chat.other.branding)
                    put("highlight", chat.other.highlightName)
                    put("platform", chat.other.platform)
                    put("last", chat.lastMessage)
                    put("time", chat.time?.toEpochMilli())
                    put("timeText", chat.timeText)
                    put("fromMe", chat.lastFromMe)
                }
            }
        }.toString()
        if (prefs.getString(key(account), null) != json) prefs.edit().putString(key(account), json).apply()
    }

    private fun key(account: Long) = "pins_$account"
}
