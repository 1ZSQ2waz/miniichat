package com.miniichat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Material 3 tonal fallback for Android 10 and other devices without dynamic color.
 * Android 12+ can derive the palette from the system wallpaper.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF605A8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF),
    onPrimaryContainer = Color(0xFF1C1944),
    secondary = Color(0xFF625D71),
    onSecondary = Color.White,
    background = Color(0xFFE8E6E1),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFF1EFEA),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFDED9E3),
    surfaceDim = Color(0xFFDAD7D2),
    surfaceBright = Color(0xFFF2F0EC),
    surfaceContainerLowest = Color(0xFFF5F3EE),
    surfaceContainerLow = Color(0xFFEEECE7),
    surfaceContainer = Color(0xFFE7E4E0),
    surfaceContainerHigh = Color(0xFFE1DEDA),
    surfaceContainerHighest = Color(0xFFDAD7D3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

// Tightened dark scheme: lighter onSurfaceVariant for legibility, slightly bolder
// surface contrast so subtle UI (chips, dividers, hint text) reads cleanly.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8C2FF),
    onPrimary = Color(0xFF302B5C),
    primaryContainer = Color(0xFF484371),
    onPrimaryContainer = Color(0xFFE5DFFF),
    secondary = Color(0xFFCBC4D8),
    onSecondary = Color(0xFF332E41),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE7E1E9),
    surface = Color(0xFF211F26),
    onSurface = Color(0xFFE7E1E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

@Composable
fun MiniiChatTheme(
    themeMode: String = "system", // system | light | dark
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx)
            else dynamicLightColorScheme(ctx).copy(
                background = LightColors.background,
                surface = LightColors.surface,
                surfaceVariant = LightColors.surfaceVariant,
                surfaceDim = LightColors.surfaceDim,
                surfaceBright = LightColors.surfaceBright,
                surfaceContainerLowest = LightColors.surfaceContainerLowest,
                surfaceContainerLow = LightColors.surfaceContainerLow,
                surfaceContainer = LightColors.surfaceContainer,
                surfaceContainerHigh = LightColors.surfaceContainerHigh,
                surfaceContainerHighest = LightColors.surfaceContainerHighest
            )
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}
