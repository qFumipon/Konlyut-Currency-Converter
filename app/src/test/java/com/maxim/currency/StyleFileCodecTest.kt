package com.maxim.currency

import com.google.gson.JsonParser
import com.maxim.currency.data.SettingsCodec
import com.maxim.currency.data.StyleFileCodec
import com.maxim.currency.domain.ColorRole
import com.maxim.currency.domain.ThemePreset
import com.maxim.currency.domain.UserSettings
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class StyleFileCodecTest {
    private val source = ThemePreset(id="personal",name="Океан",mode="dark")
        .withColor(ColorRole.MAIN,false,"123456")
        .withColor(ColorRole.KEY_ACTION,true,"A0B1C2")
        .copy(baseLight=mapOf(ColorRole.MAIN.name to "234567"),baseDark=mapOf(ColorRole.MAIN.name to "345678"))

    @Test fun exactRoundTripIncludesBothVariantsAndBaseColors() {
        val result=StyleFileCodec.decode(StyleFileCodec.encode(source)) as StyleFileCodec.DecodeResult.Success
        assertEquals("Океан",result.preset.name)
        assertEquals("dark",result.preset.mode)
        assertFalse(result.unsupportedLayout)
        ColorRole.entries.forEach {role ->
            assertEquals(source.hex(role,false),result.preset.hex(role,false))
            assertEquals(source.hex(role,true),result.preset.hex(role,true))
            assertEquals(source.baseLight[role.name] ?: role.light,result.preset.baseLight[role.name])
            assertEquals(source.baseDark[role.name] ?: role.dark,result.preset.baseDark[role.name])
        }
        assertEquals("import",result.preset.id)
    }

    @Test fun unknownFieldsAndUnsupportedLayoutKeepColors() {
        val root=JsonParser.parseString(String(StyleFileCodec.encode(source),StandardCharsets.UTF_8)).asJsonObject
        root.addProperty("futureField","ignored")
        root.getAsJsonObject("theme").addProperty("futureThemeField",true)
        root.add("layout",JsonParser.parseString("""{"id":"retro-calculator","settings":{"columns":3}}"""))
        val result=StyleFileCodec.decode(root.toString().toByteArray()) as StyleFileCodec.DecodeResult.Success
        assertTrue(result.unsupportedLayout)
        assertEquals("123456",result.preset.hex(ColorRole.MAIN,false))
    }

    @Test fun invalidVersionStructureAndSizeAreRejected() {
        val bytes=StyleFileCodec.encode(source)
        val root=JsonParser.parseString(String(bytes,StandardCharsets.UTF_8)).asJsonObject
        root.addProperty("schemaVersion",2)
        assertEquals(StyleFileCodec.Error.UNSUPPORTED_VERSION,
            (StyleFileCodec.decode(root.toString().toByteArray()) as StyleFileCodec.DecodeResult.Failure).error)
        root.addProperty("schemaVersion",1.5)
        assertEquals(StyleFileCodec.Error.INVALID,
            (StyleFileCodec.decode(root.toString().toByteArray()) as StyleFileCodec.DecodeResult.Failure).error)
        root.addProperty("schemaVersion",1)
        root.getAsJsonObject("theme").getAsJsonObject("colors").getAsJsonObject("light").addProperty("MAIN","not a color")
        assertEquals(StyleFileCodec.Error.INVALID,
            (StyleFileCodec.decode(root.toString().toByteArray()) as StyleFileCodec.DecodeResult.Failure).error)
        assertEquals(StyleFileCodec.Error.INVALID,
            (StyleFileCodec.decode("broken".toByteArray()) as StyleFileCodec.DecodeResult.Failure).error)
        assertEquals(StyleFileCodec.Error.TOO_LARGE,
            (StyleFileCodec.decode(ByteArrayInputStream(ByteArray(StyleFileCodec.MAX_BYTES+1))) as StyleFileCodec.DecodeResult.Failure).error)
    }

    @Test fun importedThemePersistsWithoutReplacingStandardOrActiveTheme() {
        val imported=(StyleFileCodec.decode(StyleFileCodec.encode(source)) as StyleFileCodec.DecodeResult.Success)
            .preset.copy(id=UUID.randomUUID().toString())
        val saved=SettingsCodec.decode(SettingsCodec.encode(UserSettings(presets=listOf(ThemePreset(),imported))))
        assertEquals("default",saved.presetId)
        assertEquals("default",saved.presets.first().id)
        assertTrue(saved.presets.first().light.isEmpty())
        assertEquals("123456",saved.presets.first {it.id==imported.id}.hex(ColorRole.MAIN,false))
    }
}
