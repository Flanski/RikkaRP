package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
    /** 垂直滑动的角度上限（相对垂直轴，正反两侧）。|dx| = |dy| 恰好 45°。 */
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
        val raw = scrollIndex + y / rowPx
        return resolveIndex(raw, itemCount)
    }
}

/* ================================================================== */
/* 按钮选单（文本型）                                                  */
/* ================================================================== */

/** 按钮按下缩放 0.8（缩小 20%），与 SharpUI 原版一致。 */
private const val ButtonMenuPressScale = 0.8f
private val DialCenterFontSize = 18.sp
private val DialRowFontSize = 14.sp
private val DialPadding = 14.dp

/**
 * SharpUI 按钮选单（文本型）。
 *
 * ## 形态
 *
 * 触发控件是**紧凑按钮**：当前值 + 下箭头，1dp 直角边框，高 36dp，宽度随内容自适应。
 * 因此可以直接放在列表行的右侧（`Row { 标题; Spacer(weight); SharpButtonMenu(...) }`）。
 *
 * ## 为什么选单用 Popup
 *
 * SharpUI 原版把表盘直接画在控件自身区域内，控件默认高 150dp 才能容下 5 行 ——
 * 放在表单里等于**常驻占掉三行高度**（用户描述的「非悬浮选单大量占用屏幕高度空间」）。
 *
 * 这里保留原版的**表盘交互**（竖向滚动、中央高亮带、只画完整落框的行、
 * 展开时 scaleY 先过冲到 1.06 再回弹），但把表盘放进 [Popup]：
 * 独立窗口，不参与父布局测量，展开/收起都不会让下方内容位移。
 *
 * ## 交互（与原版一致）
 *
 * | 操作 | 行为 |
 * |---|---|
 * | 点按钮 | 展开表盘 |
 * | 在表盘上竖向滑动 | 跟手滚动，抬手即应用中央项并收起 |
 * | 直接点表盘某一行 | 选中该行并收起 |
 * | 水平滑动 | 不响应（\|dx\| ≤ \|dy\| 才视为垂直） |
 *
 * @param menuHeight 表盘高度
 * @param rowHeight 每行高度
 */
@Composable
fun <T> SharpButtonMenu(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuHeight: Dp = 150.dp,
    rowHeight: Dp = 28.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            press.animateTo(ButtonMenuPressScale, tween(SharpMotion.PressMs, easing = SharpMotion.easing))
        } else {
            press.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }

    // 文字在 composable 上下文求值后传进表盘（表盘内部是 Canvas 绘制）
    val labels = options.map { label(it) }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    Box(modifier = modifier) {
        // ---- 触发按钮 ----
        Row(
            modifier = Modifier
                .onSizeChanged {
                    anchorWidthPx = it.width
                    anchorHeightPx = it.height
                }
                .graphicsLayer { scaleX = press.value; scaleY = press.value }
                .heightIn(min = 36.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = labels.getOrElse(selectedIndex) { "" },
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownChevron(MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- 悬浮表盘 ----
        if (expanded) {
            val menuWidthPx = with(density) { 180.dp.toPx() }
            // 表盘右边缘与按钮右边缘对齐，向左展开，避免超出屏幕
            val offsetX = (anchorWidthPx - menuWidthPx).toInt()
            Popup(
                offset = IntOffset(offsetX, anchorHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                ButtonMenuDial(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        expanded = false
                        onSelect(options[idx])
                    },
                    onDismiss = { expanded = false },
                    height = menuHeight,
                    rowHeight = rowHeight,
                    width = 180.dp,
                )
            }
        }
    }
}

/**
 * 表盘本体：Canvas 绘制 + 原版手势。
 *
 * 绘制要点（照搬 SharpUI 原版）：
 *  · 只画**完整落在框内**的行，避免出现半截文字；
 *  · 中央行高亮带（alpha 0.22）+ 字号更大 + 不透明；
 *  · 展开时整体 scaleY 从 0 过冲到 1.06 再回弹。
 */
@Composable
private fun ButtonMenuDial(
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

    // 展开动画：0 → 过冲 → 1
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

    // Canvas 的 draw lambda 不是 @Composable 上下文，主题色必须先在
    // composable 作用域取出来再捕获进 lambda（直接写 MaterialTheme.colorScheme.xxx 会编译失败）
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
                        ButtonMenuLogic.Direction.VERTICAL -> {
                            val idx = ButtonMenuLogic.resolveIndex(scrollIndex, itemCount)
                            if (idx >= 0) {
                                scrollIndex = idx.toFloat()
                                currentOnSelect(idx)
                            }
                        }
                        ButtonMenuLogic.Direction.NONE -> {
                            // 点击某一行：按落点的纵向坐标反查索引
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
                                topLeft = Offset(stroke, y - rowPx / 2f),
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

/** 向下小箭头（直角风格）。 */
@Composable
private fun DownChevron(color: Color) {
    Canvas(modifier = Modifier.size(10.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.62f)
            lineTo(w * 0.82f, h * 0.38f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
    }
}

/* ================================================================== */
/* 选项卡                                                              */
/* ================================================================== */

/**
 * SharpUI 选项卡：横排等分、直角、选中项为 SharpUI 蓝底白字。
 * 与 [SharpButtonMenu] 的分工：选项卡是常驻可见的 2~4 个并列页签；
 * 按钮选单是从较多选项里选一个，展开时才占用视觉空间（悬浮）。
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
            .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant),
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
            if (index != labels.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(SharpDimens.border)
                        .heightIn(min = SharpDimens.buttonMinHeight)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}
