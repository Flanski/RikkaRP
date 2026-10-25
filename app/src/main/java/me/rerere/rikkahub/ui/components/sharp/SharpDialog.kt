package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * ============================================================================
 * SharpUI 弹窗系统 —— 移植自 com.sharpui.widgets.dialog
 *                      + Litchi(Cyrene) ui/notification/Notification.kt 的经验
 *
 * 颜色、层级、动效一律照搬，未做任何改动：
 *   遮罩 rgba(0,0,0,.5) / 主体 #409EFF / warning 橙底黑字 / error 红底白字
 *   展开 scaleY 0 -> 1.06 (250ms cubic-bezier(0.4,0,0.2,1)) 再低阻尼弹簧(0.20/2000) 回 1.0
 * 三种形态（居中 1/3、底部 2/3、全屏）共用同一套动画，都以自身中心为原点。
 *
 * 本版新增（本轮修复）：
 *   1. 底部/全屏形态给按钮区加 navigationBarsPadding() —— 原先被三大金刚键压住
 *   2. 弹窗内**子页**切换动画（push/pop + SheetNav 方向）—— 原先无过渡
 *   3. 选项可带图标（SharpDialogOption）
 *   4. 开合动画**可被打断**（必选项）：新信号立即接管，不排队等待
 * ============================================================================
 */

/** 弹窗类型。颜色与图标照搬「通知弹窗特效 Demo」。 */
enum class SharpDialogType {
    /** 头部主体蓝 + 白字 */
    Normal,

    /** 头部橙底 + **黑字** */
    Warning,

    /** 头部红底 + 白字 */
    Error,
}

/**
 * 弹窗形态。三者的**动画完全一致**，只有高度与位置不同。
 */
enum class SharpDialogPlacement {
    /** 居中弹窗：高度为屏幕的 1/3 */
    Center,

    /** 底部弹窗：贴底、高度为屏幕的 2/3，用于展示更多内容 */
    Bottom,

    /** 全屏弹窗：铺满整屏 */
    Full,
}

/**
 * 弹窗底部按钮。
 *
 * [primary] = true 时是「主按钮」（白底蓝字）；
 * false 时是「次要按钮」（半透明白底 + 白字）。
 */
