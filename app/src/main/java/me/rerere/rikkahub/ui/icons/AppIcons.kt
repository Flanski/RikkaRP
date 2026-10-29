package me.rerere.rikkahub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 纯描边 (stroke-only) 矢量图标。
 * 路径数据取自 Lucide 官方 SVG, 经 PathParser 解析为 PathNode 后交给 ImageVector.Builder。
 * 描边颜色统一为黑色, 实际显示颜色由 Icon(tint = ...) 覆盖。
 */
private fun strokeIcon(
    name: String,
    strokeWidth: Float,
    cap: StrokeCap,
    join: StrokeJoin,
    paths: List<String>
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { d ->
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = cap,
            strokeLineJoin = join
        )
    }
    return builder.build()
}

/** Lucide 规范: stroke-width 2, linecap/linejoin = round */
private fun lucide(name: String, vararg paths: String): ImageVector =
    strokeIcon(name, 2f, StrokeCap.Round, StrokeJoin.Round, paths.toList())

/**
 * 交叉叠加图标 (X 形): 分别绕画布中心旋转指定角度后合并为单个 ImageVector。
 *
 * 用途: 需要一个"交叉的锤子和扳手"这样的组合图标。
 * 直接在 UI 层叠两个 Icon 也可行, 但那样无法作为一个 ImageVector 复用,
 * 且 tint / 尺寸 / 对齐都要在两处各自维护。
 */
/**
 * 纯填充 (fill-only) 矢量图标。
 *
 * 部分图标集 (Material Symbols / MDI 等) 只有填充型: 它们的 path 是**闭合轮廓**,
 * 靠 fill 呈现形状。若用 strokeIcon 渲染, 会额外描一圈边, 图形变粗且失真。
 *
 * ★ 必须整条 pathData 传入, 不可拆分:
 *   这类路径大量使用相对命令 (q / t / l / c), 相对命令以"上一个点"为基准,
 *   拆成多段后各自从原点重算, 坐标会全部错位 (此前图标渲染成一团就是这个原因)。
 */
/**
 * 描边型图标。
 *
 * 项目里的图标多数是填充型 (fill), 但"双箭头"这类**线条**语义的图形
 * 用描边更自然 —— 填充需要把线扩成多边形, 容易在转角处出现毛刺。
 *
 * 描边宽度取 1.8: 24 的 viewBox 下, 比 Material 默认的 2 略细,
 * 在正文里不显笨重。
 */
private fun strokedIcon(
    name: String,
    pathData: String,
    viewportSize: Float = 24f,
    strokeWidth: Float = 1.8f,
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize
    )
    builder.addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        // 圆头圆角: 箭头端点不会显得锋利
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    return builder.build()
}

private fun filledIcon(
    name: String,
    pathData: String,
    /** 源图标的 viewBox 边长。多数图标是 24, 但外部导出的图标可能不同 (如 68) */
    viewportSize: Float = 24f
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize
    )
    builder.addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    )
    return builder.build()
}

/**
 * 通知弹窗图标: 严格按模板内联 SVG —— stroke-width 2,
 * 模板未声明 linecap/linejoin, 故取 SVG 默认值 butt / miter。
 */
private fun notif(name: String, vararg paths: String): ImageVector =
    strokeIcon(name, 2f, StrokeCap.Butt, StrokeJoin.Miter, paths.toList())

// 常见几何图形转 path (Lucide 原始为 <circle>/<rect> 元素)
private fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M$cx ${cy - r}a$r $r 0 1 0 0 ${r * 2}a$r $r 0 1 0 0 ${-r * 2}"

private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r v${h - 2 * r}a$r $r 0 0 1 ${-r} $r " +
        "H${x + r}a$r $r 0 0 1 ${-r} ${-r}V${y + r}a$r $r 0 0 1 $r ${-r}z"

object AppIcons {

    // ===================== 底部导航 =====================

    val MessageCircle: ImageVector by lazy {
        lucide(
            "MessageCircle",
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 " +
                "0 1 1.099.092 10 10 0 1 0-4.777-4.719"
        )
    }

