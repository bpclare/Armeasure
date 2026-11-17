// AN ITEM: its checklist (each step measured or not; tap MEASURE to do it in AR), any other measurements, its
// photos, notes, and export. A measurement opens to its photo, its name, a tape reading to type over AR (and how far
// AR was off), RETAKE and DELETE.
package com.bpclare.armeasure.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.bpclare.armeasure.ar.MeasureActivity
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.data.Step
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.data.Templates
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.M_TO_IN
import com.bpclare.armeasure.geo.fmtBoth
import com.bpclare.armeasure.geo.fmtCm
import com.bpclare.armeasure.geo.fmtDiff
import com.bpclare.armeasure.geo.fmtIn
import com.bpclare.armeasure.geo.fmtLength
import com.bpclare.armeasure.geo.parseLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(store: Store, id: String, version: Int, unit: LengthUnit, onChanged: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = remember(id, version) { store.get(id) }
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val template = Templates.byId(item.template)
    var notes by remember(id) { mutableStateOf(item.notes) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var openM by remember { mutableStateOf<String?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }

    fun measure(name: String?, kind: Kind?, replace: String? = null) =
        ctx.startActivity(MeasureActivity.intent(ctx, id, name, kind, replace))

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        pending = null
        if (ok && f != null && f.length() > 0) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { normalizePhoto(f) } }
                store.update(id) { it.copy(photos = it.photos + f.name) }
                onChanged()
            }
        } else {
            f?.delete()
        }
    }

    fun takePhoto() {
        val f = store.newPhotoFile(id, "p")
        pending = f
        takePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }

    fun export(share: Boolean) {
        scope.launch {
            val fresh = store.get(id) ?: return@launch
            val r = withContext(Dispatchers.IO) {
                runCatching { if (share) Exports.shareIntent(ctx, store, fresh) else Exports.saveToDownloads(ctx, store, fresh) }
            }
            r.onSuccess { if (it is android.content.Intent) ctx.startActivity(it) else toast("Saved to $it") }
                .onFailure { toast("Couldn't export: ${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ ITEMS") } },
                title = { Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { renaming = true }) },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem({ Text("Save ZIP to Downloads") }, onClick = { menu = false; export(false) })
                            DropdownMenuItem({ Text("Share ZIP") }, onClick = { menu = false; export(true) })
                            DropdownMenuItem({ Text("Delete item", color = Color(0xFFFFB4A9)) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val next = Templates.nextStep(item)
                    Button(onClick = { measure(next?.name, next?.kind) }, modifier = Modifier.weight(1.4f)) {
                        Text("MEASURE IN AR", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    OutlinedButton(onClick = { takePhoto() }, modifier = Modifier.weight(1f)) { Text("TAKE PHOTO", modifier = Modifier.padding(vertical = 6.dp)) }
                }
            }
            if (template != null) {
                item {
                    val done = template.steps.count { Templates.measured(item, it) != null }
                    Section("${template.name} checklist · $done of ${template.steps.size}")
                }
                items(template.steps, key = { "step:" + it.name }) { step ->
                    val m = Templates.measured(item, step)
                    StepRow(store, id, step, m, unit, onMeasure = { measure(step.name, step.kind) }, onOpen = { m?.let { openM = it.id } })
                }
            }
            val others = item.measurements.filter { !Templates.isStep(template, it.name) }
            if (others.isNotEmpty() || template == null) item { Section(if (template != null) "Other measurements" else "Measurements") }
            if (others.isEmpty() && template == null) item { Text("None yet. Tap MEASURE IN AR.", color = Color.Gray) }
            items(others, key = { it.id }) { m -> MeasurementRow(store, id, m, unit) { openM = m.id } }
            item { Section("Photos · ${item.photos.size}") }
            item {
                if (item.photos.isEmpty()) Text("Plain photos of the item go here (TAKE PHOTO).", color = Color.Gray)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(item.photos) { p -> Thumb(store.file(id, p), Modifier.size(96.dp)) { viewPhoto = p } }
                }
            }
            item { Section("Notes") }
            item {
                OutlinedTextField(
                    notes,
                    { v ->
                        notes = v
                        store.update(id) { it.copy(notes = v) }
                    },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("Material, color, stitching, anything for the maker") },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { export(false) }, modifier = Modifier.weight(1f)) { Text("SAVE ZIP") }
                    FilledTonalButton(onClick = { export(true) }, modifier = Modifier.weight(1f)) { Text("SHARE ZIP") }
                }
            }
        }
    }

    if (renaming) TextDialog("Rename", item.name, onDismiss = { renaming = false }) { v ->
        renaming = false
        store.update(id) { it.copy(name = v) }
        onChanged()
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${item.name}?") },
        text = { Text("Its measurements and photos are deleted from this phone. Export first to keep a copy.") },
        confirmButton = {
            TextButton(onClick = {
                deleting = false
                store.delete(id)
                onChanged()
                onBack()
            }) { Text("DELETE", color = Color(0xFFFFB4A9)) }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("CANCEL") } },
    )
    item.measurements.find { it.id == openM }?.let { m ->
        MeasurementDialog(
            store, id, m, unit,
            onDismiss = { openM = null },
            onSave = { name, tape ->
                openM = null
                store.update(id) { it.copy(measurements = it.measurements.map { x -> if (x.id == m.id) x.copy(name = name, tapeMeters = tape) else x }) }
                onChanged()
            },
            onRetake = {
                openM = null
                measure(m.name, m.kind, m.id)
            },
            onDelete = {
                openM = null
                store.update(id) { it.copy(measurements = it.measurements.filter { x -> x.id != m.id }) }
                m.photo?.let { store.file(id, it).delete() }
                onChanged()
            },
            onPhoto = { viewPhoto = m.photo },
        )
    }
    viewPhoto?.let { p ->
        PhotoViewer(
            store.file(id, p),
            onDelete = if (p in item.photos) ({
                viewPhoto = null
                store.update(id) { it.copy(photos = it.photos - p) }
                store.file(id, p).delete()
                onChanged()
            }) else null,
            onDismiss = { viewPhoto = null },
        )
    }
}

