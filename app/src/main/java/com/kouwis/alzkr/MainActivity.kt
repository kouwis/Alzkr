package com.kouwis.alzkr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.ui.AlzkrApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppFonts.init(this)
        setContent { AlzkrApp() }
    }
}
