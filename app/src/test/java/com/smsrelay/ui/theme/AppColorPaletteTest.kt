package com.smsrelay.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppColorPaletteTest {
    @Test
    fun `missing or unknown preference uses monochrome`() {
        assertEquals(AppColorPalette.MONOCHROME, AppColorPalette.fromId(null))
        assertEquals(AppColorPalette.MONOCHROME, AppColorPalette.fromId("unknown"))
    }

    @Test
    fun `every palette can be restored from its stored id`() {
        AppColorPalette.entries.forEach { palette ->
            assertEquals(palette, AppColorPalette.fromId(palette.id))
        }
    }

    @Test
    fun `monochrome preserves the original scheme`() {
        val light = lightColorScheme()
        val dark = darkColorScheme()

        assertSame(light, AppColorPalette.MONOCHROME.applyTo(light, false))
        assertSame(dark, AppColorPalette.MONOCHROME.applyTo(dark, true))
    }

    @Test
    fun `accent palettes adapt to light and dark without changing status colors`() {
        AppColorPalette.entries.filter { it != AppColorPalette.MONOCHROME }.forEach { palette ->
            listOf(false to lightColorScheme(), true to darkColorScheme()).forEach { (darkMode, base) ->
                val colors = palette.applyTo(base, darkMode)

                assertEquals(if (darkMode) palette.darkAccent else palette.lightAccent, colors.primary)
                assertEquals(if (darkMode) Color.Black else Color.White, colors.onPrimary)
                assertEquals(if (darkMode) palette.darkContainer else palette.lightContainer, colors.primaryContainer)
                assertEquals(if (darkMode) Color.White else Color.Black, colors.onPrimaryContainer)
                assertEquals(colors.primary, colors.secondary)
                assertEquals(colors.primary, colors.surfaceTint)
                assertEquals(base.error, colors.error)
                assertEquals(base.onError, colors.onError)
                assertEquals(base.tertiary, colors.tertiary)
                assertEquals(base.onTertiary, colors.onTertiary)
                assertEquals(base.surface, colors.surface)
                assertEquals(base.onSurface, colors.onSurface)
            }
        }
    }
}
