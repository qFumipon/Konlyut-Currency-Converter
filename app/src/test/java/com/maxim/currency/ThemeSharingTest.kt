package com.maxim.currency

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import android.content.pm.PackageManager
import com.maxim.currency.data.StyleFileCodec
import com.maxim.currency.domain.ThemePreset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ThemeSharingTest {
    @Test fun providerIsConfiguredForThemeCacheOnly() {
        val context=ApplicationProvider.getApplicationContext<CurrencyApplication>()
        val authority="${context.packageName}.fileprovider"
        val info=context.packageManager.resolveContentProvider(authority,PackageManager.GET_META_DATA)!!
        val parser=info.loadXmlMetaData(context.packageManager,"android.support.FILE_PROVIDER_PATHS")!!
        while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG) {}
        while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG) {}
        assertEquals("cache-path",parser.name)
        assertEquals("shared_themes/",parser.getAttributeValue(null,"path"))
        if(File.separatorChar=='\\') return // FileProvider's Android path comparison cannot run on Windows Robolectric.
        val file=File(context.cacheDir,"shared_themes/test.currency-style.json")
        file.parentFile!!.mkdirs()
        file.writeBytes(StyleFileCodec.encode(ThemePreset(id="user",name="Shared")))
        val uri=FileProvider.getUriForFile(context,authority,file)
        assertEquals("content",uri.scheme)
        val imported=context.contentResolver.openInputStream(uri)!!.use {StyleFileCodec.decode(it)}
        assertEquals("Shared",(imported as StyleFileCodec.DecodeResult.Success).preset.name)
    }
}
