package com.maxim.currency

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maxim.currency.domain.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CurrencyApplication
    @Before fun resetSettings() = runBlocking {
        app.graph.store.update {UserSettings(selected=listOf("USD","EUR","RUB"),language="en")}
        withTimeout(15000) {app.graph.settings.first {it?.language=="en" && it.amount=="1"}}
        Unit
    }
    private fun waitHome() {compose.waitUntil(20000) {compose.onAllNodesWithTag("key_C").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun keypadPersistsAndCanBeReopened() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitHome()
            listOf("C","1","2","5",".","5","0").forEach {compose.onNodeWithTag("key_$it").performClick()}
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.amount=="125.50"}}}
            compose.onNodeWithTag("currency_USD").assertTextContains("125.50")
            compose.onNodeWithTag("key_⌄").performClick()
            compose.onNodeWithText("Show keypad").performClick()
            compose.onNodeWithTag("key_⌫").performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.amount=="125.5"}}}
            scenario.recreate();waitHome()
            compose.onNodeWithTag("currency_USD").assertTextContains("125.5")
        }
    }
    @Test fun searchFindsRussianAndEnglishNamesAndAddsWithoutDuplicates() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("nav_currencies").performClick()
            compose.onNodeWithTag("currency_search").performTextInput("иена")
            compose.onNodeWithText("JPY").performScrollTo().performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {"JPY" in it.selected}}}
            assertEquals(1,runBlocking {app.graph.store.settings.first()}.selected.count {it=="JPY"})
            compose.onNodeWithTag("currency_search").performTextReplacement("pound")
            compose.onNodeWithTag("currency_list").performScrollToNode(hasText("GBP"))
            compose.onNodeWithText("GBP").assertExists()
        }
    }
    @Test fun languageThemeAndPopupModePersist() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Dark",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithText("Main screen",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithText("Русский",useUnmergedTree=true).performScrollTo().performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.language=="ru" && it.theme=="dark" && it.processMode=="main"}}}
            scenario.recreate()
            compose.waitUntil(20000) {compose.onAllNodesWithText("Настройки").fetchSemanticsNodes().isNotEmpty()}
        }
    }
    @Test fun invalidSelectionDoesNotOfferConversion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(context,ProcessTextActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT,"nothing monetary").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ProcessTextActivity>(intent).use {
            compose.waitUntil(20000) {compose.onAllNodesWithText("Could not parse the selection.",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Open converter").assertDoesNotExist()
        }
    }
    @Test fun dollarSymbolRequiresExplicitSourceChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(context,ProcessTextActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT,"100$").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ProcessTextActivity>(intent).use {
            compose.waitUntil(20000) {compose.onAllNodesWithText("This symbol represents several currencies.",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Open converter").assertDoesNotExist()
        }
    }
}
