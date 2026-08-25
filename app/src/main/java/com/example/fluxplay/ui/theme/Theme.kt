package com.example.fluxplay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.fluxplay.data.model.AppSettings

@Composable
fun FluxplayTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val isDark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val primary = parseHexColor(settings.primaryColorHex, FluxPrimaryDefault)
    val accent = parseHexColor(settings.accentColorHex, FluxAccentDefault)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = FluxPrimary2Default,
            tertiary = accent,
            background = FluxDarkBg,
            surface = FluxDarkSurface,
            surfaceVariant = FluxDarkVariant,
            surfaceContainer = FluxDarkContainer,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = FluxDarkText,
            onSurface = FluxDarkText,
            onSurfaceVariant = FluxDarkSubtle,
            outline = FluxDarkOutline
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = FluxPrimary2Default,
            tertiary = accent,
            background = FluxLightBg,
            surface = FluxLightSurface,
            surfaceVariant = FluxLightVariant,
            surfaceContainer = FluxLightContainer,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = FluxLightText,
            onSurface = FluxLightText,
            onSurfaceVariant = FluxLightSubtle,
            outline = FluxLightOutline
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FluxTypography,
        content = content
    )
}
