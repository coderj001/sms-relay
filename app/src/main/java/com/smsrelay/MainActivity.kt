package com.smsrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.smsrelay.ui.SmsRelayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsRelayTheme() }
    }
}

@Composable
private fun SmsRelayTheme() {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF7BD8A2),
            secondary = Color(0xFFAACBB8),
            tertiary = Color(0xFFF2C66D),
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF146C3A),
            secondary = Color(0xFF496457),
            tertiary = Color(0xFF775A00),
            error = Color(0xFFBA1A1A),
        )
    }
    MaterialTheme(colorScheme = colors) { SmsRelayApp() }
}
