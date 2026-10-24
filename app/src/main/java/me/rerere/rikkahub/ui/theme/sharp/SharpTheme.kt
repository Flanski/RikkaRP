package me.rerere.rikkahub.ui.theme.sharp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 把 SharpUI 的蓝白语言映射到 Material 3 的 ColorScheme。
 *
 * 只做映射，不改任何业务语义：页面里原有的 MaterialTheme.colorScheme.xxx 全部继续可用，
 * 只是取到的颜色变成 RikkaRP 的一套。这样 89 个页面不必逐个改就能整体换肤。
 *
 * 两档模式：
 *  · 中性（默认）—— 页面底为冷灰白/近黑，蓝只在控件与强调处。长文本阅读更稳。
 *  · 饱和（saturated=true）—— SharpUI 原版深蓝底 #1E5A96，控件浮在同色系上。
 */
object SharpTheme {

    fun colorScheme(dark: Boolean, saturated: Boolean = false): ColorScheme =
        if (dark) dark(saturated) else light(saturated)

    private fun light(saturated: Boolean): ColorScheme {
        val bg = if (saturated) SharpColors.LegacyBackground else SharpColors.NeutralLightBg
        val surface = if (saturated) SharpColors.LegacyBackground else SharpColors.NeutralLightSurface
        val surfaceDim = if (saturated) SharpColors.LegacyBackgroundDim else SharpColors.NeutralLightSurfaceDim
        val onBg = if (saturated) SharpColors.White else SharpColors.Ink
        val onBgDim = if (saturated) SharpColors.White85 else SharpColors.Ink70
        val outline = if (saturated) SharpColors.White30 else SharpColors.Ink30
        val outlineVariant = if (saturated) SharpColors.White10 else SharpColors.Ink10

        return lightColorScheme(
            primary = SharpColors.Blue,
            onPrimary = SharpColors.White,
            primaryContainer = if (saturated) SharpColors.BlueDeep else Color(0xFFD6E9FF),
            onPrimaryContainer = if (saturated) SharpColors.White else Color(0xFF0B3A66),
            inversePrimary = SharpColors.BlueLight,

            secondary = if (saturated) SharpColors.White85 else Color(0xFF55657A),
            onSecondary = if (saturated) SharpColors.BlueDeep else SharpColors.White,
            secondaryContainer = if (saturated) SharpColors.White20 else Color(0xFFDDE5EF),
            onSecondaryContainer = if (saturated) SharpColors.White else Color(0xFF2A3644),

            tertiary = SharpColors.DialogWarning,
            onTertiary = SharpColors.OnWarning,
            tertiaryContainer = Color(0xFFFDEFD8),
            onTertiaryContainer = Color(0xFF6B4A10),

            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = surfaceDim,
            onSurfaceVariant = onBgDim,
            surfaceTint = SharpColors.Blue,
            inverseSurface = if (saturated) SharpColors.White else SharpColors.Ink,
            inverseOnSurface = if (saturated) SharpColors.BlueDeep else SharpColors.White,

            error = SharpColors.DialogError,
            onError = SharpColors.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF6B1B16),

            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0x80000000),

            surfaceBright = if (saturated) SharpColors.BlueLight else SharpColors.White,
            surfaceDim = surfaceDim,
            surfaceContainerLowest = if (saturated) SharpColors.LegacyBackgroundDim else SharpColors.White,
            surfaceContainerLow = if (saturated) SharpColors.LegacyBackground else SharpColors.NeutralLightBg,
            surfaceContainer = surfaceDim,
            surfaceContainerHigh = surfaceDim,
            surfaceContainerHighest = if (saturated) SharpColors.BlueDeep else Color(0xFFD8DFE8),
        )
    }

    private fun dark(saturated: Boolean): ColorScheme {
        val bg = if (saturated) SharpColors.LegacyBackgroundDim else SharpColors.NeutralDarkBg
        val surface = if (saturated) SharpColors.LegacyBackground else SharpColors.NeutralDarkSurface
        val surfaceDim = if (saturated) Color(0xFF123C66) else SharpColors.NeutralDarkSurfaceDim
        val onBg = if (saturated) SharpColors.White else Color(0xFFE6EBF1)
        val onBgDim = if (saturated) SharpColors.White70 else Color(0xFFA5B0BD)
        val outline = if (saturated) SharpColors.White30 else Color(0xFF3A434F)
        val outlineVariant = if (saturated) SharpColors.White10 else Color(0xFF262E38)

        return darkColorScheme(
            primary = SharpColors.BlueLight,
            onPrimary = Color(0xFF06213B),
            primaryContainer = SharpColors.BlueDeep,
            onPrimaryContainer = SharpColors.White,
            inversePrimary = SharpColors.BlueDeep,

            secondary = onBgDim,
            onSecondary = Color(0xFF0E1318),
            secondaryContainer = if (saturated) SharpColors.White20 else Color(0xFF2A323C),
            onSecondaryContainer = onBg,

            tertiary = SharpColors.DialogWarning,
            onTertiary = SharpColors.OnWarning,
            tertiaryContainer = Color(0xFF4A3310),
            onTertiaryContainer = Color(0xFFFFE0B2),

            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = surfaceDim,
            onSurfaceVariant = onBgDim,
            surfaceTint = SharpColors.BlueLight,
            inverseSurface = onBg,
            inverseOnSurface = bg,

            error = Color(0xFFFF7B7B),
            onError = Color(0xFF3A0A08),
            errorContainer = Color(0xFF5A1512),
            onErrorContainer = Color(0xFFFFDAD6),

            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0xB3000000),

            surfaceBright = if (saturated) SharpColors.BlueLight else Color(0xFF2A323C),
            surfaceDim = bg,
            surfaceContainerLowest = if (saturated) SharpColors.LegacyBackgroundDim else Color(0xFF0B0F13),
            surfaceContainerLow = surface,
            surfaceContainer = surfaceDim,
            surfaceContainerHigh = surfaceDim,
            surfaceContainerHighest = if (saturated) SharpColors.BlueDeep else Color(0xFF2A323C),
        )
    }
}
