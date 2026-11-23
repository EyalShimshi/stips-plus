package io.github.eyalshimshi.betterstips.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import io.github.eyalshimshi.betterstips.R
import io.github.eyalshimshi.betterstips.ui.components.BackButton
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.theme.LocalAppSettings

/**
 * The chats lock (v0.25, D-176): while chats are locked, the Chats tab, any chat and sending to chats show this
 * instead, and ask at once. Only with the lock on, someone signed in, and a screen lock on the phone to check:
 * without one, nothing could open them again, so they stay open.
 */
@Composable
fun ChatLockGate(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val container = LocalAppContainer.current
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val session by container.session.session.collectAsStateWithLifecycle()
    val unlocked by container.chatLock.unlocked.collectAsStateWithLifecycle()
    val locked = settings.chatLock && session.signedIn && !unlocked && DeviceAuth.available(context)
    if (locked) ChatsLocked(onBack, onUnlock = container.chatLock::unlock) else content()
}

@Composable
private fun ChatsLocked(onBack: (() -> Unit)?, onUnlock: () -> Unit) {
    val check = rememberDeviceCheck(onUnlock)
    val owner = LocalLifecycleOwner.current
    // As the screen opens, or comes back locked: the check is the reason someone's here. It waits until the screen is
    // fully in front (back from the background, or at the end of the slide in), since a prompt asked for earlier is
    // cancelled by the system.
    LaunchedEffect(owner) { owner.withResumed { check() } }
    Scaffold(
        topBar = {
            if (onBack != null) TopAppBar(title = {}, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_lock_locked_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.chat_lock_locked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = check) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_lock_unlock))
            }
        }
    }
}
