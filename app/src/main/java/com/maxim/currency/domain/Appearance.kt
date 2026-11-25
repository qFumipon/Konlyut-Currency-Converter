package com.maxim.currency.domain

/** Every application surface uses these semantic tokens. Both variants are stored per preset. */
enum class ColorRole(val en: String, val ru: String, val light: String, val dark: String) {
    MAIN("Main background", "Фон главного экрана", "F4F7F8", "101719"),
    SECONDARY("Other screens", "Фон других экранов", "F4F7F8", "101719"),
    ROW("Currency row", "Строка валюты", "FFFFFF", "1B2528"),
    SELECTED("Selected row", "Выбранная валюта", "D8F3EB", "193D35"),
    TEXT("Primary text", "Основной текст", "152E32", "E4F0F2"),
    MUTED("Secondary text", "Второстепенный текст", "536C70", "ADC4C8"),
    SELECTED_TEXT("Selected row text", "Текст выбранной валюты", "073E34", "BDFAE5"),
    BUTTON("Buttons", "Кнопки", "006B58", "83D9BE"),
    ON_BUTTON("Button text", "Текст кнопок", "FFFFFF", "00382B"),
    PRESSED("Pressed state", "Состояние нажатия", "A9DCCC", "39685B"),
    DISABLED("Disabled controls", "Неактивные элементы", "D8E0E2", "344448"),
    DISABLED_TEXT("Disabled text", "Неактивный текст", "5F7175", "99ADB1"),
    ICON("Icons", "Иконки", "335D60", "B4D9D6"),
    BORDER("Borders", "Обводки", "8DA6AA", "68858A"),
    DIVIDER("Dividers", "Разделители", "D1DEE0", "344A50"),
    INPUT("Input background", "Фон поля ввода", "FFFFFF", "1B2528"),
    INPUT_TEXT("Input text", "Текст поля ввода", "152E32", "E4F0F2"),
    CURSOR("Cursor and selection", "Курсор и выделение", "006B58", "83D9BE"),
    KEYPAD("Keypad background", "Фон клавиатуры", "E7EFF0", "142024"),
    KEY("Digit keys", "Цифровые клавиши", "FFFFFF", "25363B"),
    KEY_TEXT("Digit text", "Цифры клавиатуры", "152E32", "E4F0F2"),
    KEY_ACTION("Action keys", "Служебные клавиши", "CDE6DF", "254E43"),
    KEY_ACTION_TEXT("Action key text", "Текст служебных клавиш", "073E34", "BDFAE5"),
    DIALOG("Dialogs and popups", "Диалоги и окна", "FFFFFF", "1B292E"),
    DIALOG_TEXT("Dialog text", "Текст диалогов", "152E32", "E4F0F2"),
    ERROR("Error background", "Фон ошибки", "FFDAD6", "61342F"),
    ERROR_TEXT("Error text", "Текст ошибки", "7F1C14", "FFDAD6"),
    WARNING("Warning background", "Фон предупреждения", "FFF0C1", "514523"),
    WARNING_TEXT("Warning text", "Текст предупреждения", "5D4800", "FFE7A0"),
    INFO("Information background", "Фон уведомлений", "D7EDF6", "243F50"),
    INFO_TEXT("Information text", "Текст уведомлений", "163E50", "C8E9FA"),
    STATUS("Status bar", "Строка состояния", "F4F7F8", "101719"),
    NAVIGATION("Navigation bar", "Панель навигации", "E7EFF0", "142024"),
    SCRIM("Dialog dimming", "Затемнение под диалогом", "18272C", "000000")
}

data class ThemePreset(
    val id: String = "default", val name: String = "",
    val light: Map<String, String> = emptyMap(), val dark: Map<String, String> = emptyMap(),
    val baseLight: Map<String,String> = light, val baseDark: Map<String,String> = dark,
    val mode: String = "system"
) {
    fun hex(role: ColorRole, isDark: Boolean): String = (if (isDark) dark else light)[role.name] ?: if (isDark) role.dark else role.light
    fun withColor(role: ColorRole, isDark: Boolean, hex: String): ThemePreset =
        if (isDark) copy(dark = dark + (role.name to hex)) else copy(light = light + (role.name to hex))
    /** The simplified editor has one color control per role; untouched legacy differences remain intact. */
    fun withSharedColor(role: ColorRole, hex: String): ThemePreset =
        copy(light = light + (role.name to hex), dark = dark + (role.name to hex))
}

data class UserSettings(
    val selected: List<String> = listOf("USD", "EUR", "RUB", "KZT", "BYN", "TRY", "CNY", "AED", "GBP", "JPY"),
    val active: String = "USD", val amount: String = "1",
    val language: String = "system", val theme: String = "system", val processMode: String = "popup",
    val presets: List<ThemePreset> = listOf(ThemePreset()), val presetId: String = "default",
    val currencyPreferences: Map<String,String> = emptyMap(), val schema: Int = 2,
    val updateMode: String = "auto", val lastUpdateCheck: Long = 0L
) {
    val preset: ThemePreset get() = presets.firstOrNull { it.id == presetId && it.id != "default" } ?: ThemePreset()
}
