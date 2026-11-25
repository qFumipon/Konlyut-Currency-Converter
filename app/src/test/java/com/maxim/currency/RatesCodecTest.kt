package com.maxim.currency

import com.google.gson.JsonObject
import com.maxim.currency.data.RatesCodec
import org.junit.Assert.*
import org.junit.Test

class RatesCodecTest {
    // Synthetic values exist only in tests; production APK contains no rate fixtures.
    private fun payload(): JsonObject = JsonObject().apply {
        addProperty("result","success");addProperty("base_code","USD")
        addProperty("time_last_update_unix",1_790_000_000L);addProperty("time_next_update_unix",1_790_086_400L)
        add("rates",JsonObject().apply {addProperty("USD",1);repeat(100) {i -> addProperty("A"+('A'+i/26)+('A'+i%26),"1.123456789012345678")}})
    }
    @Test fun retainsDecimalPrecisionAndBothTimestamps() {
        val s=RatesCodec.parse(payload().toString(),1_790_000_100)
        assertEquals("1.123456789012345678",s.rates["AAA"]!!.toPlainString())
        assertEquals(1_790_000_000,s.rateTime);assertEquals(1_790_000_100,s.fetchedAt)
    }
    @Test fun rejectsNegativeRate() {val p=payload();p.getAsJsonObject("rates").addProperty("EUR",-1);assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(p.toString(),1_790_000_100)}}
    @Test fun rejectsWrongBase() {val p=payload();p.addProperty("base_code","EUR");assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(p.toString(),1_790_000_100)}}
    @Test fun rejectsFutureAndInvalidNextDate() {
        assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(payload().toString(),1_700_000_000)}
        val p=payload();p.addProperty("time_next_update_unix",1);assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(p.toString(),1_790_000_100)}
    }
    @Test fun rejectsIncompleteSnapshotAndWrongDollarRate() {
        val p=payload();p.getAsJsonObject("rates").addProperty("USD",2);assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(p.toString(),1_790_000_100)}
        p.add("rates",JsonObject().apply {addProperty("USD",1)});assertThrows(IllegalArgumentException::class.java) {RatesCodec.parse(p.toString(),1_790_000_100)}
    }
}
