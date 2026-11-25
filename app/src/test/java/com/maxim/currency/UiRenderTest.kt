package com.maxim.currency

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.domain.UserSettings
import com.maxim.currency.domain.ThemePreset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRenderTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun capture(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val directory=File(requireNotNull(System.getProperty("ui.screenshot.dir")));directory.mkdirs()
        File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun renderRussianLightAndDarkScreens() {
        val app=ApplicationProvider.getApplicationContext<CurrencyApplication>()
        runBlocking {app.graph.store.update {UserSettings(language="ru",theme="light")}}
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
            capture("main-light-ru")
            compose.onNodeWithTag("currency_USD").performClick()
            capture("keypad-light-ru")
            compose.onNodeWithTag("key_⌄").performClick()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Тёмная").performScrollTo().performClick()
            compose.waitUntil(10000) {app.graph.settings.value?.theme=="dark"}
            compose.waitForIdle()
            capture("settings-dark-ru")
            compose.onNodeWithTag("nav_home").performClick()
            capture("main-dark-ru")
        }
    }
    @Test fun renderCompactThemeList() {
        val app=ApplicationProvider.getApplicationContext<CurrencyApplication>()
        runBlocking {app.graph.store.update {UserSettings(language="ru",theme="light",
            presets=listOf(ThemePreset(),ThemePreset(id="sample",name="Морская тема с длинным названием",mode="dark")))}}
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Темы оформления").performScrollTo().performClick()
            compose.onNodeWithText("Морская тема с длинным названием").assertExists()
            capture("themes-light-ru")
            compose.onNodeWithText("Создать тему").performClick()
            compose.onNodeWithText("Новая тема").assertExists()
            capture("theme-editor-light-ru")
            compose.onNodeWithText("Тёмная",substring=false).performClick()
            capture("theme-editor-dark-palette-ru")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Развернуть все"))
            compose.onNodeWithText("Развернуть все").performClick()
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_basic"))
            capture("theme-colors-expanded-ru")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasTestTag("section_text"))
            capture("theme-text-preview-ru")
        }
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun renderNarrowThemeEditor() {
        val app=ApplicationProvider.getApplicationContext<CurrencyApplication>()
        runBlocking {app.graph.store.update {UserSettings(language="ru",theme="light")}}
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20000) {compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Темы оформления").performScrollTo().performClick()
            compose.onNodeWithText("Создать тему").performClick()
            capture("theme-editor-narrow-ru")
            compose.onNodeWithTag("appearance_list").performScrollToNode(hasText("Развернуть все"))
            capture("theme-categories-narrow-ru")
        }
    }
}
