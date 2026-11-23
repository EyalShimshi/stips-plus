package io.github.eyalshimshi.betterstips.util

import java.util.Base64

/**
 * The sign-in token as the phone keeps it (v0.25): [PREFIX], the cipher's IV, ":", and the encrypted token, both in
 * Base64. Pure: Android's keystore does the encrypting (data/TokenVault).
 */
object SealedText {
    /** Tells a sealed token from one kept before v0.25: a token from Stips wouldn't start with these words and colon. */
    const val PREFIX = "sealed1:"

    fun isSealed(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    fun join(iv: ByteArray, cipherText: ByteArray): String {
        val encoder = Base64.getEncoder()
        return PREFIX + encoder.encodeToString(iv) + ":" + encoder.encodeToString(cipherText)
    }

    /** The IV and the encrypted bytes, or null when [value] isn't sealed or is damaged. */
    fun split(value: String): Pair<ByteArray, ByteArray>? {
        if (!isSealed(value)) return null
        val parts = value.removePrefix(PREFIX).split(':')
        if (parts.size != 2) return null
        val decoder = Base64.getDecoder()
        val iv = runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return null
        val data = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        return if (iv.isEmpty() || data.isEmpty()) null else iv to data
    }
}