    /** 机器人 —— 助手列表 */
    val Bot: ImageVector by lazy {
        lucide(
            "Bot",
            "M12 8V4H8",
            roundRectPath(4f, 8f, 16f, 12f, 2f),
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2"
        )
    }

    /** 命令行 —— 工作台 */
    val Terminal: ImageVector by lazy {
        lucide(
            "Terminal",
            "M12 19h8",
            "m4 17 6-6-6-6"
        )
    }

    /** 齿轮 —— 设置 */
    val Settings: ImageVector by lazy {
        lucide(
            "Settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 " +
                "2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 " +
                "2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 " +
                "2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            circlePath(12f, 12f, 3f)
        )
    }

    // ===================== 会话列表 =====================

    val Search: ImageVector by lazy {
        lucide(
            "Search",
            "m21 21-4.34-4.34",
            circlePath(11f, 11f, 8f)
        )
    }

    /**
     * 展开输入框。
     *
     * 形状: 两个箭头**背对背** (上面朝上、下面朝下) —— 表示"还能再撑开"。
     */
    val ExpandInput: ImageVector by lazy {
        strokedIcon("ExpandInput", "M8 14 L12 10 L16 14")
    }

    /**
     * 收起输入框。
     *
     * 形状: 两个箭头**尖对尖** (上面朝下、下面朝上) —— 表示"能收拢回来"。
     * 与 [ExpandInput] 恰为镜像, 一眼能看出是同一控件的两种状态。
     */
    val CollapseInput: ImageVector by lazy {
        strokedIcon("CollapseInput", "M8 10 L12 14 L16 10")
    }

    val BellOff: ImageVector by lazy {
        lucide(
            "BellOff",
            "M8.7 3A6 6 0 0 1 18 8a21.3 21.3 0 0 0 .6 5",
            "M17 17H3s3-2 3-9a4.67 4.67 0 0 1 .3-1.7",
            "M10.3 21a1.94 1.94 0 0 0 3.4 0",
            "m2 2 20 20"
        )
    }

    val Mail: ImageVector by lazy {
        lucide(
            "Mail",
            "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7",
            roundRectPath(2f, 4f, 20f, 16f, 2f)
        )
    }

    val User: ImageVector by lazy {
        lucide(
            "User",
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            circlePath(12f, 7f, 4f)
        )
    }

    val Users: ImageVector by lazy {
        lucide(
            "Users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            circlePath(9f, 7f, 4f)
        )
    }

    val ArrowRight: ImageVector by lazy {
        lucide(
            "ArrowRight",
            "M5 12h14",
            "m12 5 7 7-7 7"
        )
    }

    val Bell: ImageVector by lazy {
        lucide(
            "Bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"
        )
    }

    val ShoppingCart: ImageVector by lazy {
        lucide(
            "ShoppingCart",
            "m2.05 2.05 1.099-.028a1 1 0 0 1 1.008.815l2.69 14.347A1 1 0 0 0 7.83 18H18",
            "M4.563 5h16.435a1 1 0 0 1 .981 1.204l-1.026 6.226A2 2 0 0 1 18.962 14H6.25",
            circlePath(18f, 20f, 2f),
            circlePath(8f, 20f, 2f)
        )
    }

    // ===================== 导航与容器管理 =====================

    /** 返回上级 (顶栏左侧) */
    val ChevronLeft: ImageVector by lazy {
        lucide("ChevronLeft", "m15 18-6-6 6-6")
    }

    /** 删除容器 */
    val Trash: ImageVector by lazy {
        lucide(
            "Trash",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
        )
    }

    /** 容器改名 */
    val Pencil: ImageVector by lazy {
        lucide(
            "Pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4"
        )
    }

    /** 容器 / Linux 环境 */
    val SquareTerminal: ImageVector by lazy {
        lucide(
            "SquareTerminal",
            "m7 11 2-2-2-2",
            "M11 13h4",
            roundRectPath(3f, 3f, 18f, 18f, 2f)
        )
    }

    // ===================== 模型厂商 =====================

