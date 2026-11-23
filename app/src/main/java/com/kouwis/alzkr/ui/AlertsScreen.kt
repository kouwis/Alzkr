package com.kouwis.alzkr.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kouwis.alzkr.alerts.AlertScheduler
import com.kouwis.alzkr.data.AlertEvents
import com.kouwis.alzkr.data.AlertsConfig
import com.kouwis.alzkr.data.Anchor
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerAlarm
import com.kouwis.alzkr.data.PrayerTimes
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.Reminder
import com.kouwis.alzkr.data.ReminderSetting
import com.kouwis.alzkr.data.SoundEntry
import com.kouwis.alzkr.data.Sounds
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.data.ArabicText.num
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val prayerTiles = mapOf(
    Prayer.Fajr to (AdhkarIc.rise to Color(0xFF2FA36B)), Prayer.Sunrise to (AdhkarIc.sun to Color(0xFFF2A516)),
    Prayer.Dhuhr to (AdhkarIc.sun to Color(0xFFE8B10C)), Prayer.Asr to (AdhkarIc.cloud to Color(0xFF1F8FE5)),
    Prayer.Maghrib to (AdhkarIc.set to Color(0xFFE5336B)), Prayer.Isha to (AdhkarIc.moon to Color(0xFF5865E0)),
)

private val reminderTiles = mapOf(
    Reminder.Morning to (AdhkarIc.rise to Color(0xFFE8B10C)), Reminder.Evening to (AdhkarIc.moon to Color(0xFF5865E0)),
    Reminder.Duha to (AdhkarIc.sun to Color(0xFFF2A516)), Reminder.Sleep to (AdhkarIc.bed to Color(0xFF6E58C9)),
    Reminder.Qiyam to (AdhkarIc.prayer to Color(0xFF2D6CB5)), Reminder.Kahf to (AdhkarIc.book to Color(0xFF14937E)),
    Reminder.FridayHour to (AdhkarIc.clock to Color(0xFF0F8F72)), Reminder.MonThu to (AdhkarIc.calendar to Color(0xFF1A9BD7)),
    Reminder.WhiteDays to (AdhkarIc.moon to Color(0xFF8E9AAF)),
)

