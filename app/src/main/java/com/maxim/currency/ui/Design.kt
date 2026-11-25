@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.maxim.currency.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.maxim.currency.domain.*
import java.util.Locale

class Palette(val preset: ThemePreset, val dark: Boolean) {
    operator fun get(role: ColorRole): Color = Color(0xFF000000 or preset.hex(role, dark).toLong(16))
}
val LocalPalette = staticCompositionLocalOf { Palette(ThemePreset(), false) }
val LocalRussian = staticCompositionLocalOf { false }
@Composable fun tr(ru: String, en: String): String = if (LocalRussian.current) ru else en
@Composable fun appLocale(): Locale = if (LocalRussian.current) Locale.forLanguageTag("ru-RU") else Locale.US

@Composable
fun CurrencyTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveMode = if(settings.presetId!="default" && settings.preset.mode!="system") settings.preset.mode else settings.theme
    val dark = when (effectiveMode) { "dark" -> true; "light" -> false; else -> systemDark }
    val language = LocalConfiguration.current.locales[0].language
    val russian = settings.language == "ru" || settings.language == "system" && language == "ru"
    val p = Palette(settings.preset, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p[ColorRole.BUTTON], onPrimary = p[ColorRole.ON_BUTTON], primaryContainer = p[ColorRole.SELECTED], onPrimaryContainer = p[ColorRole.SELECTED_TEXT],
        inversePrimary = p[ColorRole.BUTTON], secondary = p[ColorRole.ICON], onSecondary = p[ColorRole.ON_BUTTON],
        secondaryContainer = p[ColorRole.SELECTED], onSecondaryContainer = p[ColorRole.SELECTED_TEXT],
        tertiary = p[ColorRole.BUTTON], onTertiary = p[ColorRole.ON_BUTTON], tertiaryContainer = p[ColorRole.INFO], onTertiaryContainer = p[ColorRole.INFO_TEXT],
        background = p[ColorRole.MAIN], onBackground = p[ColorRole.TEXT], surface = p[ColorRole.ROW], onSurface = p[ColorRole.TEXT],
        surfaceVariant = p[ColorRole.INPUT], onSurfaceVariant = p[ColorRole.MUTED], surfaceTint = Color.Transparent,
        inverseSurface = p[ColorRole.INFO], inverseOnSurface = p[ColorRole.INFO_TEXT], outline = p[ColorRole.BORDER], outlineVariant = p[ColorRole.DIVIDER],
        error = p[ColorRole.ERROR_TEXT], onError = p[ColorRole.ERROR], errorContainer = p[ColorRole.ERROR], onErrorContainer = p[ColorRole.ERROR_TEXT],
        scrim = p[ColorRole.SCRIM], surfaceBright = p[ColorRole.ROW], surfaceDim = p[ColorRole.SECONDARY],
        surfaceContainer = p[ColorRole.DIALOG], surfaceContainerHigh = p[ColorRole.DIALOG], surfaceContainerHighest = p[ColorRole.DIALOG],
        surfaceContainerLow = p[ColorRole.ROW], surfaceContainerLowest = p[ColorRole.MAIN]
    )
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = p[ColorRole.STATUS].luminance() > .5f
                isAppearanceLightNavigationBars = p[ColorRole.NAVIGATION].luminance() > .5f
            }
        }
    }
    CompositionLocalProvider(LocalPalette provides p, LocalRussian provides russian, LocalContentColor provides p[ColorRole.TEXT],
        LocalTextSelectionColors provides TextSelectionColors(p[ColorRole.CURSOR], p[ColorRole.CURSOR].copy(alpha=.35f)),
        LocalRippleConfiguration provides RippleConfiguration(color = p[ColorRole.PRESSED])) {
        MaterialTheme(colorScheme = scheme, typography = Typography(
            headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold),
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold)
        ), content = content)
    }
}

@Composable
fun Action(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(onClick, modifier.heightIn(min=48.dp), enabled=enabled, interactionSource=interaction,
        shape=RoundedCornerShape(16.dp), contentPadding=PaddingValues(horizontal=16.dp, vertical=10.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=if (pressed) p[ColorRole.PRESSED] else if (secondary) p[ColorRole.SELECTED] else p[ColorRole.BUTTON],
            contentColor=if (secondary) p[ColorRole.SELECTED_TEXT] else p[ColorRole.ON_BUTTON],
            disabledContainerColor=p[ColorRole.DISABLED], disabledContentColor=p[ColorRole.DISABLED_TEXT])) { Text(label) }
}

@Composable
fun Field(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val p=LocalPalette.current
    OutlinedTextField(value, onValueChange, modifier, label={Text(label)}, singleLine=true, isError=error,
        shape=RoundedCornerShape(16.dp), colors=OutlinedTextFieldDefaults.colors(
            focusedContainerColor=p[ColorRole.INPUT], unfocusedContainerColor=p[ColorRole.INPUT], errorContainerColor=p[ColorRole.ERROR],
            focusedTextColor=p[ColorRole.INPUT_TEXT], unfocusedTextColor=p[ColorRole.INPUT_TEXT], errorTextColor=p[ColorRole.ERROR_TEXT],
            focusedBorderColor=p[ColorRole.CURSOR], unfocusedBorderColor=p[ColorRole.BORDER], cursorColor=p[ColorRole.CURSOR],
            focusedLabelColor=p[ColorRole.CURSOR], unfocusedLabelColor=p[ColorRole.MUTED], errorLabelColor=p[ColorRole.ERROR_TEXT]))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, warning: Boolean = false, error: Boolean = false) {
    val p=LocalPalette.current
    val bg=if(error) ColorRole.ERROR else if(warning) ColorRole.WARNING else ColorRole.INFO
    val fg=if(error) ColorRole.ERROR_TEXT else if(warning) ColorRole.WARNING_TEXT else ColorRole.INFO_TEXT
    Surface(modifier.fillMaxWidth(), color=p[bg], contentColor=p[fg], shape=RoundedCornerShape(16.dp)) {
        Text(text, Modifier.padding(12.dp), style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Heading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Text(title, style=MaterialTheme.typography.titleLarge, color=LocalPalette.current[ColorRole.TEXT])
        subtitle?.let { Text(it, color=LocalPalette.current[ColorRole.MUTED], fontSize=13.sp, modifier=Modifier.padding(top=5.dp)) }
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p=LocalPalette.current
    FilterChip(selected, onClick, label={Text(label)}, modifier=modifier.heightIn(min=48.dp),
        colors=FilterChipDefaults.filterChipColors(containerColor=p[ColorRole.ROW], labelColor=p[ColorRole.TEXT],
            selectedContainerColor=p[ColorRole.SELECTED], selectedLabelColor=p[ColorRole.SELECTED_TEXT]))
}
