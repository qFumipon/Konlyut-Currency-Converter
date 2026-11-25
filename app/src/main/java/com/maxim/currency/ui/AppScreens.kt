@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.maxim.currency.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxim.currency.*
import com.maxim.currency.data.*
import com.maxim.currency.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun copyAmount(context: Context, code: String, value: BigDecimal) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(code, "${Conversion.canonical(value)} $code"))
}

// Settings/currency management are subordinate routes, not independent bottom tabs.
enum class MainTab(val route: String, val ru: String, val en: String) {
    CONVERTER("home", "Конвертер", "Converter")
}

@Composable
fun ConverterApp(vm: ConverterViewModel, settings: UserSettings) {
    var page by rememberSaveable { mutableStateOf("home") }
    var keyboard by remember { mutableStateOf(false) }
    val p=LocalPalette.current
    val state by vm.rates.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val storageError by vm.storageError.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val updateNotice=remember {SnackbarHostState()}
    var notifiedVersion by rememberSaveable {mutableStateOf("")}
    val availableLabel=tr("Доступна версия ","Version available: ")
    val openLabel=tr("Открыть","Open")
    LaunchedEffect(settings.updateMode,connected) {
        if(settings.updateMode=="auto" && connected) vm.checkUpdates(manual=false)
    }
    LaunchedEffect(updateState,settings.updateMode) {
        val available=updateState as? UpdateUiState.Available
        if(settings.updateMode=="auto" && available!=null && available.release.version!=notifiedVersion) {
            notifiedVersion=available.release.version
            if(updateNotice.showSnackbar(availableLabel+available.release.version,
                    actionLabel=openLabel,withDismissAction=true)==SnackbarResult.ActionPerformed) page="settings"
        }
    }
    BackHandler(page != "home" || keyboard) { if(page in listOf("colors","currencies")) page="settings" else if(page != "home") page="home" else keyboard=false }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding().background(p[ColorRole.SECONDARY])) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(p[ColorRole.STATUS]))
        if (storageError) Note(tr("Не удалось сохранить настройки. Проверьте свободное место.", "Could not save settings. Check available storage."), error=true)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when(page) {
                "home" -> HomeScreen(vm, settings, input, state, connected, keyboard, {keyboard=it}, {page="settings"})
                "currencies" -> Column { TextButton({page="settings"}) {Text(tr("‹ Назад","‹ Back"))}; Box(Modifier.weight(1f)) {CurrencyScreen(vm, settings, state)} }
                "colors" -> AppearanceScreen(vm, settings) {page="settings"}
                else -> Column { TextButton({page="home"},Modifier.testTag("nav_home")) {Text(tr("‹ Конвертер","‹ Converter"))}; Box(Modifier.weight(1f)) {SettingsScreen(vm, settings, state, connected, {page="currencies"}) {page="colors"}} }
            }
            SnackbarHost(updateNotice,Modifier.align(Alignment.BottomCenter))
        }
        if(MainTab.entries.size>1 && MainTab.entries.any {it.route==page}) {
            Row(Modifier.fillMaxWidth()) {MainTab.entries.forEach {tab ->
                Choice(tr(tab.ru,tab.en),page==tab.route,{page=tab.route},Modifier.weight(1f))
            }}
        }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(p[ColorRole.NAVIGATION]))
    }
}

