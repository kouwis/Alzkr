package com.kouwis.alzkr.alerts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.ui.springClick

/**
 * Inside the app, over every page: a ringing alarm shows its full page (stop / snooze), and an adhan
 * playing shows a bar to stop it. So opening the app is always a way to silence it.
 */
@Composable
fun SoundingOverlay() {
    val context = LocalContext.current
    val ring by SoundService.ringing.collectAsState()
    val adhan by SoundService.playing.collectAsState()
    fun send(action: String) = runCatching { context.startService(Intent(context, SoundService::class.java).setAction(action)) }
    if (ring != null) {
        AlarmPage(onStop = { send(SoundService.ACTION_STOP) }, onSnooze = { send(SoundService.ACTION_SNOOZE) }, onGone = {})
        return
    }
    val e = adhan ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E4E79))
                .springClick(label = t("إيقاف الأذان")) { send(SoundService.ACTION_STOP) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t("إيقاف الأذان"), color = Color(0xFFE9C77B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
