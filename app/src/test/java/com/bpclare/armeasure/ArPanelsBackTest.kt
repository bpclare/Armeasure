// The AR screen's own layers (the name panel, help): Back closes the top one only, and the name panel never drops a
// typed name. ARCore itself can't run here; these panels can.
package com.bpclare.armeasure.ar

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bpclare.armeasure.geo.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ArPanelsBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun backKeepsATypedName() {
        var picked: String? = null
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) NamePanel("Measurement 1", null, null, LengthUnit.IN, onClose = { open = false }) { n, _ -> picked = n; open = false }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Armrest")
        back()
        assertEquals("Armrest", picked)
        compose.onNodeWithText("What are you measuring?").assertDoesNotExist()
    }

    @Test fun backWithNothingTypedJustCloses() {
        var picked: String? = null
        var closed = false
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) NamePanel("Measurement 1", null, null, LengthUnit.IN, onClose = { closed = true; open = false }) { n, _ -> picked = n }
        }
        back()
        assertTrue(closed)
        assertNull(picked)
    }

    @Test fun backClosesHelp() {
        var closed = false
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) HelpPanel(onClose = { closed = true; open = false })
        }
        compose.onNodeWithText("Getting good numbers").assertExists()
        back()
        assertTrue(closed)
    }
}
