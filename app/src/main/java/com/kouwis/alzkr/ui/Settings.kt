package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
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
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prefs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch

/** One row of the settings: a coloured tile with an icon, a title, maybe a line under it. */
private data class Setting(val title: String, val icon: ImageVector, val tint: Color, val sub: String = "", val onClick: () -> Unit)

private const val RELEASES = "https://github.com/kouwis/Alzkr/releases/latest"

/**
 * The settings: one list in groups, each row opening its own page (filled in one by one).
 * The app is free, with nothing to buy and no ads; «ادعم التطبيق» is a voluntary gift to its maker.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: AppPrefs, store: Prefs, nav: Nav) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var pickLang by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val soon = soon()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    val groups = listOf(
        t("الإعدادات") to listOf(
            Setting(t("مواقيت الصلاة"), AdhkarIc.clock, Color(0xFF2D8CFF),
                (if (prefs.autoLocate) t("تلقائي") else t("يدوي")) + " · " + prefs.place.ifBlank { t("القاهرة") }) { nav.go(Screen.PrayerSettings) },
            Setting(t("اللغة"), Ic.globe, Color(0xFF1A9BD7), Lang.of(prefs.lang).native) { pickLang = true },
            Setting(t("الأذان"), Ic.speaker, Color(0xFF1FA67A)) { soon(t("الأذان")) },
            Setting(t("المنبهات"), Ic.bell, Color(0xFFFF9F0A)) { soon(t("المنبهات")) },
            Setting(t("الصمت أثناء الصلاة"), Ic.muted, Color(0xFF6E7F8D)) { soon(t("الصمت أثناء الصلاة")) },
            Setting(t("التاريخ"), Ic.calendar, Color(0xFFFF453A), t("أم القرى · يبدأ اليوم الهجري من المغرب")) { soon(t("التاريخ")) },
            Setting(t("المصحف"), Ic.book, Color(0xFF0F8F72)) { soon(t("المصحف")) },
            Setting(t("الملفات الصوتية"), Ic.alarmClock, Color(0xFFF7A21B)) { soon(t("الملفات الصوتية")) },
            Setting(t("السبحة"), Ic.beads, Color(0xFF5E6B78)) { nav.go(Screen.Tasbih) },
        ),
        t("الودجت") to listOf(
            Setting(t("الودجت"), Ic.widgets, Color(0xFF14937E)) { soon(t("الودجت")) },
            Setting(t("شاشة القفل"), Ic.lockClock, Color(0xFF4F5BD5)) { soon(t("شاشة القفل")) },
            Setting(t("الودجت الخاص"), Ic.brush, Color(0xFF1677FF), t("صمّم ودجت لشاشتك الرئيسية")) { soon(t("الودجت الخاص")) },
        ),
        t("التطبيق") to listOf(
            Setting(t("انشر التطبيق"), Ic.share, Color(0xFFE5337A)) {
                val text = t("تطبيق «الذِّكر»: القرآن الكريم والأذكار ومواقيت الصلاة، مجاني وبلا إعلانات\n{0}", RELEASES)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, t("انشر التطبيق")))
            },
            Setting(t("الاقتراحات والشكاوى"), Ic.mail, Color(0xFF1A9BD7)) { open("https://github.com/kouwis/Alzkr/issues") },
            Setting(t("ادعم التطبيق"), Ic.gift, Color(0xFFD9A62E), t("دعم اختياري لصاحب التطبيق")) { soon(t("ادعم التطبيق")) },
        ),
    )

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.close, t("إغلاق"), { nav.back() }) }
            Text(t("الإعدادات"), color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
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
                    Text(t("الذِّكر"), color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold)
                    Text(t("مجاني بالكامل، بلا إعلانات ولا اشتراكات"), color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (version.isNotBlank()) Text(t("النسخة {0}", version), color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }

    if (pickLang) LanguageDialog(Lang.of(prefs.lang), { l -> pickLang = false; scope.launch { store.setLang(l.code) } }) { pickLang = false }
}

/** The interface languages, each written in itself; the chosen one is ticked. */
@Composable
private fun LanguageDialog(current: Lang, onPick: (Lang) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(t("اللغة"), color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(Lang.entries, key = { it.code }) { l ->
                    val on = l == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { if (on) onDismiss() else onPick(l) }.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(l.native, color = if (on) c.primary else c.text, fontSize = 17.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (on) Icon(Ic.check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.primary) } },
    )
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
