package com.bpclare.armeasure

import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.ItemJson
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.data.Templates
import com.bpclare.armeasure.geo.V3
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun m(name: String, ar: Double = 0.5, tape: Double? = null, photo: String? = null) =
        Measurement(id = name.lowercase(), name = name, kind = Kind.LINE, arMeters = ar, tapeMeters = tape, photo = photo, createdAt = 1L)

    @Test fun itemRoundTripsThroughJson() {
        val item = Item(
            id = "a1", name = "Driver seat", template = "car_seat", notes = "Grey cloth", createdAt = 10, updatedAt = 20,
            measurements = listOf(
                m("Back height", 0.56, tape = 0.565, photo = "m-1.jpg"),
                Measurement("p", "Over the top", Kind.PATH, 0.9, null, listOf(V3(0.0, 0.1, 0.2), V3(1.0, 1.5, -2.0)), null, 5),
            ),
            photos = listOf("p-1.jpg"),
        )
        val back = ItemJson.item(JSONObject(ItemJson.toJson(item).toString()))
        assertEquals(item, back)
        assertEquals(0.565, back.measurements[0].meters!!, 0.0) // the tape wins
        assertEquals(0.9, back.measurements[1].meters!!, 0.0)
    }

    @Test fun oldOrPartialJsonStillReads() {
        val back = ItemJson.item(JSONObject("""{"id":"x","measurements":[{"id":"m","kind":"WEIRD","arMeters":null}]}"""))
        assertEquals("Item", back.name)
        assertNull(back.template)
        assertEquals(Kind.LINE, back.measurements[0].kind)
        assertNull(back.measurements[0].meters)
    }

    @Test fun storeKeepsItemsOnDisk() {
        val store = Store(tmp.newFolder("items"))
        val a = store.create("Seat", "car_seat", now = 100)
        val b = store.create("Box", null, now = 200)
        assertEquals(listOf(b.id, a.id), store.list().map { it.id }) // newest first
        store.update(a.id, now = 300) { it.copy(measurements = it.measurements + m("Back height")) }
        assertEquals(listOf(a.id, b.id), store.list().map { it.id })
        assertEquals(1, Store(tmp.root.resolve("items")).get(a.id)!!.measurements.size) // read back fresh
        store.newPhotoFile(a.id, "m").writeText("jpg")
        store.delete(a.id)
        assertNull(store.get(a.id))
        assertTrue(!store.dir(a.id).exists())
        assertNull(store.update("gone") { it })
    }

    @Test fun checklistWalksItsSteps() {
        var item = Item("i", "Seat", "car_seat", createdAt = 0, updatedAt = 0)
        assertEquals("Back height", Templates.nextStep(item)!!.name)
        item = item.copy(measurements = listOf(m("back HEIGHT")))
        assertEquals("Back width, top", Templates.nextStep(item)!!.name)
        val all = Templates.CAR_SEAT.steps.map { m(it.name) }
        assertNull(Templates.nextStep(item.copy(measurements = all)))
        assertTrue(Templates.CAR_SEAT.steps.any { it.kind == Kind.PATH })
        val free = Item("f", "Thing", null, createdAt = 0, updatedAt = 0, measurements = listOf(m("Measurement 2")))
        assertNull(Templates.nextStep(free))
        assertEquals("Measurement 3", Templates.defaultName(free))
    }
}
