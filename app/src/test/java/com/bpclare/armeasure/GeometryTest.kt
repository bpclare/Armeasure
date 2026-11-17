package com.bpclare.armeasure

import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.M_TO_IN
import com.bpclare.armeasure.geo.V3
import com.bpclare.armeasure.geo.distance
import com.bpclare.armeasure.geo.fmtBoth
import com.bpclare.armeasure.geo.fmtDiff
import com.bpclare.armeasure.geo.fmtIn
import com.bpclare.armeasure.geo.fmtLength
import com.bpclare.armeasure.geo.parseCm
import com.bpclare.armeasure.geo.parseInches
import com.bpclare.armeasure.geo.parseLength
import com.bpclare.armeasure.geo.pathLength
import com.bpclare.armeasure.geo.project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryTest {
    private fun inch(v: Double) = v / M_TO_IN

    @Test fun distanceIsStraightInAnyDirection() {
        // A seat back 22 in tall, leaning: the distance is the straight line, whichever way the item faces.
        assertEquals(5.0, distance(V3(0.0, 0.0, 0.0), V3(3.0, 4.0, 0.0)), 1e-9)
        assertEquals(Math.sqrt(3.0), distance(V3(1.0, 1.0, 1.0), V3(0.0, 0.0, 0.0)), 1e-9)
    }

    @Test fun pathAddsUpItsPieces() {
        // Round a cushion's front: 3 in along the top, 4 in down the front, 3 in under.
        val pts = listOf(V3(0.0, 0.0, 0.0), V3(inch(3.0), 0.0, 0.0), V3(inch(3.0), -inch(4.0), 0.0), V3(0.0, -inch(4.0), 0.0))
        assertEquals(10.0, pathLength(pts) * M_TO_IN, 1e-9)
        assertEquals(0.0, pathLength(pts.take(1)), 0.0)
        assertEquals(0.0, pathLength(emptyList()), 0.0)
    }

    @Test fun inchesReadLikeATape() {
        assertEquals("36", fmtIn(36.0))
        assertEquals("36¼", fmtIn(36.2))
        assertEquals("48¾", fmtIn(48.76))
        assertEquals("¾", fmtIn(0.75))
        assertEquals("0", fmtIn(0.1))
        assertEquals("-1½", fmtIn(-1.5))
        assertEquals("22¼ in", fmtLength(inch(22.25), LengthUnit.IN))
        assertEquals("56.5 cm", fmtLength(0.565, LengthUnit.CM))
        assertEquals("22¼ in (56.5 cm)", fmtBoth(inch(22.25), LengthUnit.IN))
        assertEquals("56.5 cm (22¼ in)", fmtBoth(inch(22.25), LengthUnit.CM))
    }

    @Test fun differences() {
        assertEquals("+¼ in", fmtDiff(inch(0.25), LengthUnit.IN))
        assertEquals("-1 in", fmtDiff(inch(-1.0), LengthUnit.IN))
        assertEquals("0", fmtDiff(inch(0.1), LengthUnit.IN))
        assertEquals("+1.0 cm", fmtDiff(0.01, LengthUnit.CM))
    }

    @Test fun tapeReadingsAsTyped() {
        assertEquals(36.0, parseInches("36")!!, 0.0)
        assertEquals(36.25, parseInches("36.25")!!, 0.0)
        assertEquals(36.25, parseInches("36 1/4")!!, 0.0)
        assertEquals(36.25, parseInches("36-1/4")!!, 0.0)
        assertEquals(0.75, parseInches("3/4")!!, 0.0)
        assertEquals(22.0, parseInches("22\"")!!, 0.0)
        assertEquals(22.5, parseInches(" 22.5 in ")!!, 0.0)
        assertNull(parseInches(""))
        assertNull(parseInches("abc"))
        assertNull(parseInches("0"))
        assertNull(parseInches("1/0"))
        assertEquals(56.5, parseCm("56,5")!!, 0.0)
        assertEquals(56.0, parseCm("56 cm")!!, 0.0)
        assertNull(parseCm("x"))
        assertEquals(0.565, parseLength("56.5", LengthUnit.CM)!!, 1e-12)
        assertEquals(1.0, parseLength("1", LengthUnit.IN)!! * M_TO_IN, 1e-9)
    }

    @Test fun projectsOntoTheScreen() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        // A simple perspective: w = -z (the camera looks down -Z, as ARCore's does).
        val persp = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, -1f, 0f, 0f, 0f, 0f)
        val center = project(V3(0.0, 0.0, -2.0), identity, persp, 1000f, 2000f)!!
        assertEquals(500f, center.x, 1e-3f)
        assertEquals(1000f, center.y, 1e-3f)
        // Up and right of center at 1 m: half way to the top right corner.
        val ur = project(V3(0.5, 0.5, -1.0), identity, persp, 1000f, 2000f)!!
        assertEquals(750f, ur.x, 1e-3f)
        assertEquals(500f, ur.y, 1e-3f)
        assertNull(project(V3(0.0, 0.0, 1.0), identity, persp, 1000f, 2000f)) // behind the camera
        // A view matrix moving the world 1 m forward (the camera stepped back).
        val back = identity.copyOf().also { it[14] = -1f }
        assertNotNull(project(V3(0.0, 0.0, 0.5), back, persp, 1000f, 2000f))
    }
}
