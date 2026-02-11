package com.batterysales.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD84315), // Deep Orange for primary actions
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF3E2723),

    secondary = Color(0xFFFBC02D), // Golden Yellow
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFF9C4),
    onSecondaryContainer = Color(0xFF33691E),

    tertiary = Color(0xFF2E7D32), // Green
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF1B5E20),

    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),

    background = Color(0xFFF5F5F5), // Light gray background for contrast
    onBackground = Color(0xFF121212), // High contrast text

    surface = Color.White,
    onSurface = Color(0xFF121212), // High contrast text on surface
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF424242),

    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFF001A41),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDEE9FF),

    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF003D25),
    secondaryContainer = Color(0xFF059669),
    onSecondaryContainer = Color(0xFFC8F7DC),

    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3E2200),
    tertiaryContainer = Color(0xFFD97706),
    onTertiaryContainer = Color(0xFFFFDDB3),

    error = Color(0xFFF87171),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFFDC2626),
    onErrorContainer = Color(0xFFFCDEDE),

    background = Color(0xFF111827),
    onBackground = Color(0xFFF3F4F6),

    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB),

    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF6B7280),
    scrim = Color.Black
)

val LocalInputTextStyle = staticCompositionLocalOf { TextStyle.Default }

@Composable
fun BatterySalesManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSizeScale: Float = 1.0f,
    isBold: Boolean = false,
    scaleInputText: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = getTypography(fontSizeScale, isBold)

    val inputScale = if (scaleInputText) fontSizeScale else 1.0f
    val inputFontWeight = if (isBold && scaleInputText) FontWeight.Bold else FontWeight.Normal
    val inputTextStyle = TextStyle(
        fontSize = (16 * inputScale).sp,
        fontWeight = inputFontWeight
    )

    CompositionLocalProvider(LocalInputTextStyle provides inputTextStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = BatterySalesManagerShapes,
            content = content
        )
    }
}
