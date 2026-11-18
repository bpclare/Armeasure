// Back on every screen and layer (Bryan's rules, Nov 18): Back goes to the prior screen; a viewer closes on Back, top
// layer only; an editor keeps what was typed; at Home, Back puts the app in the background and never closes it.
// Runs on the computer with Robolectric.
package com.bpclare.armeasure

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bpclare.armeasure.data.Kind
import com.bpclare.armeasure.data.Measurement
import com.bpclare.armeasure.data.Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var store: Store
    private lateinit var itemId: String

    @Before fun seed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        File(ctx.filesDir, "items").deleteRecursively()
        File(ctx.filesDir, "checklists.json").delete()
        store = Store(File(ctx.filesDir, "items"))
        itemId = store.create("Practice seat (demo)", "car_seat").id
        File(store.dir(itemId), "p-demo.jpg").writeBytes(byteArrayOf(1, 2, 3))
        store.update(itemId) {
            it.copy(
                measurements = listOf(Measurement("m1", "Back height", Kind.LINE, 0.5652, createdAt = 1)),
                photos = listOf("p-demo.jpg"),
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After fun close() = scenario.close()

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun home() = compose.onNodeWithText("+  NEW ITEM").assertIsDisplayed()
    private fun openItem() {
        compose.onNodeWithText("Practice seat (demo)").performClick()
        compose.onNodeWithText("MEASURE IN AR").assertIsDisplayed()
    }
    private fun menu(entry: String) {
        compose.onNodeWithText("⋯").performClick()
        compose.onNodeWithText(entry).performClick()
        compose.waitForIdle()
    }

    @Test fun backAtHomeKeepsTheAppRunning() {
        home()
        back()
        var finishing = true
        scenario.onActivity { finishing = it.isFinishing }
        assertFalse("Back at Home must minimize, never close", finishing)
    }

    @Test fun backFromAnItemGoesHome() {
        openItem()
        back()
        home()
    }

    @Test fun backFromNewItemKeepsWhatWasTyped() {
        compose.onNodeWithText("+  NEW ITEM").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Rear bench")
        back()
        home()
        compose.onNodeWithText("+  NEW ITEM").performClick()
        compose.onNodeWithText("Rear bench").assertExists()
    }

    @Test fun backFromAMeasurementSavesItsName() {
        openItem()
        compose.onNodeWithText("Back height").performClick()
        compose.onNodeWithText("Tape reading", substring = true).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Back height")).performTextReplacement("Back height, center")
        back()
        compose.onNodeWithText("MEASURE IN AR").assertIsDisplayed()
        compose.onNodeWithText("Tape reading", substring = true).assertDoesNotExist()
        assertEquals("Back height, center", store.get(itemId)!!.live.single().name)
    }

    @Test fun backFromRenameSaves() {
        openItem()
        menu("Rename")
        compose.onNode(hasSetTextAction()).performTextReplacement("Driver seat (demo)")
        back()
        compose.onNodeWithText("MEASURE IN AR").assertIsDisplayed()
        assertEquals("Driver seat (demo)", store.get(itemId)!!.name)
    }

    @Test fun backClosesOnlyThePhoto() {
        openItem()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Photos · 1"))
        compose.onNodeWithTag("photo:p-demo.jpg").performClick()
        compose.onNodeWithText("CLOSE").assertIsDisplayed()
        back()
        compose.onNodeWithText("CLOSE").assertDoesNotExist()
        compose.onNodeWithText("MEASURE IN AR").assertExists()
    }

    @Test fun backThroughSettingsAndAChecklist() {
        menu("Settings")
        compose.onNodeWithText("Car seat").performClick()
        compose.onNodeWithText("Checklist name").assertExists()
        back()
        compose.onNodeWithText("Checklist name").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        back()
        home()
    }

    @Test fun backFromHelpAndTrash() {
        menu("Help")
        compose.onNodeWithText("Getting good numbers").assertExists()
        back()
        home()
        menu("Trash")
        compose.onNodeWithText("Trash is empty.").assertExists()
        back()
        home()
    }

    @Test fun aDeletedMeasurementComesBackWithUndo() {
        openItem()
        compose.onNodeWithText("Back height").performClick()
        compose.onNodeWithText("DELETE").performClick()
        compose.waitForIdle()
        assertEquals(0, store.get(itemId)!!.live.size)
        compose.onNodeWithText("UNDO").performClick()
        compose.waitForIdle()
        assertEquals(1, store.get(itemId)!!.live.size)
    }

    @Test fun aDeletedItemWaitsInTrash() {
        openItem()
        menu("Move item to Trash")
        home()
        compose.onNodeWithText("Practice seat (demo)").assertDoesNotExist()
        menu("Trash")
        compose.onNodeWithText("RESTORE").performClick()
        back()
        compose.onNodeWithText("Practice seat (demo)").assertExists()
    }
}
