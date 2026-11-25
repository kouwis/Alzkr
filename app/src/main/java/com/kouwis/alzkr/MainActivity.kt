package com.kouwis.alzkr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.ui.AlzkrApp

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN = "open" }

    /** An adhkar section to open, from a reminder's notification. */
    private val open = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.kouwis.alzkr.data.CrashLog.install(this)
        AppFonts.init(this)
        if (savedInstanceState == null) open.value = intent?.getStringExtra(EXTRA_OPEN)
        // the opening animation, on a fresh start only (not when a widget or a notification opens the app)
        val splash = mutableStateOf(savedInstanceState == null && open.value == null)
        setContent {
            com.kouwis.alzkr.ui.Responsive {
                androidx.compose.foundation.layout.Box {
                    AlzkrApp(open.value) { open.value = null }
                    if (splash.value) com.kouwis.alzkr.ui.Splash { splash.value = false }
                    // whatever is sounding can always be stopped from inside the app
                    com.kouwis.alzkr.alerts.SoundingOverlay()
                }
            }
        }
    }

    /** While an adhan or an alarm sounds, a volume button stops it. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val vol = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        if (vol && com.kouwis.alzkr.alerts.SoundService.active()) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) com.kouwis.alzkr.alerts.SoundService.stopAll(this)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { open.value = it }
    }
}
