package com.kouwis.alzkr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.widget.LockNotice
import com.kouwis.alzkr.widget.WidgetKind

/** «شاشة القفل»: the prayer as a quiet notification, and the small widgets made for the lock screen. */
@Composable
fun LockScreenScreen(nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    var on by remember { mutableStateOf(LockNotice.enabled(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { on = true; LockNotice.set(context, true) }
    }
    SettingsPage(t("شاشة القفل"), nav) {
        item {
            Group {
                ToggleRow(t("إظهار الصلاة على شاشة القفل"), "", on) { v ->
                    val need = v && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (need) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else { on = v; LockNotice.set(context, v) }
                }
            }
        }
        item {
            Text(
                t("إشعار ثابت بلا صوت يعرض الصلاة القادمة ووقتها والعد التنازلي لها، مع المدينة واليوم. وبين الأذان والإقامة يعرض العد التنازلي للإقامة."),
                color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        item { Header(t("ودجات شاشة القفل")) }
        items(WidgetKind.entries.filter { it.lock }, key = { it.id }) { k ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // as the phone shows it: a dark capsule (the phone draws it, and may tint the text)
                WidgetPreview(k, remember { com.kouwis.alzkr.widget.WidgetStyle(bg = "midnight", radius = 36) }, Modifier.fillMaxWidth(0.62f))
                Text(k.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(k.desc, color = c.muted, fontSize = 12.sp)
            }
        }
        item {
            Text(
                t("على هواتف سامسونج: اضغط مطوّلًا على شاشة القفل، ثم «تحرير» أو «الودجات»، واختر ودجات الذِّكر. الهاتف يرسم الكبسولة ويلوّن النص بلون شاشة القفل."),
                color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}
