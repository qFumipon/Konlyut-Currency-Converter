package com.maxim.currency

import com.maxim.currency.domain.Conversion
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ConversionTest {
    private val rates=mapOf("USD" to BigDecimal.ONE,"EUR" to BigDecimal("0.8"),"RUB" to BigDecimal("90"),"JPY" to BigDecimal("150"))
    @Test fun crossRateUsesSourceAsDenominator() {assertEquals(BigDecimal("11250"),Conversion.convert(BigDecimal("100"),"EUR","RUB",rates)?.stripTrailingZeros()?.setScale(0))}
    @Test fun arbitraryPrecisionIsNotConvertedThroughDouble() {assertEquals("1111111100111111110.11",Conversion.canonical(Conversion.convert(BigDecimal("12345678890123456.779"),"USD","RUB",rates)!!))}
    @Test fun sameCurrencyWorksWithoutNetwork() {assertEquals(BigDecimal("125.50"),Conversion.convert(BigDecimal("125.50"),"EUR","EUR",emptyMap()))}
    @Test fun missingAndZeroRatesAreUnavailable() {assertNull(Conversion.convert(BigDecimal.ONE,"EUR","AED",rates));assertNull(Conversion.convert(BigDecimal.ONE,"EUR","USD",mapOf("USD" to BigDecimal.ONE,"EUR" to BigDecimal.ZERO)))}
    @Test fun negativeAndZeroAmountsWork() {assertEquals(0,Conversion.convert(BigDecimal.ZERO,"EUR","RUB",rates)!!.signum());assertEquals("-90",Conversion.canonical(Conversion.convert(BigDecimal("-1"),"USD","RUB",rates)!!))}
    @Test fun sourceChangeDoesNotRoundToDisplayPrecision() {
        val result=Conversion.convert(BigDecimal("0.01"),"RUB","JPY",rates)!!
        val back=Conversion.convert(result,"JPY","RUB",rates)!!
        assertTrue(back.subtract(BigDecimal("0.01")).abs()<BigDecimal("1e-32"))
    }
    @Test fun localizedDisplayAndCanonicalClipboardAreSeparate() {
        val n=BigDecimal("1250.50")
        assertTrue(Conversion.display(n,Locale.forLanguageTag("ru-RU")).contains(",5"))
        assertTrue(Conversion.display(n,Locale.US).contains("1,250.5"))
        assertEquals("1250.5",Conversion.canonical(n))
    }
}
