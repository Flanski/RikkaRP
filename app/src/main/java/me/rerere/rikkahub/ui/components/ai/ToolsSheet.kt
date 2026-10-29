package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.sharp.SharpDialogController
import me.rerere.rikkahub.ui.components.sharp.SharpDialogPlacement
import me.rerere.rikkahub.ui.components.sharp.SharpDialogSpec
import me.rerere.rikkahub.ui.components.sharp.SharpDivider
import me.rerere.rikkahub.ui.icons.AppIcons

/**
 * 工具弹窗（发送区「工具」按钮）。
 *
 * ## 层级（用户规范）
 *
 * ```
 * 工具弹窗（一级，2/3 高度）
 *   ├─ 基础工具  → 二级：RikkaHub 原生工具的开关列表
 *   └─ 拓展工具  → 二级：RP 任务板 / RP 数据板
 * ```
 *
 * 子页面全部走 [SharpDialogController.push] —— 导航逻辑（标题栏 / 返回箭头 /
 * 系统返回键）只应该存在一份，都在 SharpDialog 里。各处弹窗复用它，
 * 才不会出现「改一处还要改别的弹窗」。
 *
 * ## 为什么「基础工具」只列一部分
 *
 * 原生工具实际有 25 个（不含 MCP），但开关分布在 6 个不同地方：
 * `localTools`（8 项）、`enableMemory`、`enableWebSearch`、
 * `enableRecentChatsReference`、`enabledSkills`、`workspaceId`。
 *
 * 后两者**不是布尔开关**（要选技能、要选工作区），MCP 则按 server 配置。
 * 因此这里只列**能用一个开关表达**的 11 项（覆盖 20 个工具）；
 * use_skill / workspace_* / mcp__* 不在此处出现，避免给出「点了也没用」的假开关。
 */
object ToolsSheet {

    /** localTools 的固定顺序。`LocalToolOption` 是 sealed class，没有 entries。 */
    private val LOCAL_TOOL_ORDER = listOf(
        LocalToolOption.JavascriptEngine,
        LocalToolOption.TimeInfo,
        LocalToolOption.Clipboard,
        LocalToolOption.Tts,
        LocalToolOption.AskUser,
        LocalToolOption.ScreenTime,
        LocalToolOption.Calendar,
        LocalToolOption.ChartDisplay,
    )

