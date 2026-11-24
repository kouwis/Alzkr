package com.kouwis.alzkr.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.widget.Block
import com.kouwis.alzkr.widget.WidgetColors
import com.kouwis.alzkr.widget.WidgetData
import com.kouwis.alzkr.widget.WidgetKind
import com.kouwis.alzkr.widget.WidgetSnapshot
import com.kouwis.alzkr.widget.WidgetStyle
import com.kouwis.alzkr.widget.WidgetStyles
import com.kouwis.alzkr.widget.receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The widgets' shape on the home screen, width : height. */
private fun WidgetKind.aspect(): Float = when (this) {
    WidgetKind.Next, WidgetKind.Tasbih -> 1f
    WidgetKind.Row -> 3.6f
    WidgetKind.Date -> 3.2f
    else -> 2.1f
}

/** A home-screen wallpaper behind the preview, so a see-through style shows as it will. */
private val Wallpaper = Brush.linearGradient(listOf(Color(0xFF9CC7E8), Color(0xFF3E6FB8), Color(0xFF1C3A73)))

/** The widget drawn in the app, the way it will look on the home screen. */
@Composable
fun WidgetPreview(kind: WidgetKind, s: WidgetStyle, d: WidgetSnapshot?, modifier: Modifier = Modifier) {
    val bmp = remember(s.image) { if (s.bg == "image") WidgetStyles.loadImage(s.image)?.asImageBitmap() else null }
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Wallpaper).padding(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(kind.aspect()).clip(RoundedCornerShape(s.radius.dp))) {
            if (bmp != null) {
                Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.dim)))
            } else if (s.bg != "clear") Box(Modifier.fillMaxSize().background(s.panel))
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (d == null) Text("…", color = s.ink) else PreviewBody(kind, s, d)
            }
        }
    }
}