class SharpDialogButton(
    val text: String,
    val primary: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * 弹窗列表里的一个选项。
 *
 * [icon] 非空时会在文字**左侧**显示图标（本轮新增能力）。
 */
class SharpDialogOption(
    val label: String,
    val icon: ImageVector? = null,
)

/** 一次弹窗的完整描述。 */
class SharpDialogSpec(
    val title: String,
    val message: String = "",
    val type: SharpDialogType = SharpDialogType.Normal,
    val placement: SharpDialogPlacement = SharpDialogPlacement.Center,
    val buttons: List<SharpDialogButton> = emptyList(),
    val dismissOnScrimClick: Boolean = true,
    /** 自定义内容；提供时优先于 [message]。 */
    val content: (@Composable () -> Unit)? = null,
)

/**
 * 弹窗页面的切换方向。
 *
 * 宿主据此决定滑动方向 —— 「进入子页」与「返回父页」方向相反，
 * 必须在切换**当时**记录，事后无法推断。
 */
enum class SheetNav { RESET, FORWARD, BACK }

/**
 * 弹窗的统一入口。
 *
 * 全库只使用这一个弹窗实现（动画、层级、配色完全一致）。
 */
@Stable
class SharpDialogController {

    var current: SharpDialogSpec? by mutableStateOf(null)
        private set

    /** 最近一次页面切换方向（供宿主决定滑动方向） */
    internal var nav: SheetNav by mutableStateOf(SheetNav.RESET)
        private set

    /** 页面栈：只装被推进去的父页面，栈顶是当前页的直接父页。 */
    private val backStack = mutableListOf<SharpDialogSpec>()

    /** 打开弹窗（作为顶层页面），会清空页面栈。 */
    fun show(spec: SharpDialogSpec) {
        backStack.clear()
        nav = SheetNav.RESET
        current = spec
    }

    /**
     * 进入**子页面**：当前页压栈，新页成为当前页。
     * 宿主会播「父页向左滑出、子页自右滑入」。
     */
    fun push(spec: SharpDialogSpec) {
        current?.let { backStack.add(it) }
        nav = SheetNav.FORWARD
        current = spec
    }

    /** 返回上一级；返回 false 表示已在顶层。 */
    fun pop(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        nav = SheetNav.BACK
        current = previous
        return true
    }

    /** 「返回」统一入口：有上一级就回去，没有就关闭。 */
    fun back() {
        if (!pop()) dismiss()
    }

    fun dismiss() {
        nav = SheetNav.RESET
        backStack.clear()
        current = null
    }

    /** 普通通知：只有「确定」。 */
    fun info(
        title: String,
        message: String = "",
        confirmText: String = "确定",
        placement: SharpDialogPlacement = SharpDialogPlacement.Center,
        onConfirm: (() -> Unit)? = null,
    ) {
        show(
            SharpDialogSpec(
                title = title,
                message = message,
                type = SharpDialogType.Normal,
                placement = placement,
                buttons = listOf(SharpDialogButton(confirmText, primary = true, onClick = onConfirm)),
            )
        )
    }

    /** 警告通知。 */
    fun warning(
        title: String,
        message: String = "",
        confirmText: String = "确定",
        placement: SharpDialogPlacement = SharpDialogPlacement.Center,
        onConfirm: (() -> Unit)? = null,
    ) {
        show(
            SharpDialogSpec(
                title = title,
                message = message,
                type = SharpDialogType.Warning,
                placement = placement,
                buttons = listOf(SharpDialogButton(confirmText, primary = true, onClick = onConfirm)),
            )
        )
    }

    /** 错误通知：带「重试 + 确定」两个按钮。 */
    fun error(
        title: String,
        message: String = "",
        retryText: String = "重试",
        confirmText: String = "确定",
        placement: SharpDialogPlacement = SharpDialogPlacement.Center,
        onRetry: (() -> Unit)? = null,
        onConfirm: (() -> Unit)? = null,
    ) {
        show(
            SharpDialogSpec(
                title = title,
                message = message,
                type = SharpDialogType.Error,
                placement = placement,
                buttons = listOf(
                    SharpDialogButton(retryText, primary = false, onClick = onRetry),
                    SharpDialogButton(confirmText, primary = true, onClick = onConfirm),
                ),
            )
        )
    }

    /**
     * 以「底部 2/3 弹窗 + 选项列表」的形式展示一组子选项。
     *
     * 选中即收起（先 dismiss 再回调），因此调用方在 onSelect 里直接
     * `navController.navigate(...)` 就能让「弹窗收起」与「页面滑入」**同步进行**。
     */
    fun showOptions(
        title: String,
        options: List<SharpDialogOption>,
        subtitle: String? = null,
        cancelText: String = "取消",
        onSelect: (Int) -> Unit,
    ) {
        show(
            SharpDialogSpec(
                title = title,
                message = subtitle.orEmpty(),
                placement = SharpDialogPlacement.Bottom,
                content = {
                    SharpOptionsList(
                        options = options,
                        onSelect = { index ->
                            dismiss()
                            onSelect(index)
                        },
                    )
                },
                buttons = listOf(SharpDialogButton(text = cancelText, primary = false)),
            )
        )
    }

    /**
     * 纯文本选项的版本（无图标）。
     *
     * 刻意**不**叫 showOptions：`List<SharpDialogOption>` 与 `List<String>` 擦除后
     * 都是 `Ljava/util/List;`，同名重载会报
     * 「Platform declaration clash: same JVM signature」。
     * （@JvmName 不能用于成员函数，只能用于顶层函数/属性访问器/file，故改用不同名字。）
     */
    fun showTextOptions(
        title: String,
        options: List<String>,
        subtitle: String? = null,
        cancelText: String = "取消",
        onSelect: (Int) -> Unit,
    ) = showOptions(
        title = title,
        options = options.map { SharpDialogOption(it) },
        subtitle = subtitle,
        cancelText = cancelText,
        onSelect = onSelect,
    )

    /** 确认框：次要按钮 + 主按钮。 */
    fun confirm(
        title: String,
        message: String = "",
        confirmText: String = "确定",
        cancelText: String = "取消",
        type: SharpDialogType = SharpDialogType.Normal,
        placement: SharpDialogPlacement = SharpDialogPlacement.Center,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
    ) {
        show(
            SharpDialogSpec(
                title = title,
                message = message,
                type = type,
                placement = placement,
                buttons = listOf(
                    SharpDialogButton(cancelText, primary = false, onClick = onCancel),
                    SharpDialogButton(confirmText, primary = true, onClick = onConfirm),
                ),
            )
        )
    }
}

/** 供子树获取统一的弹窗控制器。 */
val LocalSharpDialog = staticCompositionLocalOf<SharpDialogController> {
    error("SharpDialogHost 未安装：请在根部包裹 SharpDialogHost { ... }")
}

/**
 * 统一弹窗宿主。在应用根部包裹一次即可。
 */
@Composable
fun SharpDialogHost(
    modifier: Modifier = Modifier,
    controller: SharpDialogController = remember { SharpDialogController() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSharpDialog provides controller) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            SharpDialogLayer(controller)
        }
    }
}

