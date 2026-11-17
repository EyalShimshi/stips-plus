package io.github.eyalshimshi.betterstips.ui.feeds

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.EmojiTransportation
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.util.EmojiCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read once per run: the picker's groups, each with only the emoji this phone can draw. */
private object EmojiSource {
    @Volatile
    private var groups: List<EmojiCatalog.Group>? = null

    fun load(context: Context): List<EmojiCatalog.Group> = groups ?: synchronized(this) {
        groups ?: run {
            val parsed = context.assets.open(EmojiCatalog.ASSET).bufferedReader().useLines { EmojiCatalog.parse(it) }
            val paint = Paint()
            parsed.map { group -> group.copy(emoji = group.emoji.filter { paint.hasGlyph(it) }) }.filter { it.emoji.isNotEmpty() }
        }.also { groups = it }
    }
}

/**
 * Picking a feed's emoji (v0.24, D-170), any the phone has: the ones picked lately, then Unicode's groups as tabs (as
 * the keyboard's picker), and "No emoji" when the feed has one.
 */
@Composable
fun EmojiPickerSheet(current: String, recent: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<EmojiCatalog.Group>?>(null) }
    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.Default) { EmojiSource.load(context.applicationContext) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.emoji_pick_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (current.isNotEmpty()) {
                TextButton(onClick = { onPick("") }) { Text(stringResource(R.string.emoji_none)) }
            }
        }
        val loaded = groups
        if (loaded == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GRID_HEIGHT),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            EmojiGrid(current, recent, loaded, onPick)
        }
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 8.dp))
    }
}

@Composable
private fun EmojiGrid(current: String, recent: List<String>, groups: List<EmojiCatalog.Group>, onPick: (String) -> Unit) {
    val sections = remember(recent, groups) {
        val mine = recent.filter { picked -> groups.any { picked in it.emoji } }
        (if (mine.isEmpty()) emptyList() else listOf(EmojiCatalog.Group(EmojiCatalog.RECENT, mine))) + groups
    }
    // Where each section's heading is in the grid: the tabs go there, and follow the scrolling.
    val starts = remember(sections) { sections.runningFold(0) { at, section -> at + 1 + section.emoji.size }.dropLast(1) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val selected by remember(starts) {
        derivedStateOf { starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) }
    }
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp) {
        sections.forEachIndexed { index, section ->
            Tab(
                selected = index == selected,
                onClick = { scope.launch { grid.animateScrollToItem(starts[index]) } },
                icon = { Icon(groupIcon(section.key), contentDescription = groupName(section.key)) },
            )
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        state = grid,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(GRID_HEIGHT),
    ) {
        sections.forEach { section ->
            item(key = "group-" + section.key, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    groupName(section.key),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            items(section.emoji, key = { section.key + "/" + it }) { emoji ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (emoji == current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onPick(emoji) }
                        .semantics { contentDescription = emoji },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 28.sp)
                }
            }
        }
    }
}

private val GRID_HEIGHT = 340.dp

private fun groupIcon(key: String): ImageVector = when (key) {
    EmojiCatalog.RECENT -> Icons.Outlined.History
    "people" -> Icons.Outlined.EmojiPeople
    "animals" -> Icons.Outlined.EmojiNature
    "food" -> Icons.Outlined.EmojiFoodBeverage
    "travel" -> Icons.Outlined.EmojiTransportation
    "activities" -> Icons.Outlined.EmojiEvents
    "objects" -> Icons.Outlined.EmojiObjects
    "symbols" -> Icons.Outlined.EmojiSymbols
    "flags" -> Icons.Outlined.EmojiFlags
    else -> Icons.Outlined.EmojiEmotions
}

@Composable
private fun groupName(key: String): String = stringResource(
    when (key) {
        EmojiCatalog.RECENT -> R.string.emoji_group_recent
        "people" -> R.string.emoji_group_people
        "animals" -> R.string.emoji_group_animals
        "food" -> R.string.emoji_group_food
        "travel" -> R.string.emoji_group_travel
        "activities" -> R.string.emoji_group_activities
        "objects" -> R.string.emoji_group_objects
        "symbols" -> R.string.emoji_group_symbols
        "flags" -> R.string.emoji_group_flags
        else -> R.string.emoji_group_smileys
    },
)
