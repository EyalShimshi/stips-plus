package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.model.FeedOrder
import io.github.eyalshimshi.betterstips.data.model.HotWindow
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.Routes
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.components.LocalTabReselects
import io.github.eyalshimshi.betterstips.ui.components.RefreshOnEnter
import io.github.eyalshimshi.betterstips.ui.components.appViewModel
import io.github.eyalshimshi.betterstips.ui.components.shareText
import io.github.eyalshimshi.betterstips.ui.feed.QuestionList
import io.github.eyalshimshi.betterstips.ui.feed.windowLabel
import io.github.eyalshimshi.betterstips.util.TagFeedRules
import kotlinx.coroutines.launch

/**
 * The Feeds tab (#40): your feeds as tabs you swipe between. Only the feed on screen asks Stips, tag by tag, and more
 * as it's scrolled; the others wait. With none yet, ready-made ones to start from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedsScreen(onOpenQuestion: (Long) -> Unit, onOpenFeedSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val store = container.tagFeeds
    val feeds by store.feeds.collectAsStateWithLifecycle()
    val opening by store.opening.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<TagFeed?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagFeed?>(null) }
    val pager = rememberPagerState { feeds.size }

    // Entering the tab, or coming back to the app on it, reads the feed on screen again when it's been a while; tapping
    // the tab again goes back to its top and reads it again. Only the feed on screen hears them.
    var enters by remember { mutableIntStateOf(0) }
    var reselects by remember { mutableIntStateOf(0) }
    RefreshOnEnter(Routes.FEEDS) { enters++ }
    val tabReselects = LocalTabReselects.current
    LaunchedEffect(tabReselects) {
        tabReselects.collect { if (it == Routes.FEEDS) reselects++ }
    }

    // A feed just added (from a shared link, a tag or a ready-made one) opens.
    LaunchedEffect(opening, feeds) {
        val id = opening ?: return@LaunchedEffect
        val index = feeds.indexOfFirst { it.id == id }
        if (index >= 0) {
            pager.scrollToPage(index)
            store.opened()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_feeds)) },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.feed_new_title))
                    }
                    IconButton(onClick = onOpenFeedSettings) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.settings_feeds))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (feeds.isEmpty()) {
            NoFeeds(
                modifier = Modifier.padding(padding),
                onAdd = { starter ->
                    val feed = starter.feed(store.newId(), hebrew = isHebrew(context))
                    if (store.save(feed)) store.open(feed.id)
                },
                onCreate = { creating = true },
            )
        } else {
            Column(Modifier.padding(padding)) {
                val selected = pager.currentPage.coerceIn(0, feeds.size - 1)
                PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                    feeds.forEachIndexed { index, feed ->
                        Tab(
                            selected = index == selected,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(feed.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 0,
                    key = { index -> feeds.getOrNull(index)?.id ?: index },
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val feed = feeds.getOrNull(index) ?: return@HorizontalPager
                    FeedPage(
                        feed = feed,
                        active = pager.settledPage == index,
                        enters = enters,
                        reselects = reselects,
                        onOpenQuestion = onOpenQuestion,
                        onEdit = { editing = feed },
                        onDelete = { deleting = feed },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val newId = remember { store.newId() }
        FeedEditorDialog(
            initial = editing,
            newId = newId,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { feed ->
                val isNew = editing == null
                if (store.save(feed)) {
                    creating = false
                    editing = null
                    if (isNew) store.open(feed.id)
                }
            },
        )
    }
    deleting?.let { feed ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.feed_delete_title, feed.label)) },
            text = { Text(stringResource(R.string.feed_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(feed.id)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Whether the app shows Hebrew: a ready-made feed takes its name in it. */
fun isHebrew(context: android.content.Context): Boolean =
    context.resources.configuration.locales[0].language.let { it == "he" || it == "iw" }