/** 弹窗动画阶段。用于让动画**顺序**播放，不被新信号打断。 */
private enum class Phase { IDLE, OPENING, OPEN, CLOSING }

@Composable
private fun BoxScope.SharpDialogLayer(controller: SharpDialogController) {
    var spec by remember { mutableStateOf<SharpDialogSpec?>(null) }
    val scale = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.IDLE) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 越界拉动产生的整体位移
    val overscroll = remember { mutableFloatStateOf(0f) }
    var overscrollJob by remember { mutableStateOf<Job?>(null) }
    val maxOverscrollPx = with(density) { SharpMotion.DialogOverscrollDp.dp.toPx() }

    val requested = controller.current
    val open = requested != null

    /**
     * 开合动画。
     *
     * **动画必须可被打断**（用户明确要求，且这是必选项）：
     * `LaunchedEffect(open)` 的 key 一变会取消正在跑的协程，
     * 于是「展开到一半收到关闭」会立刻转入收起 —— 响应是即时的，不排队等待。
     *
     * 注意：这里**不再**使用 Channel 队列。队列会让收起必须等展开播完，
     * 表现为「点了关闭没反应，过一会儿才关」，与要求相反。
     */
    LaunchedEffect(open) {
        if (open) {
            closing = false
            phase = Phase.OPENING
            // 从当前值continue，不强制归零 —— 这样中途反向也能接上，不会跳一下
            if (scale.value <= 0.001f) scale.snapTo(0f)
            // 1) 按 Demo 曲线展开到过冲峰值
            scale.animateTo(
                SharpMotion.DialogPeak,
                tween(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing),
            )
            // 2) 到达后震荡几下
            scale.animateTo(
                1f,
                spring(
                    SharpMotion.DampingRatio,
                    SharpMotion.Stiffness,
                    visibilityThreshold = SharpMotion.VisibilityThreshold,
                ),
            )
            phase = Phase.OPEN
        } else {
            if (phase == Phase.IDLE) return@LaunchedEffect
            phase = Phase.CLOSING
            closing = true
            // 已完全展开时先反向放大一点再收（与 Demo 一致）
            if (scale.value >= 1f) {
                scale.animateTo(
                    SharpMotion.DialogPeak,
                    tween(SharpMotion.PressMs, easing = SharpMotion.easing),
                )
            }
            scale.animateTo(
                0f,
                tween(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing),
            )
            spec = null
            closing = false
            phase = Phase.IDLE
        }
    }

    // 内容更新：展示中切换内容不重播动画
    LaunchedEffect(requested) {
        if (requested != null) spec = requested
    }

    // 每次打开都把滚动位置复位
    LaunchedEffect(open) {
        if (open) {
            scrollState.scrollTo(0)
            overscroll.value = 0f
        }
    }

    /**
     * 越界量只在 onPostScroll 里累加：
     * 1. 内容不可滚动时不响应（maxValue == 0）；
     * 2. 顶部继续下拉时 available.y 为正，故 translationY = +overscroll 才能下移；
     * 3. 回弹不在 onPostScroll 里做（非挂起函数），由 isScrollInProgress 监听统一弹回。
     */
    val overscrollConnection = remember(maxOverscrollPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (scrollState.maxValue <= 0) return Offset.Zero
                if (available.y != 0f) {
                    overscroll.value = (
                        overscroll.value + available.y * SharpMotion.DialogOverscrollDamping
                        ).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                }
                return Offset.Zero
            }
        }
    }

    // 手势 / 惯性结束 → 复位。刻意用减速补间而非弹簧：弹簧会过冲抖动。
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress && overscroll.value != 0f) {
            overscrollJob?.cancel()
            overscrollJob = scope.launch {
                Animatable(overscroll.value).animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = SharpMotion.DialogOverscrollReturnMs,
                        easing = LinearOutSlowInEasing,
                    ),
                ) { overscroll.value = this.value }
            }
        }
    }

    val current = spec ?: return
    val p = scale.value
    if (p <= 0.001f) return

    val heightFraction = when (current.placement) {
        SharpDialogPlacement.Center -> 1f / 3f
        SharpDialogPlacement.Bottom -> 2f / 3f
        SharpDialogPlacement.Full -> 1f
    }
    val isBottomSheet = current.placement == SharpDialogPlacement.Bottom
    val alignment = if (isBottomSheet) Alignment.BottomCenter else Alignment.Center

    Box(modifier = Modifier.fillMaxSize()) {
        // —— 遮罩 ——
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    SharpColors.Scrim.copy(alpha = SharpColors.Scrim.alpha * p.coerceIn(0f, 1f))
                )
                .then(
                    if (current.dismissOnScrimClick) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { controller.dismiss() }
                        }
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { /* 吞掉点击，避免穿透到底层 */ }
                        }
                    }
                ),
        )

        // —— 弹窗本体 ——
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .align(alignment)
                .graphicsLayer {
                    val v = p.coerceIn(0f, 1f)
                    if (closing && isBottomSheet) {
                        // 收起时把上下两条边分开驱动：
                        // 底边用 >1 的指数（更快收到中线），顶边用 <1 的指数（相对更慢），
                        // 再由两边位置反推整体的 scaleY 与 translationY。
                        val bottomFactor = v.pow(SharpMotion.DialogBottomCollapseExponent)
                        val topFactor = v.pow(SharpMotion.DialogTopCollapseExponent)
                        scaleY = (bottomFactor + topFactor) / 2f
                        translationY = (bottomFactor - topFactor) / 2f * (size.height / 2f) +
                            overscroll.value
                    } else {
                        scaleY = v
                        translationY = overscroll.value
                    }
                    alpha = v
                    transformOrigin = TransformOrigin.Center
                }
                .background(SharpColors.DialogNormal)
                // 吃掉落在弹窗体上的点击，避免穿透到遮罩把弹窗关掉
                .pointerInput(Unit) {
                    detectTapGestures { /* 仅用于拦截 */ }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DialogHeader(current)

                // —— 弹窗内子页切换：父页向左滑出、子页自右滑入 ——
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val slide = tween<IntOffset>(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing)
                        val fade = tween<Float>(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing)
                        when (controller.nav) {
                            SheetNav.FORWARD ->
                                (slideInHorizontally(slide) { it } + fadeIn(fade)) togetherWith
                                    (slideOutHorizontally(slide) { -it } + fadeOut(fade))

                            SheetNav.BACK ->
                                (slideInHorizontally(slide) { -it } + fadeIn(fade)) togetherWith
                                    (slideOutHorizontally(slide) { it } + fadeOut(fade))

                            // 首次打开只淡入 —— 展开动画由 scale 负责，这里再做位移会重复
                            SheetNav.RESET -> fadeIn(fade) togetherWith fadeOut(fade)
                        }
                    },
                    label = "sharpSheetPage",
                    modifier = Modifier.weight(1f),
                ) { page ->
                    DialogBody(
                        spec = page,
                        scrollState = scrollState,
                        nestedScrollConnection = overscrollConnection,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                DialogFooter(
                    spec = current,
                    isBottomSheet = isBottomSheet,
                    onDismiss = { controller.dismiss() },
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(spec: SharpDialogSpec) {
    val headerColor = when (spec.type) {
        SharpDialogType.Normal -> SharpColors.DialogNormal
        SharpDialogType.Warning -> SharpColors.DialogWarning
        SharpDialogType.Error -> SharpColors.DialogError
    }
    val foreground = when (spec.type) {
        SharpDialogType.Warning -> SharpColors.OnWarning
        else -> SharpColors.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SharpDialogIcon(type = spec.type, color = foreground)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Text(
            text = spec.title,
            modifier = Modifier.weight(1f),
            color = foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DialogBody(
    spec: SharpDialogSpec,
    scrollState: ScrollState,
    nestedScrollConnection: NestedScrollConnection?,
    modifier: Modifier = Modifier,
) {
    SharpScrollColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(SharpColors.DialogNormal),
        state = scrollState,
        contentPadding = PaddingValues(16.dp),
        scrollBarColor = SharpColors.White50,
        scrollBarTrackColor = SharpColors.White20,
        nestedScrollConnection = nestedScrollConnection,
    ) {
        val custom = spec.content
        if (custom != null) {
            custom()
        } else {
            androidx.compose.material3.Text(
                text = spec.message,
                color = SharpColors.White,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun DialogFooter(
    spec: SharpDialogSpec,
    isBottomSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val buttons = spec.buttons.ifEmpty {
        listOf(SharpDialogButton(text = "确定", primary = true))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SharpColors.DialogNormal)
            // 底部形态/全屏形态贴到屏幕底边，必须避让系统导航栏（三大金刚键/手势条），
            // 否则按钮会被压在导航栏下面点不到。
            .then(if (isBottomSheet || spec.placement == SharpDialogPlacement.Full) {
                Modifier.navigationBarsPadding()
            } else {
                Modifier
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            DialogFooterButton(
                text = button.text,
                primary = button.primary,
                onClick = {
                    button.onClick?.invoke()
                    onDismiss()
                },
            )
        }
    }
}

/**
 * 弹窗底部按钮。
 *
 * 等价于 SharpUI 的 SharpButton(Inverse / InverseGhost, size = Small)：
 * 白底蓝字 / 半透明白底白字，最小高 34dp，左右 16dp，字号 14sp，
 * 按下放大 6% 后以低阻尼弹簧回弹（果冻）。
 */
@Composable
private fun DialogFooterButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(
                SharpMotion.PressScale,
                tween(SharpMotion.PressMs, easing = SharpMotion.easing),
            )
        } else {
            scale.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }

    val bg = if (primary) SharpColors.White else SharpColors.White20
    val fg = if (primary) SharpColors.DialogNormal else SharpColors.White

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .heightIn(min = 34.dp)
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 头部图标（20dp，线性描边，与 Demo 的 SVG 造型对应）。 */
@Composable
private fun SharpDialogIcon(type: SharpDialogType, color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val unit = size.minDimension / 24f
        val sw = 2f * unit

        fun at(x: Float, y: Float) = Offset(x * unit, y * unit)

        when (type) {
            SharpDialogType.Normal -> {
                drawCircle(
                    color = color,
                    radius = 10f * unit,
                    center = at(12f, 12f),
                    style = Stroke(width = sw),
                )
                drawLine(
                    color = color,
                    start = at(12f, 16f),
                    end = at(12f, 11f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color, radius = sw / 2f, center = at(12f, 8f))
            }

            SharpDialogType.Warning -> {
                val path = Path().apply {
                    moveTo(10.29f * unit, 3.86f * unit)
                    lineTo(2f * unit, 19f * unit)
                    lineTo(22f * unit, 19f * unit)
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = sw, join = StrokeJoin.Round),
                )
                drawLine(
                    color = color,
                    start = at(12f, 9f),
                    end = at(12f, 13.5f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color, radius = sw / 2f, center = at(12f, 17f))
            }

            SharpDialogType.Error -> {
                drawCircle(
                    color = color,
                    radius = 10f * unit,
                    center = at(12f, 12f),
                    style = Stroke(width = sw),
                )
                drawLine(
                    color = color,
                    start = at(15f, 9f),
                    end = at(9f, 15f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = at(9f, 9f),
                    end = at(15f, 15f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 弹窗内的子选项列表（直角、白色分隔线）。
 *
 * 选项可带图标：图标显示在文字**左侧**（本轮新增）。
 */
@Composable
internal fun SharpOptionsList(
    options: List<SharpDialogOption>,
    onSelect: (Int) -> Unit,
) {
    Column {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                SharpDivider(color = SharpColors.White30)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(index) {
                        detectTapGestures { onSelect(index) }
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = option.icon
                if (icon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SharpColors.White,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                androidx.compose.material3.Text(
                    text = option.label,
                    color = SharpColors.White,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/**
 * 全屏加载遮罩。文字形如「加载中...045%」，与 SharpUI 的差量进度写法一致。
 */
@Composable
fun SharpLoadingOverlay(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    percent: Int? = null,
) {
    if (!visible) return
    val label = if (percent != null) {
        "$text${percent.toString().padStart(3, '0')}%"
    } else {
        text
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SharpColors.Ink70)
            .pointerInput(Unit) {
                detectTapGestures { /* 吞掉点击，避免穿透到下层 */ }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(
                text = label,
                color = SharpColors.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (percent != null) {
                Spacer(Modifier.size(10.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .size(4.dp)
                        .background(SharpColors.White30)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                            .size(4.dp)
                            .background(SharpColors.Blue)
                    )
                }
            }
        }
    }
}
