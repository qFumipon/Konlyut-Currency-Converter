package com.maxim.currency

import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.data.CurrencyCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CatalogTest {
    @Test fun completeCatalogHasReadableRussianAndEnglishNames() {
        val catalog=CurrencyCatalog(ApplicationProvider.getApplicationContext())
        assertEquals(166,catalog.entries.size)
        catalog.entries.forEach {entry ->
            assertFalse(entry.code,entry.ru.contains('\uFFFD'))
            assertTrue(entry.code,entry.ru.any {it in 'А'..'я'})
            assertFalse(entry.code,entry.en.isBlank())
        }
        assertTrue(catalog.info("JPY").ru.contains("иена",true))
        assertTrue(catalog.info("GBP").en.contains("pound",true))
    }
}
