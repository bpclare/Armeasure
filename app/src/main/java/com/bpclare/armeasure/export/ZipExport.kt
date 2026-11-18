// An item as a ZIP: a summary to read, a CSV for a spreadsheet, the item's JSON, and every photo named after what it
// shows. Pure java.io (the Android half, Downloads and sharing, is in ui/Exports.kt).
package com.bpclare.armeasure.export

import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.ItemJson
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.M_TO_IN
import com.bpclare.armeasure.geo.fmtBoth
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExport {
    fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().replace(Regex("\\s+"), "_").take(60).ifEmpty { "item" }

    private fun day(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    fun baseName(item: Item) = "${safe(item.name)}-${day(item.updatedAt)}"

    fun fileName(item: Item) = "${baseName(item)}.zip"

    /** Each photo's name in the ZIP: a measurement's "01-Back_height.jpg", a plain photo's "photo-1.jpg". */
    fun photoNames(item: Item): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        item.live.forEachIndexed { i, m ->
            m.photo?.let { out[it] = String.format(Locale.US, "%02d-%s.jpg", i + 1, safe(m.name)) }
        }
        item.photos.forEachIndexed { i, p -> out[p] = "photo-${i + 1}.jpg" }
        return out
    }

    private fun cell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun num(v: Double?, k: Double, places: Int) = v?.let { String.format(Locale.US, "%.${places}f", it * k) } ?: ""

    fun csv(item: Item): String {
        val names = photoNames(item)
        val rows = mutableListOf("name,kind,inches,cm,ar_inches,tape_inches,points,photo")
        for (m in item.live) {
            rows += listOf(
                cell(m.name),
                if (m.kind == Kind.PATH) "trace" else "line",
                num(m.meters, M_TO_IN, 2),
                num(m.meters, 100.0, 1),
                num(m.arMeters, M_TO_IN, 2),
                num(m.tapeMeters, M_TO_IN, 2),
                m.points.size.toString(),
                m.photo?.let { names[it] }?.let { "photos/$it" } ?: "",
            ).joinToString(",")
        }
        return rows.joinToString("\n") + "\n"
    }

    fun summary(item: Item, unit: LengthUnit): String = buildString {
        appendLine(item.name)
        appendLine("Measured ${day(item.updatedAt)}")
        if (item.notes.isNotBlank()) appendLine("\n${item.notes.trim()}")
        appendLine()
        for (m in item.live) {
            val v = m.meters?.let { fmtBoth(it, unit) } ?: "not measured"
            val how = buildList {
                if (m.kind == Kind.PATH) add("traced, ${m.points.size} points")
                if (m.tapeMeters != null) add("tape")
            }
            appendLine("${m.name}: $v${if (how.isEmpty()) "" else " (${how.joinToString(", ")})"}")
        }
    }

    /** Writes the ZIP: <base>/summary.txt, measurements.csv, item.json and photos/. dir is the item's folder. */
    fun write(item: Item, dir: File, out: OutputStream, unit: LengthUnit = LengthUnit.IN) {
        val base = baseName(item)
        ZipOutputStream(out).use { z ->
            fun text(name: String, body: String) {
                z.putNextEntry(ZipEntry("$base/$name"))
                z.write(body.toByteArray())
                z.closeEntry()
            }
            text("summary.txt", summary(item, unit))
            text("measurements.csv", csv(item))
            text("item.json", ItemJson.toJson(item.copy(measurements = item.live, deletedPhotos = emptyList())).toString(2))
            for ((file, name) in photoNames(item)) {
                val f = File(dir, file)
                if (!f.isFile) continue
                z.putNextEntry(ZipEntry("$base/photos/$name"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }
}