/** The phone's permissions alerts need, re-checked when the user comes back from the system settings. */
@Composable
private fun rememberPermits(): Triple<Boolean, Boolean, Boolean> {
    val context = LocalContext.current
    fun read() = Triple(
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        AlertScheduler.canExact(context),
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
    )
    var permits by remember { mutableStateOf(read()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permits = read() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return permits
}

@Composable
private fun PageTop(title: String, nav: Nav) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
        Text(title, color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
    }
}

/** «الأذان والتنبيهات»: the adhan of each prayer, alarms set around the prayers, and daily reminders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = prefs.alerts
    val catalog = remember { Sounds.catalog(context) }
    val (notifyOk, exactOk, fullOk) = rememberPermits()
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!cfg.setUp) store.setAlerts(cfg.copy(setUp = true))
        if (!notifyOk && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun save(n: AlertsConfig) = scope.launch { store.setAlerts(n) }
    var editAlarm by remember { mutableStateOf<PrayerAlarm?>(null) }
    var editReminder by remember { mutableStateOf<Reminder?>(null) }
    val times = remember(prefs.prayer, prefs.lat, prefs.lng) { PrayerTimes.forDay(LocalDate.now(), prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer) }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        PageTop(t("الأذان والتنبيهات"), nav)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!notifyOk) item {
                Warning(t("الإشعارات متوقفة"), t("اسمح للتطبيق بالإشعارات ليصلك الأذان والتنبيهات")) {
                    if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            if (!exactOk) item {
                Warning(t("التنبيه في وقته بالضبط"), t("اسمح بالمنبّهات والتذكيرات حتى لا يتأخر الأذان عن وقته")) {
                    if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                }
            }
            if (!fullOk && cfg.alarms.any { it.on }) item {
                Warning(t("المنبّه على الشاشة"), t("اسمح بعرض المنبّه بملء الشاشة ليظهر فوق شاشة القفل")) {
                    if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName)))
                }
            }

            item { Header(t("تنبيه الأذان")) }
            item {
                Group {
                    AlertEvents.AlertPrayers.forEachIndexed { i, p ->
                        if (i > 0) Line()
                        val a = cfg.prayer(p)
                        val (icon, tint) = prayerTiles.getValue(p)
                        TileRow(
                            icon, tint, p.ar,
                            clock(times.getValue(p)) + " · " + if (a.on) Sounds.label(catalog, a.sound.ifBlank { Sounds.defaultFor(p, catalog) }) else t("مغلق"),
                            dot = a.on,
                        ) { nav.go(Screen.PrayerAlert(p.name)) }
                    }
                }
            }

            item { Header(t("المنبّهات")) }
            item {
                Group {
                    cfg.alarms.forEachIndexed { i, al ->
                        if (i > 0) Line()
                        AlarmRow(al, prefs, { on -> save(cfg.copy(alarms = cfg.alarms.map { if (it.id == al.id) it.copy(on = on) else it })) }) { editAlarm = al }
                    }
                    if (cfg.alarms.isNotEmpty()) Line()
                    Row(
                        Modifier.fillMaxWidth().springClick(label = t("منبّه جديد"), pressedScale = 0.98f) {
                            editAlarm = PrayerAlarm(id = (cfg.alarms.maxOfOrNull { it.id } ?: 0) + 1)
                        }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Ic.plus, null, tint = c.goldText, modifier = Modifier.size(20.dp))
                        Text(t("منبّه جديد"), color = c.goldText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    t("يرنّ المنبّه مثل منبّه الهاتف، ويتحرّك وقته وحده كل يوم مع مواقيت الصلاة."),
                    color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            item { Header(t("تذكيرات يومية")) }
            item {
                Group {
                    Reminder.entries.forEachIndexed { i, r ->
                        if (i > 0) Line()
                        val s = cfg.reminder(r)
                        val (icon, tint) = reminderTiles.getValue(r)
                        SwitchTileRow(icon, tint, r.title, reminderWhen(r, s.offset), s.on,
                            { on -> save(cfg.with(r, s.copy(on = on))) }) { editReminder = r }
                    }
                }
            }

            item { Header(t("عام")) }
            item {
                Group {
                    ToggleRow(t("الأذان في الوضع الصامت"), t("يعمل الأذان ولو كان الهاتف صامتًا أو على الاهتزاز"), cfg.playOnSilent) { on ->
                        save(cfg.copy(playOnSilent = on))
                    }
                }
            }
            item {
                Text(
                    t("يعلو الأذان والمنبّه بمستوى صوت المنبّه في الهاتف."),
                    color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }

    editAlarm?.let { al ->
        AlarmDialog(al, prefs, catalog, isNew = cfg.alarms.none { it.id == al.id },
            onSave = { n -> save(cfg.copy(alarms = if (cfg.alarms.any { it.id == n.id }) cfg.alarms.map { if (it.id == n.id) n else it } else cfg.alarms + n)); editAlarm = null },
            onDelete = { save(cfg.copy(alarms = cfg.alarms.filter { it.id != al.id })); editAlarm = null },
        ) { editAlarm = null }
    }
    editReminder?.let { r ->
        val s = cfg.reminder(r)
        OffsetDialog(r.title, s.offset, range = if (r.anchor == Anchor.LastThird) -120..240 else -180..300, label = { reminderWhen(r, it) },
            onSave = { v -> save(cfg.with(r, ReminderSetting(true, v))) }) { editReminder = null }
    }
}

/** «بعد الفجر بـ ٣٠ دقيقة» */
private fun reminderWhen(r: Reminder, offset: Int): String {
    val anchor = when (r.anchor) {
        Anchor.Fajr -> Prayer.Fajr.ar; Anchor.Sunrise -> Prayer.Sunrise.ar; Anchor.Asr -> Prayer.Asr.ar
        Anchor.Maghrib -> Prayer.Maghrib.ar; Anchor.Isha -> Prayer.Isha.ar; Anchor.LastThird -> t("الثلث الأخير من الليل")
    }
    val m = num(kotlin.math.abs(offset))
    val base = when {
        offset == 0 -> t("عند {0}", anchor)
        offset > 0 -> t("بعد {0} بـ {1} دقيقة", anchor, m)
        else -> t("قبل {0} بـ {1} دقيقة", anchor, m)
    }
    return when (r) {
        Reminder.Kahf, Reminder.FridayHour -> t("الجمعة · {0}", base)
        Reminder.MonThu -> t("ليلة الاثنين والخميس · {0}", base)
        Reminder.WhiteDays -> t("ليالي ١٣ و١٤ و١٥ · {0}", base)
        else -> base
    }
}

@Composable
private fun Warning(title: String, sub: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.heart.copy(alpha = 0.12f))
            .border(1.dp, c.heart.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .springClick(label = title, pressedScale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.bell, null, tint = c.heart, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = c.muted, fontSize = 13.sp)
        }
        Text(t("السماح"), color = c.heart, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Tile(icon: ImageVector, tint: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TileRow(icon: ImageVector, tint: Color, title: String, sub: String, dot: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = title, pressedScale = 0.98f, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tile(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (dot) Color(0xFF34C759) else c.border))
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AppSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Switch(
        checked = on, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary, uncheckedTrackColor = c.chip, uncheckedBorderColor = c.border),
    )
}

@Composable
private fun SwitchTileRow(icon: ImageVector, tint: Color, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = title, pressedScale = 0.98f, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tile(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 2)
        }
        AppSwitch(on, onChange)
    }
}

