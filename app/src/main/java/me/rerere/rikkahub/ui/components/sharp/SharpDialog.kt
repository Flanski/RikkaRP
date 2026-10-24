package me.rerere.rikkahub.ui.components.sharp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.ui.theme.sharp.SharpColors
import me.rerere.rikkahub.ui.theme.sharp.SharpDimens
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion

/**
 * 弹窗类型。配色照搬 SharpUI / 工作区「通知弹窗特效 Demo」：
 *  normal 蓝底白字 / warning 橙底黑字 / error 红底白字，主体恒为蓝底。
 */
enum class SharpDialogType { Normal, Warning, Error }

private fun SharpDialogType.headerColor(): Color = when (this) {
    SharpDialogType.Normal -> SharpColors.DialogNormal
    SharpDialogType.Warning -> SharpColors.DialogWarning
    SharpDialogType.Error -> SharpColors.DialogError
}

private fun SharpDialogType.onHeaderColor(): Color = when (this) {
    SharpDialogType.Warning -> SharpColors.OnWarning
    else -> SharpColors.White
}

/**
 * Sharp 弹窗。
 *
 * 动效（SharpUI 原值）：展开时 scaleY 以自身中心从 0 → 1.06（250ms，
 * cubic-bezier(0.4,0,0.2,1)），再用低阻尼弹簧（0.20 / 2000）弹回 1.0，
 * 于是落定时会震荡几下；收起时反向走同一条曲线。
 * 高度取屏高 1/3，垂直居中。
 */
@Composable
fun SharpDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    type: SharpDialogType = SharpDialogType.Normal,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit = {},
) {
    var rendered by remember { mutableStateOf(visible) }
    val scale = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            scale.animateTo(
                SharpMotion.DialogPeak,
                tween(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing)
            )
            scale.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        } else if (rendered) {
            scale.animateTo(
                0f,
                tween(SharpMotion.DialogMs, easing = SharpMotion.dialogEasing)
            )
            rendered = false
        }
    }

    if (!rendered) return

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dialogHeight = screenHeight * SharpDimens.DIALOG_HEIGHT_RATIO

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SharpDimens.dialogScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (dismissOnClickOutside) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = dialogHeight, max = dialogHeight)
                    .graphicsLayer { scaleY = scale.value }
                    .background(SharpColors.DialogNormal),
            ) {
                // 头部：类型色，14×16 内边距
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(type.headerColor())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = type.onHeaderColor(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 主体：恒蓝底白字
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    content()
                }
                // 底部：右对齐，12×16
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
        }
    }
}

/** 弹窗底部的白底蓝字按钮（SharpUI notification-btn 同款）。 */
@Composable
fun SharpDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bg = if (primary) SharpColors.White else SharpColors.White20
    val fg = if (primary) SharpColors.DialogNormal else SharpColors.White
    Box(
        modifier = modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(SharpDimens.corner))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 吞掉点击，避免穿透到下层 */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = SharpColors.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (percent != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(4.dp)
                        .background(SharpColors.White30)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                            .height(4.dp)
                            .background(SharpColors.Blue)
                    )
                }
            }
        }
    }
}
