package io.github.eyalshimshi.betterstips

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.betterstips.notify.Notifier
import io.github.eyalshimshi.betterstips.ui.AppRoot
import io.github.eyalshimshi.betterstips.ui.components.LocalAppContainer
import io.github.eyalshimshi.betterstips.ui.theme.StipsTheme
import io.github.eyalshimshi.betterstips.ui.theme.isDarkTheme
import io.github.eyalshimshi.betterstips.util.AppLanguage
import io.github.eyalshimshi.betterstips.util.DeepLink
import io.github.eyalshimshi.betterstips.util.Links
import io.github.eyalshimshi.betterstips.util.ShareRules
import io.github.eyalshimshi.betterstips.util.Shortcuts
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A link to open (from another app or a phone notification), until AppRoot has navigated to it. */
    private val pendingLink = MutableStateFlow<DeepLink?>(null)

    /** In the language picked in Stips+ (#32), before Android 13; from 13 Android applies it itself. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as StipsApp).container
        container.warmUp()
        // After a rotation or a restore, the back stack already holds the linked screen.
        if (savedInstanceState == null) {
            markTapped(intent)
            pendingLink.value = linkFrom(intent)
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val link by pendingLink.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            // The direction of the language the screen shows, not the one the configuration reports: after picking
            // Hebrew and then the phone's (Hebrew) language again, Android left the app left-to-right until a restart (#47).
            val locale = LocalConfiguration.current.locales[0]
            val direction = remember(locale) {
                if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
            }
            CompositionLocalProvider(LocalAppContainer provides container, LocalLayoutDirection provides direction) {
                StipsTheme(settings) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AppRoot(pendingLink = link, onLinkHandled = { pendingLink.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        markTapped(intent)
        val link = linkFrom(intent)
        if (link != null) pendingLink.value = link
    }

    override fun onStart() {
        super.onStart()
        // On screen, the app's own counts are fresh: phone notification checks wait (v0.11). Coming back also makes
        // sure they're still scheduled.
        Notifier.appVisible = true
        val container = (application as StipsApp).container
        Notifier.sync(applicationContext, container.settings.settings.value)
        // Your feeds' lists are brought up to date while the app is on screen (v0.24).
        container.feedWarmer.start()
    }

    override fun onStop() {
        Notifier.appVisible = false
        val container = (application as StipsApp).container
        container.feedWarmer.stop()
        // The first check after leaving comes a few minutes later.
        Notifier.sync(applicationContext, container.settings.settings.value)
        super.onStop()
    }

    /** A Stips link (from another app or a phone notification), a launcher shortcut, or a chats widget row. */
    private fun linkFrom(intent: Intent?): DeepLink? {
        // A notice from Stips' team opens over the app, in full (v0.18, #27); only from Stips+'s own notifications.
        intent?.let { Notifier.noticeFrom(this, it) }?.let { return DeepLink.TeamNotice(it) }
        // Text shared from another app (v0.19): the chats picker. Any app may start this; it only fills in the picker.
        if (intent != null && intent.action == ShareActivity.ACTION_SEND_TO_CHATS) {
            return ShareRules.clean(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.let { DeepLink.SendTo(it) }
        }
        val link = Links.deepLink(intent?.dataString) ?: Shortcuts.link(intent?.action, intent?.dataString)
        // A phone notification says where to open its question (v0.10).
        val focus = intent?.let { Notifier.focusFrom(it) }
        return if (link is DeepLink.Question && focus != null) link.copy(focus = focus) else link
    }

    /** A tapped phone notification's notifications are read now (v0.18, #27): the bell's badge and its "new" drop them. */
    private fun markTapped(intent: Intent?) {
        val ids = intent?.let { Notifier.notificationIdsFrom(this, it) }.orEmpty()
        if (ids.isEmpty()) return
        val container = (application as StipsApp).container
        val me = container.session.session.value.userId ?: return
        container.seenNotifications.markHandled(me, ids)
    }
}
