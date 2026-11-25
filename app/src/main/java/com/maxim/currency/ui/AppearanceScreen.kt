@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.maxim.currency.ui

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.maxim.currency.ConverterViewModel
import com.maxim.currency.data.StyleFileCodec
import com.maxim.currency.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val contrastPairs=listOf(
    ColorRole.TEXT to ColorRole.MAIN, ColorRole.TEXT to ColorRole.SECONDARY,
    ColorRole.TEXT to ColorRole.ROW, ColorRole.MUTED to ColorRole.ROW,
    ColorRole.SELECTED_TEXT to ColorRole.SELECTED, ColorRole.ON_BUTTON to ColorRole.BUTTON,
    ColorRole.INPUT_TEXT to ColorRole.INPUT, ColorRole.KEY_TEXT to ColorRole.KEY,
    ColorRole.KEY_ACTION_TEXT to ColorRole.KEY_ACTION, ColorRole.DIALOG_TEXT to ColorRole.DIALOG,
    ColorRole.ERROR_TEXT to ColorRole.ERROR, ColorRole.WARNING_TEXT to ColorRole.WARNING,
    ColorRole.INFO_TEXT to ColorRole.INFO, ColorRole.DISABLED_TEXT to ColorRole.DISABLED
)
private fun lowContrast(p: Palette): Int = contrastPairs.count { (fg,bg) ->
    val a=p[fg].luminance();val b=p[bg].luminance()
    (maxOf(a,b)+.05f)/(minOf(a,b)+.05f)<4.5f
}
private enum class ThemeFileOperation { READ, WRITE, SHARE }
private fun resolvedColors(preset: ThemePreset, dark: Boolean): Map<String, String> =
    ColorRole.entries.associate { it.name to preset.hex(it,dark) }

