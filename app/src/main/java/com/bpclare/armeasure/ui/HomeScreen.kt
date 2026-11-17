package com.bpclare.armeasure.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.bpclare.armeasure.BuildConfig
import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.data.Templates
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.update.Release
import com.bpclare.armeasure.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The update check and install, kept across screens (MainActivity holds it). */
class UpdateModel(private val app: Context) {
    var release by mutableStateOf<Release?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun check(manual: Boolean = false) {
        scope.launch {
            if (manual) status = "Checking for updates…"
            val r = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest(BuildConfig.UPDATE_REPO) }.getOrNull() }
            release = r?.takeIf { Updater.isNewer(it, BuildConfig.VERSION_CODE) }
            if (manual) status = if (release == null) "You have the latest (build ${BuildConfig.VERSION_CODE})." else null
        }
    }

    fun install(ctx: Context) {
        val r = release ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            status = "Allow installs from AR Measure, come back, and tap INSTALL again."
            return
        }
        scope.launch {
            status = "Downloading…"
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    Updater.download(r.apkUrl, File(app.cacheDir, "updates/ar-measure.apk")) { p -> scope.launch { status = "Downloading… $p%" } }
                }.getOrNull()
            }
            if (f == null) {
                status = "The download failed. Try again."
                return@launch
            }
            status = null
            Updater.install(ctx, f)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: Store,
    version: Int,
    unit: LengthUnit,
    updates: UpdateModel,
    onUnit: (LengthUnit) -> Unit,
    onOpen: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val items = remember(version) { store.list() }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Measure", fontWeight = FontWeight.Bold) },
                actions = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 8.dp)) {
                        LengthUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = unit == u, onClick = { onUnit(u) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                                Text(if (u == LengthUnit.IN) "in" else "cm")
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            updates.release?.let { r ->
                item {
                    Card {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Update ready: ${r.title}", fontWeight = FontWeight.Bold)
                                Text(updates.status ?: "You have build ${BuildConfig.VERSION_CODE}.", fontSize = 13.sp, color = Color.Gray)
                            }
                            Button(onClick = { updates.install(ctx) }) { Text("INSTALL") }
                        }
                    }
                }
            }
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+  NEW ITEM", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "Nothing measured yet. Start a new item (a car seat, a cushion, anything), then measure it in AR.",
                        color = Color.Gray,
                    )
                }
            }
            items(items, key = { it.id }) { item -> ItemCard(store, item) { onOpen(item.id) } }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Build ${BuildConfig.VERSION_CODE}", color = Color.Gray, fontSize = 12.sp)
                    if (updates.release == null && updates.status != null) Text(updates.status!!, color = Color.Gray, fontSize = 12.sp)
                    TextButton(onClick = { updates.check(manual = true) }) { Text("CHECK FOR UPDATES") }
                }
            }
        }
    }

    if (adding) NewItemDialog(onDismiss = { adding = false }) { name, template ->
        adding = false
        val item = store.create(name, template)
        onChanged()
        onOpen(item.id)
    }
}

@Composable
private fun ItemCard(store: Store, item: Item, onClick: () -> Unit) {
    val photo = item.measurements.firstNotNullOfOrNull { it.photo } ?: item.photos.firstOrNull()
    val template = Templates.byId(item.template)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(photo?.let { store.file(item.id, it) }, Modifier.size(64.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val done = template?.let { t -> " · ${t.steps.count { Templates.measured(item, it) != null }}/${t.steps.size} of the ${t.name.lowercase()} list" } ?: ""
                Text("${item.measurements.size} measured · ${item.photos.size + item.measurements.count { it.photo != null }} photos$done", fontSize = 13.sp, color = Color.Gray)
                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.updatedAt)), fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun NewItemDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf<String?>(Templates.CAR_SEAT.id) }
    val choices = listOf<Pair<String?, String>>(null to "Free measuring (name each one)") + Templates.all.map { it.id to "${it.name} checklist" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New item") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") }, placeholder = { Text("2019 F-150, driver seat") },
                )
                Spacer(Modifier.size(8.dp))
                choices.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = template == id, onClick = { template = id }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = template == id, onClick = { template = id })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim().ifEmpty { "Item" }, template) }) { Text("CREATE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
