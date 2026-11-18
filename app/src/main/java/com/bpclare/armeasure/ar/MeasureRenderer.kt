// THE AR FRAME LOOP (the GL thread). Each frame: the camera picture; the hit under the screen's center (ARCore's
// depth gives a point on any surface it can see, fabric and curves included, not only flat planes); the taps and
// buttons queued from the UI; the points (each an ARCore anchor, so they stay put as tracking improves and are read
// fresh every frame); and a Snap for the UI to draw. A capture reads the picture back, draws the measurement on it and
// hands it to the activity.
package com.bpclare.armeasure.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.V3
import com.bpclare.armeasure.geo.distance
import com.bpclare.armeasure.geo.fmtLength
import com.bpclare.armeasure.geo.pathLength
import com.bpclare.armeasure.geo.project
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

sealed interface Cmd {
    data object Tap : Cmd
    data object Undo : Cmd
    data object Reset : Cmd
    data class Mode(val kind: Kind) : Cmd
    data class Capture(val photo: Boolean, val name: String, val unit: LengthUnit) : Cmd
}

class Captured(val kind: Kind, val meters: Double, val points: List<V3>, val photo: Bitmap?)

class MeasureRenderer(
    private val density: Float,
    private val publish: (Snap) -> Unit,
    private val onCaptured: (Captured) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null
    @Volatile var rotation = 0
    @Volatile var depth = false
    val queue = ConcurrentLinkedQueue<Cmd>()

    private val bg = BackgroundRenderer()
    private val main = Handler(Looper.getMainLooper())
    private var w = 0
    private var h = 0
    @Volatile private var geometryChanged = false
    private val anchors = mutableListOf<Anchor>()
    private var kind = Kind.LINE
    private val view = FloatArray(16)
    private val proj = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        bg.create()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        w = width
        h = height
        geometryChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (geometryChanged) {
            s.setDisplayGeometry(rotation, w, h)
            geometryChanged = false
        }
        s.setCameraTextureName(bg.textureId)
        val frame = try { s.update() } catch (e: Exception) { return }
        val camera = frame.camera
        bg.draw(frame)

        val tracking = camera.trackingState == TrackingState.TRACKING
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(proj, 0, 0.03f, 50f)
        val hit = if (tracking) pick(frame.hitTest(w / 2f, h / 2f)) else null
        val aim = hit?.hitPose?.let(::v3)

        val caps = mutableListOf<Cmd.Capture>()
        while (true) {
            when (val c = queue.poll() ?: break) {
                Cmd.Tap -> if (hit != null && (kind == Kind.PATH || anchors.size < 2)) {
                    runCatching { hit.createAnchor() }.getOrNull()?.let { anchors += it }
                }
                Cmd.Undo -> anchors.removeLastOrNull()?.detach()
                Cmd.Reset -> clear()
                is Cmd.Mode -> if (anchors.isEmpty()) kind = c.kind
                is Cmd.Capture -> caps += c
            }
        }

        val pts = anchors.map { v3(it.pose) }
        val total = pathLength(pts)
        val snap = Snap(
            tracking = tracking,
            status = if (tracking) null else statusOf(camera.trackingFailureReason),
            depth = depth,
            aim = aim != null,
            aimDistM = aim?.let { distance(it, v3(camera.pose)) },
            kind = kind,
            points = pts,
            screen = pts.map { project(it, view, proj, w.toFloat(), h.toFloat()) },
            totalM = total,
            liveM = if (aim != null && pts.isNotEmpty()) distance(pts.last(), aim) else null,
            w = w,
            h = h,
        )
        publish(snap)

        for (c in caps) {
            if (!snap.complete) continue
            val photo = if (c.photo) grab(snap, "${c.name}: ${fmtLength(total, c.unit)}", c.unit) else null
            val out = Captured(kind, total, pts, photo)
            clear()
            main.post { onCaptured(out) }
        }
    }

    /** The display turned or changed size (the Fold opened): the next frame sets the new geometry. */
    fun displayChanged() {
        geometryChanged = true
    }

    private fun clear() {
        anchors.forEach { it.detach() }
        anchors.clear()
    }

    /** The picture as it is on the screen (read back before anything else is drawn), the measurement drawn on. */
    private fun grab(snap: Snap, title: String, unit: LengthUnit): Bitmap? = runCatching {
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.save()
        c.scale(1f, -1f, w / 2f, h / 2f) // GL's rows come bottom first
        c.drawBitmap(raw, 0f, 0f, null)
        c.restore()
        raw.recycle()
        Overlay.draw(c, snap, unit, density, live = false, title = title)
        out
    }.getOrNull()

    private fun pick(hits: List<HitResult>): HitResult? = hits.firstOrNull { h ->
        when (val t = h.trackable) {
            is DepthPoint -> true
            is Plane -> t.isPoseInPolygon(h.hitPose)
            is Point -> true
            else -> false
        }
    }

    private fun v3(p: Pose) = V3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble())

    private fun statusOf(r: TrackingFailureReason) = when (r) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark. Turn on more light."
        TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast. Slow down."
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aim at something with more detail."
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "The camera is busy in another app."
        else -> "Move the phone slowly side to side to start."
    }
}
