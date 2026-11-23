package com.kouwis.alzkr.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs

/** One row of the settings: a coloured tile with an icon, a title, maybe a line under it. */
private data class Setting(val title: String, val icon: ImageVector, val tint: Color, val sub: String = "", val onClick: () -> Unit)

private const val RELEASES = "https://github.com/kouwis/Alzkr/releases/latest"

/**
 * The settings: one list in groups, each row opening its own page (filled in one by one).
 * The app is free, with nothing to buy and no ads; «ادعم التطبيق» is a voluntary gift to its maker.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: AppPrefs, nav: Nav) {
    val c = LocalColors.current
    val context = LocalContext.current
    val soon = soon()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    val groups = listOf(
        "الإعدادات" to listOf(
            Setting("مواقيت الصلاة", AdhkarIc.clock, Color(0xFF2D8CFF),
                (if (prefs.autoLocate) "تلقائي" else "يدوي") + " · " + prefs.place.ifBlank { "القاهرة" }) { nav.go(Screen.PrayerSettings) },
            Setting("اللغة", Ic.globe, Color(0xFF1A9BD7), "العربية") { soon("اللغة") },
            Setting("الأذان", Ic.speaker, Color(0xFF1FA67A)) { soon("الأذان") },
            Setting("المنبهات", Ic.bell, Color(0xFFFF9F0A)) { soon("المنبهات") },
            Setting("الصمت أثناء الصلاة", Ic.muted, Color(0xFF6E7F8D)) { soon("الصمت أثناء الصلاة") },
            Setting("التاريخ", Ic.calendar, Color(0xFFFF453A), "أم القرى · يبدأ اليوم الهجري من المغرب") { soon("التاريخ") },
            Setting("المصحف", Ic.book, Color(0xFF0F8F72)) { soon("المصحف") },
            Setting("الملفات الصوتية", Ic.alarmClock, Color(0xFFF7A21B)) { soon("الملفات الصوتية") },
            Setting("السبحة", Ic.beads, Color(0xFF5E6B78)) { nav.go(Screen.Tasbih) },
        ),
        "الودجت" to listOf(
            Setting("الودجت", Ic.widgets, Color(0xFF14937E)) { soon("الودجت") },
            Setting("شاشة القفل", Ic.lockClock, Color(0xFF4F5BD5)) { soon("شاشة القفل") },
            Setting("الودجت الخاص", Ic.brush, Color(0xFF1677FF), "صمّم ودجت لشاشتك الرئيسية") { soon("الودجت الخاص") },
        ),
        "التطبيق" to listOf(
            Setting("انشر التطبيق", Ic.share, Color(0xFFE5337A)) {
                val text = "تطبيق «الذِّكر»: القرآن الكريم والأذكار ومواقيت الصلاة، مجاني وبلا إعلانات\n$RELEASES"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "انشر التطبيق"))
            },
            Setting("الاقتراحات والشكاوى", Ic.mail, Color(0xFF1A9BD7)) { open("https://github.com/kouwis/Alzkr/issues") },
            Setting("ادعم التطبيق", Ic.gift, Color(0xFFD9A62E), "دعم اختياري لصاحب التطبيق") { soon("ادعم التطبيق") },
        ),
    )

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.close, "إغلاق", { nav.back() }) }
            Text("الإعدادات", color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { (title, rows) ->
                item(key = "h:$title") {
                    Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                }
                item(key = "g:$title") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card)
                            .border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp),
                    ) {
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.6f)))
                            SettingRow(r)
                        }
                    }
                }
            }
            item(key = "foot") {
                Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الذِّكر", color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold)
                    Text("مجاني بالكامل، بلا إعلانات ولا اشتراكات", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (version.isNotBlank()) Text("النسخة $version", color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(r: Setting) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = r.title, pressedScale = 0.98f, onClick = r.onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(r.tint), contentAlignment = Alignment.Center) {
            Icon(r.icon, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (r.sub.isNotBlank()) Text(r.sub, color = c.muted, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}
