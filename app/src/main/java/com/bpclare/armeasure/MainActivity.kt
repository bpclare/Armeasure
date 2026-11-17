package com.bpclare.armeasure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bpclare.armeasure.data.Store
import com.bpclare.armeasure.ui.AppTheme
import com.bpclare.armeasure.ui.HomeScreen
import com.bpclare.armeasure.ui.ItemScreen
import com.bpclare.armeasure.ui.Prefs
import com.bpclare.armeasure.ui.UpdateModel
import java.io.File

class MainActivity : ComponentActivity() {
    /** Bumped on every return to the app (AR saves straight to the store), so the screens read it again. */
    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(File(filesDir, "items"))
        val updates = UpdateModel(applicationContext)
        updates.check()
        setContent { AppTheme { App(store, updates, resumed.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }
}

@Composable
private fun App(store: Store, updates: UpdateModel, resumed: Int) {
    val ctx = LocalContext.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var unit by remember { mutableStateOf(Prefs.unit(ctx)) }
    var edits by remember { mutableIntStateOf(0) }
    val version = resumed * 100_000 + edits
    val id = openId
    if (id == null) {
        HomeScreen(
            store = store, version = version, unit = unit, updates = updates,
            onUnit = { unit = it; Prefs.setUnit(ctx, it) },
            onOpen = { openId = it },
            onChanged = { edits++ },
        )
    } else {
        BackHandler { openId = null }
        ItemScreen(store = store, id = id, version = version, unit = unit, onChanged = { edits++ }, onBack = { openId = null })
    }
}
