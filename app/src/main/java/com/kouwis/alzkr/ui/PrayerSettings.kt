package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.CalcMethod
import com.kouwis.alzkr.data.HighLat
import com.kouwis.alzkr.data.Locator
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerConfig
import com.kouwis.alzkr.data.PrayerTimes
import com.kouwis.alzkr.data.Prefs
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Cities to pick by hand: Egypt's governorates and the main cities of the Muslim world and beyond. */
private val Cities = listOf(
    "القاهرة" to (30.0444 to 31.2357), "الجيزة" to (30.0131 to 31.2089), "الإسكندرية" to (31.2001 to 29.9187),
    "الشيخ زايد" to (30.0435 to 30.9840), "السادس من أكتوبر" to (29.9722 to 30.9440), "بورسعيد" to (31.2653 to 32.3019),
    "السويس" to (29.9668 to 32.5498), "الإسماعيلية" to (30.5965 to 32.2715), "المنصورة" to (31.0409 to 31.3785),
    "طنطا" to (30.7865 to 31.0004), "الزقازيق" to (30.5877 to 31.5020), "دمياط" to (31.4165 to 31.8133),
    "كفر الشيخ" to (31.1107 to 30.9388), "بنها" to (30.4660 to 31.1858), "شبين الكوم" to (30.5590 to 31.0090),
    "دمنهور" to (31.0341 to 30.4682), "الفيوم" to (29.3084 to 30.8428), "بني سويف" to (29.0661 to 31.0994),
    "المنيا" to (28.1099 to 30.7503), "أسيوط" to (27.1809 to 31.1837), "سوهاج" to (26.5591 to 31.6948),
    "قنا" to (26.1551 to 32.7160), "الأقصر" to (25.6872 to 32.6396), "أسوان" to (24.0889 to 32.8998),
    "الغردقة" to (27.2579 to 33.8116), "شرم الشيخ" to (27.9158 to 34.3300), "مرسى مطروح" to (31.3543 to 27.2373),
    "العريش" to (31.1316 to 33.7984), "الخارجة" to (25.4390 to 30.5590), "الطور" to (28.2414 to 33.6222),
    "مكة المكرمة" to (21.3891 to 39.8579), "المدينة المنورة" to (24.5247 to 39.5692), "الرياض" to (24.7136 to 46.6753),
    "جدة" to (21.4858 to 39.1925), "الدمام" to (26.4207 to 50.0888), "دبي" to (25.2048 to 55.2708),
    "أبوظبي" to (24.4539 to 54.3773), "الدوحة" to (25.2854 to 51.5310), "الكويت" to (29.3759 to 47.9774),
    "المنامة" to (26.2285 to 50.5860), "مسقط" to (23.5880 to 58.3829), "عمّان" to (31.9539 to 35.9106),
    "القدس" to (31.7683 to 35.2137), "بيروت" to (33.8938 to 35.5018), "دمشق" to (33.5138 to 36.2765),
    "بغداد" to (33.3152 to 44.3661), "الخرطوم" to (15.5007 to 32.5599), "طرابلس" to (32.8872 to 13.1913),
    "تونس" to (36.8065 to 10.1815), "الجزائر" to (36.7538 to 3.0588), "الرباط" to (34.0209 to -6.8416),
    "الدار البيضاء" to (33.5731 to -7.5898), "إسطنبول" to (41.0082 to 28.9784), "لندن" to (51.5074 to -0.1278),
    "باريس" to (48.8566 to 2.3522), "برلين" to (52.5200 to 13.4050), "نيويورك" to (40.7128 to -74.0060),
    "تورنتو" to (43.6532 to -79.3832), "كوالالمبور" to (3.1390 to 101.6869), "جاكرتا" to (-6.2088 to 106.8456),
)

private enum class Dialog { Method, Asr, HighLat, Shift, Adjust, Iqama, Coords, Cities, Rename }

