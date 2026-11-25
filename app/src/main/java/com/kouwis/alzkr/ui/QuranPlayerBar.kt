package com.kouwis.alzkr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kouwis.alzkr.audio.QuranAudio
import com.kouwis.alzkr.audio.Recitation
import com.kouwis.alzkr.audio.Reciters
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.QuranData
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.t

/** The recitation playing: the reciter (tap to change him), the verse, previous / play-pause / next, close. */
@Composable
fun QuranPlayerBar(q: QuranData, r: Recitation, onReciter: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reciter = Reciters.byId(r.reciter)
    val bg = Color(0xFF1E2A28)
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).springClick(label = t("القارئ"), pressedScale = 0.97f, onClick = onReciter)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("${reciter.name} · ${reciter.style}", color = Color(0xFFE9C77B), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t("{0} · الآية {1}", q.surahName(r.s), num(r.a)), color = Color.White, fontSize = 15.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
        }
        PlayerButton(Ic.nextTrack, t("الآية السابقة")) { QuranAudio.previous(context) }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE9C77B)).springClick(label = t("تشغيل")) { QuranAudio.toggle(context) },
            contentAlignment = Alignment.Center,
        ) {
            if (r.loading && r.playing) CircularProgressIndicator(color = Color(0xFF1B1B1B), strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            else Icon(if (r.playing) Ic.pause else Ic.play, null, tint = Color(0xFF1B1B1B), modifier = Modifier.size(22.dp))
        }
        PlayerButton(Ic.prevTrack, t("الآية التالية")) { QuranAudio.next(context) }
        PlayerButton(Ic.close, t("إغلاق")) { QuranAudio.stop(context) }
    }
}

@Composable
private fun PlayerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).springClick(label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Choosing the reciter (kept for next time). */
@Composable
fun ReciterPicker(onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val current = Reciters.current(context).id
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).padding(vertical = 14.dp)) {
            Text(t("القارئ"), color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(Reciters.all, key = { it.id }) { r ->
                    val on = r.id == current
                    Row(
                        Modifier.fillMaxWidth().springClick(label = r.name, pressedScale = 0.98f) { QuranAudio.choose(context, r.id); onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = if (on) c.primary else c.text, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            Text(r.style, color = c.muted, fontSize = 12.sp)
                        }
                        if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                            Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
