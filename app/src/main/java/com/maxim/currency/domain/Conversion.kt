package com.maxim.currency.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Conversion {
    private val precision = MathContext(34, RoundingMode.HALF_EVEN)
    fun convert(amount: BigDecimal, from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? {
        if (from == to) return amount
        val source = rates[from]?.takeIf { it.signum() > 0 } ?: return null
        val target = rates[to]?.takeIf { it.signum() > 0 } ?: return null
        return amount.multiply(target).divide(source, precision)
    }
    fun canonical(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
    // A source amount becomes editable from the same rounded digits shown to the user.
    // Keep this separate from canonical(): currency switches still use the full value.
    fun editable(value: BigDecimal): String = canonical(value.setScale(2, RoundingMode.HALF_UP))
    fun display(value: BigDecimal, locale: Locale): String = DecimalFormat("#,##0.##", DecimalFormatSymbols(locale)).apply {
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}

data class ParsedMoney(val amounts: List<BigDecimal>, val currencies: List<String>, val token: String = "")
enum class ParseFailure { INVALID, UNSUPPORTED, TOO_LARGE }
sealed interface ParseResult {
    data class Success(val money: ParsedMoney) : ParseResult
    data class Failure(val reason: ParseFailure) : ParseResult
}

/** Strict whole-selection parser. Ambiguous separators and symbols are never guessed. */
object MoneyParser {
    private val symbols = linkedMapOf(
        "US$" to listOf("USD"), "CA$" to listOf("CAD"), "C$" to listOf("CAD"),
        "AU$" to listOf("AUD"), "A$" to listOf("AUD"), "NZ$" to listOf("NZD"),
        "HK$" to listOf("HKD"), "S$" to listOf("SGD"), "CN¥" to listOf("CNY"), "JP¥" to listOf("JPY"),
        "د.إ" to listOf("AED"), "₽" to listOf("RUB"), "€" to listOf("EUR"),
        "₸" to listOf("KZT"), "₺" to listOf("TRY"), "₴" to listOf("UAH"),
        "₹" to listOf("INR"), "₩" to listOf("KRW", "KPW"), "Br" to listOf("BYN", "ETB"),
        "¥" to listOf("JPY", "CNY"), "￥" to listOf("JPY", "CNY"),
        "£" to listOf("GBP", "EGP", "FKP", "GIP", "SHP", "SSP", "SDG", "SYP", "LBP"),
        "$" to listOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD", "TWD", "MXN", "ARS", "CLP", "COP", "BBD", "BSD", "BMD", "BND", "BZD", "FJD", "GYD", "JMD", "KYD", "LRD", "NAD", "SBD", "SRD", "TTD", "XCD")
    )
    fun parse(text: String, supported: Set<String>): ParseResult {
        if (text.length > 160) return ParseResult.Failure(ParseFailure.TOO_LARGE)
        var rest = text.trim().replace('\u00a0', ' ').replace('\u202f', ' ').replace('−', '-')
        var candidates: List<String> = emptyList()
        val token = (symbols.keys.sortedByDescending { it.length }.map(Regex::escape) + "[A-Za-z]{3}").joinToString("|")
        val before = Regex("^($token)\\s*", RegexOption.IGNORE_CASE).find(rest)
        val after = Regex("\\s*($token)$", RegexOption.IGNORE_CASE).find(rest)
        if (before != null && after != null) return ParseResult.Failure(ParseFailure.INVALID)
        val match = before ?: after
        if (match != null) {
            val label = match.groupValues[1]
            candidates = symbols.entries.firstOrNull { it.key.equals(label, true) }?.value
                ?: listOf(label.uppercase(Locale.ROOT))
            candidates = candidates.filter { it in supported }
            if (candidates.isEmpty()) return ParseResult.Failure(ParseFailure.UNSUPPORTED)
            rest = rest.removeRange(match.range).trim()
        }
        val amounts = parseNumbers(rest) ?: return ParseResult.Failure(ParseFailure.INVALID)
        if (amounts.any { it.precision() > 36 || it.scale() > 12 }) return ParseResult.Failure(ParseFailure.TOO_LARGE)
        return ParseResult.Success(ParsedMoney(amounts, candidates))
    }
    fun parseNumbers(input: String): List<BigDecimal>? {
        if (!Regex("[+-]?[0-9][0-9 .,']*").matches(input)) return null
        val sign = if (input.startsWith('-')) "-" else ""
        val n = input.removePrefix("-").removePrefix("+").replace('\'', ' ')
        fun result(value: String) = runCatching { listOf(BigDecimal(sign + value)) }.getOrNull()
        fun integer(part: String, separator: Char? = null): String? {
            if (part.all(Char::isDigit)) return part
            val sep = separator ?: ' '
            if (!Regex("[0-9]{1,3}(" + Regex.escape(sep.toString()) + "[0-9]{3})+").matches(part)) return null
            return part.replace(sep.toString(), "")
        }
        val dots = n.count { it == '.' }; val commas = n.count { it == ',' }
        if (dots > 0 && commas > 0) {
            val decimal = if (n.lastIndexOf('.') > n.lastIndexOf(',')) '.' else ','
            if (n.count { it == decimal } != 1 || ' ' in n) return null
            val parts = n.split(decimal)
            val whole = integer(parts[0], if (decimal == '.') ',' else '.') ?: return null
            if (parts[1].isEmpty() || !parts[1].all(Char::isDigit)) return null
            return result("$whole.${parts[1]}")
        }
        val sep = if (dots > 0) '.' else if (commas > 0) ',' else null
        if (sep == null) return result(integer(n) ?: return null)
        if (n.count { it == sep } > 1) return result(integer(n, sep) ?: return null)
        val parts = n.split(sep)
        val whole = integer(parts[0]) ?: return null
        val fraction = parts[1]
        if (fraction.isEmpty() || !fraction.all(Char::isDigit)) return null
        val decimal = BigDecimal("$sign$whole.$fraction")
        return if (fraction.length == 3 && parts[0].length in 1..3 && !parts[0].startsWith('0')) {
            listOf(decimal, BigDecimal(sign + whole + fraction)).distinct()
        } else listOf(decimal)
    }
}