/** «مواقيت الصلاة» in the settings: the place, how the times are worked out, and minutes to adjust. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrayerSettingsScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = prefs.prayer
    val lat = prefs.lat ?: 30.0444
    val lng = prefs.lng ?: 31.2357
    val method = cfg.methodFor(lat, lng)
    val today = LocalDate.now()
    val times = remember(cfg, lat, lng) { PrayerTimes.forDay(today, lat, lng, cfg) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var locating by remember { mutableStateOf(false) }
    fun save(n: PrayerConfig) = scope.launch { store.setPrayer(n) }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_LONG).show()

    fun locateNow() {
        locating = true
        scope.launch {
            when (val r = Locator.locate(context)) {
                is Locator.Result.Found -> {
                    store.setLocation(r.fix.lat, r.fix.lng, r.fix.place)
                    store.setAutoLocate(true)
                    toast(if (r.fix.place.isNotBlank()) t("المواقيت الآن حسب {0}", r.fix.place) else t("تم تحديد موقعك"))
                }
                Locator.Result.Off -> toast(t("خدمة الموقع مغلقة في الهاتف — فعّلها ثم حاول مرة أخرى"))
                Locator.Result.NotFound -> toast(t("تعذّر تحديد موقعك الآن — حاول مرة أخرى بعد قليل"))
            }
            locating = false
        }
    }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) locateNow() }
    fun setPlace(la: Double, lo: Double, name: String) = scope.launch {
        store.setLocation(la, lo, name)
        store.setAutoLocate(false) // picked by hand: the phone's location no longer moves it
    }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
            Text(t("مواقيت الصلاة"), color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // today's times with these settings, to see each change at once
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.primary).padding(vertical = 14.dp, horizontal = 8.dp),
                ) {
                    Prayer.entries.forEach { p ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.ar, color = c.onPrimary.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text(clock(times.getValue(p)), color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Header(t("المدينة")) }
            item {
                Group {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("المدينة"), color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text(prefs.place.ifBlank { if (prefs.lat == null) t("القاهرة") else t("موقعك") }, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (prefs.lat == null) t("لم يُحدَّد موقعك بعد")
                            else if (prefs.autoLocate) t("تلقائي · من موقعك الجغرافي") else t("يدوي · {0}، {1}", num6(lat), num6(lng)),
                            color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Line()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !locating) {
                            if (granted()) locateNow() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("تحديد المدينة تلقائيًا من موقعي"), color = c.goldText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (locating) CircularProgressIndicator(color = c.goldText, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                    Line()
                    ToggleRow(t("تحديث الموقع تلقائيًا"), t("يتحدّث إذا انتقلت أكثر من ٢ كم"), prefs.autoLocate) { on ->
                        scope.launch { store.setAutoLocate(on) }
                        if (on) { if (granted()) locateNow() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    }
                    Line()
                    NavRow(t("تحديد يدوي بالإحداثيات")) { dialog = Dialog.Coords }
                    Line()
                    NavRow(t("اختر من قائمة المدن")) { dialog = Dialog.Cities }
                    Line()
                    NavRow(t("تعديل اسم المدينة")) { dialog = Dialog.Rename }
                }
            }

            item { Header(t("طريقة الحساب")) }
            item {
                Group {
                    ValueRow(t("طريقة الحساب"), if (cfg.method == null) t("تلقائي ({0})", method.ar) else method.ar) { dialog = Dialog.Method }
                    Line()
                    ValueRow(t("صلاة العصر"), if (cfg.asrHanafi) t("الحنفي") else t("الجمهور")) { dialog = Dialog.Asr }
                    Line()
                    ValueRow(t("خطوط العرض المرتفعة"), cfg.highLat.ar) { dialog = Dialog.HighLat }
                    Line()
                    ValueRow(t("التوقيت الصيفي"), when { cfg.shift > 0 -> t("+ ساعة"); cfg.shift < 0 -> t("− ساعة"); else -> t("حسب الهاتف") }) { dialog = Dialog.Shift }
                }
            }

            item { Header(t("تعديل المواقيت (بالدقائق)")) }
            item {
                Group {
                    NavRow(t("ضبط مواقيت الصلاة"), if (cfg.adjust.isEmpty()) t("بدون تعديل") else t("معدّلة")) { dialog = Dialog.Adjust }
                    Line()
                    NavRow(t("ضبط مواقيت الإقامة"), Prayer.entries.filter { it != Prayer.Sunrise }.joinToString(" · ") { "${it.ar} ${num(cfg.iqama[it] ?: 0)}" }) { dialog = Dialog.Iqama }
                }
            }
            item {
                Text(
                    t("تُحسب المواقيت على الهاتف دون إنترنت، وتظهر بتوقيت الهاتف."),
                    color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    when (dialog) {
        Dialog.Method -> Choice(
            t("طريقة الحساب"),
            listOf<Pair<String, String>>(t("تلقائي حسب الدولة") to t("الآن: {0}", CalcMethod.forPlace(lat, lng).ar)) +
                CalcMethod.entries.map { m -> m.ar to (t("الفجر {0}° · ", num1(m.fajr)) + if (m.ishaMinutes > 0) t("العشاء بعد المغرب بـ {0} دقيقة", num(m.ishaMinutes)) else t("العشاء {0}°", num1(m.isha))) },
            if (cfg.method == null) 0 else cfg.method.ordinal + 1,
            { i -> save(cfg.copy(method = if (i == 0) null else CalcMethod.entries[i - 1])) },
        ) { dialog = null }
        Dialog.Asr -> Choice(
            t("صلاة العصر"),
            listOf(t("الجمهور") to t("الشافعي والمالكي والحنبلي: إذا صار ظل الشيء مثله"), t("الحنفي") to t("إذا صار ظل الشيء مثليه")),
            if (cfg.asrHanafi) 1 else 0, { i -> save(cfg.copy(asrHanafi = i == 1)) },
        ) { dialog = null }
        Dialog.HighLat -> Choice(
            t("خطوط العرض المرتفعة"),
            listOf(
                HighLat.Middle.ar to t("لا يبعد الفجر والعشاء عن الشروق والمغرب أكثر من نصف الليل"),
                HighLat.Seventh.ar to t("لا يبعدان أكثر من سُبع الليل"),
                HighLat.Angle.ar to t("جزء من الليل بنسبة زاوية الفجر والعشاء"),
                HighLat.None.ar to t("الحساب كما هو"),
            ),
            cfg.highLat.ordinal, { i -> save(cfg.copy(highLat = HighLat.entries[i])) },
        ) { dialog = null }
        Dialog.Shift -> Choice(
            t("التوقيت الصيفي"),
            listOf(t("حسب الهاتف") to t("يتبع ساعة الهاتف (الأفضل)"), t("+ ساعة") to t("تأخير كل المواقيت ساعة"), t("− ساعة") to t("تقديم كل المواقيت ساعة")),
            when { cfg.shift > 0 -> 1; cfg.shift < 0 -> 2; else -> 0 },
            { i -> save(cfg.copy(shift = when (i) { 1 -> 60; 2 -> -60; else -> 0 })) },
        ) { dialog = null }
        Dialog.Adjust -> MinutesDialog(
            t("ضبط مواقيت الصلاة"), t("دقائق تُضاف إلى كل وقت أو تُطرح منه"),
            Prayer.entries, cfg.adjust, -30..30,
            label = { p, m -> clock(times.getValue(p)) + if (m != 0) "" else "" },
            onSave = { save(cfg.copy(adjust = it.filterValues { v -> v != 0 })) },
        ) { dialog = null }
        Dialog.Iqama -> MinutesDialog(
            t("ضبط مواقيت الإقامة"), t("الدقائق بين الأذان والإقامة (٠: بدون عدّاد إقامة)"),
            Prayer.entries.filter { it != Prayer.Sunrise }, cfg.iqama, 0..60,
            label = { p, m -> t("الإقامة ") + clock(times.getValue(p) + m) },
            onSave = { save(cfg.copy(iqama = it)) },
        ) { dialog = null }
        Dialog.Coords -> CoordsDialog(prefs.lat, prefs.lng, prefs.place, { la, lo, n -> setPlace(la, lo, n) }) { dialog = null }
        Dialog.Cities -> CitiesDialog({ n, la, lo -> setPlace(la, lo, n) }) { dialog = null }
        Dialog.Rename -> TextDialog(t("تعديل اسم المدينة"), prefs.place, t("اسم المدينة")) { name ->
            if (name != null && prefs.lat != null && prefs.lng != null) scope.launch { store.setLocation(prefs.lat, prefs.lng, name) }
            dialog = null
        }
        null -> {}
    }
}

private fun arDigits(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else if (it == '.') '٫' else it }.joinToString("")
private fun num1(d: Double) = arDigits(if (d % 1.0 == 0.0) d.toInt().toString() else d.toString())
private fun num6(d: Double) = arDigits("%.4f".format(java.util.Locale.US, d))

@Composable
private fun Header(t: String) {
    val c = LocalColors.current
    Text(t, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
    ) { content() }
}

@Composable
private fun Line() {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.6f)))
}

@Composable
private fun NavRow(title: String, sub: String = "", onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = title, pressedScale = 0.98f, onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp)
            if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().springClick(role = Role.Button, label = title, pressedScale = 0.98f, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(
            value, color = c.goldText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.widthInMax(190.dp).clip(RoundedCornerShape(16.dp)).background(c.goldSoft)
                .border(1.dp, c.goldText.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun Modifier.widthInMax(max: androidx.compose.ui.unit.Dp) = this.then(Modifier.widthIn(max = max))

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp)
            Text(sub, color = c.muted, fontSize = 12.sp)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary, uncheckedTrackColor = c.chip, uncheckedBorderColor = c.border),
        )
    }
}

/** A list of choices, one picked. */
@Composable
private fun Choice(title: String, options: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { i, (t, sub) ->
                    val on = i == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) c.primarySoft else Color.Transparent)
                            .clickable { onPick(i); onDismiss() }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) c.primary else c.muted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary)) }
                        Column(Modifier.weight(1f)) {
                            Text(t, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.primary) } },
    )
}

