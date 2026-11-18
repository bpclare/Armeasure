package com.bpclare.armeasure.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bpclare.armeasure.BuildConfig
import com.bpclare.armeasure.Env
import com.bpclare.armeasure.data.Checklists
import com.bpclare.armeasure.data.Item
import com.bpclare.armeasure.data.Store
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

/** "Build 3 · 1a2b3c4": what is installed, shown on Home and in Settings. */
val buildLabel get() = "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.COMMIT}"

/** The update check and install, kept across screens (MainActivity holds it). */
class UpdateModel(private val app: Context) {
    var release by mutableStateOf<Release?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun check(manual: Boolean = false) {
        if (Build.FINGERPRINT == "robolectric") return
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
fun HomeScreen(env: Env) {
    val ctx = LocalContext.current
    val items = remember(env.version) { env.store.list() }
    var menu by remember { mutableStateOf(false) }
    val updates = env.updates

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Measure", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Settings") }, onClick = { menu = false; env.nav.push("settings") })
                            DropdownMenuItem({ Text("Help") }, onClick = { menu = false; env.nav.push("help") })
                            DropdownMenuItem({ Text("Trash") }, onClick = { menu = false; env.nav.push("trash") })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Centered {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                updates.release?.let { r ->
                    item {
                        Card {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Update ready: ${r.title}", fontWeight = FontWeight.Bold)
                                    Text(updates.status ?: "You have build ${BuildConfig.VERSION_CODE}.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = { updates.install(ctx) }) { Text("INSTALL") }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { env.nav.push("new") }, modifier = Modifier.fillMaxWidth()) {
                        Text("+  NEW ITEM", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
                if (items.isEmpty()) {
                    item {
                        Text(
                            "Nothing measured yet. Start a new item (a car seat, a cushion, anything), then measure it in AR. Help is in the ⋯ menu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items, key = { it.id }) { item -> ItemCard(env, item) { env.nav.push("item:${item.id}") } }
                item {
                    Text(
                        buildLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCard(env: Env, item: Item, onClick: () -> Unit) {
    val store: Store = env.store
    val photo = item.live.firstNotNullOfOrNull { it.photo } ?: item.photos.firstOrNull()
    val checklist = env.checklists.get(item.template)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(photo?.let { store.file(item.id, it) }, Modifier.size(64.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val done = checklist?.let { c -> " · ${c.steps.count { Checklists.measured(item, it) != null }} of ${c.steps.size} on the ${c.name.lowercase()} list" } ?: ""
                Text(
                    "${item.live.size} measured · ${item.photos.size + item.live.count { it.photo != null }} photos$done",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.updatedAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
