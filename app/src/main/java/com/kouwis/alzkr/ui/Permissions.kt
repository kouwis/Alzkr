package com.kouwis.alzkr.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kouwis.alzkr.alerts.AlertScheduler
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Locator
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.launch

/** One thing the app needs from the phone. */
internal enum class Need(val arTitle: String, val arWhy: String, val required: Boolean) {
    Location("الموقع", "لحساب مواقيت الصلاة لمدينتك بالضبط", true),
    Notify("الإشعارات", "ليصلك الأذان والتنبيهات والأذكار", true),
    Exact("المنبّهات في وقتها", "حتى يؤذّن في الدقيقة نفسها دون تأخير", true),
    Battery("العمل في الخلفية", "حتى لا يوقف الهاتف التطبيق فيفوتك الأذان", true),
    FullScreen("شاشة المنبّه فوق القفل", "لتظهر شاشة المنبّه (إيقاف وغفوة) والهاتف مقفول", false),
    Overlay("الظهور فوق التطبيقات", "لتظهر شاشة المنبّه وأنت تستخدم الهاتف", false),
    ;
    val title get() = t(arTitle)
    val why get() = t(arWhy)
}

object Setup {
    private fun sp(c: Context) = c.getSharedPreferences("setup", Context.MODE_PRIVATE)
    fun done(c: Context) = sp(c).getBoolean("permissions_done", false)
    fun markDone(c: Context) = sp(c).edit().putBoolean("permissions_done", true).apply()

    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Whether the phone has already given it. */
    internal fun has(c: Context, n: Need): Boolean = when (n) {
        Need.Location -> granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
        Need.Notify -> Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS)
        Need.Exact -> AlertScheduler.canExact(c)
        Need.Battery -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
        Need.FullScreen -> Build.VERSION.SDK_INT < 34 || c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Need.Overlay -> Settings.canDrawOverlays(c)
    }

    /** Something the adhan cannot do without is missing (asked again when the app opens). */
    fun missingRequired(c: Context) = listOf(Need.Notify, Need.Exact, Need.Battery).any { !has(c, it) }
}

/** The system page where the user gives [n] (for those not asked in a dialog). */
private fun settingsFor(c: Context, n: Need): Intent? {
    val pkg = Uri.parse("package:" + c.packageName)
    return when (n) {
        Need.Exact -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
        Need.Battery -> @Suppress("BatteryLife") Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        Need.FullScreen -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
        Need.Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
        Need.Notify -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        Need.Location -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    }
}

/**
 * «قبل أن نبدأ»: everything the app needs from the phone, asked for at the start (and again when the
 * app opens while one the adhan relies on is missing), so nothing is found not working later.
 * «السماح بالكل» goes through them one after another; each row can be given on its own too.
 */
@Composable
fun PermissionsPage(onDone: () -> Unit) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Prefs(context) }
    val prefs by store.flow.collectAsState(AppPrefs())
    var tick by remember { mutableStateOf(0) }
    val state = remember(tick) { Need.entries.associateWith { Setup.has(context, it) } }
    var walking by remember { mutableStateOf(false) }   // «السماح بالكل» in progress
    var asked by remember { mutableStateOf(setOf<Need>()) }

    fun afterLocation() = scope.launch {
        (Locator.locate(context) as? Locator.Result.Found)?.fix?.let { f -> store.setFoundLocation(prefs, f.lat, f.lng, f.place); store.setAutoLocate(true) }
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_COARSE_LOCATION] == true || res[Manifest.permission.ACCESS_FINE_LOCATION] == true) afterLocation()
        tick++
    }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
    }
    fun ask(n: Need, again: Boolean = false) {
        asked = asked + n
        // refused before (the phone asks no more): its page in the settings
        if (again && (n == Need.Location || n == Need.Notify)) { settingsFor(context, n)?.let { runCatching { context.startActivity(it) } }; return }
        when (n) {
            Need.Location -> runtime.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            Need.Notify -> if (Build.VERSION.SDK_INT >= 33) one.launch(Manifest.permission.POST_NOTIFICATIONS) else settingsFor(context, n)?.let { runCatching { context.startActivity(it) } }
            else -> settingsFor(context, n)?.let { runCatching { context.startActivity(it) } }
        }
    }
    // «السماح بالكل»: the next one not yet given (each asked once in the walk), in order
    fun step() {
        val next = Need.entries.firstOrNull { state[it] == false && it !in asked }
        if (next == null) { walking = false; return }
        ask(next)
    }
    // back from a system page or a dialog: read again, and go on with the walk
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(tick) { if (walking) step() }
    // done: the adhan is on from now (it waits otherwise for «الأذان والتنبيهات» to be opened once)
    fun finish() {
        Setup.markDone(context)
        scope.launch { if (!prefs.alerts.setUp) store.setAlerts(prefs.alerts.copy(setUp = true)); onDone() }
    }

    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("قبل أن نبدأ"), color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.padding(top = 24.dp))
                Text(
                    t("يحتاج التطبيق إلى هذه الأذونات ليعمل الأذان والتنبيهات في وقتها دون مفاجآت. اضغط «السماح بالكل» وسننتقل بك إليها واحدة بعد الأخرى."),
                    color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 6.dp),
                )
                Need.entries.forEach { n -> NeedRow(n, state[n] == true) { ask(n, again = n in asked) } }
                Text(
                    t("على هواتف سامسونج: من إعدادات البطارية أبعد التطبيق عن «التطبيقات النائمة» حتى لا يتوقف الأذان."),
                    color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            val all = Need.entries.all { state[it] == true }
            Text(
                if (all) t("ابدأ") else t("السماح بالكل"),
                color = c.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.primary)
                    .springClick(label = t("السماح بالكل")) {
                        if (all) finish() else { asked = emptySet(); walking = true; step() }
                    }.padding(vertical = 14.dp),
            )
            if (!all) Text(
                t("لاحقًا"), color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).springClick(label = t("لاحقًا")) { finish() }.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun NeedRow(n: Need, ok: Boolean, onAsk: () -> Unit) {
    val c = LocalColors.current
    val icon: ImageVector = when (n) {
        Need.Location -> AdhkarIc.pin
        Need.Notify -> Ic.bell
        Need.Exact -> Ic.alarmClock
        Need.Battery -> Ic.refresh
        Need.FullScreen -> Ic.lockClock
        Need.Overlay -> Ic.widgets
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (ok) c.primarySoft else c.chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (ok) c.primary else c.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(n.title + if (n.required) "" else " " + t("(اختياري)"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(n.why, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (ok) Icon(Ic.check, t("مسموح"), tint = c.primary, modifier = Modifier.size(20.dp))
        else Text(
            t("سماح"), color = c.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.primary).springClick(label = n.title, onClick = onAsk).padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
