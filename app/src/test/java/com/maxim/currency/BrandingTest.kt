package com.maxim.currency

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class BrandingTest {
    @Test fun namesAndVersionMatchReleasePlanWithoutChangingPackage() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        fun localized(locale: Locale): String {
            val config=Configuration(context.resources.configuration).apply {setLocale(locale)}
            return context.createConfigurationContext(config).getString(R.string.app_name)
        }
        assertEquals("Konlyut: Currency Converter",localized(Locale.ENGLISH))
        assertEquals("Konlyut: Конвертер валют",localized(Locale.forLanguageTag("ru")))
        assertEquals("Konlyut",context.getString(R.string.short_name))
        assertEquals("com.maxim.currency",BuildConfig.APPLICATION_ID)
        assertEquals("1.0.0",BuildConfig.VERSION_NAME)
        assertTrue(BuildConfig.VERSION_CODE>7)
    }
}
