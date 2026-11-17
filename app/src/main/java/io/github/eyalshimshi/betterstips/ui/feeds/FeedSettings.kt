package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.AppSettings
import io.github.eyalshimshi.betterstips.data.model.TagFeed
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.components.shareText
import io.github.eyalshimshi.betterstips.ui.profile.GroupLabel
import io.github.eyalshimshi.betterstips.ui.profile.GroupRow
import io.github.eyalshimshi.betterstips.util.TagFeedRules

/**
 * Settings → Feeds (#40), the owner's picks: feeds as chips on Home, Live in feeds, and your feeds (change, reorder,
 * share, delete), with the ready-made ones. How far a feed reads, and that it asks tag by tag, aren't settings.
 */
@Composable
fun FeedsSettingsContent(settings: AppSettings) {
    val container = LocalAppContainer.current
    val store = container.tagFeeds
    val feeds by store.feeds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hebrew = LocalConfiguration.current.locales[0].language.let { it == "he" || it == "iw" }
    var editing by remember { mutableStateOf<TagFeed?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagFeed?>(null) }

    Spacer(Modifier.height(8.dp))
    GroupRow(
        title = stringResource(R.string.settings_feed_chips),
        summary = stringResource(R.string.settings_feed_chips_summary),
        index = 0,
        count = 1,
        onClick = { container.settings.update { it.copy(feedChipsOnHome = !it.feedChipsOnHome) } },
        icon = Icons.Outlined.Home,
        trailing = { Switch(checked = settings.feedChipsOnHome, onCheckedChange = null) },
    )

    GroupLabel(stringResource(R.string.settings_your_feeds))
    val rows = feeds.size + 1
    feeds.forEachIndexed { index, feed ->
        GroupRow(
            title = feed.label,
            summary = pluralStringResource(R.plurals.feed_tag_count, feed.tags.size, feed.tags.size) + " · " + feedOrderLabel(feed.order),
            index = index,
            count = rows,
            onClick = { editing = feed },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { store.move(feed.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.feed_move_up))
                    }
                    IconButton(onClick = { store.move(feed.id, 1) }, enabled = index < feeds.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.feed_move_down))
                    }
                    FeedRowMenu(
                        onShare = { shareText(context, TagFeedRules.shareLink(feed), title = feed.label) },
                        onDelete = { deleting = feed },
                    )
                }
            },
        )
    }
    GroupRow(
        title = stringResource(R.string.feed_create),
        index = feeds.size,
        count = rows,
        onClick = if (feeds.size < TagFeedRules.MAX_FEEDS) ({ creating = true }) else null,
        icon = Icons.Filled.Add,
    )

    GroupLabel(stringResource(R.string.feeds_starters))
    Column(Modifier.padding(horizontal = 16.dp)) {
        TagFeedRules.STARTERS.forEach { starter ->
            val added = feeds.any { it.name == starter.nameHe || it.name == starter.nameEn }
            StarterCard(
                starter = starter,
                hebrew = hebrew,
                added = added,
                onAdd = { store.save(starter.feed(store.newId(), hebrew)) },
            )
            Spacer(Modifier.height(8.dp))
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
                if (store.save(feed)) {
                    creating = false
                    editing = null
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

@Composable
private fun FeedRowMenu(onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.feed_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feed_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    open = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feed_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}
