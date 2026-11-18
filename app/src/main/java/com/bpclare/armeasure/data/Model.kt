// What is kept: items (a car seat, a cushion, anything measured), each with its measurements and photos. Saved as JSON
// beside the item's photos in the app's own files (Store.kt); nothing goes to a server.
package com.bpclare.armeasure.data

import com.bpclare.armeasure.geo.V3
import org.json.JSONArray
import org.json.JSONObject

/** LINE: the straight distance between two taps. PATH: a trace, the pieces between its taps added up. */
enum class Kind { LINE, PATH }

data class Measurement(
    val id: String,
    val name: String,
    val kind: Kind,
    /** What AR read, in meters. */
    val arMeters: Double?,
    /** A tape reading typed over it, in meters (it wins over AR when given). */
    val tapeMeters: Double? = null,
    /** The tapped points in AR's world space (meters), kept to redraw or re-measure later. */
    val points: List<V3> = emptyList(),
    /** The photo's file name in the item's folder, with the measurement drawn on. */
    val photo: String? = null,
    val createdAt: Long,
    /** Set when deleted: hidden, kept, restorable. */
    val deletedAt: Long? = null,
) {
    val meters: Double? get() = tapeMeters ?: arMeters
}

data class Item(
    val id: String,
    val name: String,
    /** The checklist it follows (Templates.kt), or null for free measuring. */
    val template: String?,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val measurements: List<Measurement> = emptyList(),
    /** Plain photos (the system camera's) in the item's folder. */
    val photos: List<String> = emptyList(),
    /** Plain photos deleted: their files kept, restorable. */
    val deletedPhotos: List<String> = emptyList(),
    /** Set when the item is in Trash. */
    val deletedAt: Long? = null,
) {
    /** The measurements not deleted. */
    val live: List<Measurement> get() = measurements.filter { it.deletedAt == null }
    val deletedMeasurements: List<Measurement> get() = measurements.filter { it.deletedAt != null }
}

object ItemJson {
    fun toJson(i: Item): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", i.id)
        put("name", i.name)
        put("template", i.template ?: JSONObject.NULL)
        put("notes", i.notes)
        put("createdAt", i.createdAt)
        put("updatedAt", i.updatedAt)
        put("measurements", JSONArray().apply { i.measurements.forEach { put(toJson(it)) } })
        put("photos", JSONArray().apply { i.photos.forEach { put(it) } })
        put("deletedPhotos", JSONArray().apply { i.deletedPhotos.forEach { put(it) } })
        put("deletedAt", i.deletedAt ?: JSONObject.NULL)
    }

    fun toJson(m: Measurement): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("kind", m.kind.name)
        put("arMeters", m.arMeters ?: JSONObject.NULL)
        put("tapeMeters", m.tapeMeters ?: JSONObject.NULL)
        put("points", JSONArray().apply { m.points.forEach { p -> put(JSONArray().put(p.x).put(p.y).put(p.z)) } })
        put("photo", m.photo ?: JSONObject.NULL)
        put("createdAt", m.createdAt)
        put("deletedAt", m.deletedAt ?: JSONObject.NULL)
    }

    fun item(o: JSONObject): Item = Item(
        id = o.getString("id"),
        name = o.optString("name", "Item"),
        template = o.optStr("template"),
        notes = o.optString("notes", ""),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt", o.optLong("createdAt")),
        measurements = o.optJSONArray("measurements")?.let { a -> (0 until a.length()).map { measurement(a.getJSONObject(it)) } }.orEmpty(),
        photos = o.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        deletedPhotos = o.optJSONArray("deletedPhotos")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    fun measurement(o: JSONObject): Measurement = Measurement(
        id = o.getString("id"),
        name = o.optString("name", "Measurement"),
        kind = runCatching { Kind.valueOf(o.optString("kind", "LINE")) }.getOrDefault(Kind.LINE),
        arMeters = o.optNum("arMeters"),
        tapeMeters = o.optNum("tapeMeters"),
        points = o.optJSONArray("points")?.let { a ->
            (0 until a.length()).map { k -> a.getJSONArray(k).let { p -> V3(p.getDouble(0), p.getDouble(1), p.getDouble(2)) } }
        }.orEmpty(),
        photo = o.optStr("photo"),
        createdAt = o.optLong("createdAt"),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optNum(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.optStr(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
}