@Composable
private fun PreviewBody(kind: WidgetKind, s: WidgetStyle, d: WidgetSnapshot) {
    @Composable fun next(big: Boolean) = Column {
        Text(d.place, color = s.soft, fontSize = 11.sp, maxLines = 1)
        Text(t("الصلاة القادمة"), color = s.soft, fontSize = 11.sp, maxLines = 1)
        Text(d.next.name, color = s.ink, fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(d.next.time, color = s.ink, fontSize = if (big) 28.sp else 20.sp, maxLines = 1)
        Text(t("بعد {0}", "١:٢٤:٠٠"), color = s.soft, fontSize = 11.sp, maxLines = 1)
    }
    @Composable fun timesRow() = Row(Modifier.fillMaxWidth()) {
        d.rows.filter { it.prayer.name != "Sunrise" }.forEach { r ->
            val on = r.prayer == d.next.prayer
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.name, color = if (on) s.accent else s.soft, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                Text(r.time, color = if (on) s.accent else s.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
    @Composable fun date(big: Boolean) = Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.weekday, color = s.accent, fontSize = if (big) 14.sp else 12.sp, fontWeight = FontWeight.Bold)
        Text(d.hijri, color = s.ink, fontSize = if (big) 20.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(d.gregorian, color = s.soft, fontSize = 11.sp, maxLines = 1)
    }
    when (kind) {
        WidgetKind.Prayers -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { next(true) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                d.rows.forEach { r ->
                    val on = r.prayer == d.next.prayer
                    Row {
                        Text(r.name, color = if (on) s.ink else s.soft, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(r.time, color = if (on) s.ink else s.soft, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        WidgetKind.Next -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { next(false) }
        WidgetKind.Row -> timesRow()
        WidgetKind.Date -> date(false)
        WidgetKind.Dhikr -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.dhikr, color = s.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(t("ذكر آخر"), color = s.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(s.ink.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        WidgetKind.Ayah -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("﴿ ${d.ayah} ﴾", color = s.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(d.ayahRef, color = s.accent, fontSize = 11.sp)
        }
        WidgetKind.Tasbih -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.tasbihText, color = s.soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(ArabicText.num(d.tasbihCount), color = s.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(t("من {0}", ArabicText.num(WidgetData.TASBIH_TARGET)), color = s.soft, fontSize = 11.sp)
        }
        WidgetKind.Custom -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.blocks.ifEmpty { listOf("next") }.forEach { b ->
                when (b) {
                    "next" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("الصلاة القادمة"), color = s.soft, fontSize = 10.sp)
                            Text(d.next.name + "  " + d.next.time, color = s.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(t("بعد {0}", "١:٢٤:٠٠"), color = s.accent, fontSize = 11.sp)
                    }
                    "times" -> timesRow()
                    "date" -> Row {
                        Text(d.weekday + " · " + d.hijri, color = s.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(d.gregorian, color = s.soft, fontSize = 11.sp, maxLines = 1)
                    }
                    "dhikr" -> Text(d.dhikr, color = s.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    "ayah" -> Text("﴿ ${d.ayah} ﴾", color = s.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The style controls: background (glass, clear, colour, picture), its strength, the text colour, and for the custom widget its pieces. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetDesigner(kind: WidgetKind, style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { WidgetStyles.saveImage(context, uri) }
            if (path != null) onChange(style.copy(bg = "image", image = path))
            else Toast.makeText(context, t("تعذّر فتح الصورة"), Toast.LENGTH_SHORT).show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(t("الخلفية"))
        Group {
            Segmented(
                listOf(t("زجاجي"), t("شفاف"), t("لون"), t("صورة")),
                when (style.bg) { "clear" -> 1; "color" -> 2; "image" -> 3; else -> 0 },
                { i ->
                    when (i) {
                        0 -> onChange(style.copy(bg = "glass"))
                        1 -> onChange(style.copy(bg = "clear"))
                        2 -> onChange(style.copy(bg = "color"))
                        else -> if (style.image != null) onChange(style.copy(bg = "image"))
                        else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            Reveal(style.bg == "color") {
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetColors.forEach { col ->
                        val on = style.color == col
                        val edge by animateColorAsState(if (on) c.gold else c.border, label = "sw")
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Color(col)).border(if (on) 3.dp else 1.dp, edge, CircleShape)
                                .clickable { onChange(style.copy(color = col)) },
                        )
                    }
                }
            }
            Reveal(style.bg == "image") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("الصورة"), color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(
                        t("اختر صورة"), color = c.goldText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.goldSoft)
                            .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Reveal(style.bg != "clear") {
                Column(Modifier.padding(vertical = 6.dp)) {
                    val isImage = style.bg == "image"
                    Text(if (isImage) t("تعتيم الصورة") else t("قوة الخلفية"), color = c.text, fontSize = 15.sp)
                    Slider(
                        value = if (isImage) style.dim else style.opacity,
                        onValueChange = { v -> onChange(if (isImage) style.copy(dim = v) else style.copy(opacity = v)) },
                        valueRange = if (isImage) 0f..0.8f else 0.15f..1f,
                        colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.chip),
                    )
                }
            }
        }
        Header(t("لون الكتابة"))
        Group {
            Segmented(
                listOf(t("أبيض"), t("داكن"), t("ذهبي")),
                when (style.text) { "dark" -> 1; "gold" -> 2; else -> 0 },
                { i -> onChange(style.copy(text = listOf("light", "dark", "gold")[i])) },
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        Header(t("الحواف"))
        Group {
            Segmented(
                listOf(t("حادّة"), t("مستديرة"), t("مستديرة جدًا")),
                when (style.radius) { 8 -> 0; 36 -> 2; else -> 1 },
                { i -> onChange(style.copy(radius = listOf(8, 24, 36)[i])) },
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        if (kind == WidgetKind.Custom) {
            Header(t("ما يظهر في الودجت"))
            Group {
                Block.entries.forEachIndexed { i, b ->
                    if (i > 0) Line()
                    ToggleRow(b.title, "", b.id in style.blocks) { on ->
                        val list = if (on) Block.entries.map { it.id }.filter { it in style.blocks || it == b.id } else style.blocks - b.id
                        onChange(style.copy(blocks = list))
                    }
                }
            }
        }
    }
}

@Composable
fun rememberWidgetSnapshot(): WidgetSnapshot? {
    val context = LocalContext.current
    val d by produceState<WidgetSnapshot?>(null) { value = runCatching { WidgetData.load(context.applicationContext) }.getOrNull() }
    return d
}

/** Puts a widget on the home screen (the phone asks the user where); its look is the one just designed. */
fun pinWidget(context: android.content.Context, kind: WidgetKind): Boolean {
    val am = context.getSystemService(AppWidgetManager::class.java)
    if (Build.VERSION.SDK_INT < 26 || !am.isRequestPinAppWidgetSupported) return false
    return runCatching { am.requestPinAppWidget(ComponentName(context, kind.receiver()), null, null) }.getOrDefault(false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Page(title: String, nav: Nav, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
            Text(title, color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
        }
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
        )
    }
}

/** «الودجت»: every widget, each with its preview; tap one to style it and put it on the home screen. */
@Composable
fun WidgetsScreen(nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val d = rememberWidgetSnapshot()
    Page(t("الودجت"), nav) {
        item {
            Text(
                t("اختر ودجت لتصمّم شكله ثم تضيفه إلى الشاشة الرئيسية. ولتعديل ودجت موجود اضغط عليه مطوّلًا في الشاشة الرئيسية ثم اختر الإعدادات."),
                color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        items(WidgetKind.entries.toList(), key = { it.id }) { k ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(24.dp))
                    .springClick(label = k.title, pressedScale = 0.98f) { nav.go(Screen.WidgetDesign(k.id)) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WidgetPreview(k, remember(k) { WidgetStyles.forKind(context, k) }, d, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(k.desc, color = c.muted, fontSize = 12.sp)
                    }
                    Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** One widget's design page: the live preview on top, the controls under it, and «أضف إلى الشاشة الرئيسية». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetDesignScreen(nav: Nav, kindId: String) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val kind = WidgetKind.of(kindId)
    val d = rememberWidgetSnapshot()
    var style by remember { mutableStateOf(WidgetStyles.forKind(context, kind)) }
    LaunchedEffect(style) { WidgetStyles.putKind(context, kind, style) }
    val c2 = c
    Column(Modifier.fillMaxSize().background(c2.bg)) {
        Box(Modifier.weight(1f)) {
            Page(kind.title, nav) {
                item { WidgetPreview(kind, style, d, Modifier.fillMaxWidth()) }
                item { WidgetDesigner(kind, style) { style = it } }
            }
        }
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.gold)
                .springClick(label = t("أضف إلى الشاشة الرئيسية")) {
                    WidgetStyles.putKind(context, kind, style)
                    com.kouwis.alzkr.widget.WidgetUpdater.refresh(context)
                    if (!pinWidget(context, kind)) Toast.makeText(context, t("أضفه من قائمة الودجت في الشاشة الرئيسية: اضغط مطوّلًا على مكان فارغ ثم «الودجت»"), Toast.LENGTH_LONG).show()
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(t("أضف إلى الشاشة الرئيسية"), color = Color(0xFF1B1B1B), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}
