package com.maxim.currency

import com.maxim.currency.domain.*
import org.junit.Assert.*
import org.junit.Test

class MoneyParserTest {
    private val currencies=setOf("USD","CAD","AUD","EUR","RUB","GBP","EGP","KZT","BYN","TRY","CNY","AED","JPY","ETB")
    private fun parse(text: String)=(MoneyParser.parse(text,currencies) as ParseResult.Success).money
    @Test fun requiredExamples() {
        for(text in listOf("100$","$100","100 USD")) assertEquals("100",parse(text).amounts.single().toPlainString())
        assertEquals(listOf("RUB"),parse("100 ₽").currencies)
        assertEquals("1250.50",parse("1 250,50 €").amounts.single().toPlainString())
        assertEquals("125.50",parse("125,50 €").amounts.single().toPlainString())
    }
    @Test fun mixedGroupingFormats() {
        for(text in listOf("1,250.50 USD","1.250,50 EUR","1\u00a0250,50 €","1\u202f250,50 €","1'250.50 USD")) assertEquals("1250.50",parse(text).amounts.single().toPlainString())
    }
    @Test fun ambiguousSymbolRequiresChoice() {assertEquals(listOf("USD","CAD","AUD"),parse("$100").currencies);assertEquals(listOf("JPY","CNY"),parse("100 ¥").currencies);assertEquals(listOf("GBP","EGP"),parse("100 £").currencies)}
    @Test fun explicitPrefixIsUnambiguous() {assertEquals(listOf("USD"),parse("US$100").currencies);assertEquals(listOf("CAD"),parse("CA$100").currencies)}
    @Test fun ambiguousThousandsSeparatorRequiresChoice() {assertEquals(listOf("1.250","1250"),parse("1,250 USD").amounts.map {it.toPlainString()})}
    @Test fun zeroPrefixIsDecimal() {assertEquals("0.125",parse("0,125 EUR").amounts.single().toPlainString())}
    @Test fun missingCurrencyRequiresChoice() {assertTrue(parse("100").currencies.isEmpty())}
    @Test fun unicodeMinusAndLowercaseCode() {assertEquals("-125.50",parse("−125,50 eur").amounts.single().toPlainString())}
    @Test fun repeatedGroupingIsValidated() {assertEquals("1234567",parse("1,234,567 USD").amounts.single().toPlainString());assertTrue(MoneyParser.parse("12,34,567 USD",currencies) is ParseResult.Failure)}
    @Test fun malformedSelectionsNeverConvert() {
        listOf("hello 100 USD","100 USD 200 EUR","1/2 USD","12,34.56 USD","USD 100 EUR","100$€","1e3 USD","NaN USD","100%","100-200 USD","12 34 EUR","1. EUR","","+ EUR").forEach {
            assertTrue(it,MoneyParser.parse(it,currencies) is ParseResult.Failure)
        }
    }
    @Test fun unsupportedCodeIsRejected() {assertEquals(ParseResult.Failure(ParseFailure.UNSUPPORTED),MoneyParser.parse("100 XYZ",currencies))}
    @Test fun excessivePrecisionAndLengthAreRejected() {assertTrue(MoneyParser.parse("1."+"2".repeat(13)+" EUR",currencies) is ParseResult.Failure);assertTrue(MoneyParser.parse("9".repeat(200),currencies) is ParseResult.Failure)}
}
