package io.github.eyalshimshi.betterstips.data

import io.github.eyalshimshi.betterstips.data.api.ApiException
import io.github.eyalshimshi.betterstips.data.model.FeedQuery
import io.github.eyalshimshi.betterstips.data.model.FeedSort
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.Question
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import io.github.eyalshimshi.betterstips.util.TagListRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The feeds' cache (v0.24, D-167): each tag's lists from Stips, in memory and in the app's cache folder, shared by the
 * Feeds tab, Home's feed chips and the preloading ([FeedWarmer]). Reads of the same page share one request, and a few
 * tags are asked at a time. Lists are kept per account and safe filter: Stips' answers differ by them (your pins,
 * what the filter hides). The rules are [TagListRules].
 */
class TagLists(
    private val repository: StipsRepository,
    private val session: SessionStore,
    private val dir: File,
    private val scope: CoroutineScope,
) {
    /** One of a tag's lists. */
    data class Key(val tag: String, val kind: TagListRules.Kind)

    /** What a read left: the tag's list now (null when nothing's kept), and why the read failed when it did. */
    class Outcome(val entry: TagListRules.Entry?, val error: ApiException?)

    private val lock = Mutex()
    private val memory = object : LinkedHashMap<String, TagListRules.Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TagListRules.Entry>?): Boolean = size > MEMORY_LISTS
    }
    private val flights = HashMap<String, Deferred<Outcome>>()
    private val gate = Semaphore(TagFeedRules.PARALLEL)
    private val writing = Mutex()
    private var writes = 0

    private val _changed = MutableSharedFlow<Key>(extraBufferCapacity = 64)

    /** A list changed: a read finished, or Home's new questions came into it. */
    val changed: SharedFlow<Key> = _changed.asSharedFlow()

    init {
        // Your pins, from any screen: the kept copies say so too.
        scope.launch { repository.pins.collect { (id, pinned) -> updatePinned(id, pinned) } }
    }

    private fun account(): String {
        val s = session.session.value
        val safe = if (s.signedIn) s.safeFilter else true
        return "${s.userId ?: 0}:${if (safe) 1 else 0}"
    }

    private fun idOf(key: Key, account: String = account()): String =
        account + "|" + key.kind.key + "|" + TagListRules.normalTag(key.tag)

    suspend fun known(key: Key): TagListRules.Entry? = known(listOf(key))[key]

    /** What's kept for [keys]: from memory, else from the phone's storage. Never asks Stips. */
    suspend fun known(keys: Collection<Key>): Map<Key, TagListRules.Entry> {
        val account = account()
        val found = HashMap<Key, TagListRules.Entry>()
        val missing = ArrayList<Pair<Key, String>>()
        lock.withLock {
            for (key in keys) {
                val id = idOf(key, account)
                val entry = memory[id]
                if (entry != null) found[key] = entry else missing += key to id
            }
        }
        if (missing.isEmpty()) return found
        val loaded = withContext(Dispatchers.IO) { missing.map { (key, id) -> Triple(key, id, readFile(id)) } }
        lock.withLock {
            for ((key, id, stored) in loaded) {
                // A read that finished meanwhile is newer than the file.
                val entry = memory[id] ?: stored?.also { memory[id] = it } ?: continue
                found[key] = entry
            }
        }
        return found
    }

    /**
     * Reads Stips' page [page] of [key] (1: what's new on top) into the kept list. Reads of the same page share one
     * request, which goes on when whoever asked stops waiting, so it's kept for next time. Throws only when Stips
     * paused the phone ([ApiException.RATE_LIMITED], [ApiException.BLOCKED]).
     */
    suspend fun read(key: Key, page: Int): Outcome {
        val id = idOf(key)
        val flight = "$id#$page"
        val deferred = lock.withLock { flights.getOrPut(flight) { scope.async { fetch(key, id, page, flight) } } }
        return deferred.await()
    }

    private suspend fun fetch(key: Key, id: String, page: Int, flight: String): Outcome = try {
        gate.withPermit { fetchNow(key, id, page) }
    } finally {
        withContext(NonCancellable) { lock.withLock { flights.remove(flight) } }
    }

    private suspend fun fetchNow(key: Key, id: String, page: Int): Outcome {
        // What's kept, so the page folds into it (from storage when memory let it go); held here too, since memory
        // may let it go again while Stips answers.
        var loaded = lock.withLock { memory[id] }
        if (loaded == null) {
            val stored = withContext(Dispatchers.IO) { readFile(id) }
            if (stored != null) loaded = lock.withLock { memory[id] ?: stored.also { memory[id] = it } }
        }
        val query = FeedQuery(
            sort = if (key.kind == TagListRules.Kind.New) FeedSort.New else FeedSort.Hot,
            window = if (key.kind == TagListRules.Kind.HotMonth) HotWindow.Month else HotWindow.Week,
            topic = key.tag,
        ).normalized()
        val list = try {
            repository.feedPage(query, page, keepFirstPage = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? ApiException ?: ApiException(ApiException.BAD_RESPONSE, e.message)
            if (error.code == ApiException.RATE_LIMITED || error.code == ApiException.BLOCKED) throw error
            val now = System.currentTimeMillis()
            val failed = lock.withLock {
                val before = memory[id] ?: loaded
                val after = TagListRules.failed(before, error.code, now)
                if (after != null && after != before) memory[id] = after
                after?.takeIf { it != before }
            }
            if (failed != null) persist(id, key.kind, failed)
            return Outcome(failed ?: lock.withLock { memory[id] } ?: loaded, error)
        }
        val now = System.currentTimeMillis()
        val next = lock.withLock { TagListRules.fold(memory[id] ?: loaded, key.kind, page, list, now).also { memory[id] = it } }
        persist(id, key.kind, next)
        _changed.tryEmit(key)
        return Outcome(next, null)
    }

    /** Home's newest questions (all of Stips, page 1) brought into the newest-first lists they reach (D-167). */
    fun absorb(query: FeedQuery, list: List<Question>) {
        if (query.sort != FeedSort.New || query.topic != null || query.channelName != null || query.myList != null) return
        if (list.isEmpty()) return
        val prefix = account() + "|" + TagListRules.Kind.New.key + "|"
        scope.launch {
            val changed = ArrayList<Pair<String, TagListRules.Entry>>()
            lock.withLock {
                for ((id, entry) in memory.entries.toList()) {
                    if (!id.startsWith(prefix)) continue
                    val next = TagListRules.absorb(entry, id.removePrefix(prefix), list) ?: continue
                    memory[id] = next
                    changed += id to next
                }
            }
            for ((id, entry) in changed) {
                persist(id, TagListRules.Kind.New, entry)
                _changed.tryEmit(Key(id.removePrefix(prefix), TagListRules.Kind.New))
            }
        }
    }

    /** You pinned or unpinned a question: the kept copies say so too, also after a restart. */
    private fun updatePinned(questionId: Long, pinned: Boolean) {
        scope.launch {
            val changed = ArrayList<Triple<String, TagListRules.Kind, TagListRules.Entry>>()
            lock.withLock {
                for ((id, entry) in memory.entries.toList()) {
                    if (entry.pages.none { page -> page.any { it.id == questionId } }) continue
                    val kind = TagListRules.kindOf(id.split('|').getOrNull(1).orEmpty()) ?: continue
                    val next = entry.copy(
                        pages = entry.pages.map { page -> page.map { if (it.id == questionId) TagListRules.withPin(it, pinned) else it } },
                    )
                    memory[id] = next
                    changed += Triple(id, kind, next)
                }
            }
            for ((id, kind, entry) in changed) persist(id, kind, entry)
        }
    }

    /** Signing out: nothing of the account stays on the phone (#14). */
    fun clear() {
        scope.launch {
            lock.withLock { memory.clear() }
            withContext(Dispatchers.IO) { writing.withLock { dir.listFiles()?.forEach { it.delete() } } }
        }
    }

    /** Writes the list to storage after the writes before it; each writes what's kept by its turn. */
    private fun persist(id: String, kind: TagListRules.Kind, entry: TagListRules.Entry) {
        scope.launch(Dispatchers.IO) {
            writing.withLock {
                val latest = lock.withLock { memory[id] } ?: entry
                try {
                    dir.mkdirs()
                    file(id).writeText(TagListRules.toJson(id, kind, latest))
                } catch (e: Exception) {
                    // The storage is full or gone: the list stays in memory for this session.
                }
                if (++writes % EVICT_EVERY == 0) evict()
            }
        }
    }

    private fun evict() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        TagListRules.evict(files.map { TagListRules.Kept(it.name, it.length(), it.lastModified()) }, MAX_BYTES, MAX_FILES)
            .forEach { File(dir, it).delete() }
    }

    private fun readFile(id: String): TagListRules.Entry? {
        val file = file(id)
        if (!file.exists()) return null
        val entry = try {
            TagListRules.fromJson(file.readText(), id)
        } catch (e: Exception) {
            null
        }
        // Used now: kept longer than lists no feed reads any more.
        if (entry != null) file.setLastModified(System.currentTimeMillis())
        return entry
    }

    private fun file(id: String): File = File(dir, "t-" + sha1(id) + ".json")

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Lists kept in memory; the rest wait on the storage. */
        const val MEMORY_LISTS = 160

        /** The storage keeps up to 40 MB in 800 lists, the least recently used going first. */
        const val MAX_BYTES = 40L * 1024 * 1024
        const val MAX_FILES = 800
        const val EVICT_EVERY = 25
    }
}
