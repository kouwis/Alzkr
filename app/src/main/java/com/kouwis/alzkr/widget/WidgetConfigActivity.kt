package com.kouwis.alzkr.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.ui.AlzkrTheme
import com.kouwis.alzkr.ui.LocalColors
import com.kouwis.alzkr.ui.WidgetDesigner
import com.kouwis.alzkr.ui.WidgetPreview
import com.kouwis.alzkr.ui.springClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Opens when a widget is added from the phone's widget list, or chosen for editing: style it, then save. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppFonts.init(this)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // leaving without saving keeps the widget, with the default look
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val lang = runCatching { runBlocking { Prefs(applicationContext).flow.first().lang } }.getOrDefault("ar")
        I18n.set(this, Lang.of(lang))
        val kind = kindOfReceiver(AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className)
        setContent {
            AlzkrTheme {
                val c = LocalColors.current
                val scope = rememberCoroutineScope()
                var style by remember { mutableStateOf(WidgetStyles.get(this, id, kind)) }
                Column(
                    Modifier.fillMaxSize().background(c.bg)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                ) {
                    Text(kind.title, color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
                    LazyColumn(
                        Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { WidgetPreview(kind, style, Modifier.fillMaxWidth()) }
                        item { WidgetDesigner(kind, style) { style = it } }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.gold)
                            .springClick(label = t("حفظ")) {
                                WidgetStyles.put(this@WidgetConfigActivity, id, style)
                                WidgetStyles.putKind(this@WidgetConfigActivity, kind, style)
                                scope.launch {
                                    runCatching {
                                        val gid = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id)
                                        WidgetUpdater.updateOne(this@WidgetConfigActivity, kind, gid)
                                    }
                                    finish()
                                }
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t("حفظ"), color = Color(0xFF1B1B1B), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
