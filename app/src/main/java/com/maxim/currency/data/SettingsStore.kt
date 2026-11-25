package com.maxim.currency.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.maxim.currency.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SettingsCodec {
    private val gson = Gson()
    fun encode(settings: UserSettings): String = gson.toJson(settings)
    fun decode(json: String?): UserSettings = runCatching {
        if (json == null) return UserSettings()
        val obj = JsonParser.parseString(json).asJsonObject
        val defaults = UserSettings()
        fun str(key: String, default: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
        val selected = obj.getAsJsonArray("selected")?.mapNotNull { runCatching { it.asString }.getOrNull() }
            ?.filter { Regex("[A-Z]{3}").matches(it) }?.distinct()?.take(200)?.takeIf { it.isNotEmpty() } ?: defaults.selected
        fun colors(o: JsonObject?, key: String): Map<String, String> = o?.getAsJsonObject(key)?.entrySet()?.mapNotNull { (k, v) ->
            val hex = runCatching { v.asString.uppercase() }.getOrNull()
            if (ColorRole.entries.any { it.name == k } && hex != null && Regex("[0-9A-F]{6}").matches(hex)) k to hex else null
        }?.toMap() ?: emptyMap()
        val presets = obj.getAsJsonArray("presets")?.mapNotNull {
            runCatching {
                val p = it.asJsonObject
                ThemePreset(p["id"].asString, p["name"].asString.take(50), colors(p, "light"), colors(p, "dark"),
                    if(p.has("baseLight")) colors(p,"baseLight") else colors(p,"light"),
                    if(p.has("baseDark")) colors(p,"baseDark") else colors(p,"dark"),
                    p.get("mode")?.asString?.takeIf {it in listOf("light","dark","system")} ?: "system")
            }.getOrNull()
        }?.distinctBy { it.id }?.takeIf { it.isNotEmpty() } ?: defaults.presets
        val migrated = presets.mapNotNull { p ->
            if(p.id != "default") p else if(p.light.isNotEmpty() || p.dark.isNotEmpty()) p.copy(id="legacy-default",name=p.name.ifBlank {"Моя тема / My theme"}) else null
        }
        val oldId=str("presetId","default")
        val chosen=if(oldId=="default" && migrated.any {it.id=="legacy-default"}) "legacy-default" else oldId
        val preferences=obj.getAsJsonObject("currencyPreferences")?.entrySet()?.mapNotNull { (key,value) ->
            runCatching {value.asString}.getOrNull()?.takeIf {Regex("[A-Z]{3}").matches(it)}?.let {key to it}
        }?.toMap().orEmpty()
        UserSettings(
            selected = selected, active = str("active", selected.first()).takeIf { it in selected } ?: selected.first(),
            amount = str("amount", "1").takeIf { it.length <= 100 && it.toBigDecimalOrNull() != null } ?: "1",
            language = str("language", "system").takeIf { it in listOf("system", "ru", "en") } ?: "system",
            theme = str("theme", "system").takeIf { it in listOf("system", "light", "dark") } ?: "system",
            processMode = str("processMode", "popup").takeIf { it in listOf("popup", "main") } ?: "popup",
            presets = listOf(ThemePreset()) + migrated, presetId = chosen.takeIf {id -> migrated.any {it.id==id}} ?: "default", currencyPreferences=preferences,
            updateMode = str("updateMode", "auto").takeIf {it in listOf("auto","manual")} ?: "auto",
            lastUpdateCheck = obj.get("lastUpdateCheck")?.let {runCatching {it.asLong}.getOrNull()}?.takeIf {it>=0} ?: 0L
        )
    }.getOrElse { UserSettings() }
}

class SettingsStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("settings_v1")
    val settings: Flow<UserSettings> = store.data.map { SettingsCodec.decode(it[key]) }
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { it[key] = SettingsCodec.encode(transform(SettingsCodec.decode(it[key]))) }
    }
}
