package com.maxim.currency.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import com.maxim.currency.domain.ColorRole

/** UI grouping only; role IDs and the portable style file remain independent of layout. */
data class ColorSection(val id: String, val ru: String, val en: String, val icon: ImageVector, val roles: List<ColorRole>)

object ColorSections {
    val common = listOf(
        ColorSection("basic", "Основные цвета", "Basic colors", Icons.Default.Palette,
            listOf(ColorRole.MAIN, ColorRole.SECONDARY, ColorRole.STATUS, ColorRole.NAVIGATION, ColorRole.DIVIDER)),
        ColorSection("currency", "Карточки валют", "Currency cards", Icons.Default.CreditCard,
            listOf(ColorRole.ROW, ColorRole.SELECTED, ColorRole.SELECTED_TEXT, ColorRole.BORDER)),
        ColorSection("keypad", "Клавиатура", "Keypad", Icons.Default.Dialpad,
            listOf(ColorRole.KEYPAD, ColorRole.KEY, ColorRole.KEY_TEXT, ColorRole.KEY_ACTION, ColorRole.KEY_ACTION_TEXT)),
        ColorSection("text", "Текст и иконки", "Text and icons", Icons.Default.TextFields,
            listOf(ColorRole.TEXT, ColorRole.MUTED, ColorRole.ICON, ColorRole.DISABLED_TEXT)),
        ColorSection("controls", "Кнопки и поля", "Buttons and fields", Icons.Default.TouchApp,
            listOf(ColorRole.BUTTON, ColorRole.ON_BUTTON, ColorRole.PRESSED, ColorRole.DISABLED,
                ColorRole.INPUT, ColorRole.INPUT_TEXT, ColorRole.CURSOR)),
        ColorSection("windows", "Окна и уведомления", "Windows and messages", Icons.Default.Notifications,
            listOf(ColorRole.DIALOG, ColorRole.DIALOG_TEXT, ColorRole.INFO, ColorRole.INFO_TEXT,
                ColorRole.WARNING, ColorRole.WARNING_TEXT, ColorRole.ERROR, ColorRole.ERROR_TEXT, ColorRole.SCRIM))
    )

    fun withLayoutSections(additional: List<ColorSection> = emptyList()): List<ColorSection> = common + additional
}
