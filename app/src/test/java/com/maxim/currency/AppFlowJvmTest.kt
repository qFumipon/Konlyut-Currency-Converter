package com.maxim.currency

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import com.maxim.currency.domain.UserSettings
import com.maxim.currency.domain.ThemePreset
import com.maxim.currency.domain.ColorRole
import com.maxim.currency.data.StyleFileCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppFlowJvmTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CurrencyApplication
    @Before fun resetSettings() = runBlocking {
        app.graph.store.update {UserSettings(selected=listOf("USD","EUR","RUB"),language="en")}
        withTimeout(15000) {app.graph.settings.first {it?.language=="en" && it.amount=="1"}}
        Unit
    }
    private fun waitHome() {compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun keypadPersistsAndCanBeReopened() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitHome()
            compose.onNodeWithTag("key_C").assertDoesNotExist()
            compose.onNodeWithTag("currency_USD").performClick()
            compose.onNodeWithTag("key_±").assertDoesNotExist()
            val zero=compose.onNodeWithTag("key_0").fetchSemanticsNode().boundsInRoot
            val hide=compose.onNodeWithTag("key_⌄").fetchSemanticsNode().boundsInRoot
            assertEquals(zero.center.y,hide.center.y,1f)
            assertTrue(hide.width>zero.width*1.5f)
            listOf("C","1","2","5",".","5","0").forEach {compose.onNodeWithTag("key_$it").performClick()}
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.amount=="125.50"}}}
            compose.onNodeWithTag("currency_USD").assertTextContains("125.50")
            compose.onNodeWithTag("key_⌄").performClick()
            compose.onNodeWithTag("key_C").assertDoesNotExist()
            compose.onNodeWithTag("currency_USD").performClick()
            compose.onNodeWithTag("key_⌫").performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.amount=="125.5"}}}
            scenario.recreate();waitHome()
            compose.onNodeWithTag("key_C").assertDoesNotExist()
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
    @Test fun aboutShowsBuildVersionOnlyInBothLanguages() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("About").performScrollTo().assertExists()
            compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
            compose.onNodeWithText("No ads, accounts or analytics.",substring=true).assertDoesNotExist()
            compose.onNodeWithText("Русский").performScrollTo().performClick()
            compose.waitUntil(20000) {compose.onAllNodesWithText("О приложении").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("О приложении").performScrollTo().assertExists()
            compose.onNodeWithText("Версия ${BuildConfig.VERSION_NAME}").assertExists()
            compose.onNodeWithText("Без рекламы, регистрации и аналитики.",substring=true).assertDoesNotExist()
        }
    }
    @Test fun manualUpdateModePersistsAndUnconfiguredCheckExplainsItself() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Manual",substring=false).performScrollTo().performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.updateMode=="manual"}}}
            compose.onNodeWithTag("check_updates").performScrollTo().performClick()
            compose.onNodeWithText("The update repository is not configured yet").performScrollTo().assertExists()
            scenario.recreate()
            compose.waitUntil(20000) {compose.onAllNodesWithText("About").fetchSemanticsNodes().isNotEmpty()}
            assertEquals("manual",runBlocking {app.graph.store.settings.first()}.updateMode)
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
    @Test fun standardThemesAreProtectedAndCustomDraftCanBeSaved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().performClick()
            compose.onNodeWithText("Main background").assertDoesNotExist()
            compose.onNodeWithText("Create theme",substring=false).performClick()
            compose.onNodeWithText("New theme").assertExists()
            compose.onNodeWithText("Create",substring=false).assertDoesNotExist()
            compose.onNodeWithText("Editing palette").assertDoesNotExist()
            compose.onNodeWithText("Appearance mode").assertDoesNotExist()
            compose.onNodeWithTag("theme_name").performTextInput("Ocean")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Expand all"))
            compose.onNodeWithText("Expand all").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").performScrollTo().performClick()
            compose.onNodeWithText("HEX · 6 characters").performTextReplacement("123456")
            compose.onNodeWithText("Apply").performScrollTo().performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertContentDescriptionEquals("Main background, #123456")
            compose.onNodeWithTag("preview_basic").assert(hasContentDescription("MAIN=#123456",substring=true))
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Theme base"))
            compose.onNodeWithText("Dark",substring=false).performClick()
            compose.onNodeWithText("Replace edited colors?").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertContentDescriptionEquals("Main background, #123456")
            assertEquals(null,runBlocking {app.graph.store.settings.first()}.presets.firstOrNull {it.name=="Ocean"})
            compose.onNodeWithText("Save",substring=false).performClick()
            runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.presets.any {t->t.name=="Ocean"}}}}
            val saved=runBlocking {app.graph.store.settings.first()}
            assertEquals("123456",saved.presets.first {it.name=="Ocean"}.light["MAIN"])
            assertEquals("123456",saved.presets.first {it.name=="Ocean"}.dark["MAIN"])
            assertTrue(saved.presets.first {it.id=="default"}.light.isEmpty())
            compose.onNodeWithContentDescription("Theme actions").performClick()
            compose.onNodeWithText("Edit theme").assertExists()
            compose.onNodeWithText("Rename").assertExists()
            compose.onNodeWithText("Create copy").assertExists()
            compose.onNodeWithText("Save to file").assertExists()
            compose.onNodeWithText("Delete").assertExists()
        }
    }
    @Test fun accordionAndUnsavedExitKeepDraftUntilDiscard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().performClick()
            compose.onNodeWithText("Create theme",substring=false).performClick()
            compose.onNodeWithTag("theme_name").performTextInput("Draft")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("section_basic").performClick()
            compose.onNodeWithTag("color_MAIN").assertExists()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_currency"))
            compose.onNodeWithTag("section_currency").performClick()
            compose.onNodeWithTag("color_ROW").assertExists()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertExists()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Collapse all"))
            compose.onNodeWithText("Collapse all").performClick()
            compose.onNodeWithTag("color_MAIN").assertDoesNotExist()
            compose.onNodeWithText("Expand all").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Discard changes?").assertExists()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithTag("theme_name").assertTextContains("Draft")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Discard").performClick()
            assertNull(runBlocking {app.graph.store.settings.first()}.presets.firstOrNull {it.name=="Draft"})
        }
    }
    @Test fun newThemeUsesCurrentCustomColors() {
        runBlocking {app.graph.store.update {UserSettings(language="en",presetId="custom",
            presets=listOf(ThemePreset(),ThemePreset(id="custom",name="Existing").withColor(ColorRole.MAIN,false,"123456")))};
            withTimeout(15000) {app.graph.settings.first {it?.presetId=="custom"}}}
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().performClick()
            compose.onNodeWithText("Create theme",substring=false).performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Expand all"))
            compose.onNodeWithText("Expand all").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertContentDescriptionEquals("Main background, #123456")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Theme base"))
            compose.onNodeWithText("Dark",substring=false).performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            compose.onNodeWithTag("color_MAIN").assertContentDescriptionEquals("Main background, #101719")
            assertNull(runBlocking {app.graph.store.settings.first()}.presets.firstOrNull {it.name=="New theme"})
        }
    }
    @Test fun importedThemeEditsIconInBothVariantsWithoutErasingOtherDifferences() {
        val source=ThemePreset(id="source",name="Imported",mode="dark",
            light=mapOf("MAIN" to "123456"),dark=mapOf("MAIN" to "ABCDEF"))
        val imported=(StyleFileCodec.decode(StyleFileCodec.encode(source)) as StyleFileCodec.DecodeResult.Success)
            .preset.copy(id="imported")
        runBlocking {app.graph.store.update {UserSettings(language="en",presets=listOf(ThemePreset(),imported))};
            withTimeout(15000) {app.graph.settings.first {it?.presets?.any {p->p.id=="imported"}==true}}}
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Theme actions").performClick()
            compose.onNodeWithText("Edit theme").performClick()
            compose.onNodeWithText("Editing palette").assertDoesNotExist()
            compose.onNodeWithText("Appearance mode").assertDoesNotExist()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_text"))
            compose.onNodeWithTag("section_text").performClick()
            compose.onNodeWithTag("preview_text").assert(hasContentDescription("ICON=#B4D9D6",substring=true))
            compose.onNodeWithTag("preview_text_refresh").performClick()
            compose.onNodeWithText("HEX · 6 characters").performTextReplacement("FF00AA")
            compose.onNodeWithText("Apply").performScrollTo().performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_text"))
            compose.onNodeWithTag("preview_text").assert(hasContentDescription("ICON=#FF00AA",substring=true))
            compose.onNodeWithText("Save",substring=false).performClick()
            val saved=runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.presets.any {p->p.id=="imported" && p.light["ICON"]=="FF00AA"}}}}.presets.first {it.id=="imported"}
            assertEquals("dark",saved.mode)
            assertEquals("123456",saved.hex(ColorRole.MAIN,false))
            assertEquals("ABCDEF",saved.hex(ColorRole.MAIN,true))
            assertEquals("FF00AA",saved.hex(ColorRole.ICON,false))
            assertEquals("FF00AA",saved.hex(ColorRole.ICON,true))
            compose.onNodeWithContentDescription("Theme actions").performClick()
            compose.onNodeWithText("Edit theme").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_text"))
            compose.onNodeWithTag("section_text").performClick()
            compose.onNodeWithTag("preview_text_refresh").performClick()
            compose.onNodeWithText("HEX · 6 characters").performTextReplacement("00AAFF")
            compose.onNodeWithText("Apply").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Discard").performClick()
            assertEquals("FF00AA",runBlocking {app.graph.store.settings.first()}.presets.first {it.id=="imported"}.hex(ColorRole.ICON,false))
        }
    }
    @Test fun eachSectionPreviewUpdatesBeforeSaving() {
        val changes=listOf(
            Triple("basic",ColorRole.MAIN,"112233"),
            Triple("currency",ColorRole.ROW,"223344"),
            Triple("keypad",ColorRole.KEYPAD,"334455"),
            Triple("text",ColorRole.TEXT,"445566"),
            Triple("controls",ColorRole.BUTTON,"556677"),
            Triple("windows",ColorRole.DIALOG,"667788")
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            waitHome();compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance themes").performScrollTo().performClick()
            compose.onNodeWithText("Create theme",substring=false).performClick()
            compose.onNodeWithTag("theme_name").performTextInput("Six samples")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Expand all"))
            compose.onNodeWithText("Expand all").performClick()
            for((section,role,hex) in changes) {
                compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_$section"))
                compose.onNodeWithTag("preview_$section").assertExists()
                compose.onNodeWithTag("color_${role.name}").performScrollTo().performClick()
                compose.onNodeWithTag("dialog_preview_$section").assertExists()
                compose.onNodeWithText("HEX · 6 characters").performTextReplacement(hex)
                compose.onNodeWithTag("dialog_preview_$section").assert(hasContentDescription("${role.name}=#$hex",substring=true))
                compose.onNodeWithText("Apply").performScrollTo().performClick()
                compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_$section"))
                compose.onNodeWithTag("preview_$section").assert(hasContentDescription("${role.name}=#$hex",substring=true))
            }
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_text"))
            compose.onNodeWithTag("color_TEXT").performScrollTo().performClick()
            compose.onNodeWithText("HEX · 6 characters").performTextReplacement("ABCDEF")
            compose.onNodeWithTag("dialog_preview_text").assert(hasContentDescription("TEXT=#ABCDEF",substring=true))
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.onNodeWithTag("preview_text").assert(hasContentDescription("TEXT=#445566",substring=true))
            assertNull(runBlocking {app.graph.store.settings.first()}.presets.firstOrNull {it.name=="Six samples"})
            compose.onNodeWithText("Save",substring=false).performClick()
            val saved=runBlocking {withTimeout(15000) {app.graph.store.settings.first {it.presets.any {p->p.name=="Six samples"}}}}.presets.first {it.name=="Six samples"}
            changes.forEach {(_,role,hex)->
                assertEquals(hex,saved.hex(role,false))
                assertEquals(hex,saved.hex(role,true))
            }
        }
    }
}
