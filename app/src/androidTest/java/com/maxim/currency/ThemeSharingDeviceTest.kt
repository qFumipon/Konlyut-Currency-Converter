package com.maxim.currency

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maxim.currency.data.StyleFileCodec
import com.maxim.currency.domain.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeSharingDeviceTest {
    @Test fun sharedContentUriCanBeRead() {
        val context=ApplicationProvider.getApplicationContext<CurrencyApplication>()
        val file=File(context.cacheDir,"shared_themes/device-test.currency-style.json")
        file.parentFile!!.mkdirs()
        file.writeBytes(StyleFileCodec.encode(ThemePreset(id="user",name="Shared")))
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        assertEquals("content",uri.scheme)
        val decoded=context.contentResolver.openInputStream(uri)!!.use {StyleFileCodec.decode(it)}
        assertEquals("Shared",(decoded as StyleFileCodec.DecodeResult.Success).preset.name)
    }
}
