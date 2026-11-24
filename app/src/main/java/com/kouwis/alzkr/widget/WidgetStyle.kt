package com.kouwis.alzkr.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kouwis.alzkr.data.t
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The kinds of widget, each its own entry in the phone's widget list. [w]×[h]: its usual size in home-screen cells. */
enum class WidgetKind(val id: String, val arTitle: String, val arDesc: String, val w: Int, val h: Int) {
    Prayers("prayers", "الصلاة القادمة والمواقيت", "الصلاة القادمة بخط كبير، والوقت الباقي، وكل المواقيت", 4, 2),
    Next("next", "الصلاة القادمة", "الصلاة القادمة ووقتها والوقت الباقي", 2, 2),
    Row("row", "شريط المواقيت", "الصلوات الخمس في سطر واحد", 4, 1),
    DateTimes("date_times", "التاريخ والمواقيت", "اليوم والتاريخ الهجري، والمواقيت، والإقامة القادمة", 4, 2),
    Table("table", "جدول الصلاة", "الأذان والإقامة لكل صلاة", 4, 3),
    Progress("progress", "شريط الصلاة", "كم مضى وكم بقي حتى الأذان أو الإقامة", 4, 2),
    Banner("banner", "اللافتة", "سطر واحد كبير: الصلاة القادمة والوقت الباقي", 4, 1),
    Date("date", "التقويم", "اليوم الهجري بخط كبير، واليوم، والتاريخ الميلادي", 4, 2),
    Clock("clock", "الساعة", "الساعة، والتاريخ، والصلاة القادمة", 4, 2),
    Tracker("tracker", "متابعة الصلوات", "علّم على كل صلاة صلّيتها اليوم", 4, 2),
    Ayah("ayah", "آية اليوم", "آية جديدة كل يوم", 4, 2),
    Dhikr("dhikr", "ذكر", "ذكر من الأذكار، وزر لذكر آخر", 4, 2),
    Tasbih("tasbih", "السبحة", "عدّاد تسبيح: اضغط لتعدّ", 2, 2),
    Occasions("occasions", "رمضان والعيد", "كم يومًا بقي على رمضان والعيدين", 2, 2),
    Custom("custom", "الودجت الخاص", "اختر ما يظهر فيه: الصلاة القادمة، المواقيت، التاريخ، ذكر، آية", 4, 2),
    ;
    val title: String get() = t(arTitle)
    val desc: String get() = t(arDesc)
    /** shows the day's times, so sunrise and the last third can be added */
    val hasTimes: Boolean get() = this in setOf(Prayers, Row, DateTimes, Table, Custom)
    /** width ÷ height on a typical phone */
    val aspect: Float get() = (w * 82f) / (h * 92f).coerceAtLeast(1f)

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

/** A background: frosted glass, a gradient, or nothing. [light]: a pale background, so the text is dark. */
class WidgetTheme(val id: String, val arName: String, val colors: List<Long>, val light: Boolean = false) {
    val name: String get() = t(arName)
}

val WidgetThemes = listOf(
    WidgetTheme("glass", "زجاجي", emptyList()),
    WidgetTheme("smoke", "زجاج داكن", emptyList()),
    WidgetTheme("clear", "شفاف", emptyList()),
    WidgetTheme("night", "ليل", listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364)),
    WidgetTheme("midnight", "كحلي", listOf(0xFF141E30, 0xFF243B55)),
    WidgetTheme("emerald", "زمرّدي", listOf(0xFF0B3D2E, 0xFF1B6B4F)),
    WidgetTheme("ocean", "بحري", listOf(0xFF1A2980, 0xFF1C8FB5)),
    WidgetTheme("dusk", "غروب", listOf(0xFF3A1C71, 0xFFB4506A, 0xFFE28A5C)),
    WidgetTheme("plum", "بنفسجي", listOf(0xFF2E1A47, 0xFF6A3C78)),
    WidgetTheme("coffee", "بُنّي", listOf(0xFF2B1D14, 0xFF5C3D2E)),
    WidgetTheme("sand", "رملي", listOf(0xFFF7EEDD, 0xFFE6CFA3), light = true),
    WidgetTheme("pearl", "لؤلؤي", listOf(0xFFFFFFFF, 0xFFE9EEF4), light = true),
)

