package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Star
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.sharp.SharpDivider
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.theme.sharp.SharpMotion

/**
 * 消息页底部导航栏（SharpUI 风格）。
 *
 * ## 本轮（rp.26）按用户规范重排为 4 项
 *
 *   【对话】【助手】【收藏夹】【设置】
 *
 * 变更点：
 *  · 原「助手」跳转 `Screen.Assistant`（助手设置页）→ 现在改为**助手切换弹窗**；
 *    助手设置页改由顶栏 ≡ 弹窗里的「助手设置」进入；
 *  · 原独立的「统计」项移入顶栏 ≡ 弹窗；
 *  · 原「图像生成」项移出底栏（它本来就在 ≡ 弹窗里有一份）。
 *
 * ## 为什么不做常驻选中高亮
 *
 * 这四项里只有「助手」是弹窗，其余是跳子页面。混在一起做选中态会在
 * 「从子页面返回」时和实际位置不一致。等以后真做成 tab 容器再说。
 *
 * 样式：等分 N 格，上图标下文字，直角、1dp 顶部分隔线；
 * 自己应用 navigationBarsPadding()，避免被系统手势条压住
 * （Scaffold 的 bottomBar 不会自动加这个 inset）。
 */
@Composable
fun MessagesBottomBar(
    navController: Navigator,
    /** 打开「助手切换」弹窗。由 MessagesPage 提供 —— 弹窗本体和切换逻辑都在那里。 */
    onOpenAssistantSwitcher: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SharpDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
            // 等分：每项 weight(1f)，间距由布局均分，不会因文字长短而错位
        ) {
            // ① 对话 —— 就是当前页。点击时若是从子页返回后的残留栈，退回到列表根。
            BottomItem(
                modifier = Modifier.weight(1f),
                icon = HugeIcons.BubbleChat,
                label = stringResource(R.string.bottom_nav_chat),
                onClick = {
                    // 当前页本身就是对话列表；这里只做「确保停在列表根」的兜底，
                    // 正常情况下 backStack 只有一个条目，popBackStack 不会发生。
                    navController.popBackStack()
                },
            )
            // ② 助手 —— 打开助手切换弹窗（不是助手设置页）
            BottomItem(
                modifier = Modifier.weight(1f),
                icon = HugeIcons.LookTop,
                label = stringResource(R.string.bottom_nav_assistant),
                onClick = onOpenAssistantSwitcher,
            )
            // ③ 收藏夹
            BottomItem(
                modifier = Modifier.weight(1f),
                // 镂空简约五角星（stroke 线性图标，非实心）
                icon = HugeIcons.Star,
                label = stringResource(R.string.favorite_page_title),
                onClick = { navController.navigate(Screen.Favorite) },
            )
            // ④ 设置
            BottomItem(
                modifier = Modifier.weight(1f),
                icon = HugeIcons.Settings03,
                label = stringResource(R.string.settings),
                onClick = { navController.navigate(Screen.Setting) },
            )
        }
    }
}

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // SharpUI 手感：按下线性缩小，松开低阻尼弹簧回弹
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(SharpMotion.PressScale, tween(SharpMotion.PressMs, easing = SharpMotion.easing))
        } else {
            scale.animateTo(1f, spring(SharpMotion.DampingRatio, SharpMotion.Stiffness))
        }
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