@Composable
fun HomeScreen(vm: ConverterViewModel, settings: UserSettings, input: InputState, state: RateState, connected: Boolean,
               keyboard: Boolean, setKeyboard: (Boolean)->Unit, openSettings: ()->Unit) {
    val context=LocalContext.current
    val locale=appLocale()
    val p=LocalPalette.current
    var unavailable by remember { mutableStateOf(false) }
    val refreshLabel=tr("Обновить курсы","Refresh rates")
    val settingsLabel=tr("Настройки","Settings")
    Column(Modifier.fillMaxSize().background(p[ColorRole.MAIN]).padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            val date=state.snapshot?.let { DateTimeFormatter.ofPattern("dd.MM.yy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(it.rateTime)) }
            Text(if(state.refreshing) tr("Обновление…","Updating…") else (date?.let {tr("Курсы: ","Rates: ")+it} ?: tr("Нет курсов","No rates")) + if(!connected && date!=null) tr(" · Офлайн"," · Offline") else "",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=p[ColorRole.MUTED])
            IconButton(vm::refresh,Modifier.size(48.dp).semantics { contentDescription = refreshLabel },enabled=!state.refreshing) {
                Icon(Icons.Default.Refresh,contentDescription=null,modifier=Modifier.size(24.dp),tint=p[ColorRole.ICON])
            }
            IconButton(openSettings,Modifier.size(48.dp).testTag("nav_settings").semantics { contentDescription = settingsLabel }) {
                Icon(Icons.Default.Settings,contentDescription=null,modifier=Modifier.size(24.dp),tint=p[ColorRole.ICON])
            }
        }
        TextButton({
            val clipboard=context.getSystemService(ClipboardManager::class.java)
            val text=clipboard.primaryClip?.takeIf {it.itemCount>0}?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            context.startActivity(android.content.Intent(context,ProcessTextActivity::class.java).setAction(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,text))
        },Modifier.testTag("paste_amount")) {Text(tr("Вставить сумму","Paste amount"))}
        if (unavailable) Note(tr("Для смены исходной валюты сначала загрузите её курс.", "Download this currency's rate before changing the source."), warning=true)
        if (state.snapshot == null) Note(tr("Курсы ещё не загружены. Подключитесь к интернету и нажмите ↻. Ввод суммы доступен.", "No saved rates yet. Connect to the internet and tap ↻. You can still enter an amount."), warning=true)
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth>600.dp
            val rows: @Composable (Modifier)->Unit = { modifier ->
                LazyColumn(modifier, verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(vertical=12.dp)) {
                    items(settings.selected, key={it}) { code ->
                        val info=vm.graph.catalog.info(code)
                        val amount=Conversion.convert(input.raw.toBigDecimalOrNull() ?: BigDecimal.ZERO, input.code, code, state.snapshot?.rates.orEmpty())
                        CurrencyRow(info, if(code==input.code && !input.replace) input.raw.replace('.', if(LocalRussian.current) ',' else '.') else amount?.let { Conversion.display(it,locale) } ?: "—",
                            code==input.code, { unavailable=!vm.choose(code); if(!unavailable) setKeyboard(true) },
                            { amount?.let {copyAmount(context,code,it)} })
                    }
                }
            }
            if(wide && keyboard) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                rows(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(280.dp).align(Alignment.Bottom)) { Keypad(vm::key) {setKeyboard(false)} }
            } else Column {
                rows(Modifier.weight(1f))
                if(keyboard) Keypad(vm::key) {setKeyboard(false)}
            }
        }
    }
}

