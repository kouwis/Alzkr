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
        AppFonts.init(this)
        if (savedInstanceState == null) open.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent { AlzkrApp(open.value) { open.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { open.value = it }
    }
}
