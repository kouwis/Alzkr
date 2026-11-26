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

/** Downloads the newest version straight away (every release carries the same file as Alzkr.apk). */
private const val RELEASES = "https://github.com/kouwis/Alzkr/releases/latest/download/Alzkr.apk"
/** «ادعم التطبيق»: a gift to the app's maker through InstaPay (Egypt). */
private const val INSTAPAY = "https://ipn.eg/S/mkouwis/instapay/1U7MKE"
private const val INSTAPAY_ADDRESS = "mkouwis@instapay"

/** A voluntary gift: open InstaPay, or copy the address to pay from any bank app. */
@Composable
private fun SupportDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        icon = { Icon(Ic.gift, null, tint = Color(0xFFD9A62E), modifier = Modifier.size(34.dp)) },
        title = { Text(t("ادعم التطبيق"), color = c.text, fontFamily = AppFonts.naskhBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    t("التطبيق مجاني بالكامل وبلا إعلانات. إن أحببت أن تدعم استمراره فهدية اختيارية عبر إنستاباي، وجزاك الله خيرًا."),
                    color = c.muted, fontSize = 14.sp, lineHeight = 22.sp,
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip)
                        .clickable {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("InstaPay", INSTAPAY_ADDRESS))
                            android.widget.Toast.makeText(context, t("تم النسخ"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(INSTAPAY_ADDRESS, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Ic.copy, t("نسخ"), tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onOpen(); onDismiss() }) {
                Text(t("ادفع عبر إنستاباي"), color = c.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.muted) } },
    )
}

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
    var pickDigits by remember { mutableStateOf(false) }
    var support by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var crash by remember { mutableStateOf(com.kouwis.alzkr.data.CrashLog.read(context)) }
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
            Setting(t("شكل الأرقام"), Ic.lines, Color(0xFF5E5CE6), digitLabel(com.kouwis.alzkr.data.ArabicText.digitMode)) { pickDigits = true },
            Setting(t("الأذان والتنبيهات"), Ic.speaker, Color(0xFF1FA67A), t("صوت الأذان، المنبّهات، التذكيرات")) { nav.go(Screen.Alerts) },
            Setting(t("الصمت أثناء الصلاة"), Ic.muted, Color(0xFF6E7F8D), if (prefs.alerts.silent.on) t("مفعّل") else t("غير مفعّل")) { nav.go(Screen.Silent) },
            Setting(t("التاريخ"), Ic.calendar, Color(0xFFFF453A), com.kouwis.alzkr.data.Hijri.hijriText(com.kouwis.alzkr.data.Hijri.of(java.time.LocalDate.now()))) { nav.go(Screen.DateSettings) },
            Setting(t("المصحف"), Ic.book, Color(0xFF0F8F72), t("لون خلفية الصفحة")) { nav.go(Screen.MushafSettings) },
        ),
        t("الودجت") to listOf(
            Setting(t("الودجت"), Ic.widgets, Color(0xFF14937E), t("مواقيت الصلاة، التاريخ، ذكر، آية اليوم، السبحة")) { nav.go(Screen.Widgets) },
            Setting(t("شاشة القفل"), Ic.lockClock, Color(0xFF4F5BD5), t("الصلاة القادمة على شاشة القفل، وودجات صغيرة لها")) { nav.go(Screen.LockScreen) },
            Setting(t("الودجت الخاص"), Ic.brush, Color(0xFF1677FF), t("صمّم ودجت لشاشتك الرئيسية")) { nav.go(Screen.WidgetDesign("custom")) },
        ),
        t("التطبيق") to listOf(
            Setting(t("انشر التطبيق"), Ic.share, Color(0xFFE5337A)) {
                val text = t("تطبيق «الذِّكر»: القرآن الكريم والأذكار ومواقيت الصلاة، مجاني وبلا إعلانات\n{0}", RELEASES)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, t("انشر التطبيق")))
            },
            Setting(t("الاقتراحات والشكاوى"), Ic.mail, Color(0xFF1A9BD7)) { open("https://github.com/kouwis/Alzkr/issues") },
            Setting(t("ادعم التطبيق"), Ic.gift, Color(0xFFD9A62E), t("دعم اختياري لصاحب التطبيق")) { support = true },
        ) + listOfNotNull(crash?.let { report ->
            Setting(t("إرسال تقرير العطل"), Ic.info, Color(0xFFE5484D), t("توقّف التطبيق مرة، فأرسل التقرير لنصلحه")) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }, t("إرسال تقرير العطل")))
                com.kouwis.alzkr.data.CrashLog.clear(context)
                crash = null
            }
        }),
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

    if (support) SupportDialog(onOpen = { open(INSTAPAY) }) { support = false }
    if (pickDigits) DigitsDialog(com.kouwis.alzkr.data.ArabicText.digitMode, { m ->
        pickDigits = false
        com.kouwis.alzkr.data.ArabicText.choose(context, m)
        com.kouwis.alzkr.widget.WidgetUpdater.refresh(context)
    }) { pickDigits = false }
    if (pickLang) LanguageDialog(Lang.of(prefs.lang), { l -> pickLang = false; scope.launch { store.setLang(l.code) } }) { pickLang = false }
}

private fun digitLabel(m: String) = when (m) { "ar" -> t("عربية ١٢٣"); "en" -> t("إنجليزية 123"); else -> t("حسب اللغة") }

/** The digits outside the mushaf (the mushaf keeps its Arabic digits always). */
@Composable
private fun DigitsDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(t("شكل الأرقام"), color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf("auto", "ar", "en").forEach { m ->
                    val on = m == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { if (on) onDismiss() else onPick(m) }.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(digitLabel(m), color = if (on) c.primary else c.text, fontSize = 17.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (on) Icon(Ic.check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Text(t("أرقام المصحف تبقى عربية دائمًا."), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.primary) } },
    )
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
