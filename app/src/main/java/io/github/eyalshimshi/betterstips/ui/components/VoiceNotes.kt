package io.github.eyalshimshi.betterstips.ui.components

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.VoicePlayer
import io.github.eyalshimshi.betterstips.util.Vocaroo

/**
 * Voice notes (v0.25, D-184): a row for each Vocaroo recording linked in a text, as a voice message in WhatsApp. ▶
 * loads and plays it right there; nothing is read before. [tint] colors the button and the bar ([textColor] the
 * time); [menu] adds ⋮ with the link (chat bubbles have their own long press).
 */
@Composable
fun VoiceNotes(
    recordings: List<Vocaroo.Recording>,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    menu: Boolean = true,
) {
    if (recordings.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        recordings.forEach { recording -> VoiceNoteRow(recording, tint = tint, textColor = textColor, menu = menu) }
    }
}

/** One recording: ▶ or ⏸, the bar to move through it, its time, the speed once it plays, and ⋮. */
@Composable
fun VoiceNoteRow(
    recording: Vocaroo.Recording,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    menu: Boolean = true,
) {
    val player = LocalAppContainer.current.voicePlayer
    val owner = LocalLifecycleOwner.current
    val activity = LocalActivity.current
    // The screen's own key, which stays the same when the phone turns (the back stack entry's id).
    val spot = remember(owner, activity) { VoicePlayer.Spot(owner, activity, (owner as? NavBackStackEntry)?.id) }
    val all by player.state.collectAsStateWithLifecycle()
    val state = all.of(recording.id)
    // While the row is on screen, a tap on the recording's link plays it here.
    DisposableEffect(recording.id, spot) {
        player.register(recording.id, spot)
        onDispose { player.unregister(recording.id, owner) }
    }
    val status = state.status
    val playing = status == VoicePlayer.Status.Playing
    val failed = status == VoicePlayer.Status.Failed
    val busy = status == VoicePlayer.Status.Loading || (playing && state.buffering)
    val loaded = state.durationMs > 0 &&
        (playing || status == VoicePlayer.Status.Paused || status == VoicePlayer.Status.Ended)
    val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val label = when {
        failed -> stringResource(R.string.voice_failed)
        status == VoicePlayer.Status.Loading -> stringResource(R.string.voice_loading)
        status == VoicePlayer.Status.Ended -> Vocaroo.clock(state.durationMs)
        loaded -> "\u2066" + Vocaroo.clock(state.positionMs) + " / " + Vocaroo.clock(state.durationMs) + "\u2069"
        status == VoicePlayer.Status.Idle -> stringResource(R.string.voice_note)
        else -> Vocaroo.clock(state.positionMs)
    }
    val buttonLabel = stringResource(if (playing) R.string.voice_pause else R.string.voice_play)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .clickable(role = Role.Button, onClickLabel = buttonLabel) { player.toggle(recording, spot) },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(
                    when {
                        playing -> Icons.Filled.Pause
                        failed -> Icons.Filled.Refresh
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = buttonLabel,
                    tint = tint,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp),
        ) {
            SeekBar(
                fraction = fraction,
                enabled = loaded,
                color = tint,
                track = tint.copy(alpha = 0.22f),
                onSeek = { player.seek(recording.id, it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text("Vocaroo", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        if (loaded) SpeedButton(state.speed, tint, onClick = player::cycleSpeed)
        if (menu) VoiceMenu(recording, textColor)
    }
}

/** "1.5×": a tap goes to the next speed. */
@Composable
private fun SpeedButton(speed: Float, tint: Color, onClick: () -> Unit) {
    val shown = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    val label = stringResource(R.string.voice_speed, shown)
    Box(
        Modifier
            .padding(start = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text("$shown×", style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/** ⋮: the recording on Vocaroo's site, its link copied, or shared. */
@Composable
private fun VoiceMenu(recording: Vocaroo.Recording, color: Color) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val copiedText = stringResource(R.string.swipe_link_copied)
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.voice_actions), tint = color)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.voice_open_vocaroo)) },
                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                onClick = {
                    open = false
                    openInBrowser(context, recording.link)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.swipe_copy_link)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    open = false
                    if (!copyPlainText(context, "Vocaroo", recording.link)) Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    open = false
                    shareText(context, recording.link)
                },
            )
        }
    }
}

/**
 * A thin bar for a recording's progress: a tap or a drag moves it ([onSeek] gets 0–1 when the finger lifts). It runs
 * from the start side, so right to left in Hebrew. TalkBack reads and sets it as a slider.
 */
@Composable
private fun SeekBar(fraction: Float, enabled: Boolean, color: Color, track: Color, onSeek: (Float) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)
    val seek by rememberUpdatedState(onSeek)
    fun at(x: Float, width: Int): Float {
        val raw = if (width > 0) (x / width).coerceIn(0f, 1f) else 0f
        return if (rtl) 1f - raw else raw
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        seek(value.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> seek(at(offset.x, size.width)) }
            }
            .pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragging = at(offset.x, size.width) },
                    onDragEnd = {
                        dragging?.let { seek(it) }
                        dragging = null
                    },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    dragging = at(change.position.x, size.width)
                }
            },
    ) {
        val y = size.height / 2f
        val stroke = 4.dp.toPx()
        val start = if (rtl) size.width else 0f
        val end = if (rtl) 0f else size.width
        val reached = start + (end - start) * shown
        drawLine(track, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (shown > 0f) drawLine(color, Offset(start, y), Offset(reached, y), stroke, StrokeCap.Round)
        if (enabled) drawCircle(color, 6.dp.toPx(), Offset(reached, y))
    }
}
