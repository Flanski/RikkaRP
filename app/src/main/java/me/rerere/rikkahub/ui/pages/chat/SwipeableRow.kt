package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 左滑露出的一个操作项。 */
data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

/**
 * 左滑露出操作菜单的行容器（SharpUI 直角风格）。
 *
 * 交互：
 *  · 手指按住向左拖 → 前景跟手左移，最多露出 [SwipeAction] 的总宽度；
 *  · 松手时位移超过总宽 **1/4** 即吸附展开，否则弹回；
 *  · 点任一操作后自动收起再执行回调；
 *  · 菜单展开时点内容区只收回菜单，不会顺带打开对话。
 *
 * 为什么不用 M3 的 SwipeToDismissBox：它一个方向只支持一个目标动作，
 * 而这里需要并排三个（置顶 / 更多 / 删除）。
 */
@Composable
fun SwipeableRow(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionWidth = 76.dp
    val menuWidthPx = with(density) { (actionWidth * actions.size).toPx() }
    val offsetX = remember { Animatable(0f) }
    // 只有「展开/收起」这个跨越才值得重组 —— 直接读 offsetX.value 会让每一帧都重组
    val isMenuOpen by remember { derivedStateOf { offsetX.value < -0.5f } }

    Box(modifier = modifier.fillMaxWidth()) {
        // ---- 底层：操作菜单（右对齐） ----
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End,
        ) {
            actions.forEach { action ->
                Column(
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .background(action.containerColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            scope.launch {
                                offsetX.animateTo(0f)
                                action.onClick()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        modifier = Modifier.size(18.dp),
                        tint = action.contentColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = action.label,
                        color = action.contentColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ---- 前景：内容本体，跟手平移 ----
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(menuWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount).coerceIn(-menuWidthPx, 0f)
                                offsetX.snapTo(next)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                // 吸附阈值：只要拉出总宽的 1/4 就展开（原先 1/2，要拉很远才出得来）。
                                // 另外只要位移超过单个按钮宽度的一半（说明用户明确想拉菜单）也展开。
                                val openThreshold = -menuWidthPx / 4f
                                val target = if (offsetX.value < openThreshold) -menuWidthPx else 0f
                                offsetX.animateTo(
                                    target,
                                    tween(SharpMotion.LinearMs, easing = SharpMotion.easing),
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    )
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // 已展开时，点内容区只收回菜单，不把点击透给行本身 ——
            // 否则「点空白收回」会变成「打开对话」。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isMenuOpen) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures {
                                    scope.launch {
                                        offsetX.animateTo(
                                            0f,
                                            tween(SharpMotion.LinearMs, easing = SharpMotion.easing),
                                        )
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content()
            }
        }
    }
}
