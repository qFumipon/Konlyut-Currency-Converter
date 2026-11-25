package com.maxim.currency

import com.maxim.currency.domain.ColorRole
import com.maxim.currency.domain.ThemePreset
import com.maxim.currency.ui.ColorSections
import org.junit.Assert.*
import org.junit.Test

class ColorSectionsTest {
    @Test fun everyPersistedColorAppearsInExactlyOneSection() {
        val roles=ColorSections.common.flatMap {it.roles}
        assertEquals(ColorRole.entries.size,roles.size)
        assertEquals(ColorRole.entries.toSet(),roles.toSet())
        assertEquals(ColorSections.common.size,ColorSections.common.map {it.id}.distinct().size)
    }

    @Test fun paletteVariantsStayIndependent() {
        val base=ThemePreset(id="user",name="Test")
        val light=base.withColor(ColorRole.MAIN,false,"123456")
        val dark=light.withColor(ColorRole.MAIN,true,"ABCDEF")
        assertEquals("123456",dark.hex(ColorRole.MAIN,false))
        assertEquals("ABCDEF",dark.hex(ColorRole.MAIN,true))
        assertEquals(ColorRole.MAIN.dark,light.hex(ColorRole.MAIN,true))
        val shared=dark.withSharedColor(ColorRole.ICON,"445566")
        assertEquals("445566",shared.hex(ColorRole.ICON,false))
        assertEquals("445566",shared.hex(ColorRole.ICON,true))
        assertEquals("123456",shared.hex(ColorRole.MAIN,false))
        assertEquals("ABCDEF",shared.hex(ColorRole.MAIN,true))
    }
}
