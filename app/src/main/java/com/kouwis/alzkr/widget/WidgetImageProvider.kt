package com.kouwis.alzkr.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the widgets' pictures to the home screen as files, read-only. Sending a picture inside the
 * widget update itself fails once it is large (the phone limits that message's size), which left the
 * bigger widgets stuck and their taps dead.
 */
class WidgetImageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!name.matches(Regex("[A-Za-z0-9_.-]+\\.png"))) throw FileNotFoundException()
        val f = File(dir(context!!), name)
        if (!f.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private fun dir(context: Context) = File(context.filesDir, "widget_img").apply { mkdirs() }
        private var counter = 0

        /** Saves one widget's picture under a new name (so the home screen reloads it) and drops its older ones. */
        @Synchronized
        fun save(context: Context, tag: String, bmp: Bitmap): Uri {
            val d = dir(context)
            val name = "${tag}_${System.currentTimeMillis()}_${counter++}.png"
            File(d, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // keep the previous one a moment: the home screen may still be reading it
            d.listFiles { f -> f.name.startsWith("${tag}_") && f.name != name }
                ?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
            return Uri.parse("content://${context.packageName}.widgetimg/$name")
        }
    }
}
