package com.kouwis.alzkr.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Prefs
import kotlinx.coroutines.launch

/** The tasbih list when the user hasn't changed it. 0 = no set number. */
val DefaultTasbih = listOf(
    "سُبْحَانَ اللَّهِ" to 33, "الْحَمْدُ لِلَّهِ" to 33, "اللَّهُ أَكْبَرُ" to 33,
    "لَا إِلَٰهَ إِلَّا اللَّهُ" to 100, "أَسْتَغْفِرُ اللَّهَ" to 100, "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ" to 0,
)

private fun toArabicDigits(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
private fun fromArabicDigits(s: String) = s.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("")

/**
 * A free tasbih counter: pick a dhikr (or add your own, with how many times), tap anywhere to count.
 * Long-press a dhikr to change its number or delete it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TasbihScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val list = prefs.tasbih ?: DefaultTasbih
    var pick by remember { mutableIntStateOf(0) }
    val i = pick.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    val counts = remember { mutableStateMapOf<String, Int>() }
    var editing by remember { mutableStateOf<Int?>(null) } // index, or -1 for a new one
    val (text, target) = list.getOrElse(i) { "سُبْحَانَ اللَّهِ" to 33 }
    val count = counts[text] ?: 0

    fun tap() {
        val n = count + 1
        counts[text] = n
        view.haptic(if (target > 0 && n % target == 0) Haptic.Confirm else Haptic.Tick)
    }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .pointerInput(text, target, count) { detectTapGestures { tap() } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Column(Modifier.weight(1f)) {
                Text("السبحة", color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold)
                Text("اضغط في أي مكان · ضغطة طويلة على الذكر لتعديله", color = c.muted, fontSize = 12.sp)
            }
            if (count > 0) RoundIconButton(Ic.refresh, "تصفير", { counts[text] = 0 }, haptic = Haptic.Delete)
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.forEachIndexed { k, (o, n) ->
                val on = k == i
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c.primary else c.card)
                        .border(1.dp, if (on) c.primary else c.border, RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { view.haptic(Haptic.Tick); pick = k },
                            onLongClick = { view.haptic(Haptic.Confirm); editing = k },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(o, color = if (on) c.onPrimary else c.text, fontSize = 15.sp, fontFamily = AppFonts.naskh, maxLines = 1)
                    Text(
                        if (n > 0) "×${num(n)}" else "∞", fontSize = 11.sp,
                        color = if (on) c.onPrimary.copy(alpha = 0.75f) else c.muted,
                    )
                }
            }
            Text(
                "+ ذكر جديد", color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(c.primarySoft)
                    .springClick(label = "ذكر جديد", pressedScale = 0.95f) { editing = -1 }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, color = c.text, fontSize = 30.sp, lineHeight = 52.sp, fontFamily = AppFonts.naskhBold, textAlign = TextAlign.Center)
                Text(num(count), color = c.primary, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                if (target > 0) {
                    Text(
                        if (count >= target) "${num(count / target)} × ${num(target)}" + (if (count % target > 0) " و${num(count % target)}" else "")
                        else "من ${num(target)}",
                        color = c.muted, fontSize = 14.sp,
                    )
                }
            }
        }
        DhikrCounter(
            count = if (target > 0) count % target else count, target = target, done = false, onTap = { tap() },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 28.dp),
        )
    }

    editing?.let { k ->
        val old = list.getOrNull(k)
        TasbihEditor(
            initial = old,
            canDelete = old != null && list.size > 1,
            onDismiss = { editing = null },
            onDelete = {
                scope.launch { store.setTasbih(list.filterIndexed { j, _ -> j != k }) }
                if (pick >= k && pick > 0) pick -= 1
                editing = null
            },
            onSave = { t, n ->
                val next = if (old == null) list + (t to n) else list.mapIndexed { j, e -> if (j == k) t to n else e }
                scope.launch { store.setTasbih(next) }
                if (old != null && old.first != t) counts[t] = counts.remove(old.first) ?: 0
                pick = if (old == null) next.lastIndex else k
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasbihEditor(
    initial: Pair<String, Int>?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    val c = LocalColors.current
    var text by remember { mutableStateOf(initial?.first ?: "") }
    var target by remember { mutableIntStateOf(initial?.second ?: 33) }
    var custom by remember { mutableStateOf(if (initial != null && initial.second !in listOf(0, 33, 100, 1000)) toArabicDigits(initial.second.toString()) else "") }
    val presets = listOf(33, 100, 1000, 0)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.primary, unfocusedBorderColor = c.border, cursorColor = c.primary,
        focusedTextColor = c.text, unfocusedTextColor = c.text,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(if (initial == null) "ذكر جديد" else "تعديل الذكر", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) },
                    label = { Text("الذكر") }, singleLine = false, maxLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.naskh, fontSize = 18.sp),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Text("كم مرة؟", color = c.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { n ->
                        val on = custom.isBlank() && target == n
                        Text(
                            if (n == 0) "بلا عدد" else num(n),
                            color = if (on) c.onPrimary else c.text, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.primary else c.chip)
                                .springClick(pressedScale = 0.95f, haptic = Haptic.Tick) { target = n; custom = "" }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v ->
                        val d = fromArabicDigits(v).filter { it.isDigit() }.take(5)
                        custom = toArabicDigits(d)
                        d.toIntOrNull()?.let { if (it > 0) target = it }
                    },
                    label = { Text("أو اكتب عددًا") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim(), target) }, enabled = text.isNotBlank()) {
                Text("حفظ", color = if (text.isNotBlank()) c.primary else c.muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = onDelete) { Text("حذف", color = c.heart) }
                TextButton(onClick = onDismiss) { Text("إلغاء", color = c.muted) }
            }
        },
    )
}
