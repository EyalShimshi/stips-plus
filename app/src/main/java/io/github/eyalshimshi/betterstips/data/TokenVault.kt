package io.github.eyalshimshi.betterstips.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.eyalshimshi.betterstips.util.SealedText
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the sign-in token (v0.25, D-177): AES-GCM with a key Android's keystore makes and keeps, in the phone's secure
 * hardware where it has some. The key can't be copied out, so a copy of the app's files alone doesn't hold a token
 * that works. It doesn't stop someone with root on the phone itself, who can ask the keystore as the app does.
 */
object TokenVault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "stipsplus.session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    /** What opening a sealed token found. */
    sealed interface Opened {
        data class Token(val value: String) : Opened

        /** For good: its key is gone, or the text is damaged or no longer matches it. Signing in again is the way. */
        data object Lost : Opened

        /** The keystore didn't answer this time (a busy or broken keystore): the token may open on the next start. */
        data object Unavailable : Opened
    }

    /** [plain] sealed, or null when the keystore can't (a broken one on a few phones). */
    fun seal(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey())
        SealedText.join(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    } catch (e: Exception) {
        null
    }

    /** The token in [sealed]; [Opened.Lost] only when it can never be read, any other failure [Opened.Unavailable]. */
    fun open(sealed: String): Opened {
        val (iv, data) = SealedText.split(sealed) ?: return Opened.Lost
        val key = try {
            existingKey() ?: return Opened.Lost
        } catch (e: Exception) {
            return Opened.Unavailable
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            Opened.Token(String(cipher.doFinal(data), Charsets.UTF_8))
        } catch (e: AEADBadTagException) {
            Opened.Lost
        } catch (e: KeyPermanentlyInvalidatedException) {
            Opened.Lost
        } catch (e: Exception) {
            Opened.Unavailable
        }
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey
    }

    private fun newKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
