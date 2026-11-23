package io.github.eyalshimshi.betterstips.ui.feeds

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.components.BackButton
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.components.MessageCard
import io.github.eyalshimshi.betterstips.ui.components.appViewModel
import io.github.eyalshimshi.betterstips.ui.components.shareText
import io.github.eyalshimshi.betterstips.ui.feed.QuestionList
import io.github.eyalshimshi.betterstips.ui.feed.windowLabel
import io.github.eyalshimshi.betterstips.util.Links
import io.github.eyalshimshi.betterstips.util.TagFeedRules

/**
 * A feed someone shared (v0.25, D-183), opened from its link in a chat, a question, or another app: the feed itself,
 * its questions read now, with "Add to my feeds" on top, as a profile link opens the profile. Its order can be tried
 * here; Add keeps the one picked. Nothing is saved without the tap.
 */
@Composable
fun FeedPreviewScreen(link: String, onBack: () -> Unit, onOpenQuestion: (Long) -> Unit, onOpenFeeds: () -> Unit) {
    val parsed = remember(link) { TagFeedRules.fromShareLink(link, Links.SHARED_FEED_ID) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(parsed?.label ?: stringResource(R.string.feed_preview_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (parsed != null) {
                        val context = LocalContext.current
                        IconButton(onClick = { shareText(context, link, title = parsed.label) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.feed_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (parsed == null) {
            MessageCard(stringResource(R.string.feed_link_unreadable), null, null, null, Modifier.padding(padding))
        } else {
            FeedPreview(parsed, link, Modifier.padding(padding), onOpenQuestion, onOpenFeeds)
        }
    }
}

@Composable
private fun FeedPreview(parsed: TagFeed, link: String, modifier: Modifier, onOpenQuestion: (Long) -> Unit, onOpenFeeds: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var feed by remember(link) { mutableStateOf(parsed) }
    val vm = appViewModel(key = "feed-preview-$link") { TagFeedViewModel(it.tagLists, it.repository, parsed) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val unreadable by vm.unreadable.collectAsStateWithLifecycle()
    val channels by container.channels.channels.collectAsStateWithLifecycle()
    val feeds by container.tagFeeds.feeds.collectAsStateWithLifecycle()
    val yours = feeds.firstOrNull { TagFeedRules.sameLists(it, feed) }
    LaunchedEffect(feed) { vm.update(feed) }
    LaunchedEffect(Unit) { vm.onEnter() }
    val fullText = stringResource(R.string.feed_preview_full, TagFeedRules.MAX_FEEDS)
    QuestionList(
        state = state,
        channels = channels,
        emptyTitle = stringResource(if (feed.unansweredOnly) R.string.feed_empty_unanswered else R.string.feed_empty),
        onOpen = { onOpenQuestion(it.id) },
        onRefresh = vm::refresh,
        onLoadMore = { vm.loadMore() },
        onRetry = vm::retry,
        modifier = modifier.fillMaxSize(),
        header = {
            item(key = "feed-preview-header") {
                FeedPreviewHeader(
                    feed = feed,
                    yours = yours != null,
                    progress = progress,
                    unreadable = unreadable,
                    onOrder = { feed = feed.copy(order = it) },
                    onWindow = { feed = feed.copy(window = it) },
                    onAdd = {
                        val store = container.tagFeeds
                        val added = feed.copy(id = store.newId())
                        if (store.save(added)) {
                            store.open(added.id)
                            onOpenFeeds()
                        } else {
                            Toast.makeText(context, fullText, Toast.LENGTH_LONG).show()
                        }
                    },
                    onOpenYours = {
                        yours?.let { container.tagFeeds.open(it.id) }
                        onOpenFeeds()
                    },
                )
            }
        },
    )
}

/** Its tags and how many, the order to try, and Add (or, when you have it already, a way to it). */
@Composable
private fun FeedPreviewHeader(
    feed: TagFeed,
    yours: Boolean,
    progress: Pair<Int, Int>?,
    unreadable: List<String>,
    onOrder: (FeedOrder) -> Unit,
    onWindow: (HotWindow) -> Unit,
    onAdd: () -> Unit,
    onOpenYours: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.feed_tag_count, feed.tags.size, feed.tags.size) +
                (if (feed.unansweredOnly) " · " + stringResource(R.string.channel_unanswered) else ""),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(feed.tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (feed.excluded.isNotEmpty()) {
            Text(
                stringResource(R.string.feed_shared_excluded, feed.excluded.joinToString(" · ") { "#$it" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (yours) {
            OutlinedButton(onClick = onOpenYours, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feed_preview_yours))
            }
        } else {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feed_preview_add))
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FeedOrder.entries.forEachIndexed { index, order ->
                SegmentedButton(
                    selected = feed.order == order,
                    onClick = { onOrder(order) },
                    shape = SegmentedButtonDefaults.itemShape(index, FeedOrder.entries.size),
                ) { Text(feedOrderLabel(order), maxLines = 1) }
            }
        }
        if (feed.order == FeedOrder.Hot) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(HotWindow.Week, HotWindow.Month).forEach { window ->
                    FilterChip(selected = feed.window == window, onClick = { onWindow(window) }, label = { Text(windowLabel(window)) })
                }
            }
        }
        if (progress != null && progress.second > 0) {
            Text(
                stringResource(R.string.feed_reading, progress.first, progress.second),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(progress = { progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
        }
        if (unreadable.isNotEmpty()) {
            Text(
                stringResource(R.string.feed_unreadable, unreadable.joinToString(", ") { "#$it" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