    /** DeepSeek */
    val Waves: ImageVector by lazy {
        lucide(
            "Waves",
            "M2 12q2.5 2 5 0t5 0 5 0 5 0",
            "M2 19q2.5 2 5 0t5 0 5 0 5 0",
            "M2 5q2.5 2 5 0t5 0 5 0 5 0"
        )
    }

    /** 月之暗面 */
    val Moon: ImageVector by lazy {
        lucide(
            "Moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"
        )
    }

    /** 自动获取模型 */
    val RefreshCw: ImageVector by lazy {
        lucide(
            "RefreshCw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5"
        )
    }

    /** 勾选 (Lucide check) —— 列表选中态用矢量图, 不用文字符号 */
    val Check: ImageVector by lazy {
        lucide("Check", "M20 6 9 17l-5-5")
    }

    // ===================== 聊天 =====================

    /**
     * 发送按钮图标 (向上箭头)。
     *
     * 路径取自 HugeIcons 的 ArrowUp02 —— rikkahub 的发送按钮用的就是这个图标。
     * 由 ArrowUp02Kt 字节码还原:
     *   柄:   moveTo(12, 5.5) → verticalLineTo(19)
     *   箭头: moveTo(18, 11) → curveTo(18,11, 13.5811,5.00001, 12,5)
     *                     → curveTo(10.4188,4.99999, 6,11, 6,11)
     * 与原图标一致的描边参数: stroke-width 2, round cap / round join。
     */
    val ArrowUp: ImageVector by lazy {
        lucide(
            "ArrowUp",
            "M12 5.5V19",
            "M18 11C18 11 13.5811 5.00001 12 5C10.4188 4.99999 6 11 6 11"
        )
    }

    /** 麦克风 */
    val Mic: ImageVector by lazy {
        lucide(
            "Mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            roundRectPath(9f, 2f, 6f, 13f, 3f)
        )
    }

    /** 表情 */
    val Smile: ImageVector by lazy {
        lucide(
            "Smile",
            circlePath(12f, 12f, 10f),
            "M9 10V9",
            "M15 10V9",
            "M16.472 15a6 6 0 0 1-8.943 0"
        )
    }

    /** 语音 (声波) */
    val AudioLines: ImageVector by lazy {
        lucide(
            "AudioLines",
            "M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3"
        )
    }

    // ===================== 聊天输入栏 =====================

    /** 大脑 (选择模型) —— Lucide brain */
    val Brain: ImageVector by lazy {
        lucide(
            "Brain",
            "M12 18V5",
            "M15 13a4.17 4.17 0 0 1-3-4 4.17 4.17 0 0 1-3 4",
            "M17.598 6.5A3 3 0 1 0 12 5a3 3 0 1 0-5.598 1.5",
            "M17.997 5.125a4 4 0 0 1 2.526 5.77",
            "M18 18a4 4 0 0 0 2-7.464",
            "M19.967 17.483A4 4 0 1 1 12 18a4 4 0 1 1-7.967-.517",
            "M6 18a4 4 0 0 1-2-7.464",
            "M6.003 5.125a4 4 0 0 0-2.526 5.77"
        )
    }

