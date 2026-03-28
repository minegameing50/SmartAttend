package com.SmartAttend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartAttendColors = darkColorScheme(
    primary = Accent,
    secondary = AccentBlue,
    background = Ink,
    surface = Panel,
    surfaceVariant = PanelRaised,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Text,
    onSurface = Text,
    onSurfaceVariant = Muted,
    error = Danger
)

@Composable
fun SmartAttendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartAttendColors,
        typography = Typography,
        content = content
    )
}