@Composable
fun AppearanceScreen(vm: ConverterViewModel, settings: UserSettings, back: ()->Unit) {
    val p=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var draft by remember {mutableStateOf<ThemePreset?>(null)}
    var role by remember {mutableStateOf<ColorRole?>(null)}
    var previewDark by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")}
    var original by remember {mutableStateOf<ThemePreset?>(null)}
    var initialDraft by remember {mutableStateOf<ThemePreset?>(null)}
    var initialName by remember {mutableStateOf("")}
    var basisSnapshot by remember {mutableStateOf<ThemePreset?>(null)}
    var baseChoice by remember {mutableStateOf("current")}
    var pendingBase by remember {mutableStateOf<String?>(null)}
    var confirmExit by remember {mutableStateOf(false)}
    var expanded by remember {mutableStateOf<Set<String>>(emptySet())}
    var renameTarget by remember { mutableStateOf<ThemePreset?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ThemePreset?>(null) }
    var exportTarget by remember { mutableStateOf<ThemePreset?>(null) }
    var importCandidate by remember { mutableStateOf<StyleFileCodec.DecodeResult.Success?>(null) }
    var importName by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<ThemePreset?>(null) }
    var fileError by remember { mutableStateOf<StyleFileCodec.Error?>(null) }
    var fileIoError by remember { mutableStateOf<ThemeFileOperation?>(null) }
    val shareTitle=tr("Поделиться темой","Share theme")
    val isRussian=LocalRussian.current
    val systemDark=isSystemInDarkTheme()
    val saveFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(StyleFileCodec.MIME)) { uri ->
        val target=exportTarget
        exportTarget=null
        if(uri!=null && target!=null) scope.launch {
            if(withContext(Dispatchers.IO) {runCatching {saveThemeToUri(context,uri,target)}.isFailure}) fileIoError=ThemeFileOperation.WRITE
        }
    }
    val openFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                context.contentResolver.openInputStream(uri)?.use {StyleFileCodec.decode(it)}
                    ?: StyleFileCodec.DecodeResult.Failure(StyleFileCodec.Error.INVALID)
            }.getOrNull()}
            when(result) {
                is StyleFileCodec.DecodeResult.Success -> {importCandidate=result;importName=result.preset.name}
                is StyleFileCodec.DecodeResult.Failure -> fileError=result.error
                null -> fileIoError=ThemeFileOperation.READ
            }
        }
    }
    val custom=settings.presets.filter {it.id!="default"}
    val currentMode=if(settings.presetId=="default") settings.theme else settings.preset.mode
    fun clearEditor() {draft=null;original=null;role=null;confirmExit=false;pendingBase=null}
    fun startNew(source: ThemePreset, sourceId: String, initialTitle: String = "", mode: String = source.mode) {
        val light=resolvedColors(source,false)
        val dark=resolvedColors(source,true)
        val fresh=ThemePreset(id=UUID.randomUUID().toString(),light=light,dark=dark,
            baseLight=light,baseDark=dark,mode=mode)
        draft=fresh;original=null;initialDraft=fresh;basisSnapshot=fresh
        name=initialTitle.take(50);initialName=name;baseChoice=sourceId
        previewDark=if(mode=="system") systemDark else mode=="dark";expanded=emptySet()
    }
    fun startExisting(source: ThemePreset) {
        draft=source;original=source;initialDraft=source;basisSnapshot=source
        name=source.name;initialName=source.name;baseChoice="saved"
        previewDark=if(source.mode=="system") systemDark else source.mode=="dark";expanded=emptySet()
    }
    fun requestExit() {
        if(draft!=null && (draft!=initialDraft || name!=initialName)) confirmExit=true else clearEditor()
    }
    fun applyBase(choice: String) {
        val current=draft ?: return
        if(choice=="restore") {
            val restored=current.copy(light=current.baseLight,dark=current.baseDark)
            draft=restored;basisSnapshot=restored
            return
        }
        val source=when(choice) {
            "current" -> settings.preset
            "saved" -> original ?: return
            "light","dark" -> ThemePreset()
            else -> custom.firstOrNull {it.id==choice} ?: return
        }
        val light: Map<String,String>
        val dark: Map<String,String>
        if(choice=="light" || choice=="dark") {
            val standard=ColorRole.entries.associate {it.name to if(choice=="dark") it.dark else it.light}
            light=standard;dark=standard
            previewDark=choice=="dark"
        } else {
            light=resolvedColors(source,false)
            dark=resolvedColors(source,true)
        }
        val updated=current.copy(light=light,dark=dark,baseLight=light,baseDark=dark)
        draft=updated;basisSnapshot=updated;baseChoice=choice
    }
    fun requestBase(choice: String) {
        if(choice==baseChoice) return
        val current=draft ?: return
        val baseline=basisSnapshot
        if(baseline!=null && (current.light!=baseline.light || current.dark!=baseline.dark)) pendingBase=choice
        else applyBase(choice)
    }
    androidx.activity.compose.BackHandler(draft!=null) {requestExit()}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton({if(draft!=null) requestExit() else back()},Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,contentDescription=tr("Назад","Back"),tint=p[ColorRole.ICON])
            }
            Text(if(draft==null) tr("Темы оформления","Appearance themes")
                 else if(original==null) tr("Новая тема","New theme") else tr("Редактирование темы","Edit theme"),
                Modifier.weight(1f).padding(start=4.dp),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(draft!=null) TextButton({
                val saved=draft!!.copy(name=name.trim())
                vm.edit {it.copy(presets=it.presets.filterNot {t->t.id==saved.id}+saved)}
                clearEditor()
            },Modifier.heightIn(min=48.dp),enabled=name.trim().isNotEmpty()) {
                Text(tr("Сохранить","Save"),maxLines=1)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("appearance_list"),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            if(draft==null) {
                item {
                    Text(tr("Стандартные темы","Standard themes"),style=MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("system" to tr("Как в системе","System"),"light" to tr("Светлая","Light"),"dark" to tr("Тёмная","Dark")).forEach {(key,label)->
                            Choice(label,settings.presetId=="default" && settings.theme==key,{vm.edit {it.copy(presetId="default",theme=key)}})
                        }
                    }
                    Text(tr("Мои темы","My themes"),Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Action(tr("Создать тему","Create theme"),{
                            startNew(settings.preset,if(settings.presetId=="default") "current" else settings.presetId,mode=currentMode)
                        },Modifier.weight(1f))
                        Action(tr("Из файла","Import from file"),{openFile.launch(arrayOf("*/*"))},Modifier.weight(1f),secondary=true)
                    }
                }
                items(custom,key={it.id}) {preset ->
                    ThemeCard(preset,settings.presetId==preset.id,
                        apply={vm.edit {it.copy(presetId=preset.id)}},
                        share={runCatching {shareThemeFile(context,preset,shareTitle)}.onFailure {fileIoError=ThemeFileOperation.SHARE}},
                        edit={startExisting(preset)}, rename={renameTarget=preset;renameName=preset.name},
                        copy={startNew(preset,preset.id,preset.name+" · "+if(isRussian) "копия" else "copy")},
                        save={exportTarget=preset;saveFile.launch(StyleFileCodec.suggestedFileName(preset.name))},
                        delete={deleteTarget=preset})
                }
            } else {
                val current=draft!!
                item {
                    Field(name,{name=it.take(50)},tr("Название темы","Theme name"),
                        Modifier.fillMaxWidth().padding(vertical=6.dp).testTag("theme_name"))
                }
                item {
                    EditorBlock(tr("Основа темы","Theme base")) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Choice(tr("Текущая","Current"),baseChoice=="current",{requestBase("current")})
                            if(original!=null) Choice(tr("Сохранённая основа","Saved base"),baseChoice=="saved",{requestBase("saved")})
                            Choice(tr("Светлая","Light"),baseChoice=="light",{requestBase("light")})
                            Choice(tr("Тёмная","Dark"),baseChoice=="dark",{requestBase("dark")})
                            custom.filterNot {it.id==original?.id}.forEach {preset ->
                                Choice(preset.name,baseChoice==preset.id,{requestBase(preset.id)})
                            }
                        }
                        Text(tr("Изменённый цвет применяется к обоим вариантам темы.",
                            "An edited color applies to both theme variants."),
                            style=MaterialTheme.typography.bodySmall,color=p[ColorRole.MUTED])
                    }
                }
                item {
                    ThemePreview(current,previewDark)
                    if(lowContrast(Palette(current,previewDark))>0) Note(tr("Низкий контраст: рекомендуем от 4,5:1. Цвета не меняются автоматически.","Low contrast: 4.5:1 is recommended. Colors are never changed automatically."),warning=true)
                }
                item {
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),
                        verticalArrangement=Arrangement.Center) {
                        Text(tr("Цвета","Colors"),Modifier.padding(end=8.dp).align(Alignment.CenterVertically),style=MaterialTheme.typography.titleMedium)
                        TextButton({expanded=ColorSections.withLayoutSections().map {it.id}.toSet()}) {Text(tr("Развернуть все","Expand all"))}
                        TextButton({expanded=emptySet()}) {Text(tr("Свернуть все","Collapse all"))}
                    }
                }
                items(ColorSections.withLayoutSections(),key={"section_${it.id}"}) {section ->
                    ColorSectionCard(section,current,previewDark,section.id in expanded,
                        toggle={expanded=if(section.id in expanded) expanded-section.id else expanded+section.id},
                        edit={role=it})
                }
                item {
                    TextButton({requestBase("restore")},Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(tr("Вернуть цвета основы","Restore base colors"))
                    }
                }
            }
        }
    }
    role?.let {r -> draft?.let {d ->
        val section=ColorSections.withLayoutSections().first {r in it.roles}
        ColorEditor(r,section,d,previewDark,{role=null}) {hex->draft=d.withSharedColor(r,hex);role=null}
    }}
    if(confirmExit) AlertDialog(onDismissRequest={confirmExit=false},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Выйти без сохранения?","Discard changes?"))},
        text={Text(tr("Несохранённые изменения темы будут потеряны.","Unsaved theme changes will be lost."))},
        confirmButton={TextButton({clearEditor()}) {Text(tr("Выйти","Discard"))}},
        dismissButton={TextButton({confirmExit=false}) {Text(tr("Продолжить","Keep editing"))}})
    pendingBase?.let {choice -> AlertDialog(onDismissRequest={pendingBase=null},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Заменить изменённые цвета?","Replace edited colors?"))},
        text={Text(tr("Несохранённые изменения цветов будут потеряны. Название темы сохранится.",
            "Unsaved color changes will be lost. The theme name will stay."))},
        confirmButton={TextButton({applyBase(choice);pendingBase=null}) {Text(tr("Заменить","Replace"))}},
        dismissButton={TextButton({pendingBase=null}) {Text(tr("Отмена","Cancel"))}}) }
    renameTarget?.let {target -> AlertDialog(onDismissRequest={renameTarget=null},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Переименовать тему","Rename theme"))},text={Field(renameName,{renameName=it.take(50)},tr("Название","Name"),Modifier.fillMaxWidth())},
        confirmButton={TextButton({vm.edit {s->s.copy(presets=s.presets.map {if(it.id==target.id) it.copy(name=renameName.trim()) else it})};renameTarget=null},enabled=renameName.isNotBlank()) {Text(tr("Сохранить","Save"))}},
        dismissButton={TextButton({renameTarget=null}) {Text(tr("Отмена","Cancel"))}}) }
    deleteTarget?.let {target -> AlertDialog(onDismissRequest={deleteTarget=null},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Удалить тему?","Delete theme?"))},text={Text(target.name)},
        confirmButton={TextButton({vm.edit {s->s.copy(presets=s.presets.filterNot {it.id==target.id},presetId=if(s.presetId==target.id) "default" else s.presetId)};deleteTarget=null}) {Text(tr("Удалить","Delete"))}},
        dismissButton={TextButton({deleteTarget=null}) {Text(tr("Отмена","Cancel"))}}) }
    importCandidate?.let {candidate ->
        val collision=custom.any {it.name.equals(importName.trim(),ignoreCase=true)}
        AlertDialog(onDismissRequest={importCandidate=null},containerColor=p[ColorRole.DIALOG],
            title={Text(tr("Импорт темы","Import theme"))},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Field(importName,{importName=it.take(50)},tr("Название","Name"),Modifier.fillMaxWidth(),error=collision)
                if(collision) Text(tr("Тема с таким названием уже есть. Укажите другое имя.","A theme with this name exists. Choose another name."),color=p[ColorRole.ERROR_TEXT])
                if(candidate.unsupportedLayout) Note(tr("Настройки интерфейса из файла не поддерживаются. Будут импортированы только цвета.","This layout is not supported. Only colors will be imported."),warning=true)
                ThemePreview(candidate.preset,candidate.preset.mode=="dark")
            }},
            confirmButton={TextButton({
                val added=candidate.preset.copy(id=UUID.randomUUID().toString(),name=importName.trim())
                vm.edit {it.copy(presets=it.presets+added)}
                imported=added;importCandidate=null
            },enabled=importName.isNotBlank() && !collision) {Text(tr("Импортировать","Import"))}},
            dismissButton={TextButton({importCandidate=null}) {Text(tr("Отмена","Cancel"))}})
    }
    imported?.let {target -> AlertDialog(onDismissRequest={imported=null},containerColor=p[ColorRole.DIALOG],
        title={Text(tr("Тема импортирована","Theme imported"))},text={Text(target.name)},
        confirmButton={TextButton({vm.edit {it.copy(presetId=target.id)};imported=null}) {Text(tr("Применить тему","Apply theme"))}},
        dismissButton={TextButton({imported=null}) {Text(tr("Позже","Later"))}}) }
    if(fileError!=null || fileIoError!=null) AlertDialog(onDismissRequest={fileError=null;fileIoError=null},containerColor=p[ColorRole.DIALOG],
        title={Text(when(fileIoError) {
            ThemeFileOperation.WRITE -> tr("Не удалось сохранить файл","Could not save file")
            ThemeFileOperation.SHARE -> tr("Не удалось поделиться темой","Could not share theme")
            else -> tr("Не удалось прочитать файл","Could not read file")
        })},
        text={Text(when(fileError) {
            StyleFileCodec.Error.TOO_LARGE -> tr("Файл темы слишком большой.","The theme file is too large.")
            StyleFileCodec.Error.UNSUPPORTED_VERSION -> tr("Неподдерживаемая версия файла.","Unsupported file version.")
            StyleFileCodec.Error.INVALID -> tr("Файл темы повреждён или имеет неверную структуру.","The theme file is damaged or has an invalid structure.")
            null -> tr("Проверьте доступ к файлу и попробуйте ещё раз.","Check file access and try again.")
        })},confirmButton={TextButton({fileError=null;fileIoError=null}) {Text(tr("Закрыть","Close"))}})
}