@Composable
fun CurrencyRow(info: CurrencyInfo, amount: String, selected: Boolean, onClick: ()->Unit, onCopy: ()->Unit) {
    val p=LocalPalette.current
    Surface(Modifier.fillMaxWidth().testTag("currency_${info.code}").combinedClickable(onClick=onClick, onLongClick=onCopy,
        onLongClickLabel=tr("Копировать сумму", "Copy amount")), color=p[if(selected) ColorRole.SELECTED else ColorRole.ROW],
        shape=RoundedCornerShape(20.dp), border=if(selected) BorderStroke(1.dp,p[ColorRole.CURSOR]) else null) {
        Row(Modifier.padding(horizontal=16.dp, vertical=15.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(info.flag, fontSize=28.sp)
            Column(Modifier.weight(.85f)) {
                Text(info.code, fontSize=17.sp, fontWeight=FontWeight.Bold, color=p[if(selected) ColorRole.SELECTED_TEXT else ColorRole.TEXT])
                Text(info.name(LocalRussian.current), fontSize=11.sp, color=p[if(selected) ColorRole.SELECTED_TEXT else ColorRole.MUTED], maxLines=2, overflow=TextOverflow.Ellipsis)
            }
            Text(amount, modifier=Modifier.weight(1.15f), textAlign=androidx.compose.ui.text.style.TextAlign.End,
                fontSize=24.sp, fontWeight=FontWeight.Medium, color=p[if(selected) ColorRole.SELECTED_TEXT else ColorRole.TEXT], maxLines=3)
        }
    }
}

@Composable
fun Keypad(onKey: (String)->Unit, hide: ()->Unit) {
    val p=LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p[ColorRole.KEYPAD], RoundedCornerShape(24.dp)).padding(8.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
        listOf(listOf("1","2","3","⌫"),listOf("4","5","6","C"),listOf("7","8","9","00"),listOf(".","0","⌄")).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { row.forEach { key ->
                val action=key in listOf("C","⌫","⌄")
                val description=when(key) { "C"->tr("Очистить","Clear"); "⌫"->tr("Удалить цифру","Delete digit"); "⌄"->tr("Скрыть клавиатуру","Hide keypad"); else->key }
                val interactions=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                Surface(Modifier.weight(if(key=="⌄") 2f else 1f).heightIn(min=48.dp).testTag("key_$key").combinedClickable(interactionSource=interactions, indication=null,
                    onClickLabel=description, onClick={when(key) { "⌄"->hide(); "00"->{onKey("0");onKey("0")}; else->onKey(key) }},
                    onLongClick=if(key=="⌫") ({onKey("C")}) else null),
                    shape=RoundedCornerShape(14.dp), color=p[if(pressed) ColorRole.PRESSED else if(action) ColorRole.KEY_ACTION else ColorRole.KEY],
                    contentColor=p[if(action) ColorRole.KEY_ACTION_TEXT else ColorRole.KEY_TEXT]) {
                    Box(Modifier.padding(vertical=10.dp), contentAlignment=Alignment.Center) { Text(if(key==".") tr(",", ".") else key, fontSize=22.sp) }
                }
            } }
        }
    }
}

@Composable
fun RatesSummary(state: RateState, connected: Boolean, compact: Boolean=false) {
    val p=LocalPalette.current
    val uri=LocalUriHandler.current
    val locale=appLocale()
    fun date(value: Long)=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(value))
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth(), color=p[ColorRole.BUTTON], trackColor=p[ColorRole.DIVIDER])
        state.snapshot?.let { snapshot ->
            Text(tr("Курсы: ","Rates: ")+date(snapshot.rateTime)+(if(!connected) tr(" · Офлайн", " · Offline") else ""), style=MaterialTheme.typography.labelMedium,color=p[ColorRole.MUTED])
            if(!compact) Text(tr("Успешно загружены: ","Last successful download: ")+date(snapshot.fetchedAt), color=p[ColorRole.MUTED],style=MaterialTheme.typography.bodySmall)
            if(Instant.now().epochSecond>=snapshot.nextUpdate) Text(tr("Сохранённые курсы требуют обновления", "Saved rates are due for an update"),color=p[ColorRole.WARNING_TEXT],modifier=Modifier.background(p[ColorRole.WARNING]).padding(4.dp),style=MaterialTheme.typography.labelSmall)
        }
        Text("Rates by ExchangeRate-API ↗",modifier=Modifier.clickable {uri.openUri("https://www.exchangerate-api.com")}.padding(vertical=4.dp),color=p[ColorRole.ICON],style=MaterialTheme.typography.labelSmall)
        state.error?.let { error ->
            Note(when(error) {
                RateError.NETWORK->tr("Не удалось загрузить курсы. Используются сохранённые данные, если они есть.", "Could not download rates. Saved data is used when available.")
                RateError.INVALID->tr("Источник вернул некорректные или более старые данные. Кэш сохранён.", "The source returned invalid or older data. The cache was kept.")
                RateError.CACHE->tr("Не удалось прочитать кэш. Загрузите курсы повторно.", "Could not read the cache. Download rates again.")
                RateError.LIMITED->tr("Источник ограничил запросы. Повторите позже.", "The source has rate-limited requests. Try again later.")
            },warning=true)
        }
    }
}

