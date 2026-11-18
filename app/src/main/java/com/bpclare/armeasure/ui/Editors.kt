// FULL-SCREEN EDITORS (Bryan's rules, Nov 18: "edit screens full screen (not pop-ups)"; "Back never drops unsaved
// input"). Back keeps what was typed: a new item's name stays as a draft for next time; a rename or a measurement's
// name and tape reading are saved, as SAVE would.
package com.bpclare.armeasure.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bpclare.armeasure.Env
import com.bpclare.armeasure.ar.MeasureActivity
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.geo.M_TO_IN
import com.bpclare.armeasure.geo.fmtBoth
import com.bpclare.armeasure.geo.fmtCm
import com.bpclare.armeasure.geo.fmtDiff
import com.bpclare.armeasure.geo.fmtIn
import com.bpclare.armeasure.geo.parseLength

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(title: String, onBack: () -> Unit, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                actions = { if (action != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.Bold) } },
            )
        },
    ) { pad ->
        Centered {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun NewItemScreen(env: Env, draftName: String, draftChecklist: String?, onDraft: (String, String?) -> Unit, onCreate: (String, String?) -> Unit) {
    val checklists = remember(env.version) { env.checklists.list() }
    var name by rememberSaveable { mutableStateOf(draftName) }
    var checklist by rememberSaveable { mutableStateOf(draftChecklist ?: checklists.firstOrNull()?.id) }
    val create = { onCreate(name.trim().ifEmpty { "Item" }, checklist) }
    EditorScaffold("New item", onBack = { onDraft(name, checklist); env.nav.back() }, action = "CREATE", onAction = create) {
        OutlinedTextField(
            name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") }, placeholder = { Text("2019 F-150, driver seat") },
        )
        Text("Checklist", fontWeight = FontWeight.Bold)
        val choices = listOf<Pair<String?, String>>(null to "Free measuring (name each one)") + checklists.map { it.id to it.name }
        choices.forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = checklist == id, onClick = { checklist = id }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = checklist == id, onClick = { checklist = id })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Muted("Checklists are edited in Settings.")
        Button(onClick = create, modifier = Modifier.fillMaxWidth()) { Text("CREATE", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

@Composable
fun RenameScreen(env: Env, id: String) {
    val item = remember(id) { env.store.get(id) }
    var name by rememberSaveable { mutableStateOf(item?.name ?: "") }
    val save = {
        if (name.isNotBlank() && name.trim() != item?.name) {
            env.store.update(id) { it.copy(name = name.trim()) }
            env.changed()
        }
        env.nav.back()
    }
    EditorScaffold("Rename", onBack = save, action = "SAVE", onAction = save) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
    }
}

/** A tape figure as typed back into its field: 22 1/4, or 56.5 in centimeters. */
private fun tapeText(m: Double?, unit: LengthUnit) = m?.let {
    if (unit == LengthUnit.IN) fmtIn(it * M_TO_IN).replace("¼", " 1/4").replace("½", " 1/2").replace("¾", " 3/4").trim() else fmtCm(it)
} ?: ""

@Composable
fun MeasurementScreen(env: Env, itemId: String, mid: String) {
    val ctx = LocalContext.current
    val item = remember(itemId, env.version) { env.store.get(itemId) }
    val m = item?.measurements?.find { it.id == mid }
    if (m == null || m.deletedAt != null) {
        LaunchedEffect(Unit) { env.nav.back() }
        return
    }
    val unit = env.unit
    var name by rememberSaveable(mid) { mutableStateOf(m.name) }
    var tape by rememberSaveable(mid) { mutableStateOf(tapeText(m.tapeMeters, unit)) }
    val tapeM = parseLength(tape, unit)

    fun save(): Boolean {
        val newTape = if (tape.isBlank()) null else tapeM ?: m.tapeMeters
        val newName = name.trim().ifEmpty { m.name }
        if (newName != m.name || newTape != m.tapeMeters) {
            env.store.update(itemId) { it.copy(measurements = it.measurements.map { x -> if (x.id == mid) x.copy(name = newName, tapeMeters = newTape) else x }) }
            env.changed()
        }
        return true
    }
    val done = { save(); env.nav.back() }

    EditorScaffold(m.name, onBack = done, action = "SAVE", onAction = done) {
        if (m.photo != null) {
            Thumb(env.store.file(itemId, m.photo), Modifier.fillMaxWidth().aspectRatio(0.75f), maxPx = 1200, crop = false) {
                save()
                env.nav.push("photo:$itemId:${m.photo}")
            }
            Muted("Tap the photo to see it full size.")
        }
        Text(
            "AR read " + (m.arMeters?.let { fmtBoth(it, unit) } ?: "nothing") + if (m.kind == Kind.PATH) " · traced, ${m.points.size} points" else "",
            fontSize = 15.sp,
        )
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            tape, { tape = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Tape reading (${if (unit == LengthUnit.IN) "in" else "cm"}), optional") },
            placeholder = { Text(if (unit == LengthUnit.IN) "22 1/4" else "56.5") },
            isError = tape.isNotBlank() && tapeM == null,
            supportingText = { if (tape.isNotBlank() && tapeM == null) Text(if (unit == LengthUnit.IN) "Type it like 22, 22.25 or 22 1/4" else "Type it like 56 or 56.5") },
        )
        if (tapeM != null && m.arMeters != null) Muted("AR was ${fmtDiff(m.arMeters - tapeM, unit)} from the tape. The tape's number is used.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                save()
                env.nav.back()
                ctx.startActivity(MeasureActivity.intent(ctx, itemId, m.name, m.kind, replaceId = mid))
            }) { Text("RETAKE IN AR") }
            OutlinedButton(onClick = {
                env.store.update(itemId) { it.copy(measurements = it.measurements.map { x -> if (x.id == mid) x.copy(deletedAt = System.currentTimeMillis()) else x }) }
                env.changed()
                env.nav.back()
                env.undoable("${m.name} deleted") {
                    env.store.update(itemId) { it.copy(measurements = it.measurements.map { x -> if (x.id == mid) x.copy(deletedAt = null) else x }) }
                }
            }) { Text("DELETE", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.size(8.dp))
        Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("SAVE", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

/** A photo full screen. Back (or CLOSE) closes only the photo. A plain photo can be deleted (restorable). */
@Composable
fun PhotoScreen(env: Env, itemId: String, file: String) {
    val item = remember(itemId, env.version) { env.store.get(itemId) }
    val plain = item?.photos?.contains(file) == true
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Thumb(env.store.file(itemId, file), Modifier.fillMaxSize(), maxPx = 2400, crop = false)
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (plain) OutlinedButton(onClick = {
                env.store.update(itemId) { it.copy(photos = it.photos - file, deletedPhotos = it.deletedPhotos + file) }
                env.changed()
                env.nav.back()
                env.undoable("Photo deleted") { env.store.update(itemId) { it.copy(photos = it.photos + file, deletedPhotos = it.deletedPhotos - file) } }
            }) { Text("DELETE", color = Color(0xFFFFB4A9)) }
            Button(onClick = { env.nav.back() }) { Text("CLOSE") }
        }
    }
}
