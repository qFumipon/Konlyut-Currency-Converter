package com.maxim.currency.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.currency.domain.ColorRole
import com.maxim.currency.domain.ThemePreset

/** All samples read the live draft through Palette, exactly like the app's surfaces. */
@Composable
fun SectionPreview(section: ColorSection, preset: ThemePreset, dark: Boolean, edit: (ColorRole)->Unit,
                   tagPrefix: String = "preview") {
    val p=Palette(preset,dark)
    val values=section.roles.joinToString("; ") {"${it.name}=#${preset.hex(it,dark)}"}
    Box(Modifier.fillMaxWidth().testTag("${tagPrefix}_${section.id}").semantics {contentDescription=values}) {
        when(section.id) {
            "basic" -> BasicSample(p,edit)
            "currency" -> CurrencySample(p,edit)
            "keypad" -> KeypadSample(p,edit)
            "text" -> TextSample(p,edit)
            "controls" -> ControlsSample(p,edit)
            "windows" -> WindowsSample(p,edit)
            else -> GenericSample(p,section.roles,edit)
        }
    }
}

@Composable
private fun BasicSample(p: Palette, edit: (ColorRole)->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p[ColorRole.MAIN]).clickable {edit(ColorRole.MAIN)}) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(p[ColorRole.STATUS]))
        Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("Главный экран","Main screen"),Modifier.weight(1f),fontSize=12.sp,color=p[ColorRole.TEXT],maxLines=2)
            Icon(Icons.Default.Refresh,contentDescription=null,modifier=Modifier.size(18.dp),tint=p[ColorRole.ICON])
        }
        Text(tr("Дополнительная область","Other screen"),Modifier.fillMaxWidth().padding(horizontal=8.dp)
            .background(p[ColorRole.SECONDARY],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.SECONDARY)}.padding(8.dp),fontSize=12.sp,color=p[ColorRole.TEXT])
        Spacer(Modifier.fillMaxWidth().padding(top=6.dp).height(1.dp).background(p[ColorRole.DIVIDER]))
        Box(Modifier.fillMaxWidth().height(6.dp).background(p[ColorRole.NAVIGATION]))
    }
}

@Composable
private fun CurrencySample(p: Palette, edit: (ColorRole)->Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp,p[ColorRole.BORDER],RoundedCornerShape(12.dp)).padding(6.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().background(p[ColorRole.ROW],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.ROW)}.padding(8.dp)) {
            Text("🇺🇸 USD",Modifier.weight(1f),fontSize=12.sp,color=p[ColorRole.TEXT]);Text("100",fontSize=12.sp,color=p[ColorRole.TEXT])
        }
        Row(Modifier.fillMaxWidth().border(1.dp,p[ColorRole.CURSOR],RoundedCornerShape(8.dp))
            .background(p[ColorRole.SELECTED],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.SELECTED)}.padding(8.dp)) {
            Text("🇪🇺 EUR",Modifier.weight(1f),fontSize=12.sp,color=p[ColorRole.SELECTED_TEXT]);Text("92,50",fontSize=12.sp,color=p[ColorRole.SELECTED_TEXT])
        }
    }
}

@Composable
private fun KeypadSample(p: Palette, edit: (ColorRole)->Unit) {
    Row(Modifier.fillMaxWidth().background(p[ColorRole.KEYPAD],RoundedCornerShape(12.dp)).clickable {edit(ColorRole.KEYPAD)}.padding(7.dp),
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        listOf("1","2","3").forEach {digit ->
            Box(Modifier.weight(1f).background(p[ColorRole.KEY],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.KEY)}.padding(vertical=9.dp),contentAlignment=Alignment.Center) {
                Text(digit,color=p[ColorRole.KEY_TEXT],fontSize=16.sp)
            }
        }
        Box(Modifier.weight(1f).background(p[ColorRole.KEY_ACTION],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.KEY_ACTION)}.padding(vertical=9.dp),contentAlignment=Alignment.Center) {
            Text("⌫",color=p[ColorRole.KEY_ACTION_TEXT],fontSize=16.sp)
        }
    }
}

