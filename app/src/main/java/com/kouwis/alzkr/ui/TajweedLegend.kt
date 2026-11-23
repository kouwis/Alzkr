package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours of the KFGQPC tajweed mushaf (the colours are inside the font's palette). */
val TajweedLegend = listOf(
    Triple(Color(0xFFB50000), "مدّ لازم", "يُمدّ ٦ حركات"),
    Triple(Color(0xFFF40000), "مدّ واجب متصل", "يُمدّ ٤ أو ٥ حركات"),
    Triple(Color(0xFFFF7B00), "مدّ جائز", "يُمدّ ٢ أو ٤ أو ٦ حركات"),
    Triple(Color(0xFFCE9E00), "مدّ طبيعي", "يُمدّ حركتين"),
    Triple(Color(0xFF09B000), "غُنّة وإخفاء", "غنّة بمقدار حركتين"),
    Triple(Color(0xFF3F48E6), "تفخيم", "الحروف المفخّمة، وأشهرها الراء"),
    Triple(Color(0xFF2FADFF), "قلقلة", "حروف «قطب جد» الساكنة"),
    Triple(Color(0xFFA5A5A5), "لا يُنطق", "همزة الوصل واللام الشمسية والحروف الساكتة"),
)

@Composable
fun TajweedLegendDialog(onDismiss: () -> Unit) {
    val c = LocalColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides if (com.kouwis.alzkr.data.I18n.lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
        ) {
            Column(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                    .background(c.card)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(t("دليل ألوان التجويد"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text)
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TajweedLegend.forEach { (color, name, how) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(color))
                            Column {
                                Text(t(name), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(t(how), color = c.muted, fontSize = 13.sp)
                            }
                        }
                    }
                    Text(
                        t("الألوان من مصحف التجويد الصادر عن مجمع الملك فهد، وهي مضمّنة في خط الصفحة نفسه."),
                        color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // "تم" on the right (the start side in Arabic)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = onDismiss) { Text(t("تم"), color = c.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
