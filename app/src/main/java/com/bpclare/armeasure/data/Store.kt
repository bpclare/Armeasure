// Items on the phone: one folder each under the app's files (items/<id>/), its item.json and its photos. Private to
// the app (clearing a browser can't touch it); Export writes a ZIP to Downloads or shares it. Plain java.io, so it runs
// in unit tests against a temporary folder.
package com.bpclare.armeasure.data

import org.json.JSONObject
import java.io.File
import java.util.UUID

class Store(private val root: File) {
    init {
        root.mkdirs()
    }

    fun dir(id: String) = File(root, id)

    fun list(): List<Item> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { get(it.name) }.sortedByDescending { it.updatedAt }

    fun get(id: String): Item? {
        val f = File(dir(id), FILE)
        if (!f.isFile) return null
        return runCatching { ItemJson.item(JSONObject(f.readText())) }.getOrNull()
    }

    fun save(item: Item) {
        val d = dir(item.id).apply { mkdirs() }
        val tmp = File(d, "$FILE.tmp")
        tmp.writeText(ItemJson.toJson(item).toString(2))
        if (!tmp.renameTo(File(d, FILE))) {
            File(d, FILE).writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun create(name: String, template: String?, now: Long = System.currentTimeMillis()): Item {
        val item = Item(id = newId(), name = name.ifBlank { "Item" }, template = template, createdAt = now, updatedAt = now)
        save(item)
        return item
    }

    /** Reads the item, applies the change, stamps it and saves it; null when it is gone. */
    fun update(id: String, now: Long = System.currentTimeMillis(), change: (Item) -> Item): Item? {
        val item = get(id) ?: return null
        val next = change(item).copy(updatedAt = now)
        save(next)
        return next
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    /** A new photo's file in the item's folder (prefix-time.jpg); not created. */
    fun newPhotoFile(id: String, prefix: String): File =
        File(dir(id).apply { mkdirs() }, "$prefix-${System.currentTimeMillis()}.jpg")

    fun file(id: String, name: String) = File(dir(id), name)

    companion object {
        const val FILE = "item.json"
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