@Composable
private fun TextSample(p: Palette, edit: (ColorRole)->Unit) {
    Row(Modifier.fillMaxWidth().background(p[ColorRole.MAIN],RoundedCornerShape(12.dp)).padding(9.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Заголовок","Heading"),Modifier.clickable {edit(ColorRole.TEXT)},fontSize=13.sp,fontWeight=FontWeight.Bold,color=p[ColorRole.TEXT],maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(tr("Основной текст","Primary text"),fontSize=11.sp,color=p[ColorRole.TEXT],maxLines=1)
            Text(tr("Второстепенный текст","Secondary text"),Modifier.clickable {edit(ColorRole.MUTED)},fontSize=11.sp,color=p[ColorRole.MUTED],maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(tr("Недоступно","Disabled"),Modifier.clickable {edit(ColorRole.DISABLED_TEXT)},fontSize=11.sp,color=p[ColorRole.DISABLED_TEXT],maxLines=1)
        }
        Box(Modifier.size(48.dp).testTag("preview_text_refresh").clickable {edit(ColorRole.ICON)},contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Refresh,contentDescription=tr("Цвет иконок","Icon color"),modifier=Modifier.size(22.dp),tint=p[ColorRole.ICON])
        }
        Box(Modifier.size(48.dp).testTag("preview_text_settings").clickable {edit(ColorRole.ICON)},contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Settings,contentDescription=tr("Цвет иконок","Icon color"),modifier=Modifier.size(22.dp),tint=p[ColorRole.ICON])
        }
    }
}

@Composable
private fun ControlsSample(p: Palette, edit: (ColorRole)->Unit) {
    Column(Modifier.fillMaxWidth().background(p[ColorRole.SECONDARY],RoundedCornerShape(12.dp)).padding(8.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            MiniButton(tr("Кнопка","Button"),p[ColorRole.BUTTON],p[ColorRole.ON_BUTTON],Modifier.weight(1f)) {edit(ColorRole.BUTTON)}
            MiniButton(tr("Вторичная","Secondary"),p[ColorRole.SELECTED],p[ColorRole.SELECTED_TEXT],Modifier.weight(1f)) {edit(ColorRole.SELECTED)}
            MiniButton(tr("Нажата","Pressed"),p[ColorRole.PRESSED],p[ColorRole.TEXT],Modifier.weight(1f)) {edit(ColorRole.PRESSED)}
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f).border(1.dp,p[ColorRole.BORDER],RoundedCornerShape(8.dp))
                .background(p[ColorRole.INPUT],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.INPUT)}.padding(7.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Ввод","Input"),color=p[ColorRole.INPUT_TEXT],fontSize=11.sp)
                Spacer(Modifier.padding(start=3.dp).width(2.dp).height(14.dp).background(p[ColorRole.CURSOR]))
            }
            Text(tr("Выкл.","Off"),Modifier.background(p[ColorRole.DISABLED],RoundedCornerShape(8.dp)).clickable {edit(ColorRole.DISABLED)}.padding(7.dp),
                color=p[ColorRole.DISABLED_TEXT],fontSize=11.sp)
        }
    }
}

@Composable
private fun MiniButton(label: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color, modifier: Modifier, edit: ()->Unit) {
    Box(modifier.background(background,RoundedCornerShape(8.dp)).clickable(onClick=edit).padding(vertical=7.dp,horizontal=3.dp),contentAlignment=Alignment.Center) {
        Text(label,color=foreground,fontSize=10.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun WindowsSample(p: Palette, edit: (ColorRole)->Unit) {
    Column(Modifier.fillMaxWidth().background(p[ColorRole.SCRIM],RoundedCornerShape(12.dp)).padding(6.dp)) {
        Column(Modifier.fillMaxWidth().background(p[ColorRole.DIALOG],RoundedCornerShape(9.dp)).clickable {edit(ColorRole.DIALOG)}.padding(7.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(tr("Диалог","Dialog"),color=p[ColorRole.DIALOG_TEXT],fontSize=11.sp,fontWeight=FontWeight.Bold)
            NoticeSample(tr("Информация","Information"),p[ColorRole.INFO],p[ColorRole.INFO_TEXT]) {edit(ColorRole.INFO)}
            NoticeSample(tr("Предупреждение","Warning"),p[ColorRole.WARNING],p[ColorRole.WARNING_TEXT]) {edit(ColorRole.WARNING)}
            NoticeSample(tr("Ошибка","Error"),p[ColorRole.ERROR],p[ColorRole.ERROR_TEXT]) {edit(ColorRole.ERROR)}
        }
    }
}

@Composable
private fun NoticeSample(label: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color, edit: ()->Unit) {
    Text(label,Modifier.fillMaxWidth().background(background,RoundedCornerShape(6.dp)).clickable(onClick=edit).padding(horizontal=6.dp,vertical=4.dp),
        color=foreground,fontSize=11.sp,maxLines=2)
}

@Composable
private fun GenericSample(p: Palette, roles: List<ColorRole>, edit: (ColorRole)->Unit) {
    Row(Modifier.fillMaxWidth().background(p[ColorRole.SECONDARY],RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        roles.take(5).forEach {role ->
            Box(Modifier.weight(1f).height(36.dp).background(p[role],RoundedCornerShape(8.dp)).clickable {edit(role)})
        }
    }
}
