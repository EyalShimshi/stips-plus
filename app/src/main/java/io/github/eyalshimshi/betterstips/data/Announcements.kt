package io.github.eyalshimshi.betterstips.data

import android.content.Context
import io.github.eyalshimshi.betterstips.util.AnnouncementRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Announcements from Stips+ (#41): [AnnouncementRules.URL] is read every few hours as the app comes to the screen, on
 * GitHub's connections, without cookies; nothing here talks to Stips. The last file read and which announcements were
 * seen are kept, so one pops up once and Settings → About can show the latest again, offline too.
 */
class Announcements(context: Context, private val client: OkHttpClient, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)

    private val _all = MutableStateFlow(AnnouncementRules.parse(prefs.getString(KEY_FILE, null)))

    /** Everything in the last file read, newest first. */
    val all: StateFlow<List<AnnouncementRules.Announcement>> = _all.asStateFlow()

    private val _seen = MutableStateFlow(prefs.getStringSet(KEY_SEEN, null).orEmpty().toSet())

    /** The ids of the announcements already shown. */
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var failedAt = 0L

    /** Reads the file when it's due: [AnnouncementRules.CHECK_EVERY_MS] since the last read. Never throws. */
    fun checkIfDue() {
        scope.launch {
            lock.withLock {
                val now = System.currentTimeMillis()
                if (!AnnouncementRules.dueForCheck(prefs.getLong(KEY_CHECKED, 0L), now, failedAt)) return@withLock
                val text = fetch()
                // Only a file that isn't the file at all is a failed read: an empty list is a list (announcements
                // taken down), and the last good file stays otherwise.
                val parsed = text?.let(AnnouncementRules::parseOrNull)
                if (parsed == null) {
                    failedAt = now
                    return@withLock
                }
                prefs.edit().putString(KEY_FILE, text).putLong(KEY_CHECKED, now).apply()
                _all.value = parsed
            }
        }
    }

    fun markSeen(id: String) {
        _seen.update { it + id }
        prefs.edit().putStringSet(KEY_SEEN, _seen.value).apply()
    }

    private suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(AnnouncementRules.URL).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use null
                val source = body.source()
                source.request(AnnouncementRules.MAX_BYTES.toLong() + 1)
                if (source.buffer.size > AnnouncementRules.MAX_BYTES) null else source.buffer.readUtf8()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline, or GitHub didn't answer: the next try comes later.
            null
        }
    }

    private companion object {
        const val KEY_FILE = "file"
        const val KEY_CHECKED = "checkedAt"
        const val KEY_SEEN = "seen"
    }
}
