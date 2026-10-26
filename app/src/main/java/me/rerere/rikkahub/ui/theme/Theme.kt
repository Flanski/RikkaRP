package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import me.rerere.rikkahub.ui.theme.sharp.SharpPalette
import me.rerere.rikkahub.ui.theme.sharp.SharpShapes
import me.rerere.rikkahub.ui.theme.sharp.SharpTheme
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.utils.getActivity

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * RikkaRP: 与 Material 3 Expressive 的唯一差别是 spatial 阻尼。
 *
 * material3 的 ExpressiveMotionSchemeImpl 里 defaultSpatialSpec 是
 * dampingRatio = 0.6 的弹簧（javap 读常量池确认），属欠阻尼，必然过冲。
 * 而形状动画 rememberAnimatedShape 用线性插值：
 *     corner = a * (1 - t) + b * t
 * 一旦 t 越过 1，且 a、b 一个是非零一个是零，结果就是负角半径，
 * 抛 "Corner size in Px can't be negative" 直接把 App 打崩。
 *
 * 临界阻尼（dampingRatio = 1.0）在数学上严格不过冲，t 恒在 [0, 1] 内。
 * 代价是失去位移/尺寸的过冲弹跳；本项目是直角硬边风格，影响不大。
 * 若以后要恢复弹跳，必须同时保证任何 CornerBasedShape 的相邻状态
 * 不做「非零 -> 零」的插值，否则此崩溃会复现。
 */
private val SharpMotionScheme = object : MotionScheme {
    // RikkaRP: 全局过渡时长统一 400ms（用户指定）。
    //
    // 原先这里是临界阻尼弹簧（dampingRatio = 1f + 各自 stiffness）。弹簧的「停止时刻」
    // 由 stiffness 决定、无法精确指定时长（同样的 stiffness 对不同距离的位移耗时不同），
    // 所以改为 tween(SharpMotion.LinearMs) —— 时长恒定、可预期。
    // tween 的进度恒在 [0, 1] 内，同样不会过冲，因此上面 KDoc 提到的
    // 「负角半径崩溃」不会因为这次改动而复现。
    //
    // 六个槽位的时长一律相同（用户要求全量 400ms）。按 Material 语义本可以让
    // fast/default/slow 有别，但那是「弹簧刚度分层」，换成固定时长后若再分档，
    // 就与「全量 400ms」相违，故统一。
    // 缓动沿用 SharpUI 的 cubic-bezier(0.4, 0, 0.2, 1)（= FastOutSlowIn）。
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        tween(SharpMotion.LinearMs, easing = SharpMotion.dialogEasing)
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    // RikkaRP: 统一走 Sharp 设计系统，不再使用上游预设主题（保留 settings 读取以免
    // 触发设置结构变更；如需恢复上游主题，把下面一行换回原来的 when 分支即可）。
    val colorScheme = SharpTheme.colorScheme(
        dark = darkTheme,
        saturated = SharpPalette.saturated,
    )
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // 更新状态栏图标颜色
    val view = LocalView.current
    val activity = view.context.getActivity()
    // 浮窗可能使用 Application Context，没有可更新系统栏的 Activity。
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity, darkTheme) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {
                // 嵌套主题（如终端的深色主题）退出时恢复原有系统栏图标颜色。
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = Typography,
            shapes = SharpShapes,
            content = content,
            motionScheme = SharpMotionScheme
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
