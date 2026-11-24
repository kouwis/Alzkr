package com.kouwis.alzkr.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.compose
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.widget.Block
import com.kouwis.alzkr.widget.WidgetAccents
import com.kouwis.alzkr.widget.WidgetColors
import com.kouwis.alzkr.widget.WidgetKind
import com.kouwis.alzkr.widget.WidgetStyle
import com.kouwis.alzkr.widget.WidgetStyles
import com.kouwis.alzkr.widget.WidgetThemes
import com.kouwis.alzkr.widget.receiver
import com.kouwis.alzkr.widget.widget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Previews are drawn one at a time (they share the preview style). */
private val previewLock = Mutex()

/**
 * The widget exactly as the home screen will show it: the app draws the very same widget
 * (Glance's own output) over a dark home-screen backdrop.
 */
@Composable
fun WidgetPreview(kind: WidgetKind, s: WidgetStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF1B2335), Color(0xFF3B2F4A)))),
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth - 32.dp
        val h = w / kind.aspect
        var views by remember { mutableStateOf<RemoteViews?>(null) }
        LaunchedEffect(kind, s, w) {
            views = runCatching {
                previewLock.withLock {
                    WidgetStyles.preview = s
                    try { withContext(Dispatchers.Default) { kind.widget().compose(context.applicationContext, size = DpSize(w, h)) } }
                    finally { WidgetStyles.preview = null }
                }
            }.getOrNull()
        }
        Box(Modifier.padding(vertical = 18.dp).size(w, h), contentAlignment = Alignment.Center) {
            val v = views
            if (v != null) AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    runCatching { frame.addView(v.apply(frame.context, frame)) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The style controls: background, accent, text, corners, and for the custom widget its pieces. */
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
    fun pickImage() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(t("الخلفية"))
        Group {
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WidgetThemes.forEach { th ->
                    Swatch(th.name, style.bg == th.id, when (th.id) {
                        "glass" -> Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF)))
                        "smoke" -> Brush.linearGradient(listOf(Color(0x88000000), Color(0x55000000)))
                        "clear" -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        else -> Brush.linearGradient(th.colors.map { Color(it) })
                    }) { onChange(style.copy(bg = th.id)) }
                }
                Swatch(t("لون"), style.bg == "color", Brush.linearGradient(listOf(Color(style.color), Color(style.color)))) { onChange(style.copy(bg = "color")) }
                Swatch(t("صورة"), style.bg == "image", Brush.linearGradient(listOf(Color(0xFF8E9AAF), Color(0xFF4A5568))), icon = true) {
                    if (style.image != null && style.bg != "image") onChange(style.copy(bg = "image")) else pickImage()
                }
            }
            Reveal(style.bg == "color") {
                Column(Modifier.padding(bottom = 10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WidgetColors.forEach { col ->
                            val on = style.color == col
                            val edge by animateColorAsState(if (on) c.gold else c.border, label = "sw")
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(col)).border(if (on) 3.dp else 1.dp, edge, CircleShape)
                                    .clickable { onChange(style.copy(color = col)) },
                            )
                        }
                    }
                    Text(t("قوة الخلفية"), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                    Slider(
                        value = style.opacity, onValueChange = { onChange(style.copy(opacity = it)) }, valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.chip),
                    )
                }
            }
            Reveal(style.bg == "image") {
                Column(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("تعتيم الصورة"), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            t("صورة أخرى"), color = c.goldText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.goldSoft).clickable { pickImage() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Slider(
                        value = style.dim, onValueChange = { onChange(style.copy(dim = it)) }, valueRange = 0f..0.75f,
                        colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.chip),
                    )
                }
            }
        }
        Header(t("لون التمييز"))
        Group {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                WidgetAccents.forEach { a ->
                    val on = style.accent == a.id
                    val edge by animateColorAsState(if (on) c.text else Color.Transparent, label = "acc")
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).border(2.dp, edge, CircleShape).padding(5.dp).clip(CircleShape)
                            .background(Color(a.onDark)).border(1.dp, c.border, CircleShape).clickable { onChange(style.copy(accent = a.id)) },
                    )
                }
            }
        }
        Header(t("لون الكتابة"))
        Group {
            Segmented(
                listOf(t("تلقائي"), t("فاتح"), t("داكن")),
                when (style.text) { "light" -> 1; "dark" -> 2; else -> 0 },
                { i -> onChange(style.copy(text = listOf("auto", "light", "dark")[i])) },
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        Header(t("الحواف"))
        Group {
            Segmented(
                listOf(t("حادّة"), t("مستديرة"), t("مستديرة جدًا")),
                when (style.radius) { 10 -> 0; 36 -> 2; else -> 1 },
                { i -> onChange(style.copy(radius = listOf(10, 28, 36)[i])) },
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
private fun Swatch(label: String, on: Boolean, fill: Brush, icon: Boolean = false, onClick: () -> Unit) {
    val c = LocalColors.current
    val edge by animateColorAsState(if (on) c.gold else c.border, label = "swatch")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.size(width = 58.dp, height = 40.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF3B2F4A)))).background(fill)
                .border(if (on) 3.dp else 1.dp, edge, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { if (icon) Icon(Ic.plus, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Text(label, color = if (on) c.text else c.muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** Puts a widget on the home screen (the phone asks where); its look is the one just designed. */
fun pinWidget(context: android.content.Context, kind: WidgetKind): Boolean {
    val am = context.getSystemService(AppWidgetManager::class.java)
    if (Build.VERSION.SDK_INT < 26 || !am.isRequestPinAppWidgetSupported) return false
    return runCatching { am.requestPinAppWidget(ComponentName(context, kind.receiver()), null, null) }.getOrDefault(false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Page(title: String, nav: Nav, bottom: @Composable () -> Unit = {}, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
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
            Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
        bottom()
    }
}

/** «الودجت»: every widget, drawn as it will look; tap one to style it and put it on the home screen. */
@Composable
fun WidgetsScreen(nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    Page(t("الودجت"), nav) {
        item {
            Text(
                t("اختر ودجت لتصمّم شكله ثم تضيفه إلى الشاشة الرئيسية. ولتعديل ودجت موجود اضغط عليه مطوّلًا في الشاشة الرئيسية ثم اختر الإعدادات."),
                color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        items(WidgetKind.entries.toList(), key = { it.id }) { k ->
            Column(
                Modifier.fillMaxWidth().springClick(label = k.title, pressedScale = 0.98f) { nav.go(Screen.WidgetDesign(k.id)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WidgetPreview(k, remember(k) { WidgetStyles.forKind(context, k) }, Modifier.fillMaxWidth())
                Text(k.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(k.desc, color = c.muted, fontSize = 12.sp)
            }
        }
    }
}

/** One widget's design page: the live preview on top, the controls under it, and «أضف إلى الشاشة الرئيسية». */
@Composable
fun WidgetDesignScreen(nav: Nav, kindId: String) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val kind = WidgetKind.of(kindId)
    var style by remember { mutableStateOf(WidgetStyles.forKind(context, kind)) }
    LaunchedEffect(style) { WidgetStyles.putKind(context, kind, style) }
    Page(kind.title, nav, bottom = {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.gold)
                .springClick(label = t("أضف إلى الشاشة الرئيسية")) {
                    WidgetStyles.putKind(context, kind, style)
                    com.kouwis.alzkr.widget.WidgetUpdater.refresh(context)
                    if (!pinWidget(context, kind)) Toast.makeText(context, t("أضفه من قائمة الودجت في الشاشة الرئيسية: اضغط مطوّلًا على مكان فارغ ثم «الودجت»"), Toast.LENGTH_LONG).show()
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(t("أضف إلى الشاشة الرئيسية"), color = Color(0xFF1B1B1B), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }) {
        item { WidgetPreview(kind, style, Modifier.fillMaxWidth()) }
        item { WidgetDesigner(kind, style) { style = it } }
        item { Box(Modifier.height(8.dp)) }
    }
}
