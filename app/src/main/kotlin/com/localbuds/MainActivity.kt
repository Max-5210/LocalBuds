package com.localbuds

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localbuds.find.FindSide
import com.localbuds.ui.LocalBudsApp
import com.localbuds.ui.nav.NavRequest
import com.localbuds.ui.nav.Routes
import com.localbuds.ui.nav.Tab
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single activity hosting the Compose UI. `singleTask` in the manifest, so the notification,
 * tile and widget re-use it and deliver their tab request through [onNewIntent].
 */
class MainActivity : ComponentActivity() {

    private val tabRequests = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            LocalBudsApp(tabRequests = tabRequests, onTabRequestHandled = { tabRequests.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Tab.fromExtra(intent?.getStringExtra(EXTRA_OPEN_TAB))?.let { tabRequests.value = NavRequest(it) }
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        intent?.getStringExtra(EXTRA_FIND)?.takeUnless { fromHistory }?.let { side ->
            if (fromLocalBuds(intent, "find")) {
                FindSide.entries.firstOrNull { it.name.equals(side, ignoreCase = true) }?.let { appContainer.find.start(it) }
            }
            tabRequests.value = NavRequest(Tab.HOME, Routes.HOME)
            intent.removeExtra(EXTRA_FIND)
        }
    }

    private fun fromLocalBuds(intent: Intent, what: String): Boolean {
        val origin = LaunchOrigin.of(this, intent)
        val allowed = LaunchOrigin.mayRunAppAction(origin.caller)
        if (!allowed) Log.w(TAG, "$what request ignored: started by ${origin.packageName ?: "an unknown app"} (${origin.caller})")
        return allowed
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.localbuds.EXTRA_OPEN_TAB"
        const val EXTRA_FIND = "com.localbuds.EXTRA_FIND"
        private const val TAG = "LocalBuds/Main"
    }
}
