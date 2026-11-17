package io.github.eyalshimshi.betterstips.ui.feeds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.data.model.TagFeed

/**
 * A feed someone shared (#40), as its link opened Stips+: its name, tags and order, and "Add" to make it one of
 * yours. Nothing is added without the tap.
 */
@Composable
fun SharedFeedDialog(feed: TagFeed, onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DynamicFeed, contentDescription = null) },
        title = { Text(stringResource(R.string.feed_shared_title, feed.label)) },
        text = {
            Column {
                Text(
                    pluralStringResource(R.plurals.feed_tag_count, feed.tags.size, feed.tags.size) + " · " + feedOrderLabel(feed.order),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(feed.tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodyMedium)
                if (feed.excluded.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.feed_shared_excluded, feed.excluded.joinToString(" · ") { "#$it" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(stringResource(R.string.feed_shared_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_shared_not_now)) } },
    )
}