    /**
     * 交叉的锤子和扳手 (AI 工具)。
     *
     * 图形来源: 用户指定的图标 (从外部导入的 SVG, viewBox 0 0 68 68)。
     * 形态为"锤头(带羊角) + C 形开口扳手"交叉, 用户已确认这就是要的图形。
     *
     * ★ 整条 pathData 传入, 不可拆分:
     *   该路径大量使用**相对命令** (c / s / l / a / m / z),
     *   相对命令以"上一个点"为基准, 一旦拆成多段分别解析,
     *   每段都会从原点重新计算 -> 坐标错位、图形散架。
     *   (此前图标反复出错的根因就是这个)
     *
     * ★ viewportSize = 68: 源 SVG 的 viewBox 是 68x68 而非 24x24,
     *   必须如实传入, 否则路径坐标会被按 24 的尺度解释 -> 图形跑出可视区域。
     */
    val Tools: ImageVector by lazy {
        filledIcon(
            "Tools",
                "M13.531 52.056c-2.91 2.63-3.02 7.17-.24 9.95s7.33 2.66 9.96-.26l12.92-14.34-8.06-8.56zm6.66 " +
                "6.96c-1.1 1.09-2.89 1.1-3.99.01a2.83 2.83 0 0 1 0-4c1.1-1.1 2.88-1.1 3.99.01a2.83 2.83 0 0 1 " +
                "0 3.98M47.581 34.736c.33-.36.84-.5 1.31-.37 11.173 3.013 17.705-6.496 " +
                "15.77-14.416-.183-.747-1.135-.989-1.679-.445l-6.21 6.211c-.5.5-1.31.5-1.82 " +
                "0l-5.33-5.34c-.5-.5-.5-1.31 0-1.82l6.35-6.352c.534-.533.302-1.422-.412-1.666-7.01-2.4-17.901 " +
                "4.062-14.709 15.788.13.48 0 .98-.37 1.31l-2.32 2.11 7.53 7.09zM28.811 23.676l-6.15 " +
                "6.15c-.05.05-.1.1-.16.15l29.05 30.82a5.725 5.725 0 0 0 8.2.12 5.72 5.72 0 0 " +
                "0-.13-8.2zM13.536 36.126l2.54-2.54a1.526 1.526 0 0 0 " +
                "0-2.159l-.705-.704c-.789-.79-.94-2.076-.233-2.94a2.133 2.133 0 0 1 3.164-.163l.79.791a1.526 " +
                "1.526 0 0 0 2.16 0l7.73-7.73a2.3 2.3 0 0 0 " +
                "0-3.253l-2.175-2.175c-1.196-1.196-1.209-2.656-.312-3.553 2.617-2.62 5.842-4.903 " +
                "9.557-6.916.377-.205.245-.777-.184-.79-3.836-.118-7.357.832-10.636 2.595-3.626 1.95-5.664 " +
                "4.312-13.732 11.449a1.53 1.53 0 0 0-.072 2.226l.493.493c.799.8.952 2.103.236 2.978a2.16 2.16 " +
                "0 0 1-3.206.165l-.65-.65a1.526 1.526 0 0 0-2.159 0l-2.74 2.742a1.365 1.365 0 0 0 0 " +
                "1.93l8.204 8.204a1.365 1.365 0 0 0 1.93 0",
            viewportSize = 68f
        )
    }

    /**
     * 菜单 (三条横线)。
     *
     * 刻意用**全绝对坐标的填充矩形**手写, 而不是从图标库找:
     *   * 图标库的同类图标多为描边型, 与本项目的填充型体系不一致;
     *   * 绝对坐标不存在"相对命令被拆分后错位"的风险 (那是之前反复踩的坑)。
     */
    val Menu: ImageVector by lazy {
        filledIcon(
            "Menu",
            "M3 5h18v2H3zM3 11h18v2H3zM3 17h18v2H3z"
        )
    }

    /** 拍照 */
    val Camera: ImageVector by lazy {
        lucide(
            "Camera",
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            circlePath(12f, 13f, 3f)
        )
    }

    /** 图片 */
    val Image: ImageVector by lazy {
        lucide(
            "Image",
            roundRectPath(3f, 3f, 18f, 18f, 2f),
            circlePath(9f, 9f, 2f),
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
        )
    }

    /** 选择文件 */
    val FileCode: ImageVector by lazy {
        lucide(
            "FileCode",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 12.5 8 15l2 2.5",
            "m14 12.5 2 2.5-2 2.5"
        )
    }

    /** 工作区 */
    val FolderCode: ImageVector by lazy {
        lucide(
            "FolderCode",
            "M10 10.5 8 13l2 2.5",
            "m14 10.5 2 2.5-2 2.5",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z"
        )
    }

    /** 指令管理 */
    val ListChecks: ImageVector by lazy {
        lucide(
            "ListChecks",
            "M13 5h8", "M13 12h8", "M13 19h8",
            "m3 17 2 2 4-4",
            "m3 7 2 2 4-4"
        )
    }

    /** 上下文管理 */
    val Layers: ImageVector by lazy {
        lucide(
            "Layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
            "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
            "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"
        )
    }

    // ===================== 会话列表扩展图标 =====================

