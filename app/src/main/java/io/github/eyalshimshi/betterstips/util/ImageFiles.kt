package io.github.eyalshimshi.betterstips.util

/** What kind of picture a file is, from its first bytes (#43: copying and sharing a picture as a file). Pure. */
object ImageFiles {
    /** The picture's MIME type, from its first bytes; null when it isn't one Stips+ knows. */
    fun mimeOf(head: ByteArray): String? = when {
        head.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        head.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
        head.startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
        head.size >= 12 && head.startsWith(0x52, 0x49, 0x46, 0x46) &&
            head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() && head[10] == 'B'.code.toByte() && head[11] == 'P'.code.toByte() ->
            "image/webp"
        head.startsWith(0x42, 0x4D) -> "image/bmp"
        else -> null
    }

    /** The file name ending for [mime]. */
    fun extensionOf(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { this[it] == bytes[it].toByte() }
}
