package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpDimens
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion

/* ------------------------------------------------------------------ */
/* 按钮选单（文本型，紧凑按钮形态）                                     */
/* ------------------------------------------------------------------ */

/**
 * SharpUI 按钮选单。
 *
 * 形态：一个**有边框的紧凑按钮**，内含「当前值 + 下箭头」，宽度随内容自适应（有下限/上限），
 *      因此适合放在列表项右侧（`Row { 标题; Spacer(weight); SharpButtonMenu() }`）。
 *
 * 为什么不是 combo box / 整行下拉：用户在设置页明确要求选单放在「颜色模式」这一行的**右侧**，
 * 而不是标题下方另起一行。整行控件会把行高拉高并挤占纵向空间。
 *
 * 展开的选项列表用 [Popup] 渲染 —— 独立窗口，**不参与父布局测量**，
 * 因此展开/收起都不会让下方内容位移。列表右边缘与按钮右边缘对齐，避免超出屏幕。
 */
@Composable
fun <T> SharpButtonMenu(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(SharpMotion.PressScale, tween(SharpMotion.PressMs, easing = SharpMotion.easing))
        } else {
            scale.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }

    val menuWidth = 200.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    Box(modifier = modifier) {
        // ---- 触发按钮：值 + 箭头 ----
        Row(
            modifier = Modifier
                .onSizeChanged {
                    anchorWidthPx = it.width
                    anchorHeightPx = it.height
                }
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .heightIn(min = 36.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label(selected),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownChevron(color = MaterialTheme.colorScheme.onSurfaceVariant, flipped = expanded)
        }

        // ---- 悬浮选单：右边缘与按钮右边缘对齐 ----
        if (expanded) {
            val offsetX = if (anchorWidthPx > menuWidthPx) 0 else (anchorWidthPx - menuWidthPx).toInt()
            Popup(
                offset = IntOffset(offsetX, anchorHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = menuWidth)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) SharpColors.Blue else MaterialTheme.colorScheme.surface
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onSelect(option)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label(option),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index != options.lastIndex) {
                            SharpDivider()
                        }
                    }
                }
            }
        }
    }
}

/** 向下小箭头（直角风格）。 */
@Composable
private fun DownChevron(color: Color, flipped: Boolean) {
    Canvas(modifier = Modifier.size(10.dp)) {
        val w = size.width
        val h = size.height
        val y0 = if (flipped) h * 0.62f else h * 0.38f
        val y1 = if (flipped) h * 0.38f else h * 0.62f
        val path = Path().apply {
            moveTo(w * 0.18f, y0)
            lineTo(w * 0.5f, y1)
            lineTo(w * 0.82f, y0)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
    }
}

/* ------------------------------------------------------------------ */
/* 选项卡                                                              */
/* ------------------------------------------------------------------ */

/**
 * SharpUI 选项卡：横排等分、直角、选中项为 SharpUI 蓝底白字。
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
