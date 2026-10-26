package me.rerere.rikkahub.ui.diag

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 【临时诊断代码 · 定位「进入子页后顶部第一块内容闪一下」】
 *
 * 为什么要有它：
 *   逐帧分析录屏能确定「哪几帧异常」，但无法从像素区分三种成因 ——
 *     ① 该帧根本没有执行 draw（上层/图层问题）
 *     ② 执行了 draw，但解析出的颜色是透明/未指定（颜色状态问题）
 *     ③ 执行了 draw，位置/尺寸在这一帧变了（布局问题）
 *   所以在代码里埋三个探针：是否 draw、解析出的颜色、位置尺寸，逐帧写盘。
 *
 * 落盘位置：Android/data/<包名>/files/diag/flicker_<时间戳>.txt
 *   —— 用 getExternalFilesDir 是因为外部工具（Termux）能直接读，
 *      filesDir 在 App 私有目录，读不到。
 *
 * 开关：只有 [arm] 被调用后的 [windowMs] 毫秒内才记录，之后自动落盘并停止，
 *       避免长时间运行把文件写爆。
 *
 * 定位完成后整份文件可删除（不影响任何功能）。
 */
object FlickerTrace {

    @Volatile
    private var dir: File? = null

    private val armed = AtomicBoolean(false)
    private val startNs = AtomicLong(0L)
    private val lock = Any()
    private val lines = ArrayList<String>(8192)
    private var written = 0

    fun init(context: Context) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        dir = File(base, "diag").apply { mkdirs() }
    }

    /** 开始记录。tag 用来区分是哪个页面触发的。 */
    fun arm(tag: String, windowMs: Long = 4000L) {
        if (dir == null) return
        synchronized(lock) {
            lines.clear()
            written = 0
            startNs.set(System.nanoTime())
            armed.set(true)
        }
        log("==== ARM $tag ====")
        Thread {
            try {
                Thread.sleep(windowMs)
            } catch (_: InterruptedException) {
            }
            armed.set(false)
            flush()
        }.apply { isDaemon = true }.start()
    }

    fun log(msg: String) {
        if (!armed.get()) return
        val ms = (System.nanoTime() - startNs.get()) / 1_000_000.0
        synchronized(lock) {
            if (written >= 8000) return
            lines.add("%9.2f  %s".format(ms, msg))
            written++
        }
    }

    private fun flush() {
        val d = dir ?: return
        synchronized(lock) {
            if (lines.isEmpty()) return
            runCatching {
                File(d, "flicker_${System.currentTimeMillis()}.txt")
                    .writeText(lines.joinToString("\n"))
            }
            lines.clear()
        }
    }
}

/** 记录该节点每一帧是否执行了 draw，以及当时解析出的颜色。 */
fun Modifier.flickerTraceDraw(tag: String, info: () -> String = { "" }): Modifier =
    this.drawWithContent {
        FlickerTrace.log("DRAW   " + tag + "  " + info())
        drawContent()
    }

/** 记录该节点每次布局后的位置尺寸（位移不为 0 就能立刻看出来）。 */
fun Modifier.flickerTraceLayout(tag: String): Modifier =
    this.onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        FlickerTrace.log(
            "LAYOUT $tag  x=%.1f y=%.1f w=%.1f h=%.1f".format(r.left, r.top, r.width, r.height)
        )
    }
