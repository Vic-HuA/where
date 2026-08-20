package com.vichua.where.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pencil 原型使用的页面背景色。 */
val WhereBackgroundColor = Color(0xFFF7F7F5)

/** Pencil 原型使用的主要绿色。 */
val WherePrimaryColor = Color(0xFF2F6F64)

/** Pencil 原型使用的选中胶囊背景色。 */
val WhereSelectedContainerColor = Color(0xFFE5F0ED)

/** Pencil 原型使用的主要文字色。 */
val WherePrimaryTextColor = Color(0xFF1D1D1F)

/** Pencil 原型使用的次要文字色。 */
val WhereSecondaryTextColor = Color(0xFF6B7280)

/** 输入框和未选中卡片使用的白色表面。 */
val WhereSurfaceColor = Color(0xFFFFFFFF)

/** 输入框和卡片使用的弱边框色。 */
val WhereOutlineColor = Color(0xFFE1E3E0)

private val WhereColorScheme = lightColorScheme(
    primary = WherePrimaryColor,
    onPrimary = WhereSurfaceColor,
    primaryContainer = WhereSelectedContainerColor,
    onPrimaryContainer = WherePrimaryColor,
    background = WhereBackgroundColor,
    onBackground = WherePrimaryTextColor,
    surface = WhereSurfaceColor,
    onSurface = WherePrimaryTextColor,
    surfaceVariant = WhereSurfaceColor,
    onSurfaceVariant = WhereSecondaryTextColor,
    outline = WhereOutlineColor,
    error = Color(0xFFB3261E),
)

private val WhereTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
)

/**
 * 应用 Pencil 原型的共享颜色和字体规范。
 *
 * 高对比度和适老字号只改变展示，不改家庭数据。
 *
 * @param highContrast 是否使用更高对比度的配色。
 * @param elderFriendly 是否放大主要字号。
 * @param content 使用统一主题渲染的页面内容。
 */
@Composable
fun WhereTheme(
    highContrast: Boolean = false,
    elderFriendly: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (highContrast) {
            WhereHighContrastColorScheme
        } else {
            WhereColorScheme
        },
        typography = if (elderFriendly) {
            WhereElderTypography
        } else {
            WhereTypography
        },
        content = content,
    )
}

private val WhereHighContrastColorScheme = lightColorScheme(
    primary = Color(0xFF145046),
    onPrimary = WhereSurfaceColor,
    primaryContainer = Color(0xFFD7EBE6),
    onPrimaryContainer = Color(0xFF0B2A25),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF222222),
    outline = Color(0xFF111111),
    error = Color(0xFF8C1D18),
)

private val WhereElderTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    ),
)
