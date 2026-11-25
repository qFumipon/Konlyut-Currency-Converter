package com.maxim.currency

import android.content.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.domain.UserSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class ShareFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<CurrencyApplication>()
    @Before fun setup() {runBlocking {app.graph.store.update {UserSettings(language="en")}}}
    @Test fun sendSupportsMultipleAmountsAndManualCorrection() {
        val intent=Intent(app,ProcessTextActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"100 USD and 200 EUR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ProcessTextActivity>(intent).use {
            compose.waitUntil(15000) {compose.onAllNodesWithText("Choose an amount").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Amount text").performTextReplacement("500 US dollars")
            compose.onNodeWithText("Recognize").performClick()
            compose.onNodeWithText("Open converter").performScrollTo().assertExists()
        }
    }
    @Test fun pasteReadsClipboardOnlyAfterButtonPress() {
        app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("amount","250 rubles"))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) {compose.onAllNodesWithTag("paste_amount").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("paste_amount").performClick()
            val intent=shadowOf(app).nextStartedActivity
            assertEquals(Intent.ACTION_SEND,intent.action);assertEquals("250 rubles",intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
    @Test fun mainModeUsesSameRecognizedAmount() {
        runBlocking {app.graph.store.update {it.copy(processMode="main")}}
        val intent=Intent(app,ProcessTextActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"500 US dollars").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ProcessTextActivity>(intent).use {
            var opened:Intent?=null
            compose.waitUntil(15000) {opened=shadowOf(app).peekNextStartedActivity();opened!=null}
            assertEquals("USD",opened!!.getStringExtra("currency"));assertEquals("500",opened!!.getStringExtra("amount"))
        }
    }
}