/** One feed's questions, with its order and its menu on top. [active]: it's the one on screen, so it may ask Stips. */
@Composable
private fun FeedPage(
    feed: TagFeed,
    active: Boolean,
    enters: Int,
    reselects: Int,
    onOpenQuestion: (Long) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "tag-feed-" + feed.id) { TagFeedViewModel(it.tagLists, it.repository, feed) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val unreadable by vm.unreadable.collectAsStateWithLifecycle()
    val channels by container.channels.channels.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(feed) { vm.update(feed) }
    // On screen (its page settled, v0.24): what's kept shows at once, and the lists due are read behind it.
    LaunchedEffect(active) { if (active) vm.onEnter() }
    val firstEnter = remember { enters }
    LaunchedEffect(enters) {
        if (enters != firstEnter && active) vm.onEnter()
    }
    val firstReselect = remember { reselects }
    LaunchedEffect(reselects) {
        if (reselects != firstReselect && active) {
            vm.refresh()
            listState.scrollToItem(0)
        }
    }
    // Newer lists come in only while the list's top is on screen, so what's being read stays put.
    LaunchedEffect(vm, listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect { vm.setAtTop(it) }
    }

    QuestionList(
        state = state,
        channels = channels,
        emptyTitle = stringResource(if (feed.unansweredOnly) R.string.feed_empty_unanswered else R.string.feed_empty),
        onOpen = { onOpenQuestion(it.id) },
        onRefresh = vm::refresh,
        onLoadMore = { vm.loadMore() },
        onRetry = vm::retry,
        listState = listState,
        header = {
            item(key = "feed-header") {
                FeedHeader(
                    feed = feed,
                    progress = progress,
                    unreadable = unreadable,
                    onOrder = { container.tagFeeds.save(feed.copy(order = it)) },
                    onWindow = { container.tagFeeds.save(feed.copy(window = it)) },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        },
    )
}

@Composable
private fun FeedHeader(
    feed: TagFeed,
    progress: Pair<Int, Int>?,
    unreadable: List<String>,
    onOrder: (FeedOrder) -> Unit,
    onWindow: (HotWindow) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                FeedOrder.entries.forEachIndexed { index, order ->
                    SegmentedButton(
                        selected = feed.order == order,
                        onClick = { onOrder(order) },
                        shape = SegmentedButtonDefaults.itemShape(index, FeedOrder.entries.size),
                    ) { Text(feedOrderLabel(order), maxLines = 1) }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.feed_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_edit_title)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_share)) },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            menu = false
                            shareText(context, TagFeedRules.shareLink(feed), title = feed.label)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
        if (feed.order == FeedOrder.Hot) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HotWindow.Week, HotWindow.Month).forEach { window ->
                    FilterChip(selected = feed.window == window, onClick = { onWindow(window) }, label = { Text(windowLabel(window)) })
                }
            }
        }
        // Its tags, in a line; a tap changes them.
        Text(
            feed.tags.joinToString(" · ") { "#$it" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp, end = 12.dp)
                .clickable(onClickLabel = stringResource(R.string.feed_edit_title), onClick = onEdit),
        )
        if (progress != null && progress.second > 0) {
            Text(
                stringResource(R.string.feed_reading, progress.first, progress.second),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 12.dp),
            )
        }
        // Tags Stips failed on lately, with nothing kept to show (v0.24): skipped, and tried again later.
        if (unreadable.isNotEmpty()) {
            Text(
                stringResource(R.string.feed_unreadable, unreadable.joinToString(", ") { "#$it" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            )
        }
    }
}

/** No feeds yet: what they are, ready-made ones to add with a tap, and making your own. */
@Composable
private fun NoFeeds(modifier: Modifier, onAdd: (TagFeedRules.Starter) -> Unit, onCreate: () -> Unit) {
    val hebrew = LocalConfiguration.current.locales[0].language.let { it == "he" || it == "iw" }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "intro") {
            Column(Modifier.padding(bottom = 6.dp)) {
                Icon(Icons.Outlined.DynamicFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.feeds_intro_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.feeds_intro_body, TagFeedRules.MAX_TAGS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feed_create))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.feeds_starters), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(TagFeedRules.STARTERS, key = { it.nameEn }) { starter ->
            StarterCard(starter, hebrew, onAdd = { onAdd(starter) })
        }
    }
}

/** A ready-made feed: its emoji, name and tags, and "Add". Settings → Feeds offers them too. */
@Composable
fun StarterCard(starter: TagFeedRules.Starter, hebrew: Boolean, onAdd: () -> Unit, added: Boolean = false) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(starter.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (hebrew) starter.nameHe else starter.nameEn, style = MaterialTheme.typography.titleMedium)
                Text(
                    starter.tags.joinToString(" · ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAdd, enabled = !added) {
                Text(stringResource(if (added) R.string.feed_added else R.string.feed_add))
            }
        }
    }
}
