package com.bpclare.armeasure.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.bpclare.armeasure.geo.LengthUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val Green = Color(0xFF3DDC84)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Green,
        onPrimary = Color.Black,
        secondary = Green,
        onSecondary = Color.Black,
        background = Color(0xFF111315),
        surface = Color(0xFF16191C),
        surfaceVariant = Color(0xFF22262A),
    ),
    content = content,
)

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun unit(ctx: Context) = runCatching { LengthUnit.valueOf(p(ctx).getString("unit", "IN")!!) }.getOrDefault(LengthUnit.IN)
    fun setUnit(ctx: Context, u: LengthUnit) = p(ctx).edit().putString("unit", u.name).apply()
}

/** A picture decoded no bigger than it needs to be (its longest side about maxPx). */
fun decodeSampled(file: File, maxPx: Int): Bitmap? {
    if (!file.isFile) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
}

/** A photo from the system camera made upright (its EXIF turn applied) and at most 2560 px a side, saved over itself. */
fun normalizePhoto(file: File) {
    val turn = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val src = decodeSampled(file, 2560) ?: return
    val k = minOf(1f, 2560f / maxOf(src.width, src.height))
    val m = Matrix().apply { postScale(k, k); postRotate(turn) }
    val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
}

@Composable
fun Thumb(file: File?, modifier: Modifier = Modifier, maxPx: Int = 400, crop: Boolean = true, onClick: (() -> Unit)? = null) {
    val bmp by produceState<ImageBitmap?>(null, file?.path, file?.lastModified()) {
        value = file?.let { f -> withContext(Dispatchers.IO) { runCatching { decodeSampled(f, maxPx)?.asImageBitmap() }.getOrNull() } }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8))
            .background(Color(0xFF2A2E33))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
    }
}
