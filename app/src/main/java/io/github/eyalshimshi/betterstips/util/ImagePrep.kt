package io.github.eyalshimshi.betterstips.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The profile photo (#46): the picked photo read upright for the crop screen, then the square picked there as the JPEG
 * Stips gets. Call off the main thread.
 */
object ImagePrep {
    /** The photo at [uri], upright, at most [maxSide] px on its longer side, in memory the app can cut and compress. */
    fun decodeUpright(context: Context, uri: Uri, maxSide: Int = PhotoRules.DECODE_MAX_SIDE): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns a photo upright itself, and reads HEIF too.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Android 8. Reading only the size returns no bitmap: 0.22 took that for a failure, so no photo could ever be
        // read and changing it always ended in an error (#46).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: error("Can't read the photo")).use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) error("Not a photo")
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val decoded = (resolver.openInputStream(uri) ?: error("Can't read the photo")).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Can't decode the photo")
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        if (scale < 1f) matrix.postScale(scale, scale)
        return if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    /**
     * [square] of [bitmap] as the JPEG Stips gets, at most [maxSide] px across, on white (a transparent picture would
     * turn black in a JPEG); and its side in px.
     */
    fun squareJpeg(bitmap: Bitmap, square: PhotoRules.Square, maxSide: Int = PhotoRules.UPLOAD_SIDE): Pair<ByteArray, Int> {
        val side = square.side.coerceIn(1, max(1, min(bitmap.width, bitmap.height)))
        val x = square.x.coerceIn(0, (bitmap.width - side).coerceAtLeast(0))
        val y = square.y.coerceIn(0, (bitmap.height - side).coerceAtLeast(0))
        val target = min(side, maxSide)
        val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, Rect(x, y, x + side, y + side), Rect(0, 0, target, target), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        out.recycle()
        return bytes to target
    }
}