// AN ITEM: its checklist (each step measured or not; tap MEASURE to do it in AR), any other measurements, its
// photos, notes, and export. A measurement opens full screen (MeasurementScreen). Deletes go to the item's own
// "Deleted" list (measurements, photos) or Trash (the item), each restorable, with UNDO right away.
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bpclare.armeasure.Env
import com.bpclare.armeasure.ar.MeasureActivity
import com.bpclare.armeasure.data.Checklists
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.data.Step
import com.bpclare.armeasure.geo.fmtLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(env: Env, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = env.store
    val item = remember(id, env.version) { store.get(id) }
    if (item == null || item.deletedAt != null) {
        LaunchedEffect(Unit) { env.nav.back() }
        return
    }
    val checklist = env.checklists.get(item.template)
    var notes by remember(id) { mutableStateOf(item.notes) }
    var menu by remember { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun measure(name: String?, kind: Kind?) = ctx.startActivity(MeasureActivity.intent(ctx, id, name, kind))
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending?.let { store.file(id, it) }
        pending = null
        if (ok && f != null && f.length() > 0) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { normalizePhoto(f) } }
                store.update(id) { it.copy(photos = it.photos + f.name) }
                env.changed()
            }
        } else {
            f?.delete()
        }
    }

    fun takePhoto() {
        val f = store.newPhotoFile(id, "p")
        pending = f.name
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

    fun trashItem() {
        store.trash(id)
        env.changed()
        env.nav.back()
        env.undoable("${item.name} moved to Trash") { store.restore(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { env.nav.back() } },
                title = { Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { env.nav.push("rename:$id") }) },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Rename") }, onClick = { menu = false; env.nav.push("rename:$id") })
                            DropdownMenuItem({ Text("Save ZIP to Downloads") }, onClick = { menu = false; export(false) })
                            DropdownMenuItem({ Text("Share ZIP") }, onClick = { menu = false; export(true) })
                            DropdownMenuItem({ Text("Move item to Trash") }, onClick = { menu = false; trashItem() })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Centered {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val next = Checklists.nextStep(item, checklist)
                        Button(onClick = { measure(next?.name, next?.kind) }, modifier = Modifier.weight(1.4f)) {
                            Text("MEASURE IN AR", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        OutlinedButton(onClick = { takePhoto() }, modifier = Modifier.weight(1f)) { Text("TAKE PHOTO", modifier = Modifier.padding(vertical = 6.dp)) }
                    }
                }
                if (checklist != null) {
                    item {
                        val done = checklist.steps.count { Checklists.measured(item, it) != null }
                        Section("${checklist.name} checklist · $done of ${checklist.steps.size}")
                    }
                    items(checklist.steps, key = { "step:" + it.name }) { step ->
                        val m = Checklists.measured(item, step)
                        StepRow(env, id, step, m, onMeasure = { measure(step.name, step.kind) }, onOpen = { m?.let { env.nav.push("m:$id:${it.id}") } })
                    }
                }
                val others = item.live.filter { !Checklists.isStep(checklist, it.name) }
                if (others.isNotEmpty() || checklist == null) item { Section(if (checklist != null) "Other measurements" else "Measurements") }
                if (others.isEmpty() && checklist == null) item { Muted("None yet. Tap MEASURE IN AR.") }
                items(others, key = { it.id }) { m -> MeasurementRow(env, id, m) { env.nav.push("m:$id:${m.id}") } }
                item { Section("Photos · ${item.photos.size}") }
                item {
                    if (item.photos.isEmpty()) Muted("Plain photos of the item go here (TAKE PHOTO).")
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(item.photos) { p -> Thumb(store.file(id, p), Modifier.size(96.dp).testTag("photo:$p")) { env.nav.push("photo:$id:$p") } }
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
                if (item.deletedMeasurements.isNotEmpty() || item.deletedPhotos.isNotEmpty()) {
                    item { Section("Deleted (restorable)") }
                    items(item.deletedMeasurements, key = { "del:" + it.id }) { m ->
                        DeletedRow("${m.name}: ${m.meters?.let { fmtLength(it, env.unit) } ?: "not measured"}") {
                            store.update(id) { it.copy(measurements = it.measurements.map { x -> if (x.id == m.id) x.copy(deletedAt = null) else x }) }
                            env.changed()
                        }
                    }
                    items(item.deletedPhotos, key = { "delp:$it" }) { p ->
                        DeletedRow("A photo", store.file(id, p)) {
                            store.update(id) { it.copy(photos = it.photos + p, deletedPhotos = it.deletedPhotos - p) }
                            env.changed()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Section(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun DeletedRow(label: String, photo: File? = null, onRestore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (photo != null) {
            Thumb(photo, Modifier.size(40.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRestore) { Text("RESTORE") }
    }
}

@Composable
private fun StepRow(env: Env, id: String, step: Step, m: Measurement?, onMeasure: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { if (m != null) onOpen() else onMeasure() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m?.photo != null) Thumb(env.store.file(id, m.photo), Modifier.size(52.dp))
            else Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text(if (step.kind == Kind.PATH) "〰" else "↔", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.name, fontWeight = FontWeight.Bold)
                if (step.hint.isNotBlank()) Text(step.hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.size(8.dp))
            val v = m?.meters
            if (v != null) Column(horizontalAlignment = Alignment.End) {
                Text(fmtLength(v, env.unit), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (m.tapeMeters != null) Text("tape", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else TextButton(onClick = onMeasure) { Text("MEASURE") }
        }
    }
}

@Composable
private fun MeasurementRow(env: Env, id: String, m: Measurement, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(m.photo?.let { env.store.file(id, it) }, Modifier.size(52.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.Bold)
                Text(if (m.kind == Kind.PATH) "Traced, ${m.points.size} points" else "Straight line", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            m.meters?.let { Text(fmtLength(it, env.unit), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

