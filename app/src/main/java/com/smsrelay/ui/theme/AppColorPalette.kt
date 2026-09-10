package com.smsrelay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

enum class AppColorPalette(
    val id: String,
    val label: String,
    val lightAccent: Color,
    val darkAccent: Color,
    val lightContainer: Color,
    val darkContainer: Color,
) {
    MONOCHROME("monochrome", "Monochrome", Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF111111)),
    OCEAN("ocean", "Ocean", Color(0xFF1D4ED8), Color(0xFF93C5FD), Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
    TEAL("teal", "Teal", Color(0xFF0F766E), Color(0xFF5EEAD4), Color(0xFFCCFBF1), Color(0xFF134E4A)),
    VIOLET("violet", "Violet", Color(0xFF6D28D9), Color(0xFFC4B5FD), Color(0xFFEDE9FE), Color(0xFF4C1D95)),
    ROSE("rose", "Rose", Color(0xFFBE185D), Color(0xFFFDA4AF), Color(0xFFFCE7F3), Color(0xFF831843)),
    AMBER("amber", "Amber", Color(0xFF92400E), Color(0xFFFCD34D), Color(0xFFFEF3C7), Color(0xFF78350F));

    fun applyTo(colors: ColorScheme, darkMode: Boolean): ColorScheme {
        if (this == MONOCHROME) return colors
        val accent = if (darkMode) darkAccent else lightAccent
        val onAccent = if (darkMode) Color.Black else Color.White
        val container = if (darkMode) darkContainer else lightContainer
        val onContainer = if (darkMode) Color.White else Color.Black
        return colors.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            inversePrimary = if (darkMode) lightAccent else darkAccent,
            surfaceTint = accent,
        )
    }

    companion object {
        fun fromId(id: String?): AppColorPalette = entries.firstOrNull { it.id == id } ?: MONOCHROME
    }
}
