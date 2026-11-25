package com.maxim.currency

import com.maxim.currency.domain.Conversion
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class InputStateTest {
    private val rates=mapOf("USD" to BigDecimal.ONE,"RUB" to BigDecimal("90.12345"),"EUR" to BigDecimal("0.8137"))

    @Test fun backspaceAfterCurrencySwitchDeletesVisibleDigit() {
        val entered=InputState().applyKey("5")!!.applyKey("3")!!
        assertEquals("53",entered.raw)
        val rub=entered.switchCurrency("RUB",rates)!!
        assertEquals("4776.54285",rub.raw)
        assertEquals("4,776.54",Conversion.display(rub.raw.toBigDecimal(),Locale.US))
        val edited=rub.applyKey("⌫")!!
        assertEquals("4776.5",edited.raw)
        assertFalse(edited.replace)
        assertEquals("4776.",edited.applyKey("⌫")!!.raw)
    }

    @Test fun repeatedSwitchesRetainPrecisionUntilEditing() {
        val original=InputState("USD","53.25789",false,true)
        val rub=original.switchCurrency("RUB",rates)!!
        val eur=rub.switchCurrency("EUR",rates)!!
        val usd=eur.switchCurrency("USD",rates)!!
        assertTrue(usd.raw.toBigDecimal().subtract(original.raw.toBigDecimal()).abs()<BigDecimal("1e-28"))
        val backspace=usd.applyKey("⌫")!!
        assertEquals(Conversion.editable(usd.raw.toBigDecimal()).dropLast(1),backspace.raw)
        val next=backspace.switchCurrency("RUB",rates)!!.switchCurrency("EUR",rates)!!
        assertEquals(Conversion.editable(next.raw.toBigDecimal()).dropLast(1),next.applyKey("⌫")!!.raw)
    }
}