    val Book: ImageVector by lazy {
        lucide(
            "Book",
            "M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20"
        )
    }

    val Calendar: ImageVector by lazy {
        lucide(
            "Calendar",
            "M8 2v3",
            "M16 2v3",
            roundRectPath(3f, 3f, 18f, 18f, 2f),
            "M3 9h18"
        )
    }

    val FileText: ImageVector by lazy {
        lucide(
            "FileText",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8"
        )
    }

    val Cloud: ImageVector by lazy {
        lucide(
            "Cloud",
            "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"
        )
    }

    val Chart: ImageVector by lazy {
        lucide(
            "Chart",
            "M3 3v16a2 2 0 0 0 2 2h16",
            "M7 16h8",
            "M7 11h12",
            "M7 6h3"
        )
    }

    val Alert: ImageVector by lazy {
        lucide(
            "Alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01"
        )
    }

    val Package: ImageVector by lazy {
        lucide(
            "Package",
            "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
            "M12 22V12",
            "M3.29 7L12 12L20.71 7",
            "m7.5 4.27 9 5.15"
        )
    }

    val Receipt: ImageVector by lazy {
        lucide(
            "Receipt",
            "M12 17V7",
            "M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8",
            "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z"
        )
    }

    // ===================== 工作台 / 设置 =====================

    val Box: ImageVector by lazy {
        lucide(
            "Box",
            "M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z",
            "m3.3 7 8.7 5 8.7-5",
            "M12 22V12"
        )
    }

    val Server: ImageVector by lazy {
        lucide(
            "Server",
            roundRectPath(3f, 3f, 18f, 6f, 1f),
            roundRectPath(3f, 15f, 18f, 6f, 1f),
            "M7 6h.01",
            "M7 18h.01"
        )
    }

    val Monitor: ImageVector by lazy {
        lucide(
            "Monitor",
            roundRectPath(4f, 3f, 16f, 12f, 2f),
            "M8 21h8",
            "M12 17v4"
        )
    }

    val Plug: ImageVector by lazy {
        lucide(
            "Plug",
            "M12 22v-5",
            "M9 8V2",
            "M15 8V2",
            "M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8Z"
        )
    }

    /**
     * 荔枝 —— 开屏页图标 (线型)
     * 构成: 圆形果体 + 三道鳞片纹理弧 + 果柄 + 单叶
     */
    val Litchi: ImageVector by lazy {
        lucide(
            "Litchi",
            circlePath(12f, 14f, 7f),
            "M6.2 11.6q5.8 -2.2 11.6 0",
            "M5.6 15.1q6.4 -2.3 12.8 0",
            "M7.2 18.4q4.8 -1.9 9.6 0",
            "M12 7V3.6",
            "M12 5.1C10.2 3 7.4 2.8 6.3 4.4C7.4 6 10.2 6 12 5.1Z"
        )
    }

    /** 圆形加号 —— 顶栏"更多操作" */
    val CirclePlus: ImageVector by lazy {
        lucide(
            "CirclePlus",
            circlePath(12f, 12f, 10f),
            "M8 12h8",
            "M12 8v8"
        )
    }

    /** 放大镜 —— 顶栏搜索 */
    val Plus: ImageVector by lazy {
        lucide(
            "Plus",
            "M5 12h14",
            "M12 5v14"
        )
    }

    val ChevronRight: ImageVector by lazy {
        lucide("ChevronRight", "m9 18 6-6-6-6")
    }

    // ===================== 通知弹窗 (严格按模板内联 SVG) =====================

    val NotifInfo: ImageVector by lazy {
        notif(
            "NotifInfo",
            circlePath(12f, 12f, 10f),
            "M12 16L12 12",
            "M12 8L12.01 8"
        )
    }

    val NotifWarning: ImageVector by lazy {
        notif(
            "NotifWarning",
            "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
            "M12 9L12 13",
            "M12 17L12.01 17"
        )
    }

    val NotifError: ImageVector by lazy {
        notif(
            "NotifError",
            circlePath(12f, 12f, 10f),
            "M15 9L9 15",
            "M9 9L15 15"
        )
    }
}
