package io.github.eyalshimshi.betterstips.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.eyalshimshi.betterstips.util.ImageFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Copying and sharing the picture in the viewer as a picture (#43): its bytes from the image cache (or loaded again),
 * written under the app's cache in "shared/", which a FileProvider lets the clipboard or the chosen app read. Pictures
 * only; nothing here installs anything, nor may the app ask to (D-145).
 */
object ImageSharing {
    private const val DIR = "shared"

    /** Files shared earlier go after this long: whoever got them has read them by then. */
    private const val KEEP_MS = 60L * 60 * 1000

    /** The FileProvider's authority, as the manifest declares it. */
    fun authority(context: Context): String = context.packageName + ".files"

    /** The picture at [url] as a file another app may read, and its MIME type; null when it can't be had. */
    suspend fun prepare(context: Context, loader: ImageLoader, url: String): Pair<Uri, String>? = withContext(Dispatchers.IO) {
        try {
            val bytes = cachedBytes(loader, url) ?: loadedBytes(context, loader, url) ?: return@withContext null
            val mime = ImageFiles.mimeOf(bytes.copyOf(minOf(bytes.size, 16))) ?: "image/jpeg"
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            val now = System.currentTimeMillis()
            dir.listFiles()?.filter { now - it.lastModified() > KEEP_MS }?.forEach { it.delete() }
            val file = File(dir, "stips-plus-$now.${ImageFiles.extensionOf(mime)}")
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, authority(context), file) to mime
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The file as it came, kept by the image cache. */
    @OptIn(ExperimentalCoilApi::class)
    private fun cachedBytes(loader: ImageLoader, url: String): ByteArray? =
        loader.diskCache?.openSnapshot(url)?.use { snapshot -> snapshot.data.toFile().readBytes() }

    /** Not in the cache (yet): loaded again and saved as PNG or JPEG. */
    private suspend fun loadedBytes(context: Context, loader: ImageLoader, url: String): ByteArray? {
        val result = loader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
        return ByteArrayOutputStream().use { out ->
            if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
    }

    /** Puts the picture on the clipboard. True when Android confirms the copy itself (13 and later). */
    fun copy(context: Context, uri: Uri): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Stips+", uri))
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /** Android's share sheet, with the picture itself. */
    fun share(context: Context, uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
