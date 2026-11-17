package io.github.eyalshimshi.betterstips.ui.user

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.util.PhotoRules
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Choosing which part of the new profile photo shows (#46), as the site lets you: the photo under a circle, dragged
 * and pinched into place; it always fills the circle. A double tap starts over. [onConfirm] gets the circle's square
 * in [bitmap]'s pixels.
 */
@Composable
fun PhotoCropDialog(bitmap: Bitmap, onCancel: () -> Unit, onConfirm: (PhotoRules.Square) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val diameter = min(width, height) * 0.82f
            val base = PhotoRules.coverScale(bitmap.width, bitmap.height, diameter)
            var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
            var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }

            fun clamp(value: Offset, scale: Float) = Offset(
                PhotoRules.clampPan(value.x, bitmap.width, scale, diameter),
                PhotoRules.clampPan(value.y, bitmap.height, scale, diameter),
            )

            val description = stringResource(R.string.photo_crop_title)
            // As the photo viewer's: taps (a double tap starts over), then pinching and dragging.
            val transform = rememberTransformableState { centroid, zoomChange, panChange, _ ->
                val newZoom = (zoom * zoomChange).coerceIn(1f, PhotoRules.MAX_ZOOM)
                val factor = newZoom / zoom
                // The point under the fingers stays under them while zooming.
                val fromCenter = centroid - Offset(width / 2f, height / 2f)
                val next = fromCenter - (fromCenter - pan) * factor + panChange
                zoom = newZoom
                pan = clamp(next, base * newZoom)
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(bitmap) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = 1f
                                pan = Offset.Zero
                            },
                        )
                    }
                    .transformable(transform),
            ) {
                val scale = base * zoom
                val drawnWidth = bitmap.width * scale
                val drawnHeight = bitmap.height * scale
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (width / 2f + pan.x - drawnWidth / 2f).roundToInt(),
                        (height / 2f + pan.y - drawnHeight / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                // Outside the circle is dimmed: that part won't show on the avatar.
                val center = Offset(width / 2f, height / 2f)
                val circle = Path().apply { addOval(Rect(center, diameter / 2f)) }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.62f)) }
                drawCircle(Color.White.copy(alpha = 0.9f), radius = diameter / 2f, center = center, style = Stroke(width = 2.dp.toPx()))
            }
            Text(
                stringResource(R.string.photo_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        onConfirm(PhotoRules.cropSquare(bitmap.width, bitmap.height, base * zoom, pan.x, pan.y, diameter))
                    },
                ) { Text(stringResource(R.string.photo_crop_use)) }
            }
        }
    }
}
