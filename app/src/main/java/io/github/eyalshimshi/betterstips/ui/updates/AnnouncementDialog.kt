package io.github.eyalshimshi.betterstips.ui.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.ui.components.LinkifiedText
import io.github.eyalshimshi.betterstips.ui.components.openInBrowser
import io.github.eyalshimshi.betterstips.util.AnnouncementRules

/**
 * An announcement from Stips+ (#41), in the app's language when it has one: its title, its text and its link. Closing
 * it, or opening the link, is [onDismiss]: it doesn't pop up again, and Settings → About shows it any time.
 */
@Composable
fun AnnouncementDialog(announcement: AnnouncementRules.Announcement, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hebrew = LocalConfiguration.current.locales[0].language.let { it == "he" || it == "iw" }
    val title = announcement.title?.pick(hebrew)?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.announcement_title)
    val link = announcement.link
    val gotIt: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.announcement_ok)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                LinkifiedText(announcement.text.pick(hebrew), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = if (link != null) {
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        openInBrowser(context, link)
                    },
                ) {
                    Text(announcement.linkText?.pick(hebrew)?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.announcement_open))
                }
            }
        } else {
            gotIt
        },
        dismissButton = if (link != null) gotIt else null,
    )
}
