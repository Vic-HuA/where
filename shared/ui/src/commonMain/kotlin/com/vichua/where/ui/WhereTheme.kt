package com.vichua.where.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 界面共用的一套颜色和描边。高对比度必须换这套值，否则浅描边在强光下几乎看不见。
 */
data class WherePalette(
    val background: Color,
    val primary: Color,
    val selectedContainer: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val surface: Color,
    val outline: Color,
    val softBorder: Color,
    val switchUncheckedTrack: Color,
    val strokeWidth: Dp,
    val emphasizedBorders: Boolean,
)

/** Pencil 原型默认配色。 */
val StandardWherePalette = WherePalette(
    background = Color(0xFFF7F7F5),
    primary = Color(0xFF2F6F64),
    selectedContainer = Color(0xFFE5F0ED),
    primaryText = Color(0xFF1D1D1F),
    secondaryText = Color(0xFF6B7280),
    surface = Color(0xFFFFFFFF),
    outline = Color(0xFFE1E3E0),
    softBorder = Color(0x592F6F64),
    switchUncheckedTrack = Color(0xFFD7DAD8),
    strokeWidth = 1.dp,
    emphasizedBorders = false,
)

/**
 * 高对比度配色：黑字、粗黑边、近黑主色，和默认浅灰绿明显分开。
 */
val HighContrastWherePalette = WherePalette(
    background = Color(0xFFFFFFFF),
    primary = Color(0xFF001C16),
    selectedContainer = Color(0xFF7ED0C0),
    primaryText = Color(0xFF000000),
    secondaryText = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    outline = Color(0xFF000000),
    softBorder = Color(0xFF000000),
    switchUncheckedTrack = Color(0xFF1A1A1A),
    strokeWidth = 2.5.dp,
    emphasizedBorders = true,
)

private val LocalWherePalette = staticCompositionLocalOf { StandardWherePalette }

/** 页面背景色。 */
val WhereBackgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.background

/** 主要绿色。 */
val WherePrimaryColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.primary

/** 选中胶囊背景色。 */
val WhereSelectedContainerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.selectedContainer

/** 主要文字色。 */
val WherePrimaryTextColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.primaryText

/** 次要文字色。 */
val WhereSecondaryTextColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.secondaryText

/** 输入框和未选中卡片使用的表面色。 */
val WhereSurfaceColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.surface

/** 输入框和卡片使用的边框色。 */
val WhereOutlineColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.outline

/** 关闭态开关轨道色。 */
val WhereSwitchUncheckedTrackColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.switchUncheckedTrack

/** 搜索框等浅描边；高对比度改成实黑，避免 35% 透明绿几乎看不见。 */
val WhereSoftBorderColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.softBorder

/** 卡片和输入框描边宽度。 */
val WhereStrokeWidth: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.strokeWidth

/** 高对比度下给原本无边框的卡片补描边。 */
val WhereEmphasizedBorders: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalWherePalette.current.emphasizedBorders

private fun paletteColorScheme(palette: WherePalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.surface,
    primaryContainer = palette.selectedContainer,
    onPrimaryContainer = palette.primary,
    background = palette.background,
    onBackground = palette.primaryText,
    surface = palette.surface,
    onSurface = palette.primaryText,
    surfaceVariant = palette.surface,
    onSurfaceVariant = palette.secondaryText,
    outline = palette.outline,
    error = if (palette === HighContrastWherePalette) {
        Color(0xFF8C1D18)
    } else {
        Color(0xFFB3261E)
    },
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
    val palette = if (highContrast) {
        HighContrastWherePalette
    } else {
        StandardWherePalette
    }
    CompositionLocalProvider(LocalWherePalette provides palette) {
        MaterialTheme(
            colorScheme = paletteColorScheme(palette),
            typography = if (elderFriendly) {
                WhereElderTypography
            } else {
                WhereTypography
            },
            content = content,
        )
    }
}

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
