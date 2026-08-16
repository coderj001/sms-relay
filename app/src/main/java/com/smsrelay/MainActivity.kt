package com.smsrelay

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.smsrelay.ui.SmsRelayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsRelayTheme() }
    }
}

@Composable
private fun SmsRelayTheme() {
    val darkMode = isSystemInDarkTheme()
    val colors = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF111111),
            onPrimaryContainer = Color(0xFFE8E8E8),
            secondary = Color(0xFF999999),
            onSecondary = Color(0xFF000000),
            secondaryContainer = Color(0xFF111111),
            onSecondaryContainer = Color(0xFFE8E8E8),
            tertiary = Color(0xFFD4A843),
            onTertiary = Color(0xFF000000),
            tertiaryContainer = Color(0xFF111111),
            onTertiaryContainer = Color(0xFFE8E8E8),
            error = Color(0xFFD71921),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFF111111),
            onErrorContainer = Color(0xFFD71921),
            background = Color(0xFF000000),
            onBackground = Color(0xFFE8E8E8),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFE8E8E8),
            surfaceVariant = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF999999),
            outline = Color(0xFF333333),
            outlineVariant = Color(0xFF222222),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF000000),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF1A1A1A),
            secondary = Color(0xFF666666),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFFFFF),
            onSecondaryContainer = Color(0xFF1A1A1A),
            tertiary = Color(0xFFD4A843),
            onTertiary = Color(0xFF000000),
            tertiaryContainer = Color(0xFFFFFFFF),
            onTertiaryContainer = Color(0xFF1A1A1A),
            error = Color(0xFFD71921),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFFFFF),
            onErrorContainer = Color(0xFFD71921),
            background = Color(0xFFF5F5F5),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFF5F5F5),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF666666),
            outline = Color(0xFFCCCCCC),
            outlineVariant = Color(0xFFE8E8E8),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = NothingTypography) { SmsRelayApp() }
}

/*
 * Production font mapping: Space Grotesk for UI and Space Mono for data labels.
 * These system fallbacks keep the app offline-only until the font files are bundled.
 */
private val NothingTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.9.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.8.sp),
)
