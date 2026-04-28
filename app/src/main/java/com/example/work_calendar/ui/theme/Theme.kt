package com.example.work_calendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WorkCalendarDarkScheme = darkColorScheme(
    primary = Color(0xFF42A5F5),
    onPrimary = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFEFEFEF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEFEFEF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    tertiary = Color(0xFFEC407A),
    outline = Color(0xFF424242),
)

@Composable
fun WorkcalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorkCalendarDarkScheme,
        typography = Typography,
        content = content,
    )
}
