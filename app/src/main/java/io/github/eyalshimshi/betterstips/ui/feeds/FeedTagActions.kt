package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.util.TagFeedRules

/** Opens "Add to a feed" for a tag (#40): a long press on a question's #tag, or the tag page's button. */
val LocalAddTagToFeed = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * A tag's "Add to a feed" (#40): your feeds, each ticked when it has the tag (a tap adds or takes it out; a feed keeps
 * at least one, and at most 15), and a new feed that starts with it.
 */
@Composable
fun AddTagToFeedSheet(tag: String, onDismiss: () -> Unit) {
    val store = LocalAppContainer.current.tagFeeds
    val feeds by store.feeds.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val newId = remember { store.newId() }
    if (creating) {
        FeedEditorDialog(
            initial = null,
            newId = newId,
            startTags = listOf(tag),
            onDismiss = { creating = false },
            onSave = { feed ->
                if (store.save(feed)) {
                    store.open(feed.id)
                    onDismiss()
                }
            },
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.feed_add_tag_title, tag),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val clear = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        feeds.forEach { feed ->
            val has = feed.tags.any { it.equals(tag, ignoreCase = true) }
            val enabled = if (has) feed.tags.size > 1 else feed.tags.size < TagFeedRules.MAX_TAGS
            ListItem(
                headlineContent = { Text(feed.label) },
                supportingContent = {
                    Text(
                        if (!has && !enabled) {
                            stringResource(R.string.feed_full, TagFeedRules.MAX_TAGS)
                        } else {
                            pluralStringResource(R.plurals.feed_tag_count, feed.tags.size, feed.tags.size)
                        },
                    )
                },
                trailingContent = { Checkbox(checked = has, onCheckedChange = null, enabled = enabled) },
                colors = clear,
                modifier = Modifier.clickable(enabled = enabled) {
                    val next = if (has) feed.tags.filterNot { it.equals(tag, ignoreCase = true) } else feed.tags + tag
                    store.save(feed.copy(tags = next))
                },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.feed_new_with_tag, tag)) },
            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
            colors = clear,
            modifier = Modifier.clickable { creating = true },
        )
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 12.dp))
    }
}
