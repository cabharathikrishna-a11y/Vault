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
    primary = VaultPrimaryCyanLight,
    onPrimary = DeepVaultNavy,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = VaultAccentEmerald,
    onSecondary = DeepVaultNavy,
    tertiary = VaultAccentGold,
    background = DeepVaultNavy,
    onBackground = DarkTextPrimary,
    surface = VaultSurfaceDark,
    onSurface = DarkTextPrimary,
    surfaceVariant = VaultSurfaceVariantDark,
    onSurfaceVariant = DarkTextSecondary,
    outline = VaultBorderDark,
    error = VaultDangerRose
)

private val LightColorScheme = lightColorScheme(
    primary = VaultPrimaryCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = VaultAccentEmerald,
    onSecondary = Color.White,
    tertiary = VaultAccentGold,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = VaultDangerRose
)

@Composable
fun FamilyVaultTheme(
    darkModeSetting: String = "system", // "system", "dark", "light"
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkModeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