@Composable
fun CurrencyScreen(vm: ConverterViewModel, settings: UserSettings, state: RateState) {
    val p=LocalPalette.current
    var search by rememberSaveable { mutableStateOf("") }
    var ordered by remember { mutableStateOf(settings.selected) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val latestOrder by rememberUpdatedState(ordered)
    val listState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(settings.selected) { if(dragging==null) ordered=settings.selected }
    val available=vm.graph.catalog.supported(state.snapshot).filter {
        it.code !in ordered && (it.code.contains(search,true)||it.en.contains(search,true)||it.ru.contains(search,true))
    }
    val visibleSelected=ordered.filter {code -> val info=vm.graph.catalog.info(code); code.contains(search,true)||info.en.contains(search,true)||info.ru.contains(search,true)}
    val up=tr("Переместить выше","Move up"); val down=tr("Переместить ниже","Move down")
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Heading(tr("Мои валюты","My currencies"),tr("Удерживайте ≡ и перетащите строку","Hold ≡ and drag to reorder"))
        Field(search,{search=it},tr("Код или название валюты","Currency code or name"),Modifier.fillMaxWidth().testTag("currency_search"))
        LazyColumn(modifier=Modifier.testTag("currency_list"),state=listState, contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text(tr("ВЫБРАНО","SELECTED"),color=p[ColorRole.MUTED],style=MaterialTheme.typography.labelSmall) }
            items(visibleSelected,key={it}) { code ->
                val info=vm.graph.catalog.info(code)
                Surface(color=p[if(dragging==code) ColorRole.SELECTED else ColorRole.ROW],shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().semantics {
                    customActions=listOf(CustomAccessibilityAction(up) { val i=ordered.indexOf(code); if(i>0) vm.reorder(code,ordered[i-1]); true },
                        CustomAccessibilityAction(down) { val i=ordered.indexOf(code); if(i<ordered.lastIndex) vm.reorder(code,ordered[i+1]); true })
                }) {
                    Row(Modifier.padding(start=12.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(info.flag, fontSize=24.sp)
                        Column(Modifier.weight(1f).padding(10.dp)) { Text(code,fontWeight=FontWeight.Bold); Text(info.name(LocalRussian.current),fontSize=12.sp,color=p[ColorRole.MUTED]) }
                        Text("≡",fontSize=28.sp,color=p[ColorRole.ICON],modifier=Modifier.padding(horizontal=12.dp).sizeIn(minWidth=48.dp,minHeight=48.dp).pointerInput(code) {
                            var y=0f
                            detectDragGesturesAfterLongPress(
                                onDragStart={dragging=code; y=(listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==code}?.let {it.offset+it.size/2} ?: 0).toFloat()},
                                onDragEnd={val final=latestOrder; vm.edit {it.copy(selected=final)}; dragging=null},
                                onDragCancel={ordered=settings.selected; dragging=null},
                                onDrag={change,delta ->
                                    change.consume(); y+=delta.y
                                    val target=listState.layoutInfo.visibleItemsInfo.firstOrNull {y>=it.offset && y<it.offset+it.size && it.key in latestOrder}?.key as? String
                                    if(target!=null && target!=code) {
                                        val list=latestOrder.toMutableList(); val a=list.indexOf(code); val b=list.indexOf(target)
                                        if(a>=0 && b>=0) {list.removeAt(a);list.add(b,code);ordered=list}
                                    }
                                    val edge=64
                                    val scroll=if(y>listState.layoutInfo.viewportEndOffset-edge) 22f else if(y<edge) -22f else 0f
                                    if(scroll!=0f) scope.launch {listState.scrollBy(scroll)}
                                })
                        })
                        TextButton({vm.remove(code)},enabled=ordered.size>1) { Text("×",fontSize=24.sp,color=p[if(ordered.size>1) ColorRole.ICON else ColorRole.DISABLED_TEXT]) }
                    }
                }
            }
            item { Text(tr("ДОБАВИТЬ ВАЛЮТУ","ADD CURRENCY"),color=p[ColorRole.MUTED],modifier=Modifier.padding(top=18.dp),style=MaterialTheme.typography.labelSmall) }
            if(available.isEmpty()) item {Text(tr("Валюты не найдены","No currencies found"),color=p[ColorRole.MUTED])}
            items(available,key={"add_${it.code}"}) { info ->
                Surface(onClick={vm.edit {it.copy(selected=(it.selected+info.code).distinct())}},color=p[ColorRole.ROW],shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(info.flag,fontSize=24.sp)
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text(info.code,fontWeight=FontWeight.Bold);Text(info.name(LocalRussian.current),fontSize=12.sp,color=p[ColorRole.MUTED])}
                        Text("+",fontSize=26.sp,color=p[ColorRole.ICON])
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ConverterViewModel, settings: UserSettings, state: RateState, connected: Boolean, currencies: ()->Unit, appearance: ()->Unit) {
    val p=LocalPalette.current
    val context=LocalContext.current
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    var installPermissionHint by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var backupCandidate by remember {mutableStateOf<UserSettings?>(null)}
    var backupError by remember {mutableStateOf(false)}
    var backupSaved by remember {mutableStateOf(false)}
    val exportBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SettingsBackup.MIME)) {uri ->
        if(uri!=null) scope.launch {
            backupSaved=withContext(Dispatchers.IO) {runCatching {
                context.contentResolver.openOutputStream(uri)?.use {it.write(SettingsBackup.encode(settings).toByteArray(Charsets.UTF_8))}
                    ?: error("Could not open backup")
            }.isSuccess}
            backupError=!backupSaved
        }
    }
    val importBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            backupCandidate=withContext(Dispatchers.IO) {runCatching {
                context.contentResolver.openInputStream(uri)?.use {SettingsBackup.decode(it)} ?: error("Could not open backup")
            }.getOrNull()}
            backupError=backupCandidate==null
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Heading(tr("Настройки","Settings"))
        Action(tr("Мои валюты","My currencies"),currencies,Modifier.fillMaxWidth().testTag("nav_currencies"))
        Text(tr("Общие","General"),style=MaterialTheme.typography.titleLarge)
        Text(tr("Язык приложения","App language"),color=p[ColorRole.MUTED])
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to tr("Как в системе","System"),"ru" to "Русский","en" to "English").forEach { (key,label) -> Choice(label,settings.language==key,{vm.edit {it.copy(language=key)}}) }
        }
        var help by remember {mutableStateOf(false)}
        val helpLabel=tr("Справка по конвертации текста","Text conversion help")
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Конвертация текста","Text conversion"),color=p[ColorRole.MUTED])
            IconButton({help=true},Modifier.padding(start=4.dp).size(40.dp).semantics { contentDescription = helpLabel }) {
                Text("ⓘ",fontSize=20.sp,color=p[ColorRole.ICON])
            }
        }
        if(help) TextConversionHelp { help=false }

        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice(tr("Всплывающее окно","Popup"),settings.processMode=="popup",{vm.edit {it.copy(processMode="popup")}})
            Choice(tr("Главный экран","Main screen"),settings.processMode=="main",{vm.edit {it.copy(processMode="main")}})
        }
        HorizontalDivider(Modifier.padding(vertical=12.dp),color=p[ColorRole.DIVIDER])
        Text(tr("Внешний вид","Appearance"),style=MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to tr("Как в системе","System"),"light" to tr("Светлая","Light"),"dark" to tr("Тёмная","Dark")).forEach { (key,label) -> Choice(label,settings.presetId=="default" && settings.theme==key,{vm.edit {it.copy(theme=key,presetId="default")}}) }
        }
        Action(tr("Темы оформления","Appearance themes"),appearance,Modifier.fillMaxWidth())
        HorizontalDivider(Modifier.padding(vertical=12.dp),color=p[ColorRole.DIVIDER])
        Text(tr("Курсы валют","Exchange rates"),style=MaterialTheme.typography.titleLarge)
        Text(if(connected) tr("Интернет доступен","Connected") else tr("Нет подключения к интернету","Offline"),color=p[ColorRole.MUTED])
        RatesSummary(state,connected)
        if(state.snapshot==null) Note(tr("Нет сохранённых курсов","No saved rates"),warning=true)
        Action(if(state.refreshing) tr("Загрузка…","Updating…") else tr("Обновить курсы","Refresh rates"),vm::refresh,enabled=!state.refreshing)
        Text(tr("Источник обновляет данные примерно раз в сутки. Автопроверка работает только при открытом приложении. Между запросами вручную — не менее минуты.", "The source updates about once a day. Automatic checks run only while the app is open. Manual requests are at least one minute apart."),style=MaterialTheme.typography.bodySmall,color=p[ColorRole.MUTED])
        HorizontalDivider(Modifier.padding(vertical=12.dp),color=p[ColorRole.DIVIDER])
        Text("Konlyut",style=MaterialTheme.typography.titleLarge)
        Text(tr("О приложении","About"),style=MaterialTheme.typography.titleMedium)
        Text(tr("Версия ","Version ")+BuildConfig.VERSION_NAME,style=MaterialTheme.typography.bodySmall,color=p[ColorRole.MUTED])
        Text(tr("Обновления приложения","App updates"),style=MaterialTheme.typography.titleMedium)
        Text(tr("Режим проверки","Check mode"),color=p[ColorRole.MUTED])
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice(tr("Автоматически","Automatic"),settings.updateMode=="auto",{vm.edit {it.copy(updateMode="auto")}})
            Choice(tr("Вручную","Manual"),settings.updateMode=="manual",{vm.dismissUpdate();vm.edit {it.copy(updateMode="manual")}})
        }
        Action(tr("Проверить обновления","Check for updates"),{vm.checkUpdates(manual=true)},
            Modifier.fillMaxWidth().testTag("check_updates"),enabled=updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading)
        when(val update=updateState) {
            UpdateUiState.Idle -> Unit
            UpdateUiState.Checking -> Text(tr("Проверка обновлений…","Checking for updates…"),color=p[ColorRole.MUTED])
            UpdateUiState.Latest -> Text(tr("Установлена последняя версия приложения","You have the latest version"),color=p[ColorRole.INFO_TEXT])
            is UpdateUiState.Available -> {
                Text(tr("Доступна версия ","Version available: ")+update.release.version,color=p[ColorRole.INFO_TEXT])
                Text(tr("Текущая версия: ","Current version: ")+BuildConfig.VERSION_NAME,color=p[ColorRole.MUTED])
                Text(tr("Размер APK: ","APK size: ")+"${update.release.size/1024/1024} MB",color=p[ColorRole.MUTED])
                if(update.release.notes.isNotBlank()) Text(update.release.notes.take(1200),style=MaterialTheme.typography.bodySmall,color=p[ColorRole.TEXT])
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Action(tr("Обновить","Update"),{vm.downloadUpdate(update.release,context)},Modifier.weight(1f))
                    Action(tr("Позже","Later"),vm::dismissUpdate,Modifier.weight(1f),secondary=true)
                }
            }
            is UpdateUiState.Downloading -> {
                Text(tr("Скачивание ","Downloading ")+"${update.percent}%",color=p[ColorRole.MUTED])
                LinearProgressIndicator(progress={update.percent/100f},modifier=Modifier.fillMaxWidth())
                Action(tr("Отменить","Cancel"),vm::cancelDownload,Modifier.fillMaxWidth(),secondary=true)
            }
            is UpdateUiState.Ready -> {
                Text(tr("APK проверен и готов к установке","APK verified and ready to install"),color=p[ColorRole.INFO_TEXT])
                Action(tr("Установить обновление","Install update"),{
                    try {
                        if(UpdateInstaller.canInstall(context)) UpdateInstaller.install(context,update.file)
                        else {installPermissionHint=true;context.startActivity(UpdateInstaller.permissionIntent(context))}
                    } catch(_: Exception) {installPermissionHint=true}
                },Modifier.fillMaxWidth())
                if(installPermissionHint) Note(tr("Разрешите установку из этого источника, вернитесь и нажмите «Установить обновление» ещё раз.",
                    "Allow installs from this source, return, then tap Install update again."),warning=true)
            }
            is UpdateUiState.Failure -> Text(when(update.reason) {
                UpdateUiState.Reason.OFFLINE -> tr("Нет подключения к интернету","No internet connection")
                UpdateUiState.Reason.NOT_CONFIGURED -> tr("Адрес репозитория обновлений пока не настроен","The update repository is not configured yet")
                UpdateUiState.Reason.NO_APK -> tr("В релизе нет подходящего APK","This release has no compatible APK")
                UpdateUiState.Reason.WRONG_PACKAGE -> tr("APK предназначен для другого приложения","APK belongs to another app")
                UpdateUiState.Reason.WRONG_VERSION -> tr("Версия APK несовместима","APK version is incompatible")
                UpdateUiState.Reason.WRONG_SIGNATURE -> tr("Подпись APK отличается от установленного приложения. Release нельзя установить поверх Debug.",
                    "APK signature differs from the installed app. Release cannot update Debug.")
                UpdateUiState.Reason.BAD_APK -> tr("APK повреждён","APK is damaged")
                UpdateUiState.Reason.NETWORK -> tr("Не удалось проверить или скачать обновление","Could not check or download the update")
            },color=p[ColorRole.ERROR_TEXT])
        }
        Text(tr("Перенос настроек","Transfer settings"),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Action(tr("Сохранить настройки","Export settings"),{exportBackup.launch(SettingsBackup.FILE_NAME)},Modifier.weight(1f),secondary=true)
            Action(tr("Загрузить настройки","Import settings"),{importBackup.launch(arrayOf(SettingsBackup.MIME))},Modifier.weight(1f),secondary=true)
        }
        if(backupSaved) Text(tr("Настройки сохранены в файл","Settings saved to file"),color=p[ColorRole.INFO_TEXT])
        if(backupError) Text(tr("Не удалось прочитать или сохранить файл настроек","Could not read or save the settings file"),color=p[ColorRole.ERROR_TEXT])
    }
    backupCandidate?.let {candidate -> AlertDialog(onDismissRequest={backupCandidate=null},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Загрузить настройки?","Import settings?"))},
        text={Text(tr("Текущие настройки будут заменены. Валют: ","Current settings will be replaced. Currencies: ")+candidate.selected.size+
            tr(", пользовательских тем: ",", custom themes: ")+candidate.presets.count {it.id!="default"})},
        confirmButton={TextButton({vm.edit {candidate};backupCandidate=null}) {Text(tr("Загрузить","Import"))}},
        dismissButton={TextButton({backupCandidate=null}) {Text(tr("Отмена","Cancel"))}}) }
}