/** Minutes for each prayer, with − and + buttons. */
@Composable
private fun MinutesDialog(
    title: String, note: String, prayers: List<Prayer>, initial: Map<Prayer, Int>, range: IntRange,
    label: (Prayer, Int) -> String, onSave: (Map<Prayer, Int>) -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalColors.current
    var values by remember { mutableStateOf(prayers.associateWith { (initial[it] ?: 0).coerceIn(range) }) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = {
            Column {
                Text(title, color = c.text, fontWeight = FontWeight.Bold)
                Text(note, color = c.muted, fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prayers.forEach { p ->
                    val v = values.getValue(p)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.ar, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(label(p, v), color = c.muted, fontSize = 11.sp)
                        }
                        Step("−", v > range.first) { values = values + (p to v - 1) }
                        Text(
                            (if (v > 0 && range.first < 0) "+" else "") + num(v), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.width(44.dp),
                        )
                        Step("+", v < range.last) { values = values + (p to v + 1) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values); onDismiss() }) { Text(t("حفظ"), color = c.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("إلغاء"), color = c.muted) } },
    )
}

@Composable
private fun Step(t: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (enabled) c.primarySoft else c.chip)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (enabled) c.primary else c.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun fieldColors() = LocalColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.primary, unfocusedBorderColor = c.border, cursorColor = c.primary,
        focusedTextColor = c.text, unfocusedTextColor = c.text, focusedLabelColor = c.primary, unfocusedLabelColor = c.muted,
    )
}

