package com.maxim.currency.data

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.maxim.currency.domain.UserSettings
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Explicit user-initiated transfer between differently signed installations. Rates are omitted. */
object SettingsBackup {
    const val MIME="application/json"
    const val FILE_NAME="Konlyut-settings.json"
    private const val MAX_BYTES=1_000_000
    fun encode(settings: UserSettings): String = JsonObject().apply {
        addProperty("format","konlyut-settings-backup")
        addProperty("schemaVersion",1)
        add("settings",JsonParser.parseString(SettingsCodec.encode(settings)))
    }.toString()
    fun decode(input: InputStream): UserSettings {
        val output=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            require(output.size()+count<=MAX_BYTES)
            output.write(buffer,0,count)
        }
        val bytes=output.toByteArray()
        require(bytes.size in 1..MAX_BYTES)
        val root=JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(root["format"]?.asString=="konlyut-settings-backup" && root["schemaVersion"]?.asInt==1)
        val settings=root["settings"]?.asJsonObject ?: error("Missing settings")
        require(settings.has("selected") && settings.has("presets") && settings.has("active"))
        return SettingsCodec.decode(settings.toString()).copy(lastUpdateCheck=0L)
    }
}