@Composable
private fun AlarmRow(al: PrayerAlarm, prefs: AppPrefs, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = LocalColors.current
    val times = remember(prefs.prayer, prefs.lat, prefs.lng) { PrayerTimes.forDay(LocalDate.now(), prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer) }
    val p = al.prayerEnum
    val at = times.getValue(p) + (if (al.iqama) prefs.prayer.iqama[p] ?: 0 else 0) + al.offset
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = al.label.ifBlank { AlertEvents.alarmTitle(al) }, pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tile(Ic.alarmClock, Color(0xFFF7A21B))
        Column(Modifier.weight(1f)) {
            Text(clock(((at % 1440) + 1440) % 1440), color = if (al.on) c.text else c.muted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(al.label.ifBlank { AlertEvents.alarmTitle(al) }, color = c.text, fontSize = 13.sp, maxLines = 1)
            Text(AlertEvents.daysLabel(al.days), color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        AppSwitch(al.on, onToggle)
    }
}

/** One prayer's alerts: the adhan and its sound, a heads-up before it, and the iqama. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerAlertScreen(prefs: AppPrefs, store: Prefs, nav: Nav, prayerName: String) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = Prayer.entries.firstOrNull { it.name == prayerName } ?: Prayer.Fajr
    val cfg = prefs.alerts
    val a = cfg.prayer(p)
    val catalog = remember { Sounds.catalog(context) }
    val iq = prefs.prayer.iqama[p] ?: 0
    fun save(n: com.kouwis.alzkr.data.PrayerAlert) = scope.launch { store.setAlerts(cfg.with(p, n)) }
    var picker by remember { mutableStateOf<String?>(null) } // adhan, pre, iqama
    var preDialog by remember { mutableStateOf(false) }
    val times = remember(prefs.prayer, prefs.lat, prefs.lng) { PrayerTimes.forDay(LocalDate.now(), prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer) }
    val adhanSound = a.sound.ifBlank { Sounds.defaultFor(p, catalog) }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        PageTop(p.ar, nav)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Group {
                    ToggleRow(if (p == Prayer.Sunrise) t("تنبيه الشروق") else t("تنبيه الأذان"), t("اليوم {0}", clock(times.getValue(p))), a.on) { on -> save(a.copy(on = on)) }
                    if (a.on) {
                        Line()
                        ValueRow(if (p == Prayer.Sunrise) t("الصوت") else t("صوت الأذان"), Sounds.label(catalog, adhanSound)) { picker = "adhan" }
                    }
                }
            }
            item { Header(if (p == Prayer.Sunrise) t("التنبيه قبل الشروق") else t("التنبيه قبل الأذان")) }
            item {
                Group {
                    ValueRow(t("قبل الوقت بـ"), if (a.pre == 0) t("مغلق") else t("{0} دقيقة", num(a.pre))) { preDialog = true }
                    if (a.pre > 0) {
                        Line()
                        ValueRow(t("صوت التنبيه"), Sounds.label(catalog, a.preSound)) { picker = "pre" }
                    }
                }
            }
            if (p != Prayer.Sunrise) {
                item { Header(t("تنبيه الإقامة")) }
                item {
                    Group {
                        ToggleRow(t("تنبيه الإقامة"), if (iq > 0) t("الإقامة بعد الأذان بـ {0} دقيقة", num(iq)) else t("لم يُضبط وقت للإقامة"), a.iqama) { on ->
                            save(a.copy(iqama = on))
                        }
                        if (a.iqama) {
                            Line()
                            StepperRow(t("قبل الإقامة"), a.iqamaBefore, 0..iq.coerceAtLeast(0), { v -> if (v == 0) t("عند الإقامة") else t("{0} دقائق", num(v)) }) { v ->
                                save(a.copy(iqamaBefore = v))
                            }
                            Line()
                            ValueRow(t("صوت تنبيه الإقامة"), Sounds.label(catalog, a.iqamaSound)) { picker = "iqama" }
                        }
                        Line()
                        NavRow(t("ضبط مواقيت الإقامة")) { nav.go(Screen.PrayerSettings) }
                    }
                }
            }
        }
    }

    if (preDialog) {
        val opts = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        Choice(t("قبل الوقت بـ"), opts.map { (if (it == 0) t("مغلق") else t("{0} دقيقة", num(it))) to "" }, opts.indexOf(a.pre).coerceAtLeast(0),
            { i -> save(a.copy(pre = opts[i])) }) { preDialog = false }
    }
    picker?.let { which ->
        val kind = when (which) { "adhan" -> if (p == Prayer.Sunrise) SoundKind.Alert else SoundKind.Adhan; else -> SoundKind.Alert }
        val selected = when (which) { "adhan" -> adhanSound; "pre" -> a.preSound; else -> a.iqamaSound }
        SoundPicker(kind, p == Prayer.Fajr, catalog, selected, onPick = { s ->
            save(when (which) { "adhan" -> a.copy(sound = s); "pre" -> a.copy(preSound = s); else -> a.copy(iqamaSound = s) })
        }) { picker = null }
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, label: (Int) -> String, onChange: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Step("−", value > range.first) { onChange(value - 1) }
        Text(label(value), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        Step("+", value < range.last) { onChange(value + 1) }
    }
}

enum class SoundKind { Adhan, Alert, Alarm }

/** Choose a sound: each with a ▶ to hear it first; recordings not on the phone yet are downloaded when chosen. */
@Composable
fun SoundPicker(kind: SoundKind, fajr: Boolean, catalog: List<SoundEntry>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options = remember(kind, fajr, catalog) {
        val full = catalog.filter { if (kind == SoundKind.Adhan) it.fajr == fajr else !it.fajr }.map { "full:${it.id}" }
        val fajrOthers = if (kind == SoundKind.Adhan && fajr) catalog.filter { !it.fajr }.map { "full:${it.id}" } else emptyList()
        val shorts = catalog.filter { it.short != null && (!it.fajr || catalog.none { o -> !o.fajr && o.name == it.name }) }.map { "short:${it.id}" }
        when (kind) {
            SoundKind.Adhan -> full + shorts + fajrOthers + listOf("sys", "vib")
            SoundKind.Alert -> listOf("sys", "vib") + shorts
            SoundKind.Alarm -> listOf("alarm") + shorts + full
        }.distinct()
    }
    var playing by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() { player[0]?.runCatching { stop(); release() }; player[0] = null; playing = null; loading = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    fun preview(s: String) {
        if (playing == s) { stop(); return }
        stop()
        val mp = MediaPlayer()
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        runCatching {
            when {
                s == "sys" || s == "vib" -> mp.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                s == "alarm" -> mp.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                Sounds.isBundled(catalog, s) -> context.assets.openFd("audio/" + Sounds.fileName(catalog, s)).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                Sounds.ready(context, catalog, s) -> mp.setDataSource(Sounds.localFile(context, Sounds.fileName(catalog, s)!!).path)
                else -> mp.setDataSource(Sounds.remoteUrl(catalog, s)) // heard over the network before it is downloaded
            }
            mp.setOnPreparedListener { it.start(); loading = null }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            player[0] = mp; playing = s; loading = s
            mp.prepareAsync()
        }.onFailure { mp.release(); playing = null; loading = null }
    }
    fun choose(s: String) {
        if (Sounds.ready(context, catalog, s)) { onPick(s); onDismiss(); return }
        if (downloading != null) return
        downloading = s to 0f
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Sounds.download(context, catalog, s) { f -> downloading = s to f } }
            downloading = null
            if (ok) { onPick(s); onDismiss() }
            else android.widget.Toast.makeText(context, t("تعذّر تنزيل الصوت — تأكد من الاتصال بالإنترنت"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(if (kind == SoundKind.Adhan) t("اختر صوت الأذان") else t("اختر الصوت"), color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { it }) { s ->
                    val on = s == selected
                    val e = Sounds.entry(catalog, s)
                    val ready = Sounds.ready(context, catalog, s)
                    val size = e?.let { if (s.startsWith("short:")) it.shortSize else it.fullSize } ?: 0L
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) c.primarySoft else c.chip.copy(alpha = 0.5f))
                            .border(1.dp, if (on) c.primary.copy(alpha = 0.5f) else c.border, RoundedCornerShape(18.dp))
                            .clickable { choose(s) }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (s != "vib") Box(
                            Modifier.size(44.dp).clip(CircleShape).background(c.bg).clickable { preview(s) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading == s) CircularProgressIndicator(color = c.goldText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else Icon(if (playing == s) Ic.stop else Ic.play, t("استماع"), tint = c.goldText, modifier = Modifier.size(18.dp))
                        } else Box(Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Sounds.label(catalog, s), color = c.text, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            val sub = buildList {
                                if (e != null && e.place.isNotBlank()) add(t(e.place))
                                if (s.startsWith("short:")) add(t("التكبيرات الأولى فقط"))
                                if (e != null && !ready) add(t("يُنزَّل مرة واحدة · {0} ميجابايت", com.kouwis.alzkr.data.ArabicText.digitsOf("%.1f".format(java.util.Locale.US, size / 1_048_576.0))))
                            }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp)
                        }
                        val d = downloading
                        when {
                            d != null && d.first == s -> CircularProgressIndicator(progress = { d.second }, color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                            on -> Icon(Ic.check, null, tint = c.primary, modifier = Modifier.size(22.dp))
                            e != null && !ready -> Icon(Ic.download, null, tint = c.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.primary) } },
    )
}

/** Add or change an alarm: which prayer, adhan or iqama, before or after and by how much, which days, the sound. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmDialog(
    initial: PrayerAlarm, prefs: AppPrefs, catalog: List<SoundEntry>, isNew: Boolean,
    onSave: (PrayerAlarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalColors.current
    var a by remember { mutableStateOf(initial) }
    var soundPick by remember { mutableStateOf(false) }
    val prayers = listOf(Prayer.Fajr, Prayer.Sunrise, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
    val times = remember(prefs.prayer, prefs.lat, prefs.lng) { PrayerTimes.forDay(LocalDate.now(), prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer) }
    val p = a.prayerEnum
    val at = times.getValue(p) + (if (a.iqama) prefs.prayer.iqama[p] ?: 0 else 0) + a.offset
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = {
            Column {
                Text(if (isNew) t("منبّه جديد") else t("تعديل المنبّه"), color = c.text, fontWeight = FontWeight.Bold)
                Text(t("اليوم {0}", clock(((at % 1440) + 1440) % 1440)), color = c.goldText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("الصلاة"), color = c.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prayers.forEach { pr -> ChipToggle(pr.ar, pr == p) { a = a.copy(prayer = pr.name, iqama = if (pr == Prayer.Sunrise) false else a.iqama) } }
                }
                if (p != Prayer.Sunrise) Segmented(listOf(t("الأذان"), t("الإقامة")), if (a.iqama) 1 else 0, { a = a.copy(iqama = it == 1) }, Modifier.fillMaxWidth())
                Segmented(listOf(t("قبل"), t("عند"), t("بعد")), when { a.offset < 0 -> 0; a.offset == 0 -> 1; else -> 2 }, { i ->
                    val m = kotlin.math.abs(a.offset).takeIf { it > 0 } ?: 10
                    a = a.copy(offset = when (i) { 0 -> -m; 1 -> 0; else -> m })
                }, Modifier.fillMaxWidth())
                if (a.offset != 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("بالدقائق"), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    val m = kotlin.math.abs(a.offset); val sign = if (a.offset < 0) -1 else 1
                    Step("−", m > 1) { a = a.copy(offset = sign * (if (m > 5) m - 5 else m - 1).coerceAtLeast(1)) }
                    Text(num(m), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                    Step("+", m < 240) { a = a.copy(offset = sign * (if (m < 5) m + 1 else m + 5).coerceAtMost(240)) }
                }
                Text(t("الأيام"), color = c.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 7, 1, 2, 3, 4, 5).forEach { d ->
                        val all = a.days.isEmpty()
                        ChipToggle(AlertEvents.dayShort(d), all || d in a.days) {
                            val cur = if (all) (1..7).toSet() else a.days
                            val next = if (d in cur) cur - d else cur + d
                            a = a.copy(days = if (next.size == 7) emptySet() else next.ifEmpty { setOf(d) })
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip.copy(alpha = 0.5f)).clickable { soundPick = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("الصوت"), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(Sounds.label(catalog, a.sound), color = c.goldText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(a.label, { a = a.copy(label = it.take(40)) }, label = { Text(t("اسم المنبّه (اختياري)")) }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                if (!isNew) Text(
                    t("حذف المنبّه"), color = c.heart, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDelete).padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(a.copy(on = true)) }) { Text(t("حفظ"), color = c.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("إلغاء"), color = c.muted) } },
    )
    if (soundPick) SoundPicker(SoundKind.Alarm, a.prayerEnum == Prayer.Fajr, catalog, a.sound, onPick = { a = a.copy(sound = it) }) { soundPick = false }
}

@Composable
private fun ChipToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Text(
        label, color = if (on) c.onPrimary else c.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) c.primary else c.chip)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Minutes from an anchor time, before or after, with − and +. */
@Composable
private fun OffsetDialog(title: String, initial: Int, range: IntRange, label: (Int) -> String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(label(v), color = c.goldText, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Step("−", v - 5 >= range.first) { v -= 5 }
                    Text((if (v > 0) "+" else "") + num(v), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Step("+", v + 5 <= range.last) { v += 5 }
                }
                Text(t("بالدقائق: السالب قبل الوقت، والموجب بعده"), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v); onDismiss() }) { Text(t("حفظ"), color = c.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("إلغاء"), color = c.muted) } },
    )
}
