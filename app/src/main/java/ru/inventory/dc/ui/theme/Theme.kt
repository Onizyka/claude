package ru.inventory.dc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Фирменная палитра в стиле Госкорпорации «Росатом». */
object BrandColors {
    val DarkBlue = Color(0xFF003274)
    val Blue = Color(0xFF025EA1)
    val LightBlue = Color(0xFF6CACE4)
    val Cyan = Color(0xFF56C7DA)
    val Gray = Color(0xFF6E7782)
    val LightGray = Color(0xFFD8DDE3)
    val Background = Color(0xFFF2F5F9)
    val Text = Color(0xFF1B2B3F)
    val Success = Color(0xFF1E8C5A)
    val Error = Color(0xFFD7263D)
}

private val LightColors = lightColorScheme(
    primary = BrandColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9F7),
    onPrimaryContainer = BrandColors.DarkBlue,
    inversePrimary = BrandColors.LightBlue,
    secondary = BrandColors.DarkBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EFFA),
    onSecondaryContainer = BrandColors.DarkBlue,
    tertiary = BrandColors.Cyan,
    onTertiary = Color.White,
    background = BrandColors.Background,
    onBackground = BrandColors.Text,
    surface = Color.White,
    onSurface = BrandColors.Text,
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = BrandColors.Gray,
    surfaceTint = BrandColors.Blue,
    inverseSurface = BrandColors.DarkBlue,
    inverseOnSurface = Color.White,
    error = BrandColors.Error,
    onError = Color.White,
    outline = Color(0xFFB4BFCC),
    outlineVariant = BrandColors.LightGray,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE3EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF0F4F8),
    surfaceContainerHigh = Color(0xFFEAEFF5),
    surfaceContainerHighest = Color(0xFFE4EAF1),
)

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun DcInventoryTheme(content: @Composable () -> Unit) {
    // Корпоративный стиль — всегда светлая тема.
    MaterialTheme(colorScheme = LightColors, typography = AppTypography, content = content)
}
