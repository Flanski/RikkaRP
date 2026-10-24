package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpDimens
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion

/**
 * Sharp 组件族 —— RikkaRP 的共享形态层。
 *
 * 共同约定（照搬 SharpUI）：
 *  · 圆角一律 0；描边 1dp；分隔线 0.5dp；
 *  · 按压反馈＝线性放大到 1.06（90ms）＋ 低阻尼弹簧回弹（果冻）；
 *  · 不使用 Material 的水波纹（indication = null），反馈只靠缩放与颜色。
 */

/* ------------------------------------------------------------------ */
/* 表面                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun SharpCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(containerColor)
            .border(SharpDimens.border, borderColor),
        content = content,
    )
}

@Composable
fun SharpDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SharpDimens.divider)
            .background(color)
    )
}

/* ------------------------------------------------------------------ */
/* 按钮                                                                */
/* ------------------------------------------------------------------ */

enum class SharpButtonVariant {
    /** 蓝底白字：主操作 */
    Primary,
    /** 透明底 + 描边：次操作 */
    Outline,
    /** 透明底无描边：文字操作 */
    Ghost,
    /** 红底白字：危险操作 */
    Danger,
}

private data class SharpButtonPalette(
    val background: Color,
    val content: Color,
    val border: Color?,
)

@Composable
private fun sharpButtonPalette(variant: SharpButtonVariant, enabled: Boolean): SharpButtonPalette {
    val cs = MaterialTheme.colorScheme
    val base = when (variant) {
        SharpButtonVariant.Primary -> SharpButtonPalette(SharpColors.Blue, SharpColors.White, null)
        SharpButtonVariant.Outline -> SharpButtonPalette(Color.Transparent, cs.onSurface, cs.outline)
        SharpButtonVariant.Ghost -> SharpButtonPalette(Color.Transparent, cs.primary, null)
        SharpButtonVariant.Danger -> SharpButtonPalette(SharpColors.DialogError, SharpColors.White, null)
    }
    return if (enabled) base else base.copy(
        background = base.background.copy(alpha = 0.35f),
        content = base.content.copy(alpha = 0.5f),
        border = base.border?.copy(alpha = 0.35f),
    )
}

@Composable
fun SharpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SharpButtonVariant = SharpButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(
                SharpMotion.PressScale,
                tween(SharpMotion.PressMs, easing = SharpMotion.easing)
            )
        } else {
            scale.animateTo(
                1f,
                spring(SharpMotion.DampingRatio, SharpMotion.Stiffness)
            )
        }
    }
    val palette = sharpButtonPalette(variant, enabled)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .heightIn(min = SharpDimens.buttonMinHeight)
            .background(palette.background)
            .then(
                if (palette.border != null) Modifier.border(SharpDimens.border, palette.border)
                else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = SharpDimens.buttonPaddingH,
                vertical = SharpDimens.buttonPaddingV,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.content,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图标按钮：方形直角，按压同样是果冻缩放。 */
@Composable
fun SharpIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(
                SharpMotion.PressScale,
                tween(SharpMotion.PressMs, easing = SharpMotion.easing)
            )
        } else {
            scale.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/* ------------------------------------------------------------------ */
/* 列表项                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun SharpListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showArrow: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ) else Modifier
                )
                .heightIn(min = SharpDimens.listItemMinHeight)
                .padding(
                    horizontal = SharpDimens.listItemPaddingH,
                    vertical = SharpDimens.listItemPaddingV,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showArrow) {
                Spacer(Modifier.width(8.dp))
                SharpArrow()
            }
        }
        if (showDivider) SharpDivider()
    }
}

/** 列表项右侧箭头：用两条 1.5dp 线画的直角箭头，不用字符。 */
@Composable
fun SharpArrow(color: Color = MaterialTheme.colorScheme.outline) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.34f, h * 0.18f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.34f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
    }
}

/* ------------------------------------------------------------------ */
/* 输入框                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun SharpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(SharpMotion.LinearMs, easing = SharpMotion.easing),
        label = "sharpFieldBorder",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SharpDimens.fieldMinHeight)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                SharpDimens.border,
                if (borderColor > 0.5f) SharpColors.Blue else MaterialTheme.colorScheme.outline,
            )
            .padding(
                horizontal = SharpDimens.fieldPaddingH,
                vertical = SharpDimens.fieldPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                interactionSource = interaction,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(SharpColors.Blue),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/* ------------------------------------------------------------------ */
/* 开关                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun SharpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) SharpMotion.SqueezeScale else 1f,
        animationSpec = tween(SharpMotion.PressMs, easing = SharpMotion.easing),
        label = "sharpSwitchSqueeze",
    )
    val trackColor by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(SharpMotion.LinearMs, easing = SharpMotion.easing),
        label = "sharpSwitchTrack",
    )

    Box(
        modifier = modifier
            .size(SharpDimens.switchWidth, SharpDimens.switchHeight)
            .graphicsLayer { scaleX = squeeze }
            .background(
                if (trackColor > 0.5f) SharpColors.Blue.copy(alpha = 0.35f + 0.65f * trackColor)
                else Color.Transparent
            )
            .border(
                SharpDimens.border,
                if (trackColor > 0.5f) SharpColors.Blue else MaterialTheme.colorScheme.outline,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        // 滑块：直角方块，靠位移表达开/关，不做圆角药丸
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp)
                .size(
                    width = SharpDimens.switchHeight - 6.dp,
                    height = SharpDimens.switchHeight - 6.dp,
                )
                .background(if (checked) SharpColors.White else MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

/* ------------------------------------------------------------------ */
/* 顶栏                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun SharpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 避让系统状态栏：本组件是自绘顶栏，不像 M3 TopAppBar 自带 insets 处理。
            // 背景色要铺到状态栏下面，所以 padding 施加在 Column（含背景）之外，
            // 同时给 Column 背景，使状态栏区域也是顶栏底色。
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigation != null) {
                navigation()
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            Spacer(Modifier.width(4.dp))
        }
        SharpDivider()
    }
}
