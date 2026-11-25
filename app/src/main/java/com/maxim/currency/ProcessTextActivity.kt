package com.maxim.currency

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxim.currency.domain.*
import com.maxim.currency.ui.*
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The host's selected text is read only and is never returned as a replacement.
        setResult(RESULT_CANCELED)
        val selected=if(intent.action==Intent.ACTION_PROCESS_TEXT) intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty() else if(intent.action==Intent.ACTION_SEND && intent.type=="text/plain") intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty() else ""
        val graph=(application as CurrencyApplication).graph
        setContent {
            val settings by graph.settings.collectAsStateWithLifecycle()
            val rates by graph.rates.state.collectAsStateWithLifecycle()
            val connected by graph.connected.collectAsStateWithLifecycle()
            ForegroundRefresh(graph)
            settings?.let { prefs -> CurrencyTheme(prefs) {
                val catalog=graph.catalog.supported(rates.snapshot)
                var draft by rememberSaveable {mutableStateOf(selected.take(4000))}
                var submitted by rememberSaveable {mutableStateOf(selected.take(4000))}
                val matches=remember(submitted,catalog.map {it.code}) {graph.catalog.recognition.recognize(submitted,catalog.map {it.code}.toSet())}
                var matchIndex by rememberSaveable(submitted) {mutableStateOf<Int?>(null)}
                val chosen=matchIndex?.let {matches.getOrNull(it)} ?: matches.singleOrNull()
                val parsed=chosen?.let {ParseResult.Success(it.money)} ?: ParseResult.Failure(ParseFailure.INVALID)
                val p=LocalPalette.current
                val context=LocalContext.current
                val scope=rememberCoroutineScope()
                val locale=appLocale()
                var currency by rememberSaveable(submitted,matchIndex) {mutableStateOf<String?>(null)}
                var amountIndex by rememberSaveable(submitted,matchIndex) {mutableStateOf<Int?>(null)}
                val money=(parsed as? ParseResult.Success)?.money
                var rememberChoice by rememberSaveable(submitted,matchIndex) {mutableStateOf(false)}
                val remembered=money?.let {prefs.currencyPreferences[it.token]?.takeIf {code -> code in it.currencies}}
                val code=currency ?: money?.currencies?.singleOrNull() ?: remembered
                val amount=money?.amounts?.let {amounts -> amountIndex?.let {amounts.getOrNull(it)} ?: amounts.singleOrNull()}
                fun openMain() {
                    if(code!=null && amount!=null) {
                        startActivity(Intent(this@ProcessTextActivity,MainActivity::class.java)
                            .putExtra("currency",code).putExtra("amount",Conversion.canonical(amount))
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    }
                }
                LaunchedEffect(code,amount,prefs.processMode) {if(prefs.processMode=="main") openMain()}
                Surface(color=p[ColorRole.DIALOG],contentColor=p[ColorRole.DIALOG_TEXT],shape=RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().heightIn(max=640.dp).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(tr("Конвертировать","Convert"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                            TextButton({finish()}) {Text(tr("Закрыть","Close"))}
                        }
                        Field(draft,{draft=it.take(4000)},tr("Текст с суммой","Amount text"),Modifier.fillMaxWidth())
                        Action(tr("Распознать","Recognize"),{submitted=draft;currency=null;amountIndex=null;matchIndex=null})
                        if(matches.size>1 && chosen==null) {
                            Text(tr("Выберите сумму","Choose an amount"))
                            LazyColumn(Modifier.heightIn(max=180.dp)) {items(matches.indices.toList()) {index -> TextButton({matchIndex=index}) {Text(matches[index].text)} }}
                        }
                        if(parsed is ParseResult.Failure && matches.isEmpty()) {
                            Note(when(parsed.reason) {
                                ParseFailure.INVALID->tr("Сумма не распознана. Исправьте текст и повторите.","Could not parse the selection. Edit the text and try again.")
                                ParseFailure.UNSUPPORTED->tr("Валюта не поддерживается источником курсов.","This currency is not supported by the rate source.")
                                ParseFailure.TOO_LARGE->tr("Слишком длинная или точная сумма. Допускается до 36 значащих цифр и 12 знаков после запятой.","The amount is too long or precise. Up to 36 significant digits and 12 decimal places are supported.")
                            },error=true)
                        } else if(money!=null) {
                            if(amount==null) {
                                Note(tr("Разделитель неоднозначен. Выберите сумму:","The separator is ambiguous. Choose the amount:"),warning=true)
                                money.amounts.forEachIndexed {index,value -> Action(Conversion.canonical(value),{amountIndex=index},Modifier.fillMaxWidth(),secondary=true)}
                            }
                            if(code==null) {
                                Note(if(money.currencies.isEmpty()) tr("Укажите исходную валюту","Choose the source currency") else tr("Символ обозначает несколько валют. Выберите исходную:","This symbol represents several currencies. Choose the source:"),warning=true)
                                var query by rememberSaveable {mutableStateOf("")}
                                Field(query,{query=it},tr("Найти валюту","Find currency"),Modifier.fillMaxWidth())
                                if(money.currencies.size>1) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(rememberChoice,{rememberChoice=it});Text(tr("Запомнить для этого обозначения","Remember this designation"),style=MaterialTheme.typography.bodySmall)}
                                val choices=if(money.currencies.isEmpty()) catalog else money.currencies.map(graph.catalog::info)
                                LazyColumn(Modifier.heightIn(max=180.dp)) {
                                    items(choices.filter {it.code.contains(query,true)||it.ru.contains(query,true)||it.en.contains(query,true)}.sortedBy {if(it.code in prefs.selected) 0 else 1},key={it.code}) { info ->
                                        TextButton({currency=info.code;if(rememberChoice) graph.scope.launch {graph.store.update {it.copy(currencyPreferences=it.currencyPreferences+(money.token to info.code))}}},Modifier.fillMaxWidth()) {Text("${info.flag} ${info.code} · ${info.name(LocalRussian.current)}")}
                                    }
                                }
                            }
                            if(code!=null && amount!=null) {
                                Text("${Conversion.display(amount,locale)} $code",style=MaterialTheme.typography.titleLarge)
                                Text(tr("Нажмите результат, чтобы скопировать","Tap a result to copy"),style=MaterialTheme.typography.labelSmall,color=p[ColorRole.MUTED])
                                if(rates.snapshot==null) Note(tr("Курсы ещё не загружены. Для первого обновления нужен интернет.","No rates have been saved yet. Connect to the internet for the first update."),warning=true)
                                LazyColumn(Modifier.heightIn(max=260.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    items(prefs.selected,key={it}) { target ->
                                        val value=Conversion.convert(amount,code,target,rates.snapshot?.rates.orEmpty())
                                        CurrencyRow(graph.catalog.info(target),value?.let {Conversion.display(it,locale)} ?: "—",target==code,
                                            {value?.let {copyAmount(context,target,it)}},{value?.let {copyAmount(context,target,it)}})
                                    }
                                }
                                Action(tr("Открыть конвертер","Open converter"),{openMain()},Modifier.fillMaxWidth())
                            }
                        }
                        RatesSummary(rates,connected,compact=true)
                        if(rates.snapshot==null) Action(tr("Загрузить курсы","Download rates"),{scope.launch {graph.rates.refresh(true)}},enabled=!rates.refreshing)
                    }
                }
            }}
        }
        window.setLayout((resources.displayMetrics.widthPixels*.94).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