    /** 一级：工具弹窗。 */
    fun show(
        dialog: SharpDialogController,
        onPushBase: () -> Unit,
        onPushExt: () -> Unit,
    ) {
        dialog.show(
            SharpDialogSpec(
                title = "工具",
                placement = SharpDialogPlacement.Bottom,
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MenuRow(
                            icon = AppIcons.Tools,
                            title = "基础工具",
                            subtitle = "RikkaHub 原生工具的开关",
                            onClick = onPushBase,
                        )
                        SharpDivider()
                        MenuRow(
                            icon = AppIcons.Layers,
                            title = "拓展工具",
                            subtitle = "RP 任务板 / RP 数据板",
                            onClick = onPushExt,
                        )
                    }
                },
            )
        )
    }

    /** 二级：基础工具开关列表。 */
    fun pushBaseTools(
        dialog: SharpDialogController,
        assistant: Assistant,
        onUpdateAssistant: (Assistant) -> Unit,
    ) {
        dialog.push(
            SharpDialogSpec(
                title = "基础工具",
                placement = SharpDialogPlacement.Bottom,
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LOCAL_TOOL_ORDER.forEach { option ->
                            val spec = localToolSpec(option)
                            ToggleRow(
                                icon = spec.icon,
                                title = stringResource(spec.titleRes),
                                toolNames = spec.toolNames,
                                checked = assistant.localTools.contains(option),
                                onToggle = { on ->
                                    val next = if (on) {
                                        assistant.localTools + option
                                    } else {
                                        assistant.localTools - option
                                    }
                                    onUpdateAssistant(assistant.copy(localTools = next))
                                },
                            )
                        }

                        // —— 三组「整组一个开关」的工具 ——
                        ToggleRow(
                            icon = AppIcons.Brain,
                            title = "记忆",
                            toolNames = "memory_read / write / str_replace / append / delete / list",
                            checked = assistant.enableMemory,
                            onToggle = { on ->
                                onUpdateAssistant(assistant.copy(enableMemory = on))
                            },
                        )
                        ToggleRow(
                            icon = AppIcons.Search,
                            title = "联网搜索",
                            toolNames = "search_web / scrape_web",
                            checked = assistant.enableWebSearch,
                            onToggle = { on ->
                                onUpdateAssistant(assistant.copy(enableWebSearch = on))
                            },
                        )
                        ToggleRow(
                            icon = AppIcons.Book,
                            title = "最近对话引用",
                            toolNames = "recent_chats / conversation_search / conversation_read",
                            checked = assistant.enableRecentChatsReference,
                            onToggle = { on ->
                                onUpdateAssistant(assistant.copy(enableRecentChatsReference = on))
                            },
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "另有 use_skill（由「技能」开关）、workspace_*（由工作区绑定）、" +
                                "mcp__*（由 MCP 服务器配置）控制，不能在此单独开关。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                },
            )
        )
    }

    /** 二级：拓展工具（本轮只做入口占位）。 */
    fun pushExtTools(dialog: SharpDialogController) {
        dialog.push(
            SharpDialogSpec(
                title = "拓展工具",
                placement = SharpDialogPlacement.Bottom,
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MenuRow(
                            icon = AppIcons.ListChecks,
                            title = "RP 任务板",
                            subtitle = "待实现",
                            onClick = {},
                            enabled = false,
                        )
                        SharpDivider()
                        MenuRow(
                            icon = AppIcons.Chart,
                            title = "RP 数据板",
                            subtitle = "待实现",
                            onClick = {},
                            enabled = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "这两项尚未实现，入口先占位。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                },
            )
        )
    }
}

/** 菜单行：直角、无边框、按下变浅底。 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed && enabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 工具开关行。
 *
 * 开关本体不用 M3 的 `Switch`（有圆角、有轨道动画），
 * 改用 SharpUI 的直角方块：18dp 方框，选中填标准蓝并画对勾。
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    toolNames: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onToggle(!checked) },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = toolNames,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        SquareCheck(checked = checked)
    }
}

/** 直角方块开关：20dp 方框，选中填蓝 + 对勾；未选中 1dp 描边。 */
@Composable
private fun SquareCheck(checked: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (checked) primary else Color.Transparent)
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RectangleShape,
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            CheckGlyph(color = onBg)
        }
    }
}

/** 手绘对勾（两条线），避免引入额外图标依赖。 */
@Composable
private fun CheckGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val u = size.minDimension / 14f
        val sw = 2f * u
        drawLine(
            color = color,
            start = Offset(2.5f * u, 7.5f * u),
            end = Offset(5.5f * u, 10.5f * u),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(5.5f * u, 10.5f * u),
            end = Offset(11.5f * u, 3.5f * u),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

/** 每个 LocalToolOption 的展示信息。 */
private data class LocalToolSpec(
    val icon: ImageVector,
    val titleRes: Int,
    val toolNames: String,
)

private fun localToolSpec(option: LocalToolOption): LocalToolSpec = when (option) {
    LocalToolOption.JavascriptEngine -> LocalToolSpec(
        AppIcons.FileCode, R.string.tool_sheet_javascript, "eval_javascript",
    )
    LocalToolOption.TimeInfo -> LocalToolSpec(
        AppIcons.Calendar, R.string.tool_sheet_time, "get_time_info",
    )
    LocalToolOption.Clipboard -> LocalToolSpec(
        AppIcons.FileText, R.string.tool_sheet_clipboard, "clipboard_tool",
    )
    LocalToolOption.Tts -> LocalToolSpec(
        AppIcons.AudioLines, R.string.tool_sheet_tts, "text_to_speech",
    )
    LocalToolOption.AskUser -> LocalToolSpec(
        AppIcons.MessageCircle, R.string.tool_sheet_ask_user, "ask_user",
    )
    LocalToolOption.ScreenTime -> LocalToolSpec(
        AppIcons.Chart, R.string.tool_sheet_screen_time, "get_screen_time",
    )
    LocalToolOption.Calendar -> LocalToolSpec(
        AppIcons.Calendar, R.string.tool_sheet_calendar, "calendar_query / calendar_create",
    )
    LocalToolOption.ChartDisplay -> LocalToolSpec(
        AppIcons.Chart, R.string.tool_sheet_chart, "chart_display",
    )
}
