package io.github.eyalshimshi.betterstips.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.util.ImageLinks
import io.github.eyalshimshi.betterstips.util.Links
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * A photo, full screen on black: pinch or double-tap to zoom, drag to look around (#4). [previewUrl] (already in
 * the cache) shows at once while [fullUrl] loads on top of it. A picture from a link (#15) has no [fullUrl] while
 * its page is read, is [unavailable] when it can't be, and can be opened in the browser instead. The ⋮ menu (#43)
 * copies or shares the picture itself, shares [link] (the picture's address by default), and opens the browser.
 * [onLoadFailed] hears when the picture couldn't be loaded.
 */
@Composable
fun ImageViewerDialog(
    fullUrl: String?,
    previewUrl: String?,
    caption: String?,
    onDismiss: () -> Unit,
    onOpenInBrowser: (() -> Unit)? = null,
    unavailable: Boolean = false,
    link: String? = fullUrl,
    onLoadFailed: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val loader = LocalAppContainer.current.imageLoader
        val haptics = LocalHapticFeedback.current
        var loaded by remember(fullUrl) { mutableStateOf(false) }
        var failed by remember(fullUrl) { mutableStateOf(false) }
        val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
        LaunchedEffect(unavailable, failed) {
            if (unavailable || failed) currentOnLoadFailed?.invoke()
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            fun clamp(value: Offset, zoom: Float): Offset {
                val maxX = (zoom - 1f) * width / 2f
                val maxY = (zoom - 1f) * height / 2f
                return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
            }

            val state = rememberTransformableState { centroid, zoomChange, panChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                val actualZoom = newScale / scale
                // Keep the point under the fingers in place while zooming.
                val fromCenter = centroid - Offset(width / 2f, height / 2f)
                val zoomed = fromCenter - (fromCenter - offset) * actualZoom
                scale = newScale
                offset = clamp(zoomed + panChange, newScale)
            }
            val zoomModifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1f) onDismiss() },
                        onDoubleTap = { tap ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val fromCenter = tap - Offset(width / 2f, height / 2f)
                                scale = DOUBLE_TAP_ZOOM
                                offset = clamp(fromCenter - fromCenter * DOUBLE_TAP_ZOOM, DOUBLE_TAP_ZOOM)
                            }
                        },
                    )
                }
                .transformable(state, canPan = { scale > 1f })
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
            Box(zoomModifier) {
                if (previewUrl != null) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        imageLoader = loader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (fullUrl != null) {
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = caption,
                        imageLoader = loader,
                        contentScale = ContentScale.Fit,
                        onSuccess = { loaded = true },
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            val cantShow = unavailable || (failed && previewUrl == null)
            if (cantShow) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.image_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                    if (onOpenInBrowser != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onOpenInBrowser) { Text(stringResource(R.string.image_open_browser), color = Color.White) }
                    }
                }
            } else if (!loaded && previewUrl == null) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
            // The picture that's showing: the full one once it's in, or the preview meanwhile.
            val shownUrl = when {
                fullUrl != null && loaded -> fullUrl
                previewUrl != null -> previewUrl
                else -> null
            }
            ImageMenu(
                imageUrl = shownUrl,
                link = link,
                onOpenInBrowser = onOpenInBrowser,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * The picture behind an image link (#15), in the viewer above: an imgbb page is read first (only now, on the tap),
 * then the picture loads under a spinner. imgbb's small version is a square crop, so it isn't shown first: it would
 * stick out around a wide picture. "Open in browser" keeps the old way one tap away; a picture that can't be shown
 * turns its attached link back into a link (#43).
 */
@Composable
fun ImageLinkViewer(source: ImageLinks.Source, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var picture by remember(source) { mutableStateOf<ImageLinks.Picture?>(null) }
    var unavailable by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        val found = container.imageLinks.resolve(source)
        if (found == null) unavailable = true else picture = found
    }
    ImageViewerDialog(
        fullUrl = picture?.imageUrl,
        previewUrl = null,
        caption = Links.host(source.link),
        onDismiss = onDismiss,
        onOpenInBrowser = { openInBrowser(context, source.link) },
        unavailable = unavailable,
        link = source.link,
        onLoadFailed = { container.imageLinks.markFailed(source.link) },
    )
}

/**
 * The viewer's ⋮ (#43): copy the picture, share it, share its link, open it in the browser. The picture itself waits
 * until one has loaded ([imageUrl] null before).
 */
@Composable
private fun ImageMenu(imageUrl: String?, link: String?, onOpenInBrowser: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = LocalAppContainer.current.imageLoader
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val copiedText = stringResource(R.string.image_copied)
    val failedText = stringResource(R.string.image_share_failed)

    fun withFile(action: (Uri, String) -> Unit) {
        val url = imageUrl ?: return
        if (working) return
        working = true
        scope.launch {
            try {
                val file = ImageSharing.prepare(context, loader, url)
                if (file == null) Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show() else action(file.first, file.second)
            } finally {
                working = false
            }
        }
    }

    Box(modifier) {
        FilledTonalIconButton(onClick = { open = true }) {
            if (working) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.image_actions))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_copy)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                enabled = imageUrl != null,
                onClick = {
                    open = false
                    withFile { uri, _ -> if (!ImageSharing.copy(context, uri)) Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show() }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                enabled = imageUrl != null,
                onClick = {
                    open = false
                    withFile { uri, mime -> ImageSharing.share(context, uri, mime) }
                },
            )
            if (link != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.image_share_link)) },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    onClick = {
                        open = false
                        shareText(context, link)
                    },
                )
            }
            val external = onOpenInBrowser ?: link?.let { { openInBrowser(context, it) } }
            if (external != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.image_open_browser)) },
                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                    onClick = {
                        open = false
                        external()
                    },
                )
            }
        }
    }
}