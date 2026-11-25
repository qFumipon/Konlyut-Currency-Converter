package com.maxim.currency

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.domain.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w393dp-h852dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SmallUiFixesTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<CurrencyApplication>()
    @Before fun reset()=runBlocking {app.graph.store.update {UserSettings(language="en")};Unit}

    @Test fun toolbarButtonsHaveMatchingBoundsAndSettingsOpens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
            val refresh=compose.onNodeWithContentDescription("Refresh rates").fetchSemanticsNode().boundsInRoot
            val settings=compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
            assertEquals(refresh.width,settings.width,0.1f)
            assertEquals(refresh.height,settings.height,0.1f)
            assertEquals(refresh.center.y,settings.center.y,0.1f)
            assertTrue(refresh.width>=48f)
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().assertExists()
        }
    }

    @Test fun helpIsAdjacentReadableAndClosesInBothLanguages() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav_settings").performClick()
            val title=compose.onNodeWithText("Text conversion").fetchSemanticsNode().boundsInRoot
            val help=compose.onNodeWithContentDescription("Text conversion help").fetchSemanticsNode().boundsInRoot
            assertTrue(help.left-title.right<55f)
            assertEquals(title.center.y,help.center.y,15f)
            compose.onNodeWithContentDescription("Text conversion help").performClick()
            compose.onNodeWithText("How to convert selected text").assertExists()
            compose.onNodeWithText("3. If this option is unavailable",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("In Settings, you can choose whether",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Русский").performScrollTo().performClick()
            compose.waitUntil(10000) { app.graph.settings.value?.language == "ru" }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Справка по конвертации текста").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Справка по конвертации текста").performScrollTo().performClick()
            compose.onNodeWithText("Как конвертировать текст").assertExists()
            compose.onNodeWithText("3. Если такого пункта нет",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("В настройках можно выбрать",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("Закрыть").performClick()
        }
    }

}