private fun latin(s: String) = s.map { if (it in '٠'..'٩') '0' + (it - '٠') else if (it == '٫' || it == '،') '.' else it }.joinToString("")

@Composable
private fun CoordsDialog(lat: Double?, lng: Double?, place: String, onSave: (Double, Double, String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var la by remember { mutableStateOf(lat?.toString() ?: "") }
    var lo by remember { mutableStateOf(lng?.toString() ?: "") }
    var name by remember { mutableStateOf(place) }
    val vla = latin(la).toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val vlo = latin(lo).toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(t("تحديد يدوي بالإحداثيات"), color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(la, { la = it }, label = { Text(t("خط العرض (مثل ٣٠٫٠٤)")) }, singleLine = true, isError = la.isNotBlank() && vla == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lo, { lo = it }, label = { Text(t("خط الطول (مثل ٣١٫٢٣)")) }, singleLine = true, isError = lo.isNotBlank() && vlo == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("اسم المكان")) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (vla != null && vlo != null) { onSave(vla, vlo, name.trim()); onDismiss() } }, enabled = vla != null && vlo != null) {
                Text(t("حفظ"), color = if (vla != null && vlo != null) c.primary else c.muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("إلغاء"), color = c.muted) } },
    )
}

@Composable
private fun CitiesDialog(onPick: (String, Double, Double) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var q by remember { mutableStateOf("") }
    val list = remember(q) { val n = ArabicText.normalize(q); Cities.map { t(it.first) to it.second }.filter { n.isBlank() || ArabicText.normalize(it.first).lowercase().contains(n.lowercase()) || it.first.lowercase().contains(q.trim().lowercase()) } }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(t("اختر المدينة"), color = c.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text(t("بحث")) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.first }) { (name, ll) ->
                        Text(
                            name, color = c.text, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(name, ll.first, ll.second); onDismiss() }.padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("إغلاق"), color = c.primary) } },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, label: String, onDone: (String?) -> Unit) {
    val c = LocalColors.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = c.card,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value, { value = it.take(40) }, label = { Text(label) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(value.trim().ifBlank { null }) }) { Text(t("حفظ"), color = c.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(t("إلغاء"), color = c.muted) } },
    )
}
