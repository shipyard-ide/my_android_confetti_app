package com.example.myandroidconfettiapp.ui.theme

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
    primary = Color(0xFFFF6B6B),
    secondary = Color(0xFF4ECDC4),
    tertiary = Color(0xFFFFE66D),
    background = Color(0xFF18181B),
    surface = Color(0xFF27272A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF6B6B),
    secondary = Color(0xFF4ECDC4),
    tertiary = Color(0xFFFFE66D),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun MyAndroidConfettiAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
