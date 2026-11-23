package io.github.eyalshimshi.betterstips.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.eyalshimshi.betterstips.R

/**
 * Asking who's holding the phone (v0.25, D-176), with Android's own screens and no library: the fingerprint or face,
 * or the phone's PIN, pattern or password (one prompt from Android 10; before that, the lock screen's own check).
 * Nothing about it reaches the app but "yes".
 */
object DeviceAuth {
    /** Whether the phone has a screen lock: without one there's nothing to check, so chats can't be locked. */
    fun available(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Shows the check over [activity]; [onSuccess] runs only when it passes. Before Android 10 it's the lock screen's
     * own screen, started by [onKeyguard] for its result. A cancelled check does nothing: what it guards stays shut.
     */
    @Suppress("DEPRECATION")
    fun check(activity: Activity, title: String, subtitle: String, onSuccess: () -> Unit, onKeyguard: (Intent) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builder = BiometricPrompt.Builder(activity).setTitle(title).setSubtitle(subtitle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                builder.setDeviceCredentialAllowed(true)
            }
            val prompt = try {
                builder.build()
            } catch (e: RuntimeException) {
                return
            }
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                },
            )
            return
        }
        val intent = activity.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(title, subtitle) ?: return
        onKeyguard(intent)
    }
}

/** The check, ready to show from a composable: call it, and [onSuccess] runs once the person passes. */
@Composable
fun rememberDeviceCheck(onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val succeeded by rememberUpdatedState(onSuccess)
    val title = stringResource(R.string.chat_lock_prompt_title)
    val subtitle = stringResource(R.string.chat_lock_prompt_subtitle)
    val keyguard = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) succeeded()
    }
    return remember(context, title, subtitle, keyguard) {
        {
            context.findActivity()?.let { activity ->
                DeviceAuth.check(activity, title, subtitle, onSuccess = { succeeded() }, onKeyguard = { keyguard.launch(it) })
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
