package com.maxim.currency

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.maxim.currency.data.*
import com.maxim.currency.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SettingsStoreTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun persistsAcrossClosingAndReopeningDataStore() = runBlocking {
        val file=File(folder.root,"test.preferences_pb")
        val job=SupervisorJob()
        val store=SettingsStore(PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file}))
        val preset=ThemePreset("custom","Моя тема",mapOf("TEXT" to "112233"),mapOf("KEY" to "445566"))
        val expected=UserSettings(listOf("EUR","KZT","USD"),"KZT","1250.50","ru","dark","main",listOf(ThemePreset(),preset),"custom")
        store.update {expected}
        assertEquals(expected,store.settings.first())
        job.cancelAndJoin()
        val job2=SupervisorJob()
        val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=CoroutineScope(job2+Dispatchers.IO),produceFile={file}))
        assertEquals(expected,reopened.settings.first())
        job2.cancelAndJoin()
    }
    @Test fun concurrentEditsDoNotLoseOtherFields() = runBlocking {
        val job=SupervisorJob()
        val store=SettingsStore(PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={File(folder.root,"concurrent.preferences_pb")}))
        coroutineScope {
            launch {store.update {it.copy(language="en")}}
            launch {store.update {it.copy(theme="dark")}}
            launch {store.update {it.copy(amount="42.25")}}
        }
        val actual=store.settings.first()
        assertEquals("en",actual.language);assertEquals("dark",actual.theme);assertEquals("42.25",actual.amount)
        job.cancelAndJoin()
    }
    @Test fun malformedJsonFallsBackSafely() {assertEquals(UserSettings(),SettingsCodec.decode("not json"));assertEquals(UserSettings(),SettingsCodec.decode(null))}
    @Test fun duplicateAndInvalidCurrenciesAreRemoved() {
        val s=SettingsCodec.decode("""{"selected":["EUR","EUR","oops","USD"],"active":"XXX","amount":"NaN"}""")
        assertEquals(listOf("EUR","USD"),s.selected);assertEquals("EUR",s.active);assertEquals("1",s.amount)
    }
    @Test fun invalidColorsAndEnumValuesDoNotCrash() {
        val s=SettingsCodec.decode("""{"language":"zz","theme":"bad","presets":[{"id":"p","name":"Test","light":{"TEXT":"GGGGGG","ROW":"ABCDEF"},"dark":{}}],"presetId":"p"}""")
        assertEquals("system",s.language);assertEquals("system",s.theme);assertEquals("p",s.presetId)
        assertEquals("ABCDEF",s.preset.hex(ColorRole.ROW,false));assertEquals(ColorRole.TEXT.light,s.preset.hex(ColorRole.TEXT,false))
    }
}
