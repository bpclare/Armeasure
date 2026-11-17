// The arithmetic of measuring in AR, pure (no Android, no ARCore) so it runs in unit tests.
//
// ARCore gives every tapped point as a position in meters in its world space. A LINE is the straight distance between
// two taps; a PATH (a trace) is the sum of the straight pieces between its taps, so tapping along a curve (over a seat's
// bolster, round a cushion's front) comes close to what a tape laid along it reads. Ported from Contrain's window tool
// (web/photos/src/measure/measure.js): the inches, the tape's quarter-inch display and parsing, the projection.
package com.bpclare.armeasure.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

const val M_TO_IN = 39.37007874

data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    fun length() = sqrt(x * x + y * y + z * z)
}

/** A point on the screen, in pixels. */
data class Pt(val x: Float, val y: Float)

enum class LengthUnit { IN, CM }

fun distance(a: V3, b: V3) = (a - b).length()

/** The length of the pieces joining the points in order (0 for fewer than two). */
fun pathLength(points: List<V3>) = points.zipWithNext { a, b -> distance(a, b) }.sum()

fun midpoint(a: V3, b: V3) = (a + b) * 0.5

/** Meters to inches, to the nearest quarter. */
fun quarterInches(m: Double) = round(m * M_TO_IN * 4) / 4

private val FRACTIONS = mapOf(1 to "¼", 2 to "½", 3 to "¾")

/** Inches as a tape reads them, to the quarter: 36, 36¼, ¾, -1½. */
fun fmtIn(inches: Double): String {
    val q = round(abs(inches) * 4).toLong()
    val whole = q / 4
    val frac = FRACTIONS[(q % 4).toInt()] ?: ""
    val sign = if (inches < 0 && q > 0) "-" else ""
    val w = if (whole > 0 || frac.isEmpty()) whole.toString() else ""
    return "$sign$w$frac"
}

fun fmtCm(m: Double): String = String.format(Locale.US, "%.1f", m * 100)

/** A length in meters as the chosen unit says it: "22¼ in" or "56.5 cm". */
fun fmtLength(m: Double, unit: LengthUnit) = when (unit) {
    LengthUnit.IN -> "${fmtIn(m * M_TO_IN)} in"
    LengthUnit.CM -> "${fmtCm(m)} cm"
}

/** Both units, the chosen one first: "22¼ in (56.5 cm)". */
fun fmtBoth(m: Double, unit: LengthUnit) = when (unit) {
    LengthUnit.IN -> "${fmtLength(m, LengthUnit.IN)} (${fmtLength(m, LengthUnit.CM)})"
    LengthUnit.CM -> "${fmtLength(m, LengthUnit.CM)} (${fmtLength(m, LengthUnit.IN)})"
}

/** A signed difference as the tape says it: +¼ in, -1 in, 0. */
fun fmtDiff(m: Double, unit: LengthUnit): String {
    val zero = if (unit == LengthUnit.IN) round(m * M_TO_IN * 4) == 0.0 else round(m * 1000) == 0.0
    if (zero) return "0"
    return (if (m > 0) "+" else "") + fmtLength(m, unit)
}

/** A tape reading in inches as typed: 36, 36.25, 36 1/4, 36-1/4, 3/4, 36", 36 in. Null when it is not one. */
fun parseInches(s: String): Double? {
    val t = s.trim().removeSuffix("\"").trim().removeSuffix("in").trim()
    Regex("""^(\d+(?:\.\d+)?)$""").matchEntire(t)?.let { m ->
        val v = m.groupValues[1].toDouble()
        return if (v > 0) v else null
    }
    Regex("""^(?:(\d+)[\s-]+)?(\d+)/(\d+)$""").matchEntire(t)?.let { m ->
        val den = m.groupValues[3].toDouble()
        if (den <= 0) return null
        val v = (m.groupValues[1].ifEmpty { "0" }.toDouble()) + m.groupValues[2].toDouble() / den
        return if (v > 0) v else null
    }
    return null
}

/** A reading in centimeters as typed: 56, 56.5, 56,5, 56.5 cm. Null when it is not one. */
fun parseCm(s: String): Double? {
    val t = s.trim().removeSuffix("cm").trim().replace(',', '.')
    val v = Regex("""^\d+(?:\.\d+)?$""").matchEntire(t)?.value?.toDouble() ?: return null
    return if (v > 0) v else null
}

/** A typed reading in the chosen unit, to meters. */
fun parseLength(s: String, unit: LengthUnit): Double? = when (unit) {
    LengthUnit.IN -> parseInches(s)?.let { it / M_TO_IN }
    LengthUnit.CM -> parseCm(s)?.let { it / 100 }
}

/**
 * A world point onto the screen: { x, y } in pixels of a w × h view, or null behind the camera. view and projection are
 * ARCore's (Camera.getViewMatrix, Camera.getProjectionMatrix), column-major 4 × 4.
 */
fun project(p: V3, view: FloatArray, projection: FloatArray, w: Float, h: Float): Pt? {
    val c = mul(projection, mul(view, doubleArrayOf(p.x, p.y, p.z, 1.0)))
    if (c[3] <= 1e-6) return null
    return Pt(((c[0] / c[3] + 1) / 2 * w).toFloat(), ((1 - c[1] / c[3]) / 2 * h).toFloat())
}

private fun mul(m: FloatArray, v: DoubleArray) =
    DoubleArray(4) { r -> m[r] * v[0] + m[4 + r] * v[1] + m[8 + r] * v[2] + m[12 + r] * v[3] }
