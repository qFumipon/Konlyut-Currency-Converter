package com.maxim.currency.domain

import java.util.Locale

data class MoneyMatch(val text: String, val money: ParsedMoney)
class TextRecognition(aliases: Map<String,List<String>>) {
    private val dictionary=aliases.mapKeys {normalize(it.key)}
    private val number=Regex("[+-]?[0-9]+(?:[ .,‘'’][0-9]+)*")
    companion object {fun normalize(s: String)=s.lowercase(Locale.ROOT).replace('ё','е').replace('\u00a0',' ').replace('\u202f',' ').trim().replace(Regex("\\s+")," ")}
    fun recognize(text: String,supported: Set<String>): List<MoneyMatch> {
        if(text.length>4000) return emptyList()
        val input=normalize(text).replace('−','-')
        val whole=MoneyParser.parse(text,supported)
        if(whole is ParseResult.Success) {
            val token=input.replace(number,"").trim()
            return listOf(MoneyMatch(text,whole.money.copy(token=token)))
        }
        val numbers=number.findAll(input).take(30).toList()
        val matches=mutableListOf<MoneyMatch>()
        for((index,n) in numbers.withIndex()) {
            val start=if(index==0) 0 else numbers[index-1].range.last+1
            val end=numbers.getOrNull(index+1)?.range?.first ?: input.length
            val prefix=input.substring(start,n.range.first).trim()
            val suffix=input.substring(n.range.last+1,end).trim()
            fun boundary(c: Char?)=c==null || !c.isLetter()
            val after=dictionary.keys.filter {suffix.startsWith(it) && boundary(suffix.getOrNull(it.length))}.maxByOrNull {it.length}
            val before=dictionary.keys.filter {prefix.endsWith(it) && boundary(prefix.getOrNull(prefix.length-it.length-1))}.maxByOrNull {it.length}
            // Prefer following currency; a preceding name may belong to the previous amount.
            val token=after ?: before
            var money: ParsedMoney?=null
            if(token!=null) {
                val codes=dictionary[token].orEmpty().filter {it in supported}
                val values=MoneyParser.parseNumbers(n.value)
                if(codes.isNotEmpty() && values!=null && values.all {it.precision()<=36 && it.scale()<=12}) money=ParsedMoney(values,codes,token)
            } else {
                for(piece in listOf(n.value+suffix,prefix+n.value)) {
                    val result=MoneyParser.parse(piece,supported)
                    if(result is ParseResult.Success && result.money.currencies.isNotEmpty()) {money=result.money.copy(token=piece.replace(number,"").trim());break}
                }
            }
            if(money!=null) matches+=MoneyMatch(n.value+" "+money.token,money)
        }
        return matches.take(30)
    }
}
