package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.feed.windowLabel
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Making or changing a feed (#40), full screen: an emoji and a name, up to 15 tags (Stips' own suggestions come as you
 * type, as on the site), tags to leave out, its order, and whether it's only unanswered questions. [initial] null is
 * a new feed, starting with [startTags].
 */
@Composable
fun FeedEditorDialog(
    initial: TagFeed?,
    newId: String,
    startTags: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (TagFeed) -> Unit,
) {
    val store = LocalAppContainer.current.tagFeeds
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji.orEmpty()) }
    var pickingEmoji by rememberSaveable { mutableStateOf(false) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(initial?.tags ?: TagFeedRules.cleanTags(startTags)) } }
    val excluded = remember { mutableStateListOf<String>().apply { addAll(initial?.excluded.orEmpty()) } }
    var order by rememberSaveable { mutableStateOf(initial?.order ?: FeedOrder.New) }
    var window by rememberSaveable { mutableStateOf(initial?.window ?: HotWindow.Week) }
    var unanswered by rememberSaveable { mutableStateOf(initial?.unansweredOnly ?: false) }
    val draft = TagFeedRules.cleaned(
        TagFeed(
            id = initial?.id ?: newId,
            name = name,
            emoji = emoji,
            tags = tags.toList(),
            excluded = excluded.toList(),
            order = order,
            window = window,
            unansweredOnly = unanswered,
        ),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (initial == null) R.string.feed_new_title else R.string.feed_edit_title)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                            }
                        },
                        actions = {
                            TextButton(onClick = { onSave(draft) }, enabled = TagFeedRules.valid(draft)) {
                                Text(stringResource(R.string.feed_save))
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // The feed's emoji (any the phone has, v0.24) by its name.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiButton(emoji) { pickingEmoji = true }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(TagFeedRules.MAX_NAME) },
                            label = { Text(stringResource(R.string.feed_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    SectionLabel(stringResource(R.string.feed_tags, tags.size, TagFeedRules.MAX_TAGS))
                    TagsEditor(tags, TagFeedRules.MAX_TAGS, stringResource(R.string.feed_add_tag))
                    if (tags.isEmpty()) {
                        Text(
                            stringResource(R.string.feed_tags_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SectionLabel(stringResource(R.string.feed_order))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FeedOrder.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = order == choice,
                                onClick = { order = choice },
                                shape = SegmentedButtonDefaults.itemShape(index, FeedOrder.entries.size),
                            ) { Text(feedOrderLabel(choice), maxLines = 1) }
                        }
                    }
                    Text(
                        feedOrderSummary(order),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (order == FeedOrder.Hot) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(HotWindow.Week, HotWindow.Month).forEach { choice ->
                                FilterChip(selected = window == choice, onClick = { window = choice }, label = { Text(windowLabel(choice)) })
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clickable(role = Role.Switch) { unanswered = !unanswered },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.feed_unanswered), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.feed_unanswered_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = unanswered, onCheckedChange = null)
                    }

                    SectionLabel(stringResource(R.string.feed_excluded, excluded.size, TagFeedRules.MAX_EXCLUDED))
                    Text(
                        stringResource(R.string.feed_excluded_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TagsEditor(excluded, TagFeedRules.MAX_EXCLUDED, stringResource(R.string.feed_add_excluded))
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        if (pickingEmoji) {
            EmojiPickerSheet(
                current = emoji,
                recent = remember { store.recentEmoji() },
                onPick = { picked ->
                    emoji = picked
                    store.usedEmoji(picked)
                    pickingEmoji = false
                },
                onDismiss = { pickingEmoji = false },
            )
        }
    }
}

/** The feed's emoji, big and round (a smiley with a + when it has none): a tap opens the picker. */
@Composable
private fun EmojiButton(emoji: String, onClick: () -> Unit) {
    val label = if (emoji.isEmpty()) stringResource(R.string.feed_emoji_add) else stringResource(R.string.feed_emoji_change, emoji)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (emoji.isEmpty()) {
                Icon(Icons.Outlined.AddReaction, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                Text(emoji, fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Tags as chips (✕ takes one out), a field to add one, and Stips' own suggestions for what's typed. */
@Composable
private fun TagsEditor(tags: SnapshotStateList<String>, max: Int, hint: String) {
    val repository = LocalAppContainer.current.repository
    var input by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val full = tags.size >= max

    fun add(raw: String) {
        val tag = TagFeedRules.cleanTag(raw) ?: return
        if (!full && tags.none { it.equals(tag, ignoreCase = true) }) tags += tag
        input = ""
    }

    // As on the site's tag field: a little after typing stops, from two letters on.
    LaunchedEffect(input) {
        val typed = input.trim().trimStart('#')
        if (typed.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = try {
            repository.completeTag(typed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { tags.remove(tag) },
                    label = { Text("#$tag") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.feed_remove_tag, tag),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it.take(TagFeedRules.MAX_TAG + 1) },
        placeholder = { Text(hint) },
        singleLine = true,
        enabled = !full,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add(input) }),
        trailingIcon = {
            IconButton(onClick = { add(input) }, enabled = !full && TagFeedRules.cleanTag(input) != null) {
                Icon(Icons.Filled.Add, contentDescription = hint)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
    val offered = suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }
    if (offered.isNotEmpty() && !full) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            offered.forEach { suggestion -> SuggestionChip(onClick = { add(suggestion) }, label = { Text("#$suggestion") }) }
        }
    }
}

/** A feed's order in a word, as its switch says it. */
@Composable
fun feedOrderLabel(order: FeedOrder): String = stringResource(
    when (order) {
        FeedOrder.New -> R.string.feed_order_new
        FeedOrder.Hot -> R.string.feed_order_hot
        FeedOrder.Mixed -> R.string.feed_order_mixed
    },
)

@Composable
private fun feedOrderSummary(order: FeedOrder): String = stringResource(
    when (order) {
        FeedOrder.New -> R.string.feed_order_new_summary
        FeedOrder.Hot -> R.string.feed_order_hot_summary
        FeedOrder.Mixed -> R.string.feed_order_mixed_summary
    },
)
