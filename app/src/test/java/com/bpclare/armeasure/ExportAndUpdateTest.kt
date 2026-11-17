package com.bpclare.armeasure

import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.export.ZipExport
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.M_TO_IN
import com.bpclare.armeasure.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExportAndUpdateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val item = Item(
        id = "i", name = "F-150 driver, seat", template = "car_seat", notes = "Tan leather", createdAt = 0, updatedAt = 1_700_000_000_000,
        measurements = listOf(
            Measurement("a", "Back height", Kind.LINE, 22.25 / M_TO_IN, null, emptyList(), "m-1.jpg", 1),
            Measurement("b", "Cushion, \"front\"", Kind.PATH, 10.0 / M_TO_IN, 10.5 / M_TO_IN, emptyList(), null, 2),
        ),
        photos = listOf("p-1.jpg", "p-missing.jpg"),
    )

    @Test fun csvHasOneRowAMeasurement() {
        val lines = ZipExport.csv(item).trim().lines()
        assertEquals("name,kind,inches,cm,ar_inches,tape_inches,points,photo", lines[0])
        assertEquals("Back height,line,22.25,56.5,22.25,,0,photos/01-Back_height.jpg", lines[1])
        assertEquals("\"Cushion, \"\"front\"\"\",trace,10.50,26.7,10.00,10.50,0,", lines[2])
    }

    @Test fun summaryReads() {
        val s = ZipExport.summary(item, LengthUnit.IN)
        assertTrue(s, s.contains("Back height: 22¼ in (56.5 cm)"))
        assertTrue(s, s.contains("Cushion, \"front\": 10½ in (26.7 cm) (traced, 0 points, tape)"))
        assertTrue(s.contains("Tan leather"))
    }

    @Test fun zipHoldsTheSheetsAndPhotos() {
        val dir = tmp.newFolder("i")
        dir.resolve("m-1.jpg").writeText("measured")
        dir.resolve("p-1.jpg").writeText("plain")
        val out = ByteArrayOutputStream()
        ZipExport.write(item, dir, out)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> while (true) names += (z.nextEntry ?: break).name }
        val base = ZipExport.baseName(item)
        assertTrue(base.startsWith("F-150_driver_seat-"))
        assertEquals(
            listOf("summary.txt", "measurements.csv", "item.json", "photos/01-Back_height.jpg", "photos/photo-1.jpg").map { "$base/$it" },
            names,
        )
    }

    @Test fun latestReleaseFromGithub() {
        val json = """{"tag_name":"v14","name":"Build 14","body":"Trace mode",
            "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                      {"name":"ar-measure-14.apk","browser_download_url":"https://x/ar-measure-14.apk"}]}"""
        val r = Updater.parse(json)!!
        assertEquals(14, r.code)
        assertEquals("Build 14", r.title)
        assertEquals("https://x/ar-measure-14.apk", r.apkUrl)
        assertTrue(Updater.isNewer(r, 13))
        assertFalse(Updater.isNewer(r, 14))
        assertFalse(Updater.isNewer(null, 1))
        assertNull(Updater.parse("""{"tag_name":"v3","assets":[]}"""))
        assertNull(Updater.parse("""{"tag_name":"latest","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
        assertNull(Updater.parse("not json"))
    }
}
