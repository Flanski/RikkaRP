package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 轻扫露出的一个操作项。 */
data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

/** 滑动速度阈值（px/帧）。超过它视为"快速轻扫"，不必拉到 1/5 也能吸附。 */
private const val FLING_THRESHOLD = 12f

/**
 * 轻扫菜单的行容器（SharpUI 直角风格）。
 *
 * ## 交互（严格按需求）
 *
 * | 手势 | 行为 |
 * |---|---|
 * | **从右往左**轻扫 | 滑出菜单 |
 * | **从左往右**轻扫 | **收回**菜单（不会打开） |
 * | 点空白 / 点其他地方 | 收回菜单 |
 * | 菜单已展开时**再次从右往左**轻扫 | 继续拉伸末尾的「删除」按钮；拉够即**删除对话** |
 *
 * ## 两个曾经踩过的坑（写在代码里防复发）
 *
 * 1. **右滑不能打开菜单**：吸附判据原本用 `abs(velocity) > 阈值`，不区分方向。
 *    右滑时位移被 `coerceIn` 夹在 0，但速度仍然很大，于是判成"展开" ——
 *    表现就是「从左往右轻扫也能打开菜单」。现在速度判据**必须**带方向
 *    （`velocity < -阈值` 才算向左甩）。
 *
 * 2. **图标贴顶**：菜单单元格用 `fillMaxHeight()` + 居中，行高只有 48dp 时
 *    图标 + 间距 + 文字（约 35dp）上下余量极小，看起来就是"贴到顶部"。
 *    现在调用方把行高提到 56dp，且单元格内部留白按 16/5/11sp 收紧。
 *
 * @param isOpen 由外部统一控制「当前展开的是哪一行」，保证同时只有一行展开，
 *               点其他行时也能收回。
 * @param onOpenChange 展开/收起时回调。
 */
@Composable
fun SwipeableRow(
    rightActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    isOpen: Boolean = false,
    onOpenChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val actionWidth = 76.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val menuWidthPx = actionWidthPx * rightActions.size

    // 末尾「删除」按钮最多可再拉伸的宽度
    val maxStretchPx = with(density) { 104.dp.toPx() }
    // 拉伸超过这个量就算"拉够了"，触发删除
    val deleteTriggerPx = with(density) { 62.dp.toPx() }

    // 负数 = 向左拉开。范围 [-menuWidth - maxStretch, 0]
    val offsetX = remember { Animatable(0f) }
    var menuOpen by remember { mutableStateOf(false) }

    // 拉伸量（仅当越过菜单总宽时为正）
    val stretchPx by remember {
        derivedStateOf {
            val over = -(offsetX.value + menuWidthPx)
            if (over > 0f) over else 0f
        }
    }

    val settleMs = SharpMotion.LinearMs

    suspend fun settleTo(target: Float) {
        offsetX.animateTo(target, tween(settleMs, easing = SharpMotion.easing))
        menuOpen = target < -1f
        onOpenChange(menuOpen)
    }

    fun close() {
        if (offsetX.value != 0f || menuOpen) {
            scope.launch { settleTo(0f) }
        } else {
            onOpenChange(false)
        }
    }

    // 外部要求收起（例如点了别的行）：本行同步收回
    LaunchedEffect(isOpen) {
        if (!isOpen && offsetX.value != 0f) {
            offsetX.animateTo(0f, tween(settleMs, easing = SharpMotion.easing))
            menuOpen = false
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ---------------- 底层：右侧菜单 ----------------
        if (rightActions.isNotEmpty()) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
            ) {
                rightActions.forEachIndexed { index, action ->
                    val isLast = index == rightActions.lastIndex
                    // 只有最后一个按钮（删除）跟着拉伸量加宽；
                    // Row 是 End 对齐，加宽后它会**向左长出来**。
                    SwipeActionCell(
                        action = action,
                        width = if (isLast && stretchPx > 0f) {
                            with(density) { (actionWidthPx + stretchPx).toDp() }
                        } else {
                            actionWidth
                        },
                        highlight = isLast && stretchPx >= deleteTriggerPx,
                        onDone = ::close,
                    )
                }
            }
        }

        // ---------------- 前景：内容本体，跟手平移 ----------------
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(menuWidthPx, maxStretchPx, rightActions.size) {
                    var lastDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            lastDrag = dragAmount
                            scope.launch {
                                // 第一级只允许拉到 -menuWidth；
                                // 已经展开时（第二次左滑）才允许继续拉伸删除按钮。
                                val lowerBound = if (menuOpen) {
                                    -(menuWidthPx + maxStretchPx)
                                } else {
                                    -menuWidthPx
                                }
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(lowerBound, 0f)
                                )
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val cur = offsetX.value
                                if (cur <= -menuWidthPx) {
                                    // —— 第二级：拉伸态 ——
                                    val extra = -cur - menuWidthPx
                                    if (extra >= deleteTriggerPx) {
                                        // 拉够了：先把菜单完全推开，再执行删除
                                        offsetX.animateTo(
                                            -(menuWidthPx),
                                            tween(settleMs, easing = SharpMotion.easing),
                                        )
                                        rightActions.lastOrNull()?.onClick?.invoke()
                                        menuOpen = false
                                        onOpenChange(false)
                                    } else {
                                        settleTo(-menuWidthPx)
                                    }
                                } else {
                                    // —— 第一级：开或关 ——
                                    // 速度判据**必须带方向**，否则右滑也会被判成展开。
                                    val flungLeft = lastDrag < -FLING_THRESHOLD
                                    val enough = cur < -menuWidthPx / 5f
                                    settleTo(
                                        if (enough || (flungLeft && cur < 0f)) -menuWidthPx else 0f
                                    )
                                }
                            }
                        },
                        onDragCancel = { scope.launch { settleTo(0f) } },
                    )
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()

            // 展开时盖一层透明层拦截点击 —— 必须画在 content **之上**，
            // 否则内层的 combinedClickable 会先吃掉点击，「点空白收回」永远不会触发。
            if (menuOpen) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures { close() }
                        },
                )
            }
        }
    }
}

/** 单个操作按钮。 */
@Composable
private fun SwipeActionCell(
    action: SwipeAction,
    width: Dp,
    highlight: Boolean,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDone()
                action.onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            modifier = Modifier.size(if (highlight) 20.dp else 17.dp),
            tint = action.contentColor,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = action.label,
            color = action.contentColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
