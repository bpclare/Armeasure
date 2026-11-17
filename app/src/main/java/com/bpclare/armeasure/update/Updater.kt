// UPDATES FROM GITHUB. Every push to main builds a signed APK and publishes it as a release tagged v<versionCode>
// (.github/workflows/release.yml). On launch the app asks GitHub for the repo's latest release; when its code is newer
// than this build's, Home offers INSTALL: the APK is downloaded and handed to Android's installer (one tap to confirm;
// Android never updates a side-loaded app silently). The repo must be public for the download to need no login.
package com.bpclare.armeasure.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val code: Int, val title: String, val apkUrl: String, val notes: String)

object Updater {
    /** The latest release from GitHub's JSON: its code from the tag (v12 → 12) and its .apk asset; null without one. */
    fun parse(json: String): Release? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = o.optString("tag_name")
        val code = Regex("""\d+""").find(tag)?.value?.toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Release(code, o.optString("name").ifBlank { tag }, a.getString("browser_download_url"), o.optString("body"))
            }
        }
        return null
    }

    fun isNewer(r: Release?, current: Int) = r != null && r.code > current

    fun fetchLatest(repo: String): Release? {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "ar-measure-app")
            if (c.responseCode != 200) null else parse(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /** Downloads the APK to dest, reporting 0–100 as it goes. */
    fun download(url: String, dest: File, progress: (Int) -> Unit): File {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = true
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "ar-measure-app")
            if (c.responseCode != 200) error("The download answered ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) {
                            val p = (got * 100 / total).toInt()
                            if (p != last) { last = p; progress(p) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        dest.delete()
        if (!tmp.renameTo(dest)) error("Could not save the download")
        return dest
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
