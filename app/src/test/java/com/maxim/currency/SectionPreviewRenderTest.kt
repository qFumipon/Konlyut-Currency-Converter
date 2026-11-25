package com.maxim.currency

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.maxim.currency.domain.ColorRole
import com.maxim.currency.domain.ThemePreset
import com.maxim.currency.domain.UserSettings
import com.maxim.currency.ui.ColorSections
import com.maxim.currency.ui.CurrencyTheme
import com.maxim.currency.ui.SectionPreview
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w320dp-h640dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SectionPreviewRenderTest {
    @get:Rule val compose=createComposeRule()

    @Test fun iconPreviewPixelsChangeWithUnsavedDraft() {
        val section=ColorSections.common.first {it.id=="text"}
        var draft by mutableStateOf(ThemePreset(id="draft",name="Draft"))
        compose.setContent {CurrencyTheme(UserSettings(language="en")) {
            SectionPreview(section,draft,false,{})
        }}
        val before=compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle {draft=draft.withSharedColor(ColorRole.ICON,"FF00AA")}
        val after=compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse(before.sameAs(after))
        val directory=File(requireNotNull(System.getProperty("ui.screenshot.dir"))).apply {mkdirs()}
        File(directory,"icon-preview-before.png").outputStream().use {before.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(directory,"icon-preview-after.png").outputStream().use {after.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
