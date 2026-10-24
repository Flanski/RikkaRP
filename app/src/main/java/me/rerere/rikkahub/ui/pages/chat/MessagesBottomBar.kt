package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
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
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.sharp.SharpDivider
import me.rerere.rikkahub.ui.context.Navigator

/**
 * 消息页底部导航栏（SharpUI 风格）。
 *
 * 取代原先塞在列表末尾的横排圆形图标行：
 *  · 等分 5 格，上图标下文字，直角、1dp 顶部分隔线；
 *  · 自己应用 navigationBarsPadding()，避免被系统手势条压住
 *    （Scaffold 的 bottomBar 不会自动加这个 inset）。
 *
 * 说明：这些入口是「跳到子页面」而不是切换同级 tab，所以不做常驻选中高亮
 * （否则从子页返回时状态会和实际位置不一致）。
 */
@Composable
fun MessagesBottomBar(navController: Navigator) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SharpDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomItem(
                icon = HugeIcons.LookTop,
                label = stringResource(R.string.assistant_page_title),
                onClick = { navController.navigate(Screen.Assistant) },
            )
            BottomItem(
                icon = HugeIcons.Sparkles,
                label = stringResource(R.string.chat_page_menu_image_generation),
                onClick = { navController.navigate(Screen.ImageGen) },
            )
            BottomItem(
                icon = HugeIcons.InLove,
                label = stringResource(R.string.favorite_page_title),
                onClick = { navController.navigate(Screen.Favorite) },
            )
            BottomItem(
                icon = HugeIcons.ChartColumn,
                label = stringResource(R.string.messages_bottom_stats),
                onClick = { navController.navigate(Screen.Stats) },
            )
            BottomItem(
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
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
