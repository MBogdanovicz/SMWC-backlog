package com.capivaraec.smwc.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capivaraec.smwc.app.R

/** The app's own palette: a dark night-sky ground with "SMW coin" gold as the accent. */
private val SmwcDark = darkColorScheme(
    primary = Color(0xFFF5C542),
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF3A3320),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFF4FB3FF),
    onSecondary = Color(0xFF071827),
    secondaryContainer = Color(0xFF13222F),
    onSecondaryContainer = Color(0xFFBFE2FF),
    tertiary = Color(0xFF5FD38D),
    onTertiary = Color(0xFF072014),
    tertiaryContainer = Color(0xFF1A2420),
    onTertiaryContainer = Color(0xFFA8F0C4),
    background = Color(0xFF101218),
    onBackground = Color(0xFFECEEF4),
    surface = Color(0xFF101218),
    onSurface = Color(0xFFECEEF4),
    surfaceVariant = Color(0xFF232736),
    onSurfaceVariant = Color(0xFF9AA1B5),
    surfaceContainerLowest = Color(0xFF0B0C11),
    surfaceContainerLow = Color(0xFF161922),
    surfaceContainer = Color(0xFF181B24),
    surfaceContainerHigh = Color(0xFF1C1F2A),
    surfaceContainerHighest = Color(0xFF2A2E3D),
    outline = Color(0xFF2E3342),
    outlineVariant = Color(0xFF232736),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2D0505),
)

/** The same palette for light mode; the gold is darkened to stay readable on white. */
private val SmwcLight = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE6A3),
    onPrimaryContainer = Color(0xFF2B1F00),
    secondary = Color(0xFF0F6AA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6ECFF),
    onSecondaryContainer = Color(0xFF052A45),
    tertiary = Color(0xFF1E7A47),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF5DD),
    onTertiaryContainer = Color(0xFF062A15),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF15171E),
    surface = Color(0xFFF7F6F2),
    onSurface = Color(0xFF15171E),
    surfaceVariant = Color(0xFFE7E6E0),
    onSurfaceVariant = Color(0xFF555B6B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F0EB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDECE6),
    surfaceContainerHighest = Color(0xFFE3E2DC),
    outline = Color(0xFFCFCDC5),
    outlineVariant = Color(0xFFE3E2DC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

/** Plus Jakarta Sans (variable font, bundled; SIL Open Font License). */
private val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private fun TextStyle.jakarta(weight: FontWeight? = null) = copy(fontFamily = Jakarta, fontWeight = weight ?: fontWeight)

private val SmwcTypography = Typography().run {
    copy(
        displayLarge = displayLarge.jakarta(FontWeight.ExtraBold),
        displayMedium = displayMedium.jakarta(FontWeight.ExtraBold),
        displaySmall = displaySmall.jakarta(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.jakarta(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.jakarta(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.jakarta(FontWeight.ExtraBold),
        titleLarge = titleLarge.jakarta(FontWeight.Bold),
        titleMedium = titleMedium.jakarta(FontWeight.Bold),
        titleSmall = titleSmall.jakarta(FontWeight.Bold),
        bodyLarge = bodyLarge.jakarta(),
        bodyMedium = bodyMedium.jakarta(),
        bodySmall = bodySmall.jakarta(),
        labelLarge = labelLarge.jakarta(FontWeight.Bold),
        labelMedium = labelMedium.jakarta(FontWeight.SemiBold),
        labelSmall = labelSmall.jakarta(FontWeight.SemiBold),
    )
}

/** Rounded corners rather than Material's pills, for buttons and fields alike. */
private val SmwcShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Colors outside the Material scheme: one per SMW Central difficulty, plus whether the theme is dark. */
@Immutable
data class SmwcExtraColors(val dark: Boolean) {
    /** Background and text color of a difficulty chip, from green (Newcomer) to purple (Grandmaster). */
    fun difficulty(id: String?): Pair<Color, Color> {
        val (darkPair, lightPair) = DIFFICULTY[id] ?: return if (dark) Color(0xFF232736) to Color(0xFFC9CEDC) else Color(0xFFE7E6E0) to Color(0xFF3B4050)
        return if (dark) darkPair else lightPair
    }

    private companion object {
        val DIFFICULTY = mapOf(
            "diff_1" to ((Color(0xFF1F3B2A) to Color(0xFF7FE3A1)) to (Color(0xFFD3F3DF) to Color(0xFF145C33))),
            "diff_2" to ((Color(0xFF1D3440) to Color(0xFF7FD0F0)) to (Color(0xFFD3EEF9) to Color(0xFF0B4E6A))),
            "diff_3" to ((Color(0xFF2E2F46) to Color(0xFFAEB4FF)) to (Color(0xFFE2E4FF) to Color(0xFF2E3591))),
            "diff_4" to ((Color(0xFF3D3218) to Color(0xFFF5C542)) to (Color(0xFFFFEDB8) to Color(0xFF6B4E00))),
            "diff_5" to ((Color(0xFF43261A) to Color(0xFFFF9D6B)) to (Color(0xFFFFE0D1) to Color(0xFF8A3510))),
            "diff_6" to ((Color(0xFF45202A) to Color(0xFFFF7F96)) to (Color(0xFFFFDDE4) to Color(0xFF8C1B33))),
            "diff_7" to ((Color(0xFF3A1F45) to Color(0xFFD99BFF)) to (Color(0xFFF1DDFB) to Color(0xFF64238A))),
        )
    }
}

val LocalSmwcColors = staticCompositionLocalOf { SmwcExtraColors(dark = true) }

/**
 * The app theme: its own palette and font, or [useSystemColors] for Material You (colors from
 * the wallpaper). Light or dark follows the system either way.
 */
@Composable
fun SmwcTheme(useSystemColors: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors: ColorScheme = when {
        useSystemColors -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> SmwcDark
        else -> SmwcLight
    }
    CompositionLocalProvider(LocalSmwcColors provides SmwcExtraColors(dark)) {
        MaterialTheme(colorScheme = colors, typography = SmwcTypography, shapes = SmwcShapes, content = content)
    }
}
