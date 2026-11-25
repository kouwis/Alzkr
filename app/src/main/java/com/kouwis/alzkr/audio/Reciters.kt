package com.kouwis.alzkr.audio

import android.content.Context
import com.kouwis.alzkr.data.t

/**
 * The reciters, each a folder of verse-by-verse recordings on everyayah.com
 * (https://everyayah.com/data/<folder>/SSSAAA.mp3; every folder was checked to answer).
 */
data class Reciter(val id: String, val arName: String, val arStyle: String, val folder: String) {
    val name: String get() = t(arName)
    val style: String get() = t(arStyle)
}

object Reciters {
    val all = listOf(
        Reciter("basit_murattal", "عبد الباسط عبد الصمد", "مرتل", "Abdul_Basit_Murattal_192kbps"),
        Reciter("basit_mujawwad", "عبد الباسط عبد الصمد", "مجود", "Abdul_Basit_Mujawwad_128kbps"),
        Reciter("husary", "محمود خليل الحصري", "مرتل", "Husary_128kbps"),
        Reciter("husary_muallim", "محمود خليل الحصري", "المصحف المعلم", "Husary_Muallim_128kbps"),
        Reciter("husary_mujawwad", "محمود خليل الحصري", "مجود", "Husary_128kbps_Mujawwad"),
        Reciter("minshawi", "محمد صديق المنشاوي", "مرتل", "Minshawy_Murattal_128kbps"),
        Reciter("minshawi_mujawwad", "محمد صديق المنشاوي", "مجود", "Minshawy_Mujawwad_192kbps"),
        Reciter("banna", "محمود علي البنا", "مرتل", "Mahmoud_Ali_Al_Banna_32kbps"),
        Reciter("tablawi", "محمد محمود الطبلاوي", "مرتل", "Mohammad_al_Tablaway_128kbps"),
        Reciter("mustafa_ismail", "مصطفى إسماعيل", "مجود", "Mustafa_Ismail_48kbps"),
        Reciter("alafasy", "مشاري راشد العفاسي", "مرتل", "Alafasy_128kbps"),
        Reciter("ajamy", "أحمد بن علي العجمي", "مرتل", "Ahmed_ibn_Ali_al-Ajamy_128kbps_ketaballah.net"),
        Reciter("sudais", "عبد الرحمن السديس", "مرتل", "Abdurrahmaan_As-Sudais_192kbps"),
        Reciter("shuraim", "سعود الشريم", "مرتل", "Saood_ash-Shuraym_128kbps"),
        Reciter("maher", "ماهر المعيقلي", "مرتل", "MaherAlMuaiqly128kbps"),
        Reciter("dosari", "ياسر الدوسري", "مرتل", "Yasser_Ad-Dussary_128kbps"),
        Reciter("qatami", "ناصر القطامي", "مرتل", "Nasser_Alqatami_128kbps"),
        Reciter("shatri", "أبو بكر الشاطري", "مرتل", "Abu_Bakr_Ash-Shaatree_128kbps"),
        Reciter("hudhaify", "علي الحذيفي", "مرتل", "Hudhaify_128kbps"),
        Reciter("ayyub", "محمد أيوب", "مرتل", "Muhammad_Ayyoub_128kbps"),
        Reciter("jibreel", "محمد جبريل", "مرتل", "Muhammad_Jibreel_128kbps"),
        Reciter("ali_jaber", "علي جابر", "مرتل", "Ali_Jaber_64kbps"),
        Reciter("ghamdi", "سعد الغامدي", "مرتل", "Ghamadi_40kbps"),
        Reciter("basfar", "عبد الله بصفر", "مرتل", "Abdullah_Basfar_192kbps"),
        Reciter("rifai", "هاني الرفاعي", "مرتل", "Hani_Rifai_192kbps"),
        Reciter("fares", "فارس عباد", "مرتل", "Fares_Abbad_64kbps"),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all.first()

    private fun sp(context: Context) = context.getSharedPreferences("quran_audio", Context.MODE_PRIVATE)
    fun current(context: Context) = byId(sp(context).getString("reciter", null))
    fun choose(context: Context, id: String) = sp(context).edit().putString("reciter", id).apply()

    /** Grouped by reciter: one name, his styles. */
    val byName: List<Pair<String, List<Reciter>>> get() = all.groupBy { it.arName }.toList()


    /** The number of verses in each surah. */
    val ayahs = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )
}
