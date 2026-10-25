package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpDimens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * SharpUI 可滚动列（带常驻直角滚动条）。
 *
 * 移植自 com.sharpui.widgets.SharpScrollColumn，行为一致：
 *  - 滚动条宽度 9dp，与内容**并排**而非覆盖，内容不会被滑块挡住；
 *  - **可拖动**：按住滑块拖动即滚动；点轨道其它位置先把滑块跳到该处再继续拖动；
 *  - 内容不可滚动时只隐藏滑块，**轨道宽度仍占位**，内容增减不会引起横向跳动。
 *
 * 与原版的差异：原版支持在滚动条上画「批注」标记，弹窗系统用不到，
 * 故未移植该能力，其余参数与几何算法一致。
 */
@Composable
fun SharpScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollBarColor: Color = SharpColors.White50,
    scrollBarTrackColor: Color = SharpColors.White10,
    nestedScrollConnection: NestedScrollConnection? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (nestedScrollConnection != null) {
                        Modifier.nestedScroll(nestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(state)
                .padding(contentPadding),
            content = content,
        )

        SharpScrollBar(
            state = state,
            modifier = Modifier
                .width(SharpDimens.scrollbar)
                .fillMaxHeight(),
            thumbColor = scrollBarColor,
            trackColor = scrollBarTrackColor,
        )
    }
}

/**
 * 直角滚动条。几何计算集中在 [scrollBarGeometry]，
 * 绘制与手势共用同一份结果，因此滑块画在哪就一定能拖到哪。
 */
@Composable
private fun SharpScrollBar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thumbColor: Color = SharpColors.White50,
    trackColor: Color = SharpColors.White10,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var viewport by remember { mutableIntStateOf(0) }

    val minThumbPx = with(density) { SharpDimens.scrollbarMinThumb.toPx() }
    val maxScroll = state.maxValue.toFloat()
    val scrollable = maxScroll > 0f && viewport > 0

    Box(
        modifier = modifier
            .onSizeChanged { viewport = it.height }
            .background(if (scrollable) trackColor else Color.Transparent)
            .pointerInput(state, viewport, maxScroll, minThumbPx) {
                if (!scrollable) return@pointerInput
                detectDragGestures(
                    onDragStart = { pos ->
                        val (thumb, offset, trackRange) =
                            scrollBarGeometry(state, viewport, minThumbPx)
                        if (pos.y < offset || pos.y > offset + thumb) {
                            // 点在轨道上：先把滑块跳到该处（滑块中心对准手指），随后可继续拖动
                            val target = if (trackRange > 0f) {
                                ((pos.y - thumb / 2f) / trackRange) * maxScroll
                            } else {
                                0f
                            }
                            scope.launch {
                                state.scrollTo(target.roundToInt().coerceIn(0, state.maxValue))
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val (_, _, trackRange) = scrollBarGeometry(state, viewport, minThumbPx)
                        if (trackRange > 0f) {
                            // 向下拖 → 内容下移 → 滚动位置增大
                            state.dispatchRawDelta(dragAmount.y * (maxScroll / trackRange))
                        }
                    },
                )
            }
            .pointerInput(state, viewport, maxScroll, minThumbPx) {
                if (!scrollable) return@pointerInput
                detectTapGestures { pos ->
                    val (thumb, _, trackRange) = scrollBarGeometry(state, viewport, minThumbPx)
                    if (trackRange <= 0f) return@detectTapGestures
                    val target = ((pos.y - thumb / 2f) / trackRange) * maxScroll
                    scope.launch {
                        state.scrollTo(target.roundToInt().coerceIn(0, state.maxValue))
                    }
                }
            },
    ) {
        if (scrollable) {
            val (thumb, offset, _) = scrollBarGeometry(state, viewport, minThumbPx)
            if (thumb > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, offset.roundToInt()) }
                        .fillMaxWidth()
                        .height(with(density) { thumb.toDp() })
                        .background(thumbColor),
                )
            }
        }
    }
}

/**
 * 计算滚动条的 (滑块高度, 滑块偏移, 可滑动行程)，单位均为像素。
 * 由 内容高 = 视口高 + maxScroll 推出滑块高度；[minThumbPx] 保证滑块不会小到抓不住。
 */
private fun scrollBarGeometry(
    state: ScrollState,
    viewport: Int,
    minThumbPx: Float,
): Triple<Float, Float, Float> {
    val maxScroll = state.maxValue.toFloat()
    if (viewport <= 0 || maxScroll <= 0f) return Triple(0f, 0f, 0f)
    val content = viewport + maxScroll
    val thumb = (viewport.toFloat() * viewport / content).coerceIn(minThumbPx, viewport.toFloat())
    val trackRange = (viewport - thumb).coerceAtLeast(0f)
    val offset = ((state.value / maxScroll) * trackRange).coerceIn(0f, trackRange)
    return Triple(thumb, offset, trackRange)
}
