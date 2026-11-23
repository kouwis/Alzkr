package com.kouwis.alzkr.alerts

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.ui.AlzkrTheme
import com.kouwis.alzkr.ui.springClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/** The alarm, full screen over the lock screen: the time, what it is for, «إيقاف» and «غفوة». */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppFonts.init(this)
        val lang = runCatching { runBlocking { Prefs(applicationContext).flow.first().lang } }.getOrDefault("ar")
        I18n.set(this, Lang.of(lang))
        setContent { AlzkrTheme { AlarmPage(::stop, ::snooze, ::finish) } }
    }

    private fun send(action: String) {
        startService(Intent(this, SoundService::class.java).setAction(action))
        finish()
    }

    private fun stop() = send(SoundService.ACTION_STOP)
    private fun snooze() = send(SoundService.ACTION_SNOOZE)
}

@Composable
private fun AlarmPage(onStop: () -> Unit, onSnooze: () -> Unit, onGone: () -> Unit) {
    val e by SoundService.ringing.collectAsState()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(e) { if (e != null) seen = true else if (seen) onGone() }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    val h12 = if (now.hour % 12 == 0) 12 else now.hour % 12
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D2A3F), Color(0xFF1E4E79), Color(0xFF0E3B33)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ArabicText.digitsOf("%d:%02d".format(h12, now.minute)), color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Light)
            Text(e?.title.orEmpty(), color = Color.White, fontSize = 24.sp, fontFamily = AppFonts.naskhBold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp))
            if (e != null && e?.text != e?.title) Text(e?.text.orEmpty(), color = Color(0xCCFFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.height(80.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Big(t("غفوة {0} دقائق", ArabicText.num(SoundService.SNOOZE_MIN)), Color(0x33FFFFFF), Modifier.weight(1f), onSnooze)
                Big(t("إيقاف"), Color(0xFFE9C77B), Modifier.weight(1f), onStop, dark = true)
            }
        }
    }
}

@Composable
private fun Big(label: String, bg: Color, modifier: Modifier, onClick: () -> Unit, dark: Boolean = false) {
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).background(bg).springClick(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (dark) Color(0xFF1B1B1B) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}
