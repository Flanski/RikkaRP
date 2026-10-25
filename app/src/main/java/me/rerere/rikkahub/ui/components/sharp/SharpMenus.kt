package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpDimens
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* ================================================================== */
/* 手势判定（移植自 SharpUI widgets/ButtonMenuLogic.kt，纯 Kotlin）    */
/* ================================================================== */

internal object ButtonMenuLogic {
    /** 垂直滑动的角度上限：|dx| = |dy| 恰好 45°。 */
    const val ANGLE_LIMIT_DEG = 45f

    enum class Direction { NONE, VERTICAL, HORIZONTAL }

    fun isVertical(dx: Float, dy: Float): Boolean {
        if (dx.isNaN() || dy.isNaN()) return false
        if (dx == 0f && dy == 0f) return false
        return abs(dx) <= abs(dy)
    }

    fun direction(dx: Float, dy: Float, touchSlop: Float): Direction {
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= touchSlop) return Direction.NONE
        return if (isVertical(dx, dy)) Direction.VERTICAL else Direction.HORIZONTAL
    }

    fun resolveIndex(scrollIndex: Float, itemCount: Int): Int {
        if (itemCount <= 0) return -1
        if (scrollIndex.isNaN()) return 0
        return scrollIndex.roundToInt().coerceIn(0, itemCount - 1)
    }

    fun clampScroll(scrollIndex: Float, itemCount: Int): Float {
        if (itemCount <= 0) return 0f
        if (scrollIndex.isNaN()) return 0f
        return scrollIndex.coerceIn(0f, (itemCount - 1).toFloat())
    }

    fun indexAtY(y: Float, rowPx: Float, scrollIndex: Float, itemCount: Int): Int {
        if (itemCount <= 0 || rowPx <= 0f) return -1
        return resolveIndex(scrollIndex + y / rowPx, itemCount)
    }
}

/* ================================================================== */
/* 按钮选单（严格移植 SharpUI SharpButtonMenu）                        */
/* ================================================================== */

/** 按下时缩小的比例：变为 0.8（即缩小 20%），与 SharpUI 原版一致。 */
private const val ButtonMenuPressScale = 0.8f

/** 选单中央（选中项）的字号 —— 比其它行略大。 */
private val DialCenterFontSize = 18.sp

/** 选单其它行的字号。 */
private val DialRowFontSize = 14.sp

/** 选单左右留白：文字不贴边、不横向溢出。 */
private val DialPadding = 14.dp

/**
 * SharpUI 按钮选单（文本型）。
 *
 * ## 外观（严格照搬 SharpUI）
 *
 * **无边框**，内容居中，按下时**缩小到 80%**，抬起恢复。
 * 当前值文字直接作为按钮内容 —— 不是 combo box（没有边框、没有下拉箭头）。
 *
 * ## 三种操作逻辑（SharpUI 原版定义）
 *
 * | 逻辑 | 操作 | 行为 |
 * |---|---|---|
 * | ① | 点击按钮 | **立即弹出选单**；在里面选择后应用并收起 |
 * | ② | 在按钮上**按住上下滑动** | 按钮上的值跟手变化，**抬手即应用** |
 * | ③ | 手势范围与方向 | 只响应控件自身区域；**不响应水平滑动**；垂直轨迹最多 ±45° |
 *
 * ## 选单为悬浮层
 *
 * 表盘放在 [Popup] 里 —— 独立窗口，**不参与父布局测量**，
 * 展开/收起都不会让下方内容位移，也不会常驻占用高度。
 *
 * 选单绘制严格照搬原版：竖向滚动、中央高亮带（alpha 0.22）、
 * 只画**完整落在框内**的行、展开时 `scaleY` 从 0 过冲到 1.06 再回弹。
 *
 * @param height 按钮高度
 * @param menuHeight 表盘高度
 * @param rowHeight 表盘每行高度
 */
