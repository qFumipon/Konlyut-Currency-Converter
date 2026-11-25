package com.maxim.currency.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.maxim.currency.domain.ColorRole
import com.maxim.currency.domain.ThemePreset
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Portable color data. Layout is an optional, independent namespace for future versions. */
object StyleFileCodec {
    const val MIME = "application/json"
    const val EXTENSION = ".currency-style.json"
    const val MAX_BYTES = 128 * 1024
    private const val FORMAT = "currency-converter-style"
    private const val VERSION = 1
    private val gson = Gson()
    private val hexPattern = Regex("[0-9A-Fa-f]{6}")

    enum class Error { TOO_LARGE, INVALID, UNSUPPORTED_VERSION }
    sealed interface DecodeResult {
        data class Success(val preset: ThemePreset, val unsupportedLayout: Boolean) : DecodeResult
        data class Failure(val error: Error) : DecodeResult
    }

    fun suggestedFileName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(50).ifBlank { "theme" } + EXTENSION

    fun encode(preset: ThemePreset): ByteArray {
        require(preset.id != "default")
        val theme = JsonObject().apply {
            addProperty("base", "embedded")
            addProperty("mode", preset.mode)
            add("colors", variants(preset.light, preset.dark))
            add("baseColors", variants(preset.baseLight, preset.baseDark))
        }
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("schemaVersion", VERSION)
            addProperty("name", preset.name)
            add("theme", theme)
        }
        return gson.toJson(root).toByteArray(StandardCharsets.UTF_8)
    }

    private fun variants(light: Map<String, String>, dark: Map<String, String>): JsonObject = JsonObject().apply {
        add("light", colorMap(light, false))
        add("dark", colorMap(dark, true))
    }

    private fun colorMap(overrides: Map<String, String>, dark: Boolean): JsonObject = JsonObject().apply {
        ColorRole.entries.forEach { role -> addProperty(role.name, overrides[role.name] ?: if (dark) role.dark else role.light) }
    }

    fun decode(input: InputStream): DecodeResult {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_BYTES) return DecodeResult.Failure(Error.TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > MAX_BYTES) return DecodeResult.Failure(Error.TOO_LARGE)
        return try {
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val root = JsonParser.parseString(text).asJsonObject
            if (root.string("format") != FORMAT) return DecodeResult.Failure(Error.INVALID)
            val versionValue = root.get("schemaVersion")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?: return DecodeResult.Failure(Error.INVALID)
            val version = versionValue.asString.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                ?: return DecodeResult.Failure(Error.INVALID)
            if (version != VERSION) return DecodeResult.Failure(Error.UNSUPPORTED_VERSION)
            val name = root.string("name")?.trim() ?: return DecodeResult.Failure(Error.INVALID)
            if (name.isEmpty() || name.length > 50 || name.any { it.isISOControl() }) return DecodeResult.Failure(Error.INVALID)
            val theme = root.getAsJsonObject("theme") ?: return DecodeResult.Failure(Error.INVALID)
            val mode = theme.string("mode") ?: return DecodeResult.Failure(Error.INVALID)
            if (mode !in listOf("system", "light", "dark")) return DecodeResult.Failure(Error.INVALID)
            val colors = theme.getAsJsonObject("colors") ?: return DecodeResult.Failure(Error.INVALID)
            val base = theme.getAsJsonObject("baseColors") ?: return DecodeResult.Failure(Error.INVALID)
            val light = parseColors(colors.getAsJsonObject("light")) ?: return DecodeResult.Failure(Error.INVALID)
            val dark = parseColors(colors.getAsJsonObject("dark")) ?: return DecodeResult.Failure(Error.INVALID)
            val baseLight = parseColors(base.getAsJsonObject("light")) ?: return DecodeResult.Failure(Error.INVALID)
            val baseDark = parseColors(base.getAsJsonObject("dark")) ?: return DecodeResult.Failure(Error.INVALID)
            val layout = root.get("layout")
            val unsupportedLayout = layout != null && !layout.isJsonNull &&
                (!layout.isJsonObject || layout.asJsonObject.string("id") != "classic-list")
            DecodeResult.Success(ThemePreset(id = "import", name = name, light = light, dark = dark,
                baseLight = baseLight, baseDark = baseDark, mode = mode), unsupportedLayout)
        } catch (_: Exception) {
            DecodeResult.Failure(Error.INVALID)
        }
    }

    private fun parseColors(obj: JsonObject?): Map<String, String>? {
        if (obj == null) return null
        val colors = mutableMapOf<String, String>()
        ColorRole.entries.forEach { role ->
            val value = runCatching { obj.get(role.name)?.asString }.getOrNull() ?: return null
            if (!hexPattern.matches(value)) return null
            colors[role.name] = value.uppercase()
        }
        return colors
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
