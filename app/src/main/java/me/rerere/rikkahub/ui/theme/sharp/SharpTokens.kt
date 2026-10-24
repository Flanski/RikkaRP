package me.rerere.rikkahub.ui.theme.sharp

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * RikkaRP 设计令牌 —— 移植自 SharpUI（未发布时期的内部名）的形态语言。
 *
 * 形态语言是完全照搬的：
 *  · 直角：全库圆角 0dp，任何控件不得引入圆角；
 *  · 线框：1dp 描边，分隔线 0.5dp，配色只取白系不同透明度；
 *  · 动效：按下线性 90ms → 松开低阻尼弹簧回弹（果冻）；弹窗 scaleY 先过冲到 1.06 再回 1.0。
 *
 * 底色策略（与 SharpUI 原版唯一的差异，理由见 SharpPalette）：聊天类界面正文阅读量大，
 * 大面积饱和蓝会压过内容，因此把「页面底」压到中性色，蓝色只在控件与强调处出现。
 * SharpPalette.Saturated 保留原版的深蓝底，作为可选的「饱和模式」。
 */
object SharpColors {
    /** 主体蓝：按钮/开关/选中态/强调 */
    val Blue = Color(0xFF409EFF)
    val BlueDeep = Color(0xFF2B7CD3)
    val BlueLight = Color(0xFF5CADFF)

    /** SharpUI 原版页面底（饱和模式用） */
    val LegacyBackground = Color(0xFF1E5A96)
    val LegacyBackgroundDim = Color(0xFF16487A)

    /** 中性模式（默认）：页面底与容器面 */
    val NeutralLightBg = Color(0xFFEEF1F5)
    val NeutralLightSurface = Color(0xFFFFFFFF)
    val NeutralLightSurfaceDim = Color(0xFFE4E9F0)
    val NeutralDarkBg = Color(0xFF101418)
    val NeutralDarkSurface = Color(0xFF181D23)
    val NeutralDarkSurfaceDim = Color(0xFF20262E)

    val White = Color(0xFFFFFFFF)
    val White85 = Color(0xD9FFFFFF)
    val White70 = Color(0xB3FFFFFF)
    val White50 = Color(0x80FFFFFF)
    val White30 = Color(0x4DFFFFFF)
    val White20 = Color(0x33FFFFFF)
    val White10 = Color(0x1AFFFFFF)

    /** 深色描边（中性模式下替代白描边） */
    val Ink = Color(0xFF12161B)
    val Ink85 = Color(0xD912161B)
    val Ink70 = Color(0xB312161B)
    val Ink50 = Color(0x8012161B)
    val Ink30 = Color(0x4D12161B)
    val Ink20 = Color(0x3312161B)
    val Ink10 = Color(0x1A12161B)

    /** 弹窗类型色（照搬 SharpUI / 通知弹窗特效 Demo） */
    val DialogNormal = Color(0xFF409EFF)
    val DialogWarning = Color(0xFFE6A23C)
    val DialogError = Color(0xFFF56C6C)
    /** warning 头部用黑字，其余用白字 */
    val OnWarning = Color(0xFF000000)
}

/** 饱和模式开关：true 时页面底用 SharpUI 原版深蓝。 */
object SharpPalette {
    var saturated: Boolean = false
}

/**
 * 尺寸表。全部数值与 SharpUI 保持一致，改动需同步两侧。
 */
object SharpDimens {
    /** 全局圆角 = 0（直角铁律） */
    val corner = 0.dp

    val border = 1.dp
    val divider = 0.5.dp

    val screenPadding = 16.dp

    val listItemMinHeight = 54.dp
    val listItemPaddingH = 16.dp
    val listItemPaddingV = 10.dp

    val buttonMinHeight = 44.dp
    val buttonPaddingH = 20.dp
    val buttonPaddingV = 10.dp

    val fieldMinHeight = 44.dp
    val fieldPaddingH = 12.dp
    val fieldPaddingV = 10.dp

    val switchWidth = 54.dp
    val switchHeight = 28.dp

    val boxSize = 20.dp
    val boxBorder = 1.5.dp
    val boxInner = 8.dp

    val arrowWidth = 8.dp
    val arrowHeight = 8.dp

    /** 小圆点/角标 */
    val dot = 6.dp

    /** 弹窗：高度占屏 1/3（Demo 原值 33.333vh） */
    const val DIALOG_HEIGHT_RATIO = 0.33333f
    /** 弹窗遮罩 */
    val dialogScrim = Color(0x80000000)
}

/**
 * 动效表。SharpUI 的核心手感都在这里。
 */
object SharpMotion {
    /** 线性过渡基准（250–300ms 区间） */
    const val LinearMs = 260
    val easing: Easing = LinearEasing

    /** 按下用时（比回弹快，手感干脆） */
    const val PressMs = 90

    /** 回弹弹簧：低阻尼 → 过冲后抖动 2–3 次 */
    const val DampingRatio = 0.20f
    const val Stiffness = 2000f

    const val VisibilityThreshold = 0.001f

    /** 按下放大比例 */
    const val PressScale = 1.06f

    /** 开关拖拽时滑块的横向拉伸上限 */
    const val SqueezeScale = 1.14f

    /** 逐字淡入/淡出 */
    const val CharFadeInMs = 170
    const val CharFadeOutMs = 150

    /** 弹窗展开/收起 */
    const val DialogMs = 250
    val dialogEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 弹窗过冲峰值：先放大到该值再弹回 1.0 */
    const val DialogPeak = 1.06f

    /** 页面切换 */
    const val PageMs = 300

    /** 列表逐项进场，每项错开 */
    const val StaggerMs = 28
    /** 进场位移（纵向） */
    val EnterOffset = 12.dp
}

/**
 * 全局直角形状表：交给 MaterialTheme 后，所有 M3 组件（Card/Button/TextField/Dialog/
 * BottomSheet/Snackbar…）一次性变直角，这是覆盖面最大的一个改动点。
 */
val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
    // 下面三个槽位必须一并归零。Material3 的 Shapes 共 8 个槽位，只传前 5 个时
    // 后三个沿用库默认的非零圆角；而 Button/Chip/NavigationDrawerItem 等组件的
    // 按压形变动画会用 rememberAnimatedShape 在「非零」与「零」之间做弹簧插值，
    // 弹簧过冲会把角半径算成负数，抛 "Corner size in Px can't be negative"。
    largeIncreased = RoundedCornerShape(0.dp),
    extraLargeIncreased = RoundedCornerShape(0.dp),
    extraExtraLarge = RoundedCornerShape(0.dp),
)
