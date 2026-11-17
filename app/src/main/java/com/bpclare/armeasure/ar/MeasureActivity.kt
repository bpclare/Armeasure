// MEASURING IN AR. Opened from an item (with a checklist step's name and kind, or free, or RETAKE of one). Point the
// ring at where the measurement starts and tap anywhere; then at where it ends and tap. LINE takes two points; TRACE
// takes as many as you tap along a curve. Then SAVE (the number) or PHOTO + SAVE (the camera picture with the line and
// the length drawn on). Saved straight into the item; the next checklist step comes up, so a whole seat is measured
// without leaving AR (DONE goes back). UNDO takes the last point back.
package com.bpclare.armeasure.ar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.data.Templates
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.fmtBoth
import com.bpclare.armeasure.geo.fmtLength
import com.bpclare.armeasure.ui.AppTheme
import com.bpclare.armeasure.ui.Green
import com.bpclare.armeasure.ui.Prefs
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

data class MeasureUi(
    val name: String,
    val kind: Kind,
    val hint: String? = null,
    val message: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

class MeasureActivity : ComponentActivity() {
    companion object {
        private const val ITEM = "item"
        private const val NAME = "name"
        private const val KIND = "kind"
        private const val REPLACE = "replace"

        fun intent(ctx: Context, itemId: String, name: String? = null, kind: Kind? = null, replaceId: String? = null) =
            Intent(ctx, MeasureActivity::class.java)
                .putExtra(ITEM, itemId)
                .putExtra(NAME, name)
                .putExtra(KIND, kind?.name)
                .putExtra(REPLACE, replaceId)
    }

    private val store by lazy { Store(File(filesDir, "items")) }
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var itemId: String
    private var replaceId: String? = null
    private lateinit var unit: LengthUnit
    private var session: Session? = null
    private var installRequested = false
    private var permissionAsked = false
    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: MeasureRenderer
    private val snap = MutableStateFlow<Snap?>(null)
    private val ui = MutableStateFlow(MeasureUi("Measurement", Kind.LINE))

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) ui.update { it.copy(error = "The camera permission is needed to measure. Allow it in Settings → Apps → AR Measure.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        itemId = intent.getStringExtra(ITEM) ?: run { finish(); return }
        replaceId = intent.getStringExtra(REPLACE)
        unit = Prefs.unit(this)
        val item = store.get(itemId) ?: run { finish(); return }
        val askedName = intent.getStringExtra(NAME)
        val askedKind = intent.getStringExtra(KIND)?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
        ui.value = if (askedName != null) stepUi(item, askedName, askedKind ?: Kind.LINE) else nextUi(item, Kind.LINE)

        renderer = MeasureRenderer(resources.displayMetrics.density, publish = { snap.value = it }, onCaptured = ::saveCaptured)
        renderer.queue += Cmd.Mode(ui.value.kind)
        @Suppress("DEPRECATION")
        renderer.rotation = windowManager.defaultDisplay.rotation
        surface = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        val controls = ComposeView(this).apply {
            setContent { AppTheme { Screen() } }
        }
        setContentView(FrameLayout(this).apply {
            addView(surface)
            addView(controls)
        })
    }

    private fun stepUi(item: Item, name: String, kind: Kind) = MeasureUi(
        name = name,
        kind = kind,
        hint = Templates.byId(item.template)?.steps?.find { it.name.equals(name, true) }?.hint,
    )

    /** The next checklist step not yet measured, else a free measurement keeping the current mode. */
    private fun nextUi(item: Item, kind: Kind): MeasureUi {
        val step = Templates.nextStep(item)
        return if (step != null) stepUi(item, step.name, step.kind) else MeasureUi(Templates.defaultName(item), kind)
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (!permissionAsked) {
                    permissionAsked = true
                    permission.launch(Manifest.permission.CAMERA)
                }
                return
            }
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    else -> Unit
                }
                val s = Session(this)
                val config = Config(s).apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                }
                s.configure(config)
                renderer.depth = config.depthMode == Config.DepthMode.AUTOMATIC
                session = s
            } catch (e: UnavailableUserDeclinedInstallationException) {
                ui.update { it.copy(error = "AR needs Google Play Services for AR. Install it from the Play Store, then come back.") }
                return
            } catch (e: UnavailableDeviceNotCompatibleException) {
                ui.update { it.copy(error = "This phone doesn't support ARCore.") }
                return
            } catch (e: UnavailableException) {
                ui.update { it.copy(error = "AR couldn't start: ${e.message ?: e.javaClass.simpleName}") }
                return
            }
        }
        try {
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            ui.update { it.copy(error = "The camera isn't available. Close other camera apps and try again.") }
            session = null
            return
        }
        renderer.session = session
        surface.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            surface.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer.session = null
        session?.close()
        session = null
        io.shutdown()
    }

    private fun saveCaptured(c: Captured) {
        val state = ui.value
        io.execute {
            val photoName = c.photo?.let { bmp ->
                val f = store.newPhotoFile(itemId, "m")
                runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }.getOrNull()?.let { f.name }
                    .also { bmp.recycle() }
            }
            val now = System.currentTimeMillis()
            var old: Measurement? = null
            val item = store.update(itemId, now) { item ->
                val replace = replaceId?.let { id -> item.measurements.find { it.id == id } }
                old = replace
                val m = Measurement(
                    id = replace?.id ?: Store.newId(),
                    name = state.name,
                    kind = c.kind,
                    arMeters = c.meters,
                    tapeMeters = replace?.tapeMeters,
                    points = c.points,
                    photo = photoName,
                    createdAt = now,
                )
                if (replace != null) item.copy(measurements = item.measurements.map { if (it.id == replace.id) m else it })
                else item.copy(measurements = item.measurements + m)
            }
            old?.photo?.let { store.file(itemId, it).delete() }
            runOnUiThread {
                val said = "Saved ${state.name}: ${fmtBoth(c.meters, unit)}"
                if (replaceId != null || item == null) {
                    finish()
                    return@runOnUiThread
                }
                val next = nextUi(item, c.kind)
                renderer.queue += Cmd.Mode(next.kind)
                ui.value = next.copy(message = said)
            }
        }
    }

    @Composable
    private fun Screen() {
        val s by snap.collectAsState()
        val u by ui.collectAsState()
        var naming by remember { mutableStateOf(false) }
        val density = resources.displayMetrics.density

        LaunchedEffect(u.message) {
            if (u.message != null) {
                delay(3500)
                ui.update { if (it.message == u.message) it.copy(message = null) else it }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { renderer.queue += Cmd.Tap } }) {
                val now = s ?: return@Canvas
                drawIntoCanvas { Overlay.draw(it.nativeCanvas, now, unit, density, live = true) }
            }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { finish() }) { Text(if (replaceId != null) "CANCEL" else "DONE") }
                    Spacer(Modifier.padding(4.dp))
                    Box(
                        Modifier.weight(1f).background(Color(0xCC000000), RoundedCornerShape(10.dp))
                            .clickable(enabled = replaceId == null) { naming = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Text(u.name + if (replaceId == null) "  ✎" else "", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                            u.hint?.let { Text(it, fontSize = 12.sp, color = Color(0xFFCCCCCC)) }
                        }
                    }
                }
                Spacer(Modifier.padding(4.dp))
                val words = u.error ?: u.message ?: say(s, u)
                Text(
                    words,
                    color = if (u.error != null) Color(0xFFFFB4A9) else Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                val snapNow = s
                if (snapNow != null && snapNow.points.isNotEmpty()) {
                    val v = snapNow.totalM + if (!snapNow.complete || snapNow.kind == Kind.PATH) (snapNow.liveM?.takeIf { snapNow.aim && snapNow.canAdd } ?: 0.0) else 0.0
                    Text(
                        fmtLength(v, unit),
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).background(Color(0xB3000000), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.padding(6.dp))
                }
                val empty = snapNow?.points.isNullOrEmpty()
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Kind.LINE to "LINE (2 points)", Kind.PATH to "TRACE (a curve)").forEachIndexed { i, (k, label) ->
                        SegmentedButton(
                            selected = u.kind == k,
                            onClick = {
                                renderer.queue += Cmd.Mode(k)
                                ui.update { it.copy(kind = k) }
                            },
                            enabled = empty && !u.saving,
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.padding(4.dp))
                val ready = snapNow?.complete == true && !u.saving
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { renderer.queue += Cmd.Undo }, enabled = !empty && !u.saving, modifier = Modifier.weight(1f)) { Text("UNDO") }
                    FilledTonalButton(onClick = { capture(false) }, enabled = ready, modifier = Modifier.weight(1f)) { Text("SAVE") }
                    Button(
                        onClick = { capture(true) }, enabled = ready, modifier = Modifier.weight(1.4f),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
                    ) { Text("PHOTO + SAVE", fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (naming) NameDialog(u, onDismiss = { naming = false }) { name, kind ->
            naming = false
            val item = store.get(itemId)
            val next = if (item != null) stepUi(item, name, kind ?: u.kind) else u.copy(name = name)
            if (empty(s)) renderer.queue += Cmd.Mode(next.kind)
            ui.value = next.copy(kind = if (empty(s)) next.kind else u.kind)
        }
    }

    private fun empty(s: Snap?) = s?.points.isNullOrEmpty()

    private fun capture(photo: Boolean) {
        ui.update { it.copy(saving = true) }
        renderer.queue += Cmd.Capture(photo, ui.value.name, unit)
        // Saving resets this (saveCaptured); a capture that never lands (tracking lost) lets the buttons back.
        surface.postDelayed({ ui.update { if (it.saving) it.copy(saving = false) else it } }, 4000)
    }

    private fun say(s: Snap?, u: MeasureUi): String {
        if (u.saving) return "Saving…"
        if (s == null) return "Starting AR…"
        if (!s.tracking) return s.status ?: "Move the phone slowly side to side to start."
        val far = (s.aimDistM ?: 0.0) > 1.5
        val n = s.points.size
        return when {
            s.complete && s.kind == Kind.LINE -> "Tap SAVE, or PHOTO + SAVE to keep a picture of it. UNDO moves the end."
            !s.aim -> if (s.depth) "Move a little closer, slowly, until the ring turns green." else "Aim at a spot with some detail until the ring turns green."
            n == 0 -> "Put the ring on where it starts and tap anywhere." + if (far) " Get within about 3 ft for accuracy." else ""
            s.kind == Kind.LINE -> "Now the ring on where it ends, and tap."
            else -> "Tap along the curve, a point every few inches. SAVE when you reach the end ($n points)."
        }
    }

    @Composable
    private fun NameDialog(u: MeasureUi, onDismiss: () -> Unit, onPick: (String, Kind?) -> Unit) {
        val item = remember { store.get(itemId) }
        val template = Templates.byId(item?.template)
        var text by remember { mutableStateOf(u.name) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What are you measuring?") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    if (template != null && item != null) {
                        Spacer(Modifier.padding(4.dp))
                        Text("${template.name} checklist", fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(template.steps) { step ->
                                val done = Templates.measured(item, step)
                                Row(
                                    Modifier.fillMaxWidth().clickable { onPick(step.name, step.kind) }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (done != null) "✓ " else "○ ", color = if (done != null) Green else Color.Gray)
                                    Text(step.name, Modifier.weight(1f))
                                    done?.meters?.let { Text(fmtLength(it, unit), color = Color.Gray) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onPick(text.trim(), null) }) { Text("USE NAME") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        )
    }
}
