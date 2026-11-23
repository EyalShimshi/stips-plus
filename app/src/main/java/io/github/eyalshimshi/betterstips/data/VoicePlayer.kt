package io.github.eyalshimshi.betterstips.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.eyalshimshi.betterstips.diag.NetLog
import io.github.eyalshimshi.betterstips.util.Links
import io.github.eyalshimshi.betterstips.util.Vocaroo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/**
 * Plays Vocaroo recordings in the app (v0.25, D-184), one at a time, from their voice-note rows. Nothing is read until
 * ▶: then Android's own player streams the MP3 with Vocaroo's Referer, since its media server answers nothing else. It
 * pauses when another screen comes on top or the app is left, when another app takes the sound, or when headphones are
 * unplugged, and stops when the screen that plays it closes. Turning the phone changes none of that: the row on the
 * new screen takes over. Used on the main thread only.
 */
class VoicePlayer(private val context: Context, private val userAgent: String) {
    enum class Status { Idle, Loading, Playing, Paused, Ended, Failed }

    data class State(
        /** The recording loaded or playing; null when none is. */
        val id: String? = null,
        val status: Status = Status.Idle,
        val positionMs: Int = 0,
        /** 0 until it's known. */
        val durationMs: Int = 0,
        val buffering: Boolean = false,
        /** One of [SPEEDS]; it carries over to the next recording. */
        val speed: Float = 1f,
    ) {
        /** What the row for [recordingId] shows: this, or a recording not started yet. */
        fun of(recordingId: String): State = if (id == recordingId) this else State(speed = speed)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var recording: Vocaroo.Recording? = null
    private var urlIndex = 0
    private var focus: AudioFocusRequest? = null
    private var noisyRegistered = false

    /**
     * Where a row is: its screen's lifecycle, the activity (to tell turning the phone from closing the screen), and the
     * screen's key, which stays the same when the phone turns.
     */
    class Spot(val owner: LifecycleOwner, activity: Activity?, val screen: String?) {
        private val activity = activity?.let { WeakReference(it) }

        /** The activity is being made again (the phone turned, the window changed size): nothing is closing. */
        val turning: Boolean get() = activity?.get()?.isChangingConfigurations == true
    }

    /** The screen of the recording loaded now. */
    private var bound: Spot? = null

    /** The rows on screen, by recording: a tap on a link plays it in its row. */
    private val rows = HashMap<String, Spot>()

    // The screen closed for good, not turned: the recording stops with it.
    private val ownerObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY && bound?.turning != true) stop()
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val media = player ?: return
            if (_state.value.status != Status.Playing) return
            val position = runCatching { media.currentPosition }.getOrNull()
            if (position != null) _state.update { it.copy(positionMs = position) }
            main.postDelayed(this, TICK_MS)
        }
    }

    private val loadTimeout = Runnable {
        if (_state.value.status == Status.Loading) failure(MediaPlayer.MEDIA_ERROR_TIMED_OUT, 0)
    }

    /** A row for recording [id] is on screen at [spot]. After the phone turns, the playing recording's row takes over. */
    fun register(id: String, spot: Spot) {
        rows[id] = spot
        if (_state.value.id == id && player != null && bound?.owner?.lifecycle?.currentState == Lifecycle.State.DESTROYED) bind(spot)
    }

    fun unregister(id: String, owner: LifecycleOwner) {
        if (rows[id]?.owner === owner) rows.remove(id)
    }

    /** A tap on [recording]'s link: true when a row on screen plays it; false leaves the link to the browser. */
    fun toggleLink(recording: Vocaroo.Recording): Boolean {
        val spot = rows[recording.id] ?: return false
        toggle(recording, spot)
        return true
    }

    /** ▶ or ⏸ on [recording]'s row, which is at [spot]. */
    fun toggle(recording: Vocaroo.Recording, spot: Spot) {
        val current = _state.value
        if (current.id != recording.id || player == null) {
            start(recording, spot)
            return
        }
        when (current.status) {
            Status.Playing -> pause()
            Status.Paused, Status.Ended -> resume(spot)
            Status.Loading -> stop()
            Status.Idle, Status.Failed -> start(recording, spot)
        }
    }

    /** The screen now on top ([screen], AppRoot's): a recording playing on another one pauses. */
    fun screenShown(screen: String?) {
        val playing = bound?.screen ?: return
        if (screen != null && screen != playing) pause()
    }

    /** The app left the screen (not a rotation). */
    fun appLeft() = pause()

    /** Moves recording [id] to [fraction] of its length, once it's loaded. */
    fun seek(id: String, fraction: Float) {
        val current = _state.value
        val media = player ?: return
        if (current.id != id || current.durationMs <= 0) return
        if (current.status != Status.Playing && current.status != Status.Paused && current.status != Status.Ended) return
        val ms = (fraction.coerceIn(0f, 1f) * current.durationMs).toInt()
        runCatching { media.seekTo(ms) }
        _state.update { it.copy(positionMs = ms, status = if (it.status == Status.Ended) Status.Paused else it.status) }
    }

    /** 1×, 1.5×, 2×, and around again. */
    fun cycleSpeed() {
        val index = SPEEDS.indexOf(_state.value.speed)
        _state.update { it.copy(speed = SPEEDS[(index + 1) % SPEEDS.size]) }
        // Setting the speed of a paused player would start it: a paused one gets it as it plays again.
        if (_state.value.status == Status.Playing) applySpeed()
    }

    fun pause() {
        when (_state.value.status) {
            Status.Loading -> stop()
            Status.Playing -> {
                val media = player
                runCatching { media?.pause() }
                val position = media?.let { runCatching { it.currentPosition }.getOrNull() }
                _state.update { it.copy(status = Status.Paused, positionMs = position ?: it.positionMs, buffering = false) }
                quiet()
            }
            else -> Unit
        }
    }

    fun stop() {
        release()
        quiet()
        unbind()
        recording = null
        _state.value = State(speed = _state.value.speed)
    }

    private fun start(recording: Vocaroo.Recording, spot: Spot) {
        release()
        quiet()
        this.recording = recording
        urlIndex = 0
        bind(spot)
        _state.value = State(id = recording.id, status = Status.Loading, speed = _state.value.speed)
        prepare()
    }

    private fun prepare() {
        val id = recording?.id ?: return
        val url = Vocaroo.mediaUrls(id).getOrNull(urlIndex) ?: return failed("no server had it")
        NetLog.event("voice", "loading a Vocaroo recording from ${Links.host(url)}")
        val media = MediaPlayer()
        player = media
        try {
            media.setAudioAttributes(attributes)
            media.setDataSource(context, Uri.parse(url), mapOf("Referer" to Vocaroo.REFERER, "User-Agent" to userAgent))
            media.setOnPreparedListener { if (player === it) prepared(it) }
            media.setOnCompletionListener { if (player === it) ended() }
            media.setOnErrorListener { which, what, extra ->
                if (player === which) failure(what, extra)
                true
            }
            media.setOnInfoListener { which, what, _ ->
                if (player === which) {
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> _state.update { it.copy(buffering = true) }
                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> _state.update { it.copy(buffering = false) }
                    }
                }
                false
            }
            media.prepareAsync()
            main.removeCallbacks(loadTimeout)
            main.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
        } catch (e: Exception) {
            failure(0, 0)
        }
    }

    /** Loading failed: the other server may have it; anything else is a recording that can't play now. */
    private fun failure(what: Int, extra: Int) {
        val id = recording?.id
        if (_state.value.status == Status.Loading && id != null && urlIndex + 1 < Vocaroo.mediaUrls(id).size) {
            release()
            urlIndex++
            prepare()
            return
        }
        failed("error $what/$extra")
    }

    private fun failed(reason: String) {
        NetLog.event("voice", "a Vocaroo recording couldn't play: $reason")
        release()
        quiet()
        _state.update { it.copy(status = Status.Failed, buffering = false) }
    }

    private fun prepared(media: MediaPlayer) {
        main.removeCallbacks(loadTimeout)
        val duration = runCatching { media.duration }.getOrDefault(0).coerceAtLeast(0)
        _state.update { it.copy(status = Status.Paused, durationMs = duration, positionMs = 0, buffering = false) }
        play(media)
    }

    private fun resume(spot: Spot) {
        val media = player ?: return
        bind(spot)
        if (_state.value.status == Status.Ended) {
            runCatching { media.seekTo(0) }
            _state.update { it.copy(positionMs = 0) }
        }
        play(media)
    }

    /** Plays [media] if the phone lets the app have the sound now; during a call it stays paused. */
    private fun play(media: MediaPlayer) {
        if (!requestFocus()) return
        try {
            media.start()
        } catch (e: IllegalStateException) {
            failed("couldn't start")
            return
        }
        applySpeed()
        _state.update { it.copy(status = Status.Playing) }
        listenForNoisy()
        main.removeCallbacks(tick)
        main.post(tick)
    }

    private fun applySpeed() {
        val media = player ?: return
        val speed = _state.value.speed
        runCatching { media.playbackParams = media.playbackParams.setSpeed(speed) }
    }

    private fun ended() {
        main.removeCallbacks(tick)
        _state.update { it.copy(status = Status.Ended, positionMs = it.durationMs, buffering = false) }
        quiet()
    }

    /** Gives the sound back: the focus, the headphones' receiver and the position ticks. */
    private fun quiet() {
        main.removeCallbacks(tick)
        focus?.let { request -> runCatching { audio?.abandonAudioFocusRequest(request) } }
        if (noisyRegistered) {
            runCatching { context.unregisterReceiver(noisy) }
            noisyRegistered = false
        }
    }

    private fun requestFocus(): Boolean {
        val manager = audio ?: return true
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            // Speech isn't ducked under another sound: it pauses.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) pause() }, main)
            .build()
            .also { focus = it }
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun listenForNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun release() {
        main.removeCallbacks(loadTimeout)
        main.removeCallbacks(tick)
        val media = player ?: return
        player = null
        runCatching { media.reset() }
        runCatching { media.release() }
    }

    private fun bind(spot: Spot) {
        val previous = bound
        bound = spot
        if (previous?.owner === spot.owner) return
        previous?.owner?.lifecycle?.removeObserver(ownerObserver)
        spot.owner.lifecycle.addObserver(ownerObserver)
    }

    private fun unbind() {
        bound?.owner?.lifecycle?.removeObserver(ownerObserver)
        bound = null
    }

    companion object {
        val SPEEDS = listOf(1f, 1.5f, 2f)
        private const val TICK_MS = 250L
        private const val LOAD_TIMEOUT_MS = 20_000L
    }
}
