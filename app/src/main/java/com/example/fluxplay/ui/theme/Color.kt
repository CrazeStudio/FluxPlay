package com.example.fluxplay.ui.theme

import androidx.compose.ui.graphics.Color

val FluxDarkBg = Color(0xFF0D0E14)
val FluxDarkSurface = Color(0xFF151620)
val FluxDarkContainer = Color(0xFF191A26)
val FluxDarkVariant = Color(0xFF1E1F2D)
val FluxDarkText = Color(0xFFF3F4F6)
val FluxDarkSubtle = Color(0xFF9CA3AF)
val FluxDarkOutline = Color(0x1FFFFFFF)

val FluxLightBg = Color(0xFFF5F5F7)
val FluxLightSurface = Color(0xFFFFFFFF)
val FluxLightContainer = Color(0xFFEEEEF4)
val FluxLightVariant = Color(0xFFE5E7EB)
val FluxLightText = Color(0xFF111827)
val FluxLightSubtle = Color(0xFF6B7280)
val FluxLightOutline = Color(0x1F000000)

val FluxPrimaryDefault = Color(0xFFA78BFA)
val FluxPrimary2Default = Color(0xFF6366F1)
val FluxAccentDefault = Color(0xFFF43F5E)

val FluxSuccess = Color(0xFF34D399)
val FluxWarning = Color(0xFFFBBF24)
val FluxError = Color(0xFFFB7185)

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> (0xFF000000 or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}
