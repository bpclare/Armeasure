// Checklists: the measurements a kind of item needs, in the order to take them. Kept on the phone and edited in
// Settings (Bryan, Nov 18: "Editable in settings"); car seat and box are the starting copies. Free measuring has none
// (each measurement named as it's taken). AR walks the item's checklist: after each one is saved, the next not yet
// measured comes up. Steps are matched to measurements by name, so renaming a step leaves old items as they were.
package com.bpclare.armeasure.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Step(val name: String, val hint: String = "", val kind: Kind = Kind.LINE)

data class Checklist(
    val id: String,
    val name: String,
    val steps: List<Step>,
    /** Steps taken off the list: kept here, restorable. */
    val removed: List<Step> = emptyList(),
    /** Set when the checklist is in Trash. */
    val deletedAt: Long? = null,
)

object Checklists {
    val CAR_SEAT = Checklist(
        "car_seat", "Car seat",
        listOf(
            Step("Back height", "From the seam where the back meets the cushion, straight up to the top of the back"),
            Step("Back width, top", "Across the top of the back, side to side"),
            Step("Back width, bottom", "Across the back just above the cushion"),
            Step("Back thickness", "At the side: the front face of the back to its rear"),
            Step("Back, over the top", "Trace from the seam, up the front, over the top, down to the back's bottom edge", Kind.PATH),
            Step("Cushion width", "Across the cushion at its widest, bolster to bolster"),
            Step("Cushion depth", "From the front edge back to the seam"),
            Step("Cushion, over the front", "Trace from the seam, along the top, round the front edge, down to the bottom", Kind.PATH),
            Step("Bolster height", "A side bolster: its top down to the flat of the cushion"),
            Step("Headrest width", "Across the headrest at its widest"),
            Step("Headrest height", "Bottom of the headrest to its top"),
            Step("Headrest posts apart", "Center of one post to the center of the other"),
        ),
    )

    val BOX = Checklist(
        "box", "Box or object (W × D × H)",
        listOf(Step("Width", "Side to side"), Step("Depth", "Front to back"), Step("Height", "Bottom to top")),
    )

    /** The starting copies, written to the phone the first time. */
    val DEFAULTS = listOf(CAR_SEAT, BOX)

    fun isStep(c: Checklist?, name: String) = c?.steps?.any { it.name.equals(name, ignoreCase = true) } == true

    fun measured(item: Item, step: Step): Measurement? = item.live.find { it.name.equals(step.name, ignoreCase = true) }

    /** The first step of the checklist not yet measured on the item, or null. */
    fun nextStep(item: Item, c: Checklist?): Step? = c?.steps?.firstOrNull { measured(item, it) == null }

    /** A free measurement's name: Measurement N, the first N not already used. */
    fun defaultName(item: Item): String {
        var n = item.live.size + 1
        while (item.live.any { it.name.equals("Measurement $n", ignoreCase = true) }) n++
        return "Measurement $n"
    }

    fun toJson(c: Checklist): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("steps", steps(c.steps))
        put("removed", steps(c.removed))
        put("deletedAt", c.deletedAt ?: JSONObject.NULL)
    }

    private fun steps(list: List<Step>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("name", it.name).put("hint", it.hint).put("kind", it.kind.name)) }
    }

    private fun steps(a: JSONArray?) = a?.let {
        (0 until it.length()).map { k ->
            val o = it.getJSONObject(k)
            Step(o.optString("name"), o.optString("hint"), runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.LINE))
        }
    }.orEmpty()

    fun fromJson(o: JSONObject) = Checklist(
        id = o.getString("id"),
        name = o.optString("name", "Checklist"),
        steps = steps(o.optJSONArray("steps")),
        removed = steps(o.optJSONArray("removed")),
        deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null,
    )
}

/** The checklists on the phone: one JSON file, the starting copies written the first time it is read. */
class ChecklistStore(private val file: File) {
    private fun readAll(): List<Checklist> {
        if (!file.isFile) {
            writeAll(Checklists.DEFAULTS)
            return Checklists.DEFAULTS
        }
        return runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { Checklists.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(Checklists.DEFAULTS)
    }

    private fun writeAll(list: List<Checklist>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONArray().apply { list.forEach { put(Checklists.toJson(it)) } }.toString(2))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    /** The checklists not in Trash, in their order. */
    fun list(): List<Checklist> = readAll().filter { it.deletedAt == null }

    fun trash(): List<Checklist> = readAll().filter { it.deletedAt != null }

    /** Any checklist by id, in Trash or not (an item keeps following its own). */
    fun get(id: String?): Checklist? = id?.let { i -> readAll().find { it.id == i } }

    fun save(c: Checklist) {
        val all = readAll()
        writeAll(if (all.any { it.id == c.id }) all.map { if (it.id == c.id) c else it } else all + c)
    }

    fun create(name: String): Checklist = Checklist(Store.newId(), name.ifBlank { "New checklist" }, emptyList()).also(::save)

    fun trash(id: String, now: Long = System.currentTimeMillis()) = get(id)?.let { save(it.copy(deletedAt = now)) }

    fun restore(id: String) = get(id)?.let { save(it.copy(deletedAt = null)) }
}
