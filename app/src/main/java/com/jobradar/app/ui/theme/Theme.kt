package com.jobradar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = JobRadarPrimary,
    secondary = JobRadarAccent,
    error = JobRadarError,
    background = JobRadarSurfaceLight,
    surface = JobRadarCardBg,
)

@Composable
fun JobRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
