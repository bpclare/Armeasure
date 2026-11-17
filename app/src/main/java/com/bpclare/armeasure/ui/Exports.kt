// The Android half of export: the ZIP saved to Downloads/AR Measure, or handed to the share sheet (email, Drive,
// Messages...).
package com.bpclare.armeasure.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.export.ZipExport
import java.io.File

object Exports {
    /** Saves the ZIP to Downloads/AR Measure; returns where it went. */
    fun saveToDownloads(ctx: Context, store: Store, item: Item): String {
        val name = ZipExport.fileName(item)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AR Measure")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Downloads refused the file")
        ctx.contentResolver.openOutputStream(uri)!!.use { ZipExport.write(item, store.dir(item.id), it, Prefs.unit(ctx)) }
        return "Downloads/AR Measure/$name"
    }

    /** Writes the ZIP to the cache and returns the share sheet's intent (start it on the main thread). */
    fun shareIntent(ctx: Context, store: Store, item: Item): Intent {
        val f = File(ctx.cacheDir, "exports/${ZipExport.fileName(item)}")
        f.parentFile?.mkdirs()
        f.outputStream().use { ZipExport.write(item, store.dir(item.id), it, Prefs.unit(ctx)) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, item.name)
            .putExtra(Intent.EXTRA_TEXT, ZipExport.summary(item, Prefs.unit(ctx)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share ${item.name}")
    }
}
