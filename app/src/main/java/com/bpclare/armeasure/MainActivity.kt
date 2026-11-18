// The app's screens as a stack (Bryan's rules, Nov 18): Back always goes to the prior screen; a viewer or editor
// closes on Back, top layer only; an editor keeps what was typed (it saves, or keeps a draft); at the root (Home) Back
// puts the app in the background with everything kept, never closes it and never asks "Exit?".
package com.bpclare.armeasure

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bpclare.armeasure.data.ChecklistStore
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.geo.LengthUnit
import com.bpclare.armeasure.ui.AppTheme
import com.bpclare.armeasure.ui.ChecklistScreen
import com.bpclare.armeasure.ui.HelpScreen
import com.bpclare.armeasure.ui.HomeScreen
import com.bpclare.armeasure.ui.ItemScreen
import com.bpclare.armeasure.ui.MeasurementScreen
import com.bpclare.armeasure.ui.NewItemScreen
import com.bpclare.armeasure.ui.PhotoScreen
import com.bpclare.armeasure.ui.Prefs
import com.bpclare.armeasure.ui.RenameScreen
import com.bpclare.armeasure.ui.SettingsScreen
import com.bpclare.armeasure.ui.TrashScreen
import com.bpclare.armeasure.ui.UpdateModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    /** Bumped on every return to the app (AR saves straight to the store), so the screens read it again. */
    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(File(filesDir, "items"))
        val checklists = ChecklistStore(File(filesDir, "checklists.json"))
        val updates = UpdateModel(applicationContext)
        if (Build.FINGERPRINT != "robolectric") updates.check()
        setContent { AppTheme { App(store, checklists, updates, resumed.intValue) { moveTaskToBack(true) } } }
    }

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }
}

/** Moving between screens. Routes: home, item:<id>, new, rename:<id>, m:<item>:<measurement>, photo:<item>:<file>,
 *  settings, checklist:<id>, trash, help. */
class Nav(val push: (String) -> Unit, val back: () -> Unit, val replace: (String) -> Unit)

/** What every screen needs. */
class Env(
    val store: Store,
    val checklists: ChecklistStore,
    val unit: LengthUnit,
    val version: Int,
    val nav: Nav,
    val updates: UpdateModel,
    /** Something was saved: the screens read the store again. */
    val changed: () -> Unit,
    /** Shows what was just done with UNDO for a while; undo runs if tapped. */
    val undoable: (String, () -> Unit) -> Unit,
    val setUnit: (LengthUnit) -> Unit,
)

@Composable
fun App(store: Store, checklists: ChecklistStore, updates: UpdateModel, resumed: Int, minimize: () -> Unit) {
    val ctx = LocalContext.current
    var stackText by rememberSaveable { mutableStateOf("home") }
    val stack = stackText.split('\n')
    var unit by remember { mutableStateOf(Prefs.unit(ctx)) }
    var edits by remember { mutableIntStateOf(0) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftChecklist by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val nav = Nav(
        push = { stackText = (stack + it).joinToString("\n") },
        back = { if (stack.size > 1) stackText = stack.dropLast(1).joinToString("\n") },
        replace = { stackText = (stack.dropLast(1) + it).joinToString("\n") },
    )
    val env = Env(
        store = store, checklists = checklists, unit = unit, version = resumed * 100_000 + edits, nav = nav, updates = updates,
        changed = { edits++ },
        undoable = { msg, undo ->
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                if (snack.showSnackbar(msg, "UNDO", withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    undo()
                    edits++
                }
            }
        },
        setUnit = { unit = it; Prefs.setUnit(ctx, it) },
    )

    // At the root, Back minimizes; anywhere else it goes to the prior screen. A screen with something to keep (an
    // editor) adds its own handler, which runs first.
    BackHandler(enabled = stack.size == 1) { minimize() }
    BackHandler(enabled = stack.size > 1) { nav.back() }

    Box(Modifier.fillMaxSize()) {
        val top = stack.last()
        val parts = top.split(':')
        when (parts[0]) {
            "item" -> ItemScreen(env, parts[1])
            "new" -> NewItemScreen(env, draftName, draftChecklist,
                onDraft = { n, c -> draftName = n; draftChecklist = c },
                onCreate = { n, c ->
                    draftName = ""
                    draftChecklist = null
                    val item = store.create(n, c)
                    edits++
                    nav.replace("item:${item.id}")
                })
            "rename" -> RenameScreen(env, parts[1])
            "m" -> MeasurementScreen(env, parts[1], parts[2])
            "photo" -> PhotoScreen(env, parts[1], parts.drop(2).joinToString(":"))
            "settings" -> SettingsScreen(env)
            "checklist" -> ChecklistScreen(env, parts[1])
            "trash" -> TrashScreen(env)
            "help" -> HelpScreen(env)
            else -> HomeScreen(env)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp))
    }
}
