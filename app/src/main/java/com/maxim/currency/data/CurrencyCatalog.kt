package com.maxim.currency.data

import android.content.Context
import com.google.gson.JsonParser
import java.util.Currency
import java.util.Locale

data class CurrencyInfo(val code: String, val en: String, val ru: String, val country: String) {
    fun name(russian: Boolean): String = if (russian) ru else en
    val flag: String get() = if (country.length == 2) country.uppercase(Locale.ROOT).map {
        String(Character.toChars(0x1F1E6 + it.code - 'A'.code))
    }.joinToString("") else "🌐"
}
class CurrencyCatalog(context: Context) {
    val recognition = com.maxim.currency.domain.TextRecognition(context.assets.open("currency_aliases.json").bufferedReader().use { reader ->
        JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (name, codes) -> name to codes.asJsonArray.map {it.asString} }
    })
    val entries: List<CurrencyInfo> = context.assets.open("currencies.json").bufferedReader().use { reader ->
        JsonParser.parseReader(reader).asJsonArray.map {
            val o = it.asJsonObject
            CurrencyInfo(o["code"].asString, o["en"].asString, o["ru"].asString, o["country"].asString)
        }
    }
    private val byCode = entries.associateBy { it.code }
    fun info(code: String): CurrencyInfo = byCode[code] ?: CurrencyInfo(code,
        runCatching { Currency.getInstance(code).getDisplayName(Locale.ENGLISH) }.getOrDefault(code),
        runCatching { Currency.getInstance(code).getDisplayName(Locale.forLanguageTag("ru")) }.getOrDefault(code), "")
    fun supported(rates: RateSnapshot?): List<CurrencyInfo> = (rates?.rates?.keys ?: byCode.keys).map(::info).sortedBy { it.code }
}