@Composable
private fun EditorBlock(title: String, description: String? = null, content: @Composable ()->Unit) {
    val p=LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.titleSmall,color=p[ColorRole.TEXT])
        description?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=p[ColorRole.MUTED])}
        content()
    }
}

@Composable
private fun ColorSectionCard(section: ColorSection, preset: ThemePreset, dark: Boolean,
    expanded: Boolean, toggle: ()->Unit, edit: (ColorRole)->Unit) {
    val p=LocalPalette.current
    Surface(color=p[ColorRole.ROW],shape=RoundedCornerShape(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("section_${section.id}").clickable(onClick=toggle).padding(horizontal=12.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(section.icon,contentDescription=null,modifier=Modifier.size(20.dp),tint=p[ColorRole.ICON])
                Text(if(LocalRussian.current) section.ru else section.en,Modifier.weight(1f),
                    style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Icon(if(expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription=if(expanded) tr("Свернуть","Collapse") else tr("Развернуть","Expand"),
                    modifier=Modifier.size(20.dp),tint=p[ColorRole.ICON])
            }
            if(expanded) Column(Modifier.padding(start=10.dp,end=10.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                SectionPreview(section,preset,dark,edit)
                section.roles.forEach {item ->
                    val hex=preset.hex(item,dark)
                    val label=if(LocalRussian.current) item.ru else item.en
                    BoxWithConstraints {
                        val showHex=maxWidth>=330.dp
                        Surface(onClick={edit(item)},modifier=Modifier.testTag("color_${item.name}").semantics {contentDescription="$label, #$hex"},
                            color=p[ColorRole.SECONDARY],shape=RoundedCornerShape(10.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=10.dp,vertical=6.dp),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(28.dp).background(Color(0xFF000000 or hex.toLong(16)),RoundedCornerShape(7.dp))
                                    .border(1.dp,p[ColorRole.BORDER],RoundedCornerShape(7.dp)))
                                Text(label,Modifier.weight(1f),
                                    style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                if(showHex) Text("#$hex",style=MaterialTheme.typography.labelSmall,color=p[ColorRole.MUTED])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(preset: ThemePreset, active: Boolean, apply: ()->Unit, share: ()->Unit,
    edit: ()->Unit, rename: ()->Unit, copy: ()->Unit, save: ()->Unit, delete: ()->Unit) {
    val p=LocalPalette.current
    var menu by remember {mutableStateOf(false)}
    Surface(color=p[ColorRole.ROW],shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    listOf(ColorRole.MAIN,ColorRole.BUTTON,ColorRole.SELECTED).forEach {role ->
                        Box(Modifier.size(16.dp).background(Color(0xFF000000 or preset.hex(role,preset.mode=="dark").toLong(16)),RoundedCornerShape(5.dp)))
                    }
                }
                Text(preset.name,Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleSmall,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Box {
                    IconButton({menu=true},Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert,contentDescription=tr("Действия с темой","Theme actions"),tint=p[ColorRole.ICON])
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false},containerColor=p[ColorRole.DIALOG]) {
                        fun close(action: ()->Unit) {menu=false;action()}
                        DropdownMenuItem(text={Text(tr("Изменить тему","Edit theme"))},onClick={close(edit)})
                        DropdownMenuItem(text={Text(tr("Переименовать","Rename"))},onClick={close(rename)})
                        DropdownMenuItem(text={Text(tr("Создать копию","Create copy"))},onClick={close(copy)})
                        DropdownMenuItem(text={Text(tr("Сохранить в файл","Save to file"))},onClick={close(save)})
                        DropdownMenuItem(text={Text(tr("Удалить","Delete"))},onClick={close(delete)})
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(active) Box(Modifier.weight(1f).heightIn(min=48.dp),contentAlignment=Alignment.Center) {
                    Text(tr("Активна","Active"),color=p[ColorRole.MUTED])
                } else Action(tr("Применить","Apply"),apply,Modifier.weight(1f))
                Action(tr("Поделиться","Share"),share,Modifier.weight(1f),secondary=true)
            }
        }
    }
}

@Composable
private fun ThemePreview(preset: ThemePreset, dark: Boolean) {
    val p=Palette(preset,dark)
    Column(Modifier.fillMaxWidth().background(p[ColorRole.MAIN],RoundedCornerShape(20.dp)).border(1.dp,p[ColorRole.BORDER],RoundedCornerShape(20.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("Предпросмотр","Preview"),fontWeight=FontWeight.Bold,color=p[ColorRole.TEXT])
        Text(tr("Второстепенный текст","Secondary text"),color=p[ColorRole.MUTED])
        Row(Modifier.fillMaxWidth().background(p[ColorRole.ROW],RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("🇺🇸 USD",color=p[ColorRole.TEXT],modifier=Modifier.weight(1f));Text("100",color=p[ColorRole.TEXT])
        }
        Row(Modifier.fillMaxWidth().background(p[ColorRole.SELECTED],RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("🇪🇺 EUR",color=p[ColorRole.SELECTED_TEXT],modifier=Modifier.weight(1f));Text("125,50",color=p[ColorRole.SELECTED_TEXT])
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).background(p[ColorRole.BUTTON],RoundedCornerShape(12.dp)).padding(12.dp),contentAlignment=Alignment.Center) {Text(tr("Кнопка","Button"),color=p[ColorRole.ON_BUTTON])}
            Row(Modifier.weight(1f).background(p[ColorRole.KEYPAD],RoundedCornerShape(12.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).background(p[ColorRole.KEY],RoundedCornerShape(8.dp)).padding(8.dp),contentAlignment=Alignment.Center) {Text("1  2",color=p[ColorRole.KEY_TEXT])}
                Box(Modifier.background(p[ColorRole.KEY_ACTION],RoundedCornerShape(8.dp)).padding(8.dp),contentAlignment=Alignment.Center) {Text("C",color=p[ColorRole.KEY_ACTION_TEXT])}
            }
        }
        Text(tr("Информация","Information"),color=p[ColorRole.INFO_TEXT],modifier=Modifier.fillMaxWidth().background(p[ColorRole.INFO],RoundedCornerShape(10.dp)).padding(8.dp))
    }
}

@Composable
private fun ColorEditor(role: ColorRole,section: ColorSection,preset: ThemePreset,dark: Boolean,dismiss: ()->Unit,save: (String)->Unit) {
    val p=LocalPalette.current
    var hex by remember {mutableStateOf(preset.hex(role,dark))}
    val valid=Regex("[0-9A-Fa-f]{6}").matches(hex)
    val hsv=remember {FloatArray(3).also {AndroidColor.colorToHSV("#${preset.hex(role,dark)}".toColorInt(),it)}}
    var hue by remember {mutableFloatStateOf(hsv[0])}
    var saturation by remember {mutableFloatStateOf(hsv[1])}
    var brightness by remember {mutableFloatStateOf(hsv[2])}
    fun setHsv(h: Float,s: Float,v: Float) {hue=h;saturation=s;brightness=v;hex=String.format("%06X",AndroidColor.HSVToColor(floatArrayOf(h,s,v)) and 0xFFFFFF)}
    Dialog(onDismissRequest=dismiss) {
        Surface(color=p[ColorRole.DIALOG],contentColor=p[ColorRole.DIALOG_TEXT],shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(LocalRussian.current) role.ru else role.en,style=MaterialTheme.typography.titleLarge)
                Text(tr("Насыщенность и яркость","Saturation and brightness"),style=MaterialTheme.typography.labelMedium)
                val currentHue by rememberUpdatedState(hue)
                Canvas(Modifier.fillMaxWidth().height(140.dp)
                    .pointerInput(Unit) {detectTapGestures {pos ->setHsv(currentHue,(pos.x/size.width).coerceIn(0f,1f),(1-pos.y/size.height).coerceIn(0f,1f))}}
                    .pointerInput(Unit) {detectDragGestures {change,_ ->change.consume();setHsv(currentHue,(change.position.x/size.width).coerceIn(0f,1f),(1-change.position.y/size.height).coerceIn(0f,1f))}}) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White,Color(AndroidColor.HSVToColor(floatArrayOf(hue,1f,1f))))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
                    drawCircle(Color.White,7.dp.toPx(),Offset(saturation*size.width,(1-brightness)*size.height),style=Stroke(2.dp.toPx()))
                }
                Text(tr("Цветовой тон","Hue"),style=MaterialTheme.typography.labelMedium)
                Slider(hue,{setHsv(it,saturation,brightness)},valueRange=0f..360f,modifier=Modifier.background(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)),RoundedCornerShape(20.dp)))
                Field(hex,{value -> hex=value.removePrefix("#").take(6).uppercase(); if(Regex("[0-9A-F]{6}").matches(hex)) {
                    val values=FloatArray(3);AndroidColor.colorToHSV("#$hex".toColorInt(),values);hue=values[0];saturation=values[1];brightness=values[2]
                }},tr("HEX · 6 символов","HEX · 6 characters"),Modifier.fillMaxWidth(),error=!valid)
                if(valid) {
                    SectionPreview(section,preset.withSharedColor(role,hex),dark,edit={},tagPrefix="dialog_preview")
                    if(lowContrast(Palette(preset.withSharedColor(role,hex),dark))>0) Note(tr("Проверьте контраст текста перед сохранением.","Check text contrast before saving."),warning=true)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Action(tr("Отмена","Cancel"),dismiss,Modifier.weight(1f),secondary=true)
                    Action(tr("Применить","Apply"),{save(hex)},Modifier.weight(1f),enabled=valid)
                }
            }
        }
    }
}
