package com.example.fluxplay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.fluxplay.data.model.AppThemeMode

fun getThemeColorScheme(mode: AppThemeMode): ColorScheme {
    return when (mode) {
        AppThemeMode.AMOLED_MIDNIGHT -> darkColorScheme(
            primary = FluxPrimary,
            onPrimary = FluxBgDark,
            primaryContainer = FluxPrimaryDark,
            onPrimaryContainer = FluxTextPrimary,
            secondary = FluxSecondary,
            onSecondary = FluxBgDark,
            tertiary = FluxAccent,
            background = FluxBgDark,
            onBackground = FluxTextPrimary,
            surface = FluxSurfaceDark,
            onSurface = FluxTextPrimary,
            surfaceVariant = FluxCardDark,
            onSurfaceVariant = FluxTextSecondary,
            outline = FluxCardBorder
        )
        AppThemeMode.CYBERPUNK_NEON -> darkColorScheme(
            primary = CyberpunkPrimary,
            onPrimary = CyberpunkBg,
            primaryContainer = CyberpunkSecondary,
            onPrimaryContainer = FluxTextPrimary,
            secondary = CyberpunkSecondary,
            onSecondary = CyberpunkBg,
            tertiary = CyberpunkAccent,
            background = CyberpunkBg,
            onBackground = FluxTextPrimary,
            surface = CyberpunkSurface,
            onSurface = FluxTextPrimary,
            surfaceVariant = CyberpunkCard,
            onSurfaceVariant = FluxTextSecondary,
            outline = CyberpunkBorder
        )
        AppThemeMode.CRIMSON_FLAME -> darkColorScheme(
            primary = CrimsonPrimary,
            onPrimary = CrimsonBg,
            primaryContainer = CrimsonAccent,
            onPrimaryContainer = FluxTextPrimary,
            secondary = CrimsonSecondary,
            onSecondary = CrimsonBg,
            tertiary = CrimsonAccent,
            background = CrimsonBg,
            onBackground = FluxTextPrimary,
            surface = CrimsonSurface,
            onSurface = FluxTextPrimary,
            surfaceVariant = CrimsonCard,
            onSurfaceVariant = FluxTextSecondary,
            outline = CrimsonBorder
        )
        AppThemeMode.EMERALD_MATRIX -> darkColorScheme(
            primary = EmeraldPrimary,
            onPrimary = EmeraldBg,
            primaryContainer = EmeraldSecondary,
            onPrimaryContainer = FluxTextPrimary,
            secondary = EmeraldSecondary,
            onSecondary = EmeraldBg,
            tertiary = EmeraldAccent,
            background = EmeraldBg,
            onBackground = FluxTextPrimary,
            surface = EmeraldSurface,
            onSurface = FluxTextPrimary,
            surfaceVariant = EmeraldCard,
            onSurfaceVariant = FluxTextSecondary,
            outline = EmeraldBorder
        )
        AppThemeMode.SUNSET_GOLD -> darkColorScheme(
            primary = SunsetPrimary,
            onPrimary = SunsetBg,
            primaryContainer = SunsetSecondary,
            onPrimaryContainer = FluxTextPrimary,
            secondary = SunsetSecondary,
            onSecondary = SunsetBg,
            tertiary = SunsetAccent,
            background = SunsetBg,
            onBackground = FluxTextPrimary,
            surface = SunsetSurface,
            onSurface = FluxTextPrimary,
            surfaceVariant = SunsetCard,
            onSurfaceVariant = FluxTextSecondary,
            outline = SunsetBorder
        )
        AppThemeMode.NORDIC_SLATE -> darkColorScheme(
            primary = NordicPrimary,
            onPrimary = NordicBg,
            primaryContainer = NordicSecondary,
            onPrimaryContainer = FluxTextPrimary,
            secondary = NordicSecondary,
            onSecondary = NordicBg,
            tertiary = NordicAccent,
            background = NordicBg,
            onBackground = FluxTextPrimary,
            surface = NordicSurface,
            onSurface = FluxTextPrimary,
            surfaceVariant = NordicCard,
            onSurfaceVariant = FluxTextSecondary,
            outline = NordicBorder
        )
    }
}

@Composable
fun FluxplayTheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED_MIDNIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = getThemeColorScheme(themeMode)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