@Composable
private fun TextConversionHelp(close: () -> Unit) {
    val palette=LocalPalette.current
    val spacing=Modifier.padding(bottom=10.dp)
    AlertDialog(onDismissRequest=close,containerColor=palette[ColorRole.DIALOG],
        titleContentColor=palette[ColorRole.DIALOG_TEXT],textContentColor=palette[ColorRole.DIALOG_TEXT],
        title={Text(tr("Как конвертировать текст","How to convert selected text"))},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Вы можете конвертировать суммы прямо из других приложений.",
                "You can convert currency amounts directly from other apps."),spacing)
            Text(tr("1. Выделите сумму вместе с названием или обозначением валюты, например 500 рублей, 20 USD или 15 €.",
                "1. Select an amount together with its currency name or symbol, such as 500 rubles, 20 USD, or 15 €."),spacing)
            Text(tr("2. В появившемся меню выберите «Конвертировать».",
                "2. Tap “Convert” in the selection menu."),spacing)
            Text(tr("3. Если такого пункта нет, воспользуйтесь функцией «Поделиться» и выберите наш конвертер.",
                "3. If this option is unavailable, use “Share” and select the currency converter."),spacing)
            Text(tr("Также можно скопировать сумму и нажать «Вставить сумму» на главном экране приложения.",
                "You can also copy an amount and tap “Paste amount” on the app's main screen."),spacing)
            Text(tr("Если в тексте несколько сумм или невозможно однозначно определить валюту, приложение предложит выбрать нужный вариант.",
                "If the text contains multiple amounts or the currency is ambiguous, the app will ask you to select the correct option."),spacing)
            Text(tr("В настройках можно выбрать, где показывать результат: во всплывающем окне или на главном экране.",
                "In Settings, you can choose whether results appear in a pop-up window or on the main screen."))
        }},
        confirmButton={TextButton(close) {Text(tr("Закрыть","Close"))}})
}
