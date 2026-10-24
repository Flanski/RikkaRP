package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/* ------------------------------------------------------------------ */
/* 按钮选单（文本型）                                                  */
/* ------------------------------------------------------------------ */

/**
 * SharpUI 按钮选单：外观是一个按钮，点击后弹出**悬浮**的下拉列表。
 *
 * 关键约束（用户明确要求）：选单必须悬浮，不得挤压其它控件的空间。
 * 这里的实现用 [Popup] —— 弹出层独立于父布局测量流程，不会让父 Column 增高，
 * 因此把它放在表单里不会把下方内容顶走。这正是它相对「横排按钮组」的价值：
 * 横排三选一会永久占掉一整行高度。
 *
 * 交互：点击按钮展开 → 点某一项选中并收起；点外部或用返回键收起。
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
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        // ---- 触发按钮 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SharpDimens.fieldMinHeight)
                .onSizeChanged { anchorHeightPx = it.height }
                .background(if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) { expanded = !expanded }
                .padding(horizontal = SharpDimens.fieldPaddingH, vertical = SharpDimens.fieldPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label(selected),
                modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownChevron(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // 展开时箭头朝上，给出明确的开合反馈
                flipped = expanded,
            )
        }

        // ---- 悬浮选单 ----
        if (expanded) {
            Popup(
                // 用像素偏移紧贴按钮下沿；Popup 是独立窗口，不参与父布局测量
                offset = IntOffset(0, anchorHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(with(density) { 240.dp })
                        .background(MaterialTheme.colorScheme.surface)
                        .border(SharpDimens.border, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) SharpColors.Blue
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onSelect(option)
                                    expanded = false
                                }
                                .padding(
                                    horizontal = SharpDimens.fieldPaddingH,
                                    vertical = 12.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label(option),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index != options.lastIndex) {
                            SharpDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** 向下的小箭头（直角风格，和 SharpArrow 区分：这个是纵向）。 */
@Composable
private fun DownChevron(color: Color, flipped: Boolean) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        // 展开时整体上下翻转成向上箭头
        val y0 = if (flipped) h * 0.64f else h * 0.36f
        val y1 = if (flipped) h * 0.36f else h * 0.64f
        val path = Path().apply {
            moveTo(w * 0.2f, y0)
            lineTo(w * 0.5f, y1)
            lineTo(w * 0.8f, y0)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
        // 展开时在下方补一条短线，形成"上下双箭头"的暗示
        if (flipped) {
            val path2 = Path().apply {
                moveTo(w * 0.2f, h * 0.78f)
                lineTo(w * 0.5f, h * 0.56f)
                lineTo(w * 0.8f, h * 0.78f)
            }
            drawPath(
                path = path2,
                color = color,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 选项卡                                                              */
/* ------------------------------------------------------------------ */

/**
 * SharpUI 选项卡：横排等分、直角、选中项为 SharpUI 蓝底白字。
 *
 * 与 [SharpButtonMenu] 的分工：
 *  · 选项卡 = 固定 2~4 个并列页签，始终可见，点一下切页；
 *  · 按钮选单 = 从较多选项里选一个，展开时才占用视觉空间（悬浮）。
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
                    .clickable(
                        interactionSource = itemInteraction,
                        indication = null,
                    ) { onSelect(index) }
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
