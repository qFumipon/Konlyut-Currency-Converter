package com.maxim.currency

import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.data.*
import com.maxim.currency.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RevisionTest {
    private fun catalog()=CurrencyCatalog(ApplicationProvider.getApplicationContext())
    private fun read(s:String):List<MoneyMatch> {val c=catalog();return c.recognition.recognize(s,c.entries.map {it.code}.toSet())}
    @Test fun displayRoundsOnlyAtPresentationBoundary() {
        listOf("9.15641" to "9.16","819.17395" to "819.17","4548.02635" to "4,548.03","66.79299" to "66.79","100.00000" to "100","12.50000" to "12.5","1.005" to "1.01").forEach {(input,output)->assertEquals(output,Conversion.display(BigDecimal(input),Locale.US))}
        assertEquals("9,16",Conversion.display(BigDecimal("9.15641"),Locale.forLanguageTag("ru")))
        val value=Conversion.convert(BigDecimal.TEN,"USD","EUR",mapOf("USD" to BigDecimal.ONE,"EUR" to BigDecimal(".915641")))!!
        assertEquals("9.15641",Conversion.canonical(value));assertEquals("9.16",Conversion.display(value,Locale.US))
    }
    @Test fun namesAbbreviationsAndInflections() {
        mapOf("100 российских рублей" to "RUB","500 долларов США" to "USD","20 долл. США" to "USD","75 евро" to "EUR","500 тенге" to "KZT","200 юаней" to "CNY","50 фунтов стерлингов" to "GBP","100 турецких лир" to "TRY","200 белорусских рублей" to "BYN","300 дирхамов ОАЭ" to "AED","100 японских иен" to "JPY","100 US dollars" to "USD","100 British pounds" to "GBP","100 yen" to "JPY","200 yuan" to "CNY","500 tenge" to "KZT").forEach {(input,code)->assertEquals(input,listOf(code),read(input).single().money.currencies)}
    }
    @Test fun ambiguousNamesRequireCountrySelection() {
        for(text in listOf("100 рублей","250 руб.","50 руб","рублей 100","500руб","100,5 рублей","250 rubles")) assertEquals(text,setOf("RUB","BYN"),read(text).single().money.currencies.toSet())
        assertTrue(read("100 dollars").single().money.currencies.size>1)
        assertTrue(read("100 kr").single().money.currencies.size>1)
    }
    @Test fun numbersMultipleAmountsAndFalsePositives() {
        for(text in listOf("1 250,50 €","1,250.50 USD","1250.50 dollars")) assertEquals(text,BigDecimal("1250.50"),read(text).single().money.amounts.single())
        assertEquals(2,read("100 USD and 200 EUR").size)
        assertTrue(read("500 рубинов").isEmpty());assertTrue(read("нет суммы").isEmpty())
    }
    @Test fun migratesEditedDefaultWithoutLosingMoneyOrOrder() {
        val old="""{"selected":["EUR","BYN"],"active":"BYN","amount":"123.456","presets":[{"id":"default","name":"","light":{"MAIN":"123456"},"dark":{}}],"presetId":"default"}"""
        val s=SettingsCodec.decode(old)
        assertEquals(listOf("EUR","BYN"),s.selected);assertEquals("123.456",s.amount)
        assertEquals("legacy-default",s.presetId);assertEquals("123456",s.preset.light["MAIN"])
        assertTrue(s.presets.first {it.id=="default"}.light.isEmpty())
        assertEquals(s,SettingsCodec.decode(SettingsCodec.encode(s)))
    }
    @Test fun savedAmbiguityPreferencesAndUnlimitedThemeListRoundTrip() {
        val themes=(1..40).map {ThemePreset("t$it","Theme $it",mode="dark")}
        val s=UserSettings(presets=listOf(ThemePreset())+themes,presetId="t40",currencyPreferences=mapOf("рублей" to "BYN"))
        assertEquals(s,SettingsCodec.decode(SettingsCodec.encode(s)))
    }
}
