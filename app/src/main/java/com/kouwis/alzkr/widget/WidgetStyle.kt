package com.kouwis.alzkr.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.kouwis.alzkr.data.t
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The kinds of widget, each its own entry in the phone's widget list. */
enum class WidgetKind(val id: String, val arTitle: String, val arDesc: String) {
    Prayers("prayers", "مواقيت الصلاة", "الصلاة القادمة والعدّ التنازلي وكل المواقيت"),
    Next("next", "الصلاة القادمة", "اسم الصلاة القادمة ووقتها والوقت الباقي"),
    Row("row", "شريط المواقيت", "مواقيت الصلوات الخمس في سطر واحد"),
    Date("date", "التاريخ", "اليوم والتاريخ الهجري والميلادي"),
    Dhikr("dhikr", "ذكر", "ذكر من الأذكار، وزر لذكر آخر"),
    Ayah("ayah", "آية اليوم", "آية جديدة كل يوم"),
    Tasbih("tasbih", "السبحة", "عدّاد تسبيح على الشاشة الرئيسية"),
    Custom("custom", "الودجت الخاص", "اختر ما يظهر فيه: الصلاة القادمة، المواقيت، التاريخ، ذكر، آية"),
    ;
    val title: String get() = t(arTitle)
    val desc: String get() = t(arDesc)

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Prayers }
}

/** The pieces the custom widget can show. */
enum class Block(val id: String, val arTitle: String) {
    Next("next", "الصلاة القادمة والوقت الباقي"),
    Times("times", "مواقيت الصلوات"),
    Date("date", "التاريخ"),
    Dhikr("dhikr", "ذكر"),
    Ayah("ayah", "آية اليوم"),
    ;
    val title: String get() = t(arTitle)
}

/**
 * How a widget looks. [bg]: "glass" (frosted, see-through), "clear" (nothing behind the text),
 * "color", or "image" (a picture from the gallery, darkened by [dim]).
 */
@Serializable
data class WidgetStyle(
    val bg: String = "glass",
    val color: Long = 0xFF1E4E79,
    val opacity: Float = 0.85f,
    val image: String? = null,
    val dim: Float = 0.35f,
    /** "light" (white text), "dark", or "gold" */
    val text: String = "light",
    val radius: Int = 24,
    val blocks: List<String> = listOf("next", "times", "date"),
) {
    val ink: Color get() = when (text) { "dark" -> Color(0xFF1B1B1B); "gold" -> Color(0xFFF2D58F); else -> Color.White }
    val soft: Color get() = ink.copy(alpha = 0.72f)
    val accent: Color get() = when (text) { "dark" -> Color(0xFF8A5F1A); "gold" -> Color.White; else -> Color(0xFFF2D58F) }
    /** the panel behind the text */
    val panel: Color get() = when (bg) {
        "glass" -> (if (text == "dark") Color.White else Color(0xFF0B1A2A)).copy(alpha = opacity.coerceIn(0.1f, 0.9f) * 0.6f)
        "color" -> Color(color).copy(alpha = opacity)
        else -> Color.Transparent
    }
}

val WidgetColors = listOf(
    0xFF1E4E79, 0xFF0F3B33, 0xFF1F5E55, 0xFF2D6CB5, 0xFF5865E0, 0xFF6E58C9,
    0xFF8E2C48, 0xFFB8893B, 0xFF3A3A3A, 0xFF000000, 0xFFFFFFFF, 0xFFF4EAD7,
)

object WidgetStyles {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun sp(context: Context) = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    /** A placed widget's own style, or the last one designed for its kind, or the default. */
    fun get(context: Context, appWidgetId: Int, kind: WidgetKind): WidgetStyle {
        val s = sp(context)
        val raw = s.getString("id:$appWidgetId", null) ?: s.getString("kind:${kind.id}", null)
        return raw?.let { runCatching { json.decodeFromString(WidgetStyle.serializer(), it) }.getOrNull() } ?: WidgetStyle()
    }

    fun forKind(context: Context, kind: WidgetKind): WidgetStyle =
        sp(context).getString("kind:${kind.id}", null)?.let { runCatching { json.decodeFromString(WidgetStyle.serializer(), it) }.getOrNull() } ?: WidgetStyle()

    fun put(context: Context, appWidgetId: Int, style: WidgetStyle) =
        sp(context).edit().putString("id:$appWidgetId", json.encodeToString(WidgetStyle.serializer(), style)).apply()

    fun putKind(context: Context, kind: WidgetKind, style: WidgetStyle) =
        sp(context).edit().putString("kind:${kind.id}", json.encodeToString(WidgetStyle.serializer(), style)).apply()

    fun remove(context: Context, appWidgetId: Int) = sp(context).edit().remove("id:$appWidgetId").apply()

    /** Copies a picture from the gallery into the app, scaled down (a widget can carry only so many pixels). */
    fun saveImage(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "widget_bg").apply { mkdirs() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = 900f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val f = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.path
    }.getOrNull()

    fun loadImage(path: String?): Bitmap? = path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
}