@Composable
fun <T> SharpButtonMenu(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 36.dp,
    menuHeight: Dp = 168.dp,
    rowHeight: Dp = 28.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // 文字在 composable 上下文求值后传进表盘（表盘内部是 Canvas 绘制）
    val labels = options.map { label(it) }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    val press = remember { Animatable(1f) }
    var pressed by remember { mutableStateOf(false) }

    // 逻辑 ② 的临时索引：按住滑动时按钮上的值跟手变化
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    val rowPx = with(density) { rowHeight.toPx() }
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(pressed, dragging) {
        if (pressed || dragging) {
            press.animateTo(
                ButtonMenuPressScale,
                tween(SharpMotion.PressMs, easing = SharpMotion.easing),
            )
        } else {
            press.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }

    // 外部改值时同步
    LaunchedEffect(selectedIndex) {
        if (!dragging) dragIndex = selectedIndex.toFloat()
    }

    val displayIndex = when {
        dragging -> ButtonMenuLogic.resolveIndex(dragIndex, labels.size)
        else -> selectedIndex
    }

    Box(modifier = modifier) {
        // ---------------- 按钮本体（无边框、内容居中） ----------------
        Box(
            modifier = Modifier
                // 不能用 fillMaxWidth：本控件常放在 Row 的右侧（标题 + Spacer(weight) + 选单），
                // fillMaxWidth 会先占满整行，把标题挤掉。这里按内容宽度收敛。
                .widthIn(min = 96.dp)
                .height(height)
                .onSizeChanged {
                    anchorWidthPx = it.width
                    anchorHeightPx = it.height
                }
                .graphicsLayer { scaleX = press.value; scaleY = press.value }
                .pointerInput(enabled, rowPx, labels.size) {
                    if (!enabled || labels.isEmpty()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true

                        var dir = ButtonMenuLogic.Direction.NONE
                        var accDx = 0f
                        var accDy = 0f
                        val slop = viewConfiguration.touchSlop
                        // 从当前选中项开始滑
                        dragIndex = selectedIndex.toFloat()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            if (dx == 0f && dy == 0f) continue

                            accDx += dx
                            accDy += dy

                            if (dir == ButtonMenuLogic.Direction.NONE) {
                                dir = ButtonMenuLogic.direction(accDx, accDy, slop)
                                if (dir == ButtonMenuLogic.Direction.VERTICAL) {
                                    dragging = true
                                }
                            }

                            if (dir == ButtonMenuLogic.Direction.VERTICAL) {
                                change.consume()
                                // 向上拖 → 索引增大（与表盘同向）
                                dragIndex = ButtonMenuLogic.clampScroll(
                                    dragIndex - dy / rowPx,
                                    labels.size,
                                )
                            }
                            // HORIZONTAL：不消费、不处理 —— 完全让给外层容器
                        }

                        pressed = false

                        when (dir) {
                            // 逻辑 ②：滑动过 → 抬手即应用
                            ButtonMenuLogic.Direction.VERTICAL -> {
                                val idx = ButtonMenuLogic.resolveIndex(dragIndex, labels.size)
                                dragging = false
                                if (idx >= 0 && idx != selectedIndex) {
                                    currentOnSelect(options[idx])
                                }
                            }

                            // 逻辑 ③：水平滑动不响应
                            ButtonMenuLogic.Direction.HORIZONTAL -> {
                                dragging = false
                            }

                            // 逻辑 ①：纯点击 → 弹出选单
                            ButtonMenuLogic.Direction.NONE -> {
                                dragging = false
                                expanded = true
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = labels.getOrElse(displayIndex) { "" },
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ---------------- 悬浮表盘 ----------------
        if (expanded) {
            val menuWidthPx = with(density) { 190.dp.toPx() }
            // 右边缘与按钮右边缘对齐，向左展开，避免超出屏幕
            val offsetX = (anchorWidthPx - menuWidthPx).toInt()
            Popup(
                offset = IntOffset(offsetX, anchorHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                SharpButtonMenuDial(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        expanded = false
                        currentOnSelect(options[idx])
                    },
                    onDismiss = { expanded = false },
                    height = menuHeight,
                    rowHeight = rowHeight,
                    width = 190.dp,
                )
            }
        }
    }
}

/**
 * 表盘本体：Canvas 绘制 + SharpUI 原版手势。
 *
 * 绘制要点（照搬原版）：
 *  · 只画**完整落在框内**的行，避免出现半截文字；
 *  · 中央行高亮带（alpha 0.22）+ 字号更大 + 不透明；
 *  · 展开时整体 scaleY 从 0 过冲到 1.06 再回弹。
 */
@Composable
private fun SharpButtonMenuDial(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    height: Dp,
    rowHeight: Dp,
    width: Dp,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val itemCount = labels.size
    val currentOnSelect by rememberUpdatedState(onSelect)

    var scrollIndex by remember {
        mutableFloatStateOf(selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)).toFloat())
    }

    // 展开：0 → 过冲 1.06 → 1
    val dial = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        dial.animateTo(
            SharpMotion.DialogPeak,
            tween(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing),
        )
        dial.animateTo(
            1f,
            spring(
                SharpMotion.DampingRatio,
                SharpMotion.Stiffness,
                visibilityThreshold = SharpMotion.VisibilityThreshold,
            ),
        )
    }

    val centerStyle = remember { TextStyle(fontSize = DialCenterFontSize, fontWeight = FontWeight.Bold) }
    val rowStyle = remember { TextStyle(fontSize = DialRowFontSize) }
    val rowPx = with(density) { rowHeight.toPx() }

    // Canvas 的 draw lambda 不是 @Composable，颜色必须提前取出
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clipToBounds()
            .pointerInput(itemCount, rowPx) {
                if (itemCount <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dir = ButtonMenuLogic.Direction.NONE
                    var accDx = 0f
                    var accDy = 0f
                    val slop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        if (dx == 0f && dy == 0f) continue
                        accDx += dx
                        accDy += dy
                        if (dir == ButtonMenuLogic.Direction.NONE) {
                            dir = ButtonMenuLogic.direction(accDx, accDy, slop)
                        }
                        if (dir == ButtonMenuLogic.Direction.VERTICAL) {
                            change.consume()
                            scrollIndex = ButtonMenuLogic.clampScroll(
                                scrollIndex - dy / rowPx,
                                itemCount,
                            )
                        }
                    }

                    when (dir) {
                        // 滑动过：抬手应用中央项
                        ButtonMenuLogic.Direction.VERTICAL -> {
                            val idx = ButtonMenuLogic.resolveIndex(scrollIndex, itemCount)
                            if (idx >= 0) {
                                scrollIndex = idx.toFloat()
                                currentOnSelect(idx)
                            }
                        }
                        // 纯点击：按落点的纵向坐标反查索引
                        ButtonMenuLogic.Direction.NONE -> {
                            val centerY = size.height / 2f
                            val idx = ButtonMenuLogic.indexAtY(
                                y = down.position.y - centerY,
                                rowPx = rowPx,
                                scrollIndex = scrollIndex,
                                itemCount = itemCount,
                            )
                            if (idx >= 0) {
                                scrollIndex = idx.toFloat()
                                currentOnSelect(idx)
                            }
                        }
                        ButtonMenuLogic.Direction.HORIZONTAL -> onDismiss()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f || itemCount == 0) return@Canvas
            val d = dial.value
            val alpha = d.coerceIn(0f, 1f)
            if (alpha <= 0.002f) return@Canvas

            val stroke = SharpDimens.border.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            scale(scaleX = 1f, scaleY = d, pivot = center) {
                clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                    drawRect(
                        color = surfaceColor,
                        alpha = alpha,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                    drawRect(
                        color = SharpColors.Blue,
                        alpha = alpha,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(
                            (size.width - stroke).coerceAtLeast(0f),
                            (size.height - stroke).coerceAtLeast(0f),
                        ),
                        style = Stroke(width = stroke),
                    )

                    val halfRows = (size.height / 2f) / rowPx
                    val firstSlot = floor(scrollIndex - halfRows).toInt() - 1
                    val lastSlot = ceil(scrollIndex + halfRows).toInt() + 1

                    for (i in firstSlot..lastSlot) {
                        if (i < 0 || i >= itemCount) continue
                        val y = center.y + (i - scrollIndex) * rowPx
                        val isCenter = abs(i - scrollIndex) < 0.5f
                        val layout = measurer.measure(
                            text = AnnotatedString(labels[i]),
                            style = if (isCenter) centerStyle else rowStyle,
                        )
                        val halfText = layout.size.height / 2f
                        // 只画完整落在框内的行
                        if (y - halfText < 0f || y + halfText > size.height) continue

                        if (isCenter) {
                            drawRect(
                                color = SharpColors.Blue,
                                alpha = alpha * 0.22f,
                                topLeft = Offset(stroke, (y - rowPx / 2f).coerceAtLeast(0f)),
                                size = Size(
                                    (size.width - stroke * 2f).coerceAtLeast(0f),
                                    rowPx,
                                ),
                            )
                        }
                        drawText(
                            textLayoutResult = layout,
                            color = onSurfaceColor,
                            alpha = alpha * (if (isCenter) 1f else 0.5f),
                            topLeft = Offset(
                                center.x - layout.size.width / 2f,
                                y - halfText,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================== */
/* 选项卡                                                              */
/* ================================================================== */

/**
 * SharpUI 选项卡：横排等分、直角、选中项为 SharpUI 蓝底白字。
 * 与 [SharpButtonMenu] 的分工：选项卡是常驻可见的 2~4 个并列页签；
 * 按钮选单是从较多选项里选一个，选单悬浮展开。
 */
@Composable
fun SharpTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
    ) {
        labels.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            val itemInteraction = remember(index) { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SharpDimens.buttonMinHeight)
                    .background(if (selected) SharpColors.Blue else Color.Transparent)
                    .clickable(interactionSource = itemInteraction, indication = null) { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = SharpDimens.buttonPaddingV),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = icons?.getOrNull(index)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
