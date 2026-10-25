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

/**
 * 轻扫菜单的行容器（SharpUI 直角风格）。
 *
 * 交互：
 *  · **向左轻扫** → 从右侧露出 [rightActions]
 *  · **向右轻扫** → 把菜单推回去（**收回**），不是开另一个菜单
 *  · **松手即吸附**：拉出 `总宽 × 1/5` 或滑动够快就会展开 ——
 *    原先要拉过一半才出得来，手感很差
 *  · **菜单展开时点内容区只收回菜单**，不会顺带打开对话
 *
 * 「点空白收回」的实现要点：
 *   不能只在外层包一个 pointerInput —— 子组件（ConversationItem）的
 *   `combinedClickable` 会先消费点击，外层收不到。这里在展开时**在内容之上
 *   覆盖一层透明 Box** 手动拦截点击，因此内层永远拿不到事件。
 */
@Composable
fun SwipeableRow(
    rightActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val actionWidth = 76.dp
    val rightWidthPx = with(density) { (actionWidth * rightActions.size).toPx() }

    // 正数 = 向右拉开（露出左侧菜单），负数 = 向左拉开（露出右侧菜单）
    val offsetX = remember { Animatable(0f) }
    // 只在「展开/收起」跨越时重组，避免每帧重组
    var menuOpen by remember { mutableStateOf(false) }

    val settleMs = SharpMotion.LinearMs

    suspend fun settleTo(target: Float) {
        offsetX.animateTo(target, tween(settleMs, easing = SharpMotion.easing))
        menuOpen = target < -0.5f
    }

    fun close() {
        scope.launch { settleTo(0f) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ---------------- 底层：右侧菜单 ----------------
        if (rightActions.isNotEmpty()) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
            ) {
                rightActions.forEach { action ->
                    SwipeActionCell(action, actionWidth, ::close)
                }
            }
        }

        // ---------------- 前景：内容本体，跟手平移 ----------------
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(rightWidthPx, rightActions.size) {
                    var velocity = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocity = dragAmount
                            scope.launch {
                                // 只允许 [-宽度, 0]：向左滑拉开右侧菜单，
                                // **向右滑就是把菜单推回去（收回）**
                                val next = (offsetX.value + dragAmount)
                                    .coerceIn(-rightWidthPx, 0f)
                                offsetX.snapTo(next)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                // 吸附判据：拉出总宽 1/5 或滑动够快即展开，否则收回
                                val fast = abs(velocity) > 6f
                                val cur = offsetX.value
                                val enough = cur < -rightWidthPx / 5f
                                settleTo(if (enough || fast) -rightWidthPx else 0f)
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
                        .pointerInput(menuOpen) {
                            detectTapGestures {
                                close()
                            }
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
    width: androidx.compose.ui.unit.Dp,
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
            modifier = Modifier.size(16.dp),
            tint = action.contentColor,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = action.label,
            color = action.contentColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
