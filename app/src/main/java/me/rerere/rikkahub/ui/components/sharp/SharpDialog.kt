package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * ============================================================================
 * SharpUI 弹窗系统 —— 完整移植自 com.sharpui.widgets.dialog
 *
 * 移植原则：**颜色、层级、动效一律不改**。
 *   · 遮罩 rgba(0,0,0,.5)，主体恒为主体蓝 #409EFF
 *   · 头部按类型着色：normal 蓝 / warning 橙（**黑字**）/ error 红（白字）
 *   · 展开：scaleY 0 → 1.06（250ms，cubic-bezier(0.4,0,0.2,1)）
 *           再用低阻尼弹簧 (0.20 / 2000) 回到 1.0 → 落定时震荡几下
 *   · 收起：先反向放大到 1.06 再收到 0；底部形态的底边收得更快（指数 1.6 / 0.7）
 *   · 三种形态（居中 1/3、底部 2/3、全屏）共用同一套动画，都以**自身中心**为原点
 *
 * 与原版的实现差异（不影响观感）：
 *   · 文字直接用 Material3 Text + 显式颜色/字号（等价于原版 SharpText）
 *   · 底部按钮在本文件内实现（等价于原版 SharpButtonStyle.Inverse / InverseGhost +
 *     SharpButtonSize.Small），因为项目的 SharpButton 没有尺寸参数
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
 * 弹窗形态。三者的**动画完全一致**（同样的时长、曲线、震荡），只有高度与位置不同。
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
 * false 时是「次要按钮」（半透明白底 + 白字），对应 Demo 里的 .btn-retry。
 */
class SharpDialogButton(
    val text: String,
    val primary: Boolean = true,
    val onClick: (() -> Unit)? = null,
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
 * 弹窗的统一入口。
 *
 * 全库只使用这一个弹窗实现（动画、层级、配色完全一致），
 * 各页面通过 [LocalSharpDialog] 或直接持有本控制器来弹出。
 */
class SharpDialogController {

    var current: SharpDialogSpec? by mutableStateOf(null)
        private set

    fun show(spec: SharpDialogSpec) {
        current = spec
    }

    fun dismiss() {
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

    /** 错误通知：Demo 里错误类型带「重试 + 确定」两个按钮。 */
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
     * 供列表项右侧带向下箭头（表示含有隐藏子选项）时使用，
     * 保证子选项统一使用标准的 2/3 弹窗，而不是各页自行拼一套。
     */
    fun showOptions(
        title: String,
        options: List<String>,
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
 * 统一弹窗宿主。
 *
 * 在应用根部包裹一次即可，之后**任何页面**都通过 [LocalSharpDialog] 弹窗；
 * 居中 / 底部 / 全屏三种形态共用同一套动画、层级与配色。
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

@Composable
private fun BoxScope.SharpDialogLayer(controller: SharpDialogController) {
    var spec by remember { mutableStateOf<SharpDialogSpec?>(null) }
    val scale = remember { Animatable(0f) }
    // 是否处于收起阶段（收起时底部弹窗的底边要收得更快）
    var closing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 越界拉动产生的整体位移
    val overscroll = remember { mutableFloatStateOf(0f) }
    var overscrollJob by remember { mutableStateOf<Job?>(null) }
    val maxOverscrollPx = with(density) { SharpMotion.DialogOverscrollDp.dp.toPx() }

    val requested = controller.current
    val open = requested != null

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
     * 越界量只在 onPostScroll 里累加。三点约束：
     * 1. 内容不可滚动时不响应（maxValue == 0 说明没有可滚区域）；
     * 2. 符号：顶部继续下拉时 available.y 为正、底部继续上拉时为负，
     *    因此 translationY = +overscroll 才能得到「顶部下拉 → 弹窗下移」；
     * 3. 回弹不在 onPostScroll 里做（它不是挂起函数），
     *    改由 ScrollState.isScrollInProgress 的监听在滚动结束后统一弹回。
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

    // 手势 / 惯性结束 → 复位。刻意用减速补间而不是弹簧：弹簧会过冲产生抖动。
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

    // 展开 / 收起。以 open 为 key：展开期间被关闭会立即取消展开动画并转入收起。
    LaunchedEffect(open) {
        if (open) {
            closing = false
            scale.snapTo(0f)
            // 1) 按 Demo 曲线展开到过冲峰值
            scale.animateTo(
                targetValue = SharpMotion.DialogPeak,
                animationSpec = tween(
                    durationMillis = SharpMotion.DialogMs,
                    easing = SharpMotion.dialogEasing,
                ),
            )
            // 2) 到达后震荡几下
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = SharpMotion.DampingRatio,
                    stiffness = SharpMotion.Stiffness,
                    visibilityThreshold = SharpMotion.VisibilityThreshold,
                ),
            )
        } else {
            closing = true
            // 只有「已经完全展开」时才先展开一点再收起；
            // 若展开尚未完成就收到关闭指令，则直接反向收回去。
            if (scale.value >= 1f) {
                scale.animateTo(
                    targetValue = SharpMotion.DialogPeak,
                    animationSpec = tween(
                        durationMillis = SharpMotion.PressMs,
                        easing = SharpMotion.easing,
                    ),
                )
            }
            scale.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SharpMotion.DialogMs,
                    easing = SharpMotion.dialogEasing,
                ),
            )
            spec = null
            closing = false
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
                    // 三种形态都以自身中心为原点，展开过程才与 1/3 弹窗一致
                    transformOrigin = TransformOrigin.Center
                }
                .background(SharpColors.DialogNormal)
                // 吃掉落在弹窗体上的点击，避免穿透到遮罩把弹窗关掉
                .pointerInput(Unit) {
                    detectTapGestures { /* 仅用于拦截，不产生行为 */ }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DialogHeader(current)
                // weight 属于 ColumnScope，只能由调用处求值后再传入
                DialogBody(
                    spec = current,
                    scrollState = scrollState,
                    nestedScrollConnection = overscrollConnection,
                    modifier = Modifier.weight(1f),
                )
                DialogFooter(current, onDismiss = { controller.dismiss() })
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
private fun DialogFooter(spec: SharpDialogSpec, onDismiss: () -> Unit) {
    val buttons = spec.buttons.ifEmpty {
        listOf(SharpDialogButton(text = "确定", primary = true))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SharpColors.DialogNormal)
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
 * 单独放在这里，是为了让「列表项 → 2/3 弹窗显示子选项」这条链路
 * 有统一的实现，不需要每个页面各写一遍。
 */
@Composable
internal fun SharpOptionsList(
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    Column {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                SharpDivider(color = SharpColors.White30)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(index) {
                        detectTapGestures { onSelect(index) }
                    }
                    .padding(vertical = 14.dp),
            ) {
                androidx.compose.material3.Text(
                    text = label,
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
