// SETTINGS: units, the checklists (Bryan, Nov 18: "Editable in settings"), Trash, Help, the build and its commit,
// updates. The checklist editor saves each change as it is made, so Back never loses anything; a removed step stays
// under "Removed steps" with RESTORE, and a deleted checklist goes to Trash.
package com.bpclare.armeasure.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bpclare.armeasure.Env
import com.bpclare.armeasure.data.Checklist
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Step
import com.bpclare.armeasure.geo.LengthUnit
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                actions = { actions() },
            )
        },
    ) { pad ->
        Centered {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
fun SettingsScreen(env: Env) {
    val checklists = remember(env.version) { env.checklists.list() }
    val trashCount = remember(env.version) { env.store.trash().size + env.checklists.trash().size }
    ListScaffold("Settings", onBack = { env.nav.back() }) {
        item { Section("Units") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LengthUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(selected = env.unit == u, onClick = { env.setUnit(u) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (u == LengthUnit.IN) "Inches (to the ¼)" else "Centimeters")
                    }
                }
            }
        }
        item { Section("Checklists") }
        item { Muted("The measurements an item needs, in order. AR walks the list. Tap one to change it.") }
        items(checklists, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().clickable { env.nav.push("checklist:${c.id}") }) {
                Column(Modifier.padding(14.dp)) {
                    Text(c.name, fontWeight = FontWeight.Bold)
                    Text("${c.steps.size} measurements", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(onClick = {
                val c = env.checklists.create("New checklist")
                env.changed()
                env.nav.push("checklist:${c.id}")
            }, modifier = Modifier.fillMaxWidth()) { Text("+  NEW CHECKLIST") }
        }
        item { Section("More") }
        item { SettingRow("Trash", if (trashCount == 0) "Empty" else "$trashCount in Trash") { env.nav.push("trash") } }
        item { SettingRow("Help", "How to measure, checklists, export, updates") { env.nav.push("help") } }
        item { Section("About") }
        item { Text(buildLabel) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { env.updates.check(manual = true) }) { Text("CHECK FOR UPDATES") }
                env.updates.status?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChecklistScreen(env: Env, id: String) {
    var c by remember(id) { mutableStateOf(env.checklists.get(id)) }
    var menu by remember { mutableStateOf(false) }
    val cur = c
    if (cur == null || cur.deletedAt != null) {
        LaunchedEffect(Unit) { env.nav.back() }
        return
    }
    fun update(f: (Checklist) -> Checklist) {
        val next = f(c ?: return)
        c = next
        env.checklists.save(next)
        env.changed()
    }
    fun setStep(i: Int, s: Step) = update { x -> x.copy(steps = x.steps.toMutableList().also { it[i] = s }) }
    fun move(i: Int, d: Int) = update { x ->
        val j = i + d
        if (j !in x.steps.indices) x else x.copy(steps = x.steps.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
    }

    ListScaffold(cur.name, onBack = { env.nav.back() }, actions = {
        Box {
            TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Move checklist to Trash") }, onClick = {
                    menu = false
                    env.checklists.trash(id)
                    env.changed()
                    env.nav.back()
                    env.undoable("${cur.name} moved to Trash") { env.checklists.restore(id) }
                })
            }
        }
    }) {
        item {
            OutlinedTextField(cur.name, { v -> update { it.copy(name = v) } }, singleLine = true, label = { Text("Checklist name") }, modifier = Modifier.fillMaxWidth())
        }
        item { Muted("Changes save as you type. Items already measured keep their numbers; a renamed step shows as a new one on them.") }
        itemsIndexed(cur.steps, key = { i, _ -> "s$i" }) { i, s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        OutlinedTextField(s.name, { v -> setStep(i, s.copy(name = v)) }, singleLine = true, label = { Text("Measurement") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(s.hint, { v -> setStep(i, s.copy(hint = v)) }, label = { Text("Where to measure (optional)") }, modifier = Modifier.fillMaxWidth())
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(Kind.LINE to "Straight line", Kind.PATH to "Trace a curve").forEachIndexed { k, (kind, label) ->
                            SegmentedButton(selected = s.kind == kind, onClick = { setStep(i, s.copy(kind = kind)) }, shape = SegmentedButtonDefaults.itemShape(k, 2)) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { move(i, -1) }, enabled = i > 0) { Text("↑ UP") }
                        OutlinedButton(onClick = { move(i, 1) }, enabled = i < cur.steps.size - 1) { Text("↓ DOWN") }
                        TextButton(onClick = { update { x -> x.copy(steps = x.steps.filterIndexed { k, _ -> k != i }, removed = x.removed + s) } }) {
                            Text("REMOVE", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { update { it.copy(steps = it.steps + Step("Measurement ${it.steps.size + 1}")) } }, modifier = Modifier.fillMaxWidth()) {
                Text("+  ADD MEASUREMENT")
            }
        }
        if (cur.removed.isNotEmpty()) {
            item { Section("Removed (restorable)") }
            itemsIndexed(cur.removed, key = { i, _ -> "r$i" }) { i, s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { update { x -> x.copy(steps = x.steps + s, removed = x.removed.filterIndexed { k, _ -> k != i }) } }) { Text("RESTORE") }
                }
            }
        }
    }
}

@Composable
fun TrashScreen(env: Env) {
    val items = remember(env.version) { env.store.trash() }
    val lists = remember(env.version) { env.checklists.trash() }
    var forever by remember { mutableStateOf<String?>(null) }
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
    ListScaffold("Trash", onBack = { env.nav.back() }) {
        item { Muted("Deleted items and checklists wait here until you restore them. Deleted measurements and photos are restored from their item, at the bottom of it.") }
        if (items.isEmpty() && lists.isEmpty()) item { Muted("Trash is empty.") }
        if (items.isNotEmpty()) item { Section("Items") }
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("Deleted ${fmt.format(Date(item.deletedAt ?: 0))} · ${item.live.size} measured", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { env.store.restore(item.id); env.changed() }) { Text("RESTORE") }
                    TextButton(onClick = { forever = item.id }) { Text("DELETE FOREVER", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
        }
        if (lists.isNotEmpty()) item { Section("Checklists") }
        items(lists, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { env.checklists.restore(c.id); env.changed() }) { Text("RESTORE") }
                }
            }
        }
    }
    forever?.let { id ->
        val name = items.find { it.id == id }?.name ?: "this item"
        AlertDialog(
            onDismissRequest = { forever = null },
            title = { Text("Delete $name for good?") },
            text = { Text("Its measurements and photos are removed from the phone and can't be restored. Export it first to keep a copy.") },
            confirmButton = { TextButton(onClick = { env.store.deleteForever(id); forever = null; env.changed() }) { Text("DELETE FOREVER", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { forever = null }) { Text("KEEP IT") } },
        )
    }
}

/** The help, in sections. measuringOnly: the part the AR screen shows. */
val HELP: List<Pair<String, String>> = listOf(
    "Measuring" to "Point the ring in the middle of the screen at where the measurement starts. When the ring turns green the phone has found the surface: tap anywhere on the screen. Then point the ring at where it ends and tap again.\n\nLINE measures the straight distance between two points, in any direction. TRACE adds up the pieces between as many points as you tap along a curve (over a bolster, round a cushion's front edge). Tap a point every few inches.",
    "Getting good numbers" to "Stand 1 to 3 feet away. Use good light. When AR starts, move the phone slowly side to side for a few seconds so it can find its place. Hold steady when you tap. Dark, shiny or plain surfaces are harder: aim at a seam or an edge.",
    "Saving" to "SAVE keeps the number. PHOTO + SAVE also keeps the camera picture with the line and the length drawn on. UNDO takes the last point back. On a checklist, the next measurement comes up on its own; DONE goes back to the item. Tap the name at the top to measure something else.",
    "Checking against a tape" to "Open a measurement and type the tape's reading (22, 22.25 or 22 1/4). The app shows how far AR was off and uses the tape's number from then on.",
    "Checklists" to "A checklist is the list of measurements a kind of item needs (car seat, box). Change them in Settings, Checklists: rename, reorder, add, remove (restorable), choose straight line or trace, or make a new one.",
    "Export" to "On an item, SAVE ZIP puts a summary, a spreadsheet file and the photos in Downloads, AR Measure. SHARE ZIP sends the same by email, Drive or text. Everything else stays only on this phone: uninstalling the app removes it, so export what you want to keep.",
    "Deleting and restoring" to "Nothing is deleted for good by accident. A deleted measurement or photo is listed at the bottom of its item with RESTORE. A deleted item or checklist goes to Trash (Home, ⋯ menu). UNDO also shows right after a delete.",
    "The Fold" to "AR works with the phone closed or open, upright or sideways.",
    "Updates" to "When a new build is out, Home shows Update ready with INSTALL. Android asks you to confirm. The build you have is shown at the bottom of Home and in Settings.",
)

@Composable
fun HelpScreen(env: Env) {
    ListScaffold("Help", onBack = { env.nav.back() }) {
        HELP.forEach { (title, body) ->
            item { Section(title) }
            item { Text(body, fontSize = 15.sp) }
        }
    }
}
