// What is drawn over the camera: the ring at the center (where a tap puts a point; green when the phone has a surface
// under it), the points and the lines between them, and their lengths. One function draws it on the live screen and
// on the photo, so the photo shows exactly what was measured.
package com.bpclare.armeasure.ar

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.Pt
import com.bpclare.armeasure.geo.V3
import com.bpclare.armeasure.geo.distance
import com.bpclare.armeasure.geo.fmtLength

/** One frame's view of the measuring, made on the GL thread and drawn on the UI's. */
data class Snap(
    val tracking: Boolean,
    val status: String?,
    val depth: Boolean,
    val aim: Boolean,
    val aimDistM: Double?,
    val kind: Kind,
    val points: List<V3>,
    val screen: List<Pt?>,
    val totalM: Double,
    /** From the last point to the aim, while another point can still be added. */
    val liveM: Double?,
    val w: Int,
    val h: Int,
) {
    val complete get() = if (kind == Kind.LINE) points.size == 2 else points.size >= 2
    val canAdd get() = kind == Kind.PATH || points.size < 2
}

object Overlay {
    const val GREEN = 0xFF3DDC84.toInt()

    /** live: the ring and the line to it (the screen); else only what was measured, with title in a banner (the photo). */
    fun draw(c: Canvas, s: Snap, unit: LengthUnit, density: Float, live: Boolean, title: String? = null) {
        val d = density
        val cx = s.w / 2f
        val cy = s.h / 2f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN; strokeWidth = 3 * d; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        val outline = Paint(line).apply { color = 0x99000000.toInt(); strokeWidth = 6 * d }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; style = Paint.Style.FILL }
        val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * d }

        // The lines between the points (skipping any behind the camera).
        for (i in 0 until s.screen.size - 1) {
            val a = s.screen[i] ?: continue
            val b = s.screen[i + 1] ?: continue
            c.drawLine(a.x, a.y, b.x, b.y, outline)
            c.drawLine(a.x, a.y, b.x, b.y, line)
        }
        // The line still being drawn: the last point to the ring.
        val last = s.screen.lastOrNull()
        if (live && last != null && s.canAdd && s.aim) {
            val dash = Paint(line).apply { pathEffect = DashPathEffect(floatArrayOf(10 * d, 8 * d), 0f) }
            c.drawLine(last.x, last.y, cx, cy, dash)
        }
        for (p in s.screen) {
            if (p == null) continue
            c.drawCircle(p.x, p.y, 6 * d, dot)
            c.drawCircle(p.x, p.y, 6 * d, dotRing)
        }

        // The lengths: a line's at its middle; a trace's total at its last point.
        if (s.kind == Kind.LINE) {
            if (s.points.size == 2) {
                val a = s.screen[0]
                val b = s.screen[1]
                if (a != null && b != null) pill(c, fmtLength(distance(s.points[0], s.points[1]), unit), (a.x + b.x) / 2, (a.y + b.y) / 2, d)
            } else if (live && last != null && s.liveM != null && s.aim) {
                pill(c, fmtLength(s.liveM, unit), (last.x + cx) / 2, (last.y + cy) / 2, d)
            }
        } else if (last != null && s.points.size >= 2) {
            pill(c, fmtLength(s.totalM + if (live && s.aim) (s.liveM ?: 0.0) else 0.0, unit), last.x, last.y - 26 * d, d)
        }

        if (live) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (s.aim) GREEN else 0xB3FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3 * d
            }
            c.drawCircle(cx, cy, 18 * d, Paint(ring).apply { color = 0x66000000; strokeWidth = 6 * d })
            c.drawCircle(cx, cy, 18 * d, ring)
            c.drawLine(cx - 6 * d, cy, cx + 6 * d, cy, ring)
            c.drawLine(cx, cy - 6 * d, cx, cy + 6 * d, ring)
        }
        if (title != null) banner(c, title, d)
    }

    private fun pill(c: Canvas, text: String, x: Float, y: Float, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 18 * d; isFakeBoldText = true }
        val w = p.measureText(text)
        val padX = 10 * d
        val padY = 6 * d
        val r = RectF(x - w / 2 - padX, y - p.textSize / 2 - padY, x + w / 2 + padX, y + p.textSize / 2 + padY)
        c.drawRoundRect(r, 14 * d, 14 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() })
        c.drawText(text, x - w / 2, y + p.textSize / 2 - 4 * d, p)
    }

    private fun banner(c: Canvas, text: String, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 22 * d; isFakeBoldText = true }
        val pad = 12 * d
        val w = p.measureText(text)
        c.drawRoundRect(RectF(pad, pad * 3, pad * 3 + w, pad * 5 + p.textSize), 10 * d, 10 * d, Paint().apply { color = 0xB3000000.toInt() })
        c.drawText(text, pad * 2, pad * 4 + p.textSize - 4 * d, p)
    }
}