@Composable
private fun Section(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun StepRow(store: Store, id: String, step: Step, m: Measurement?, unit: LengthUnit, onMeasure: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { if (m != null) onOpen() else onMeasure() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m?.photo != null) Thumb(store.file(id, m.photo), Modifier.size(52.dp)) else Box(Modifier.size(52.dp).background(Color(0xFF22262A))) {
                Text(if (step.kind == Kind.PATH) "〰" else "↔", Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 20.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.name, fontWeight = FontWeight.Bold)
                Text(step.hint, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
            }
            Spacer(Modifier.size(8.dp))
            if (m?.meters != null) Column(horizontalAlignment = Alignment.End) {
                Text(fmtLength(m.meters!!, unit), fontWeight = FontWeight.Bold, color = Green)
                if (m.tapeMeters != null) Text("tape", fontSize = 11.sp, color = Color.Gray)
            } else TextButton(onClick = onMeasure) { Text("MEASURE") }
        }
    }
}

@Composable
private fun MeasurementRow(store: Store, id: String, m: Measurement, unit: LengthUnit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(m.photo?.let { store.file(id, it) }, Modifier.size(52.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.Bold)
                Text(if (m.kind == Kind.PATH) "Traced, ${m.points.size} points" else "Straight line", fontSize = 12.sp, color = Color.Gray)
            }
            m.meters?.let { Text(fmtLength(it, unit), fontWeight = FontWeight.Bold, color = Green) }
        }
    }
}

@Composable
private fun MeasurementDialog(
    store: Store, id: String, m: Measurement, unit: LengthUnit,
    onDismiss: () -> Unit, onSave: (String, Double?) -> Unit, onRetake: () -> Unit, onDelete: () -> Unit, onPhoto: () -> Unit,
) {
    var name by remember(m.id) { mutableStateOf(m.name) }
    val tapeStart = m.tapeMeters?.let { if (unit == LengthUnit.IN) fmtIn(it * M_TO_IN) else fmtCm(it) } ?: ""
    var tape by remember(m.id) { mutableStateOf(tapeStart.replace("¼", " 1/4").replace("½", " 1/2").replace("¾", " 3/4").trim()) }
    val tapeM = parseLength(tape, unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(m.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.photo != null) Thumb(store.file(id, m.photo), Modifier.fillMaxWidth().aspectRatio(0.75f), maxPx = 900, crop = false, onClick = onPhoto)
                Text("AR: " + (m.arMeters?.let { fmtBoth(it, unit) } ?: "—") + if (m.kind == Kind.PATH) " · traced, ${m.points.size} points" else "", fontSize = 14.sp)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    tape, { tape = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tape reading (${if (unit == LengthUnit.IN) "in" else "cm"}), optional") },
                    placeholder = { Text(if (unit == LengthUnit.IN) "22 1/4" else "56.5") },
                    isError = tape.isNotBlank() && tapeM == null,
                )
                if (tapeM != null && m.arMeters != null) Text("AR was ${fmtDiff(m.arMeters - tapeM, unit)} from the tape. The tape's number is used.", fontSize = 13.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetake) { Text("RETAKE") }
                    OutlinedButton(onClick = onDelete) { Text("DELETE", color = Color(0xFFFFB4A9)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { m.name }, if (tape.isBlank()) null else tapeM ?: m.tapeMeters) }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}

@Composable
private fun TextDialog(title: String, start: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var v by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onDone(v.trim()) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun PhotoViewer(file: File, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Thumb(file, Modifier.fillMaxSize(), maxPx = 2400, crop = false)
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete) { Text("DELETE", color = Color(0xFFFFB4A9)) }
                Button(onClick = onDismiss) { Text("CLOSE") }
            }
        }
    }
}