/** Accent colours: [onDark] on dark backgrounds, [onLight] on pale ones. */
class WidgetAccent(val id: String, val onDark: Long, val onLight: Long)

val WidgetAccents = listOf(
    WidgetAccent("gold", 0xFFF5C76B, 0xFF9A6B12),
    WidgetAccent("mint", 0xFF6EE7B7, 0xFF0F766E),
    WidgetAccent("sky", 0xFF7DD3FC, 0xFF0369A1),
    WidgetAccent("rose", 0xFFFDA4AF, 0xFFBE123C),
    WidgetAccent("white", 0xFFFFFFFF, 0xFF111827),
)

/**
 * How a widget looks. [bg]: a [WidgetThemes] id, "color" (one colour, with [opacity]) or "image"
 * (a picture from the gallery, darkened by [dim]). [text]: "auto", "light" or "dark".
 */
@Serializable
data class WidgetStyle(
    val bg: String = "glass",
    val color: Long = 0xFF1E4E79,
    val opacity: Float = 0.9f,
    val image: String? = null,
    val dim: Float = 0.35f,
    val text: String = "auto",
    val accent: String = "gold",
    val radius: Int = 28,
    val blocks: List<String> = listOf("next", "times", "date"),
    /** sunrise and the last third of the night among the times (off unless chosen) */
    val sunrise: Boolean = false,
    val lastThird: Boolean = false,
) {
    private val theme: WidgetTheme? get() = WidgetThemes.firstOrNull { it.id == bg }
    /** the background is pale, so the text is dark */
    val lightBg: Boolean get() = when (text) {
        "light" -> false
        "dark" -> true
        else -> when (bg) {
            "color" -> luminance(color) > 0.6f && opacity > 0.5f
            else -> theme?.light == true
        }
    }
    val ink: Color get() = if (lightBg) Color(0xFF14171C) else Color.White
    val soft: Color get() = ink.copy(alpha = if (lightBg) 0.62f else 0.72f)
    val faint: Color get() = ink.copy(alpha = if (lightBg) 0.10f else 0.14f)
    val accentColor: Color get() = WidgetAccents.firstOrNull { it.id == accent }.let { a ->
        Color((if (lightBg) a?.onLight else a?.onDark) ?: if (lightBg) 0xFF9A6B12 else 0xFFF5C76B)
    }

    private fun luminance(c: Long): Float {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}

/** A palette for one-colour backgrounds. */
val WidgetColors = listOf(
    0xFF0F172A, 0xFF1E3A5F, 0xFF1E4E79, 0xFF0F3B33, 0xFF14532D, 0xFF3B0764,
    0xFF6B2142, 0xFF7C2D12, 0xFF3F3F46, 0xFF000000, 0xFFF5F5F4, 0xFFF4EAD7,
)

object WidgetStyles {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun sp(context: Context) = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    /** The style used while drawing a preview inside the app (no real widget id). */
    @Volatile var preview: WidgetStyle? = null

    private fun decode(raw: String?) = raw?.let { runCatching { json.decodeFromString(WidgetStyle.serializer(), it) }.getOrNull() }

    /** A placed widget's own style, or the last one designed for its kind, or the default. */
    fun get(context: Context, appWidgetId: Int, kind: WidgetKind): WidgetStyle {
        if (appWidgetId <= 0) preview?.let { return it }
        val s = sp(context)
        return decode(s.getString("id:$appWidgetId", null)) ?: decode(s.getString("kind:${kind.id}", null)) ?: WidgetStyle()
    }

    fun forKind(context: Context, kind: WidgetKind): WidgetStyle = decode(sp(context).getString("kind:${kind.id}", null)) ?: WidgetStyle()

    fun put(context: Context, appWidgetId: Int, style: WidgetStyle) =
        sp(context).edit().putString("id:$appWidgetId", json.encodeToString(WidgetStyle.serializer(), style)).apply()

    fun putKind(context: Context, kind: WidgetKind, style: WidgetStyle) =
        sp(context).edit().putString("kind:${kind.id}", json.encodeToString(WidgetStyle.serializer(), style)).apply()

    /** The widgets of this kind already on the home screen take this style too. */
    fun applyToPlaced(context: Context, kind: WidgetKind, style: WidgetStyle) {
        val ids = runCatching {
            android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(android.content.ComponentName(context, kind.receiver()))
        }.getOrDefault(IntArray(0))
        val e = sp(context).edit()
        ids.forEach { e.putString("id:$it", json.encodeToString(WidgetStyle.serializer(), style)) }
        e.apply()
    }

    /**
     * Copies a picture from the gallery into the app, upright (the camera's rotation applied) and
     * scaled down: a widget can carry only so many pixels.
     */
    fun saveImage(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "widget_bg").apply { mkdirs() }
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val big = maxOf(info.size.width, info.size.height)
                if (big > 1400) { val k = 1400f / big; dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
                dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val deg = context.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
            if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
        }
        val scale = 1100f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val f = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        f.path
    }.getOrNull()

    private fun loadImage(path: String?): Bitmap? = path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }

    /**
     * The widget's background, drawn as one picture at its real size: rounded, with the glass
     * sheen, gradient, colour or photo (cropped to fit, darkened towards the bottom for the text).
     */
    fun background(s: WidgetStyle, wDp: Float, hDp: Float, density: Float): Bitmap? {
        if (s.bg == "clear") return null
        // keep the picture small enough for the home screen (it is carried across processes)
        val scale = minOf(density, 760f / maxOf(wDp, 1f), 760f / maxOf(hDp, 1f))
        val w = (wDp * scale).toInt().coerceAtLeast(8); val h = (hDp * scale).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = s.radius * scale
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val theme = WidgetThemes.firstOrNull { it.id == s.bg }
        when {
            s.bg == "image" -> {
                val img = loadImage(s.image)
                if (img != null) {
                    // centre-crop
                    val k = maxOf(w / img.width.toFloat(), h / img.height.toFloat())
                    val m = Matrix().apply { postScale(k, k); postTranslate((w - img.width * k) / 2f, (h - img.height * k) / 2f) }
                    c.drawBitmap(img, m, Paint(Paint.FILTER_BITMAP_FLAG))
                    p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                        intArrayOf(Color.Black.copy(alpha = s.dim * 0.6f).toArgb(), Color.Black.copy(alpha = (s.dim + 0.25f).coerceAtMost(0.9f)).toArgb()),
                        null, Shader.TileMode.CLAMP)
                    c.drawRect(rect, p)
                } else {
                    p.color = Color(0xFF1E293B).toArgb(); c.drawRect(rect, p)
                }
            }
            s.bg == "color" -> {
                val base = Color(s.color)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(base.copy(alpha = s.opacity).toArgb(), androidx.compose.ui.graphics.lerp(base, Color.Black, 0.25f).copy(alpha = s.opacity).toArgb()),
                    null, Shader.TileMode.CLAMP)
                c.drawRect(rect, p)
            }
            s.bg == "glass" || s.bg == "smoke" -> {
                val dark = s.bg == "smoke"
                p.color = (if (dark) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.16f)).toArgb()
                c.drawRect(rect, p)
                // a soft sheen from the top
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.6f,
                    intArrayOf(Color.White.copy(alpha = if (dark) 0.06f else 0.14f).toArgb(), Color.Transparent.toArgb()), null, Shader.TileMode.CLAMP)
                c.drawRect(rect, p)
                p.shader = null
            }
            theme != null && theme.colors.isNotEmpty() -> {
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), theme.colors.map { Color(it).toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP)
                c.drawRect(rect, p)
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f,
                    intArrayOf(Color.White.copy(alpha = 0.08f).toArgb(), Color.Transparent.toArgb()), null, Shader.TileMode.CLAMP)
                c.drawRect(rect, p)
            }
        }
        // a fine edge
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * scale
        p.color = (if (s.lightBg) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = if (s.bg == "glass") 0.30f else 0.12f)).toArgb()
        val inset = p.strokeWidth / 2
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r - inset, r - inset, p)
        return bmp
    }
}
