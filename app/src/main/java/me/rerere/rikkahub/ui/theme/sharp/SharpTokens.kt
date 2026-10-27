package me.rerere.rikkahub.ui.theme.sharp

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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

    // —— 以下三项为 SharpUI 弹窗系统的依赖，数值照抄原版 ——

    /** 线框 / 分隔线（点缀白） */
    val Outline = Color(0x66FFFFFF)

    /** 弹窗遮罩（Demo: rgba(0,0,0,.5)） */
    val Scrim = Color(0x80000000)

    /** 白底按钮的按下态（蓝色容器内，如弹窗底部按钮） */
    val InversePressed = Color(0xFFD6E9FF)
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

    /** 直角滚动条宽度（SharpUI 原值 9dp） */
    val scrollbar = 9.dp

    /** 滚动条滑块最小高度 */
    val scrollbarMinThumb = 24.dp

    /** 弹窗：高度占屏 1/3（Demo 原值 33.333vh） */
    const val DIALOG_HEIGHT_RATIO = 0.33333f
    /** 弹窗遮罩 */
    val dialogScrim = Color(0x80000000)
}

/**
 * 动效表。SharpUI 的核心手感都在这里。
 */
object SharpMotion {
    /** 全局动画基准时长（用户指定：一律 300ms） */
    const val LinearMs = 300
    val easing: Easing = LinearEasing

    /**
     * 按下用时（比回弹快，手感干脆）。
     *
     * 刻意不参与「全局 300ms」：这是一次接触反馈，不是过渡动画。手指按住通常只有
     * 100–200ms 就抬起，若把它拉到 300ms，动画会在缩到一半时被松手回弹打断，观感是
     * 「按下去没反应」。若要一并改为 300ms，改这一个常量即可。
     */
    const val PressMs = 90

    /** 回弹弹簧：低阻尼 → 过冲后抖动 2–3 次 */
    const val DampingRatio = 0.20f
    const val Stiffness = 2000f

    const val VisibilityThreshold = 0.001f

    /** 按下放大比例 */
    const val PressScale = 1.06f

    /** 开关拖拽时滑块的横向拉伸上限 */
    const val SqueezeScale = 1.14f

    /** 逐字淡入/淡出（时长，当前无使用点；随全局 300ms） */
    const val CharFadeInMs = 300
    const val CharFadeOutMs = 300

    /** 弹窗展开/收起 */
    const val DialogMs = 300
    val dialogEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * 全局过渡规格：300ms + SharpUI 缓动。
     *
     * 用来替代 Compose 各组件的默认 spring —— 默认 spring 的停止时刻由 stiffness 决定
     * （fadeIn 默认 spring(StiffnessMediumLow)，实际约 250–300ms），无法精确等于 300ms。
     * 凡是需要「和全局一致的 300ms」的地方都用本函数，不要再手写数字。
     */
    fun <T> spec(): FiniteAnimationSpec<T> = tween(LinearMs, easing = dialogEasing)

    /**
     * 弹窗过冲峰值（0 -> 1.06 -> 1 的那一下）。
     *
     * ★ rp.23 起**已不再使用**：用户要求去掉「弹窗展开后的抖动」，
     *   SharpDialog 与 SharpMenus 的展开都改成了单段 0 -> 1（对齐
     *   工作区 `通知弹窗特效Demo.html` 的 modalExpand 关键帧）。
     *   常量保留只为「日后想恢复过冲手感」时能一眼找到原值，勿在新代码里引用。
     */
    const val DialogPeak = 1.06f

    // —— 弹窗内越界拉动 ——

    /** 越界拉动时弹窗整体的最大位移 */
    const val DialogOverscrollDp = 5f

    /** 越界拉动量的阻尼系数（0..1） */
    const val DialogOverscrollDamping = 1f

    /** 越界拉动松手后的复位时长。刻意不用弹簧 —— 弹簧会过冲抖动。 */
    const val DialogOverscrollReturnMs = 300

    /** 底部弹窗收起时底边的收缩指数（>1 比顶边收得更快） */
    const val DialogBottomCollapseExponent = 1.6f

    /** 底部弹窗收起时顶边的收缩指数（<1 相对更慢） */
    const val DialogTopCollapseExponent = 0.7f

    /** 页面切换（与弹窗统一 300ms） */
    const val PageMs = 300

    /** 列表逐项进场，每项错开（这是「间隔」，不是单段动画时长，故不随 300ms） */
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
