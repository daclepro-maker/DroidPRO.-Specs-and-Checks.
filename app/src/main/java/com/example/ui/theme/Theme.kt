package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00A3FF),       // Neon Tech Blue
    secondary = Color(0xFF10B981),     // Safe Green
    tertiary = Color(0xFFF59E0B),      // Alert Orange
    background = Color(0xFF0B0F19),    // Cosmic Deep Slate
    surface = Color(0xFF161C2C),       // Darker Secondary Navy
    onBackground = Color(0xFFF9FAFB),  // Vivid White-Gray
    onSurface = Color(0xFFE5E7EB),     // Mid Grays
    primaryContainer = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF064E3B),
    surfaceVariant = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0082D9),
    secondary = Color(0xFF059669),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF374151),
    primaryContainer = Color(0xFFE0F2FE),
    secondaryContainer = Color(0xFFD1FAE5),
    surfaceVariant = Color(0xFFE5E7EB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep consistent modern style, dynamicColor true can be used or overridden
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
