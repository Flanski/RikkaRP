package me.rerere.rikkahub.ui.diag

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 【临时诊断代码 · 定位「进入子页后顶部内容闪一下」】
 *
 * 已确认的事实（见 rp.15 / rp.16 的探针日志与录屏逐帧分析）：
 *   · 不是页面转场 —— rp.16 把转场改成瞬时切换后仍然闪
 *   · 不是位移 —— 两帧之间纵向互相关位移 = 0
 *   · 不是整页透明渐变 —— 同屏其它元素不变色
 *   · 异常帧的那块区域颜色 = Scaffold 的 containerColor（卡片白底没画，露出底色）
 *   · Choreographer 层面没有长帧（进入时最差 33ms），所以不是"卡"
 *
 * 因此需要看"屏幕上到底画了什么"。本版加 [startWindowCapture]：
 *   用 PixelCopy 逐帧抓窗口顶部区域，逐帧存盘，事后可比对出闪的那几帧长什么样。
 *
 * 落盘：Android/data/<包名>/files/diag/
 *   · flicker_<ts>.txt     文本探针
 *   · cap_<ts>.bin         逐帧截图（自定义格式，见下）
 *
 * cap_*.bin 格式：
 *   [4] magic "CAP1"  [4] 帧数 n  [4] 宽 w  [4] 高 h  然后 n 段、每段 w*h*4 字节 ARGB_8888
 *
 * 定位完成后整份文件可删除。
 */
object FlickerTrace {

    @Volatile
    private var dir: File? = null

    private val armed = AtomicBoolean(false)
    private val startNs = AtomicLong(0L)
    private val lock = Any()
    private val lines = ArrayList<String>(8192)
    private var written = 0

    // ---- 逐帧计时（Choreographer）----
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameNs = 0L

    // ---- 逐帧窗口截图（PixelCopy）----
    private var capWindow: android.view.Window? = null
    private var capBitmap: Bitmap? = null
    private var capScaled: Bitmap? = null
    private val capFrames = ArrayList<ByteArray>()
    private var capMaxFrames = 90
    private var capBusy = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // 必须先于 ioThread 声明：Kotlin object 的属性按书写顺序初始化，
    // 若 ioQueue 写在后面，ioThread 启动时读到的会是 null。
    private val ioQueue = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()

    private val ioThread = Thread {
        // 仅用于把帧数据写盘，避免占用主线程
        while (true) {
            val job = ioQueue.take()
            runCatching { job() }
        }
    }.apply { isDaemon = true; start() }

    fun init(context: Context) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        dir = File(base, "diag").apply { mkdirs() }
    }

    /** 开始记录文本探针。 */
    fun arm(tag: String, windowMs: Long = 4000L) {
        if (dir == null) return
        synchronized(lock) {
            lines.clear()
            written = 0
            startNs.set(System.nanoTime())
            armed.set(true)
        }
        startFrameTrace()
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

    /**
     * 开始逐帧抓窗口内容。
     *
     * 每帧用 PixelCopy 抓 [srcHeight] 高的顶部区域，缩放到 [CAP_W]×[CAP_H] 后存原始像素。
     * 只抓前 [maxFrames] 帧，抓满即停 —— 诊断用途，不给正常使用增加负担。
     */
    fun startWindowCapture(activity: Activity?, maxFrames: Int = 90) {
        if (dir == null || activity == null) return
        val w = activity.window ?: return
        synchronized(lock) {
            capWindow = w
            capFrames.clear()
            capMaxFrames = maxFrames
            capBitmap = null
            capScaled = null
            capBusy = false
        }
        log("==== CAPTURE START ====")
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

    private fun startFrameTrace() {
        val c = runCatching { Choreographer.getInstance() }.getOrNull() ?: return
        frameCallback?.let { c.removeFrameCallback(it) }
        lastFrameNs = 0L
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!armed.get()) return
                val gap =
                    if (lastFrameNs == 0L) 0.0 else (frameTimeNanos - lastFrameNs) / 1_000_000.0
                lastFrameNs = frameTimeNanos
                if (gap > 0.0) log("FRAME gap=" + "%.1f".format(gap))
                captureOneFrame()
                c.postFrameCallback(this)
            }
        }
        frameCallback = cb
        c.postFrameCallback(cb)
    }

    /** 抓一帧。PixelCopy 是异步的，回调里再采样存内存。 */
    private fun captureOneFrame() {
        val w = synchronized(lock) { capWindow } ?: return
        val need = synchronized(lock) {
            if (capBusy || capFrames.size >= capMaxFrames) return
            capBusy = true
            capFrames.size < capMaxFrames
        }
        if (!need) {
            synchronized(lock) { capBusy = false }
            return
        }
        val decor = w.decorView
        val srcW = decor.width
        val srcH = decor.height
        if (srcW <= 0 || srcH <= 0) {
            synchronized(lock) { capBusy = false }
            return
        }
        val capSrcH = (srcH * 0.5f).toInt().coerceIn(64, 900)   // 上半屏足够覆盖顶栏与首屏内容
        val bmp = Bitmap.createBitmap(srcW, capSrcH, Bitmap.Config.ARGB_8888)
        val rect = android.graphics.Rect(0, 0, srcW, capSrcH)
        try {
            PixelCopy.request(
                w, rect, bmp,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        val scaled = Bitmap.createScaledBitmap(bmp, CAP_W, CAP_H, false)
                        val px = IntArray(CAP_W * CAP_H)
                        scaled.getPixels(px, 0, CAP_W, 0, 0, CAP_W, CAP_H)
                        val bytes = ByteArray(CAP_W * CAP_H * 4)
                        var p = 0
                        for (v in px) {
                            bytes[p++] = (v ushr 24).toByte()
                            bytes[p++] = (v ushr 16).toByte()
                            bytes[p++] = (v ushr 8).toByte()
                            bytes[p++] = v.toByte()
                        }
                        synchronized(lock) {
                            capFrames.add(bytes)
                            capBusy = false
                            val n = capFrames.size
                            if (n == 1 || n % 15 == 0) log("CAP n=$n")
                        }
                        if (n >= capMaxFrames) flushFrames()
                    } else {
                        synchronized(lock) { capBusy = false }
                        log("CAP fail result=$result")
                    }
                    bmp.recycle()
                },
                mainHandler,
            )
        } catch (t: Throwable) {
            synchronized(lock) { capBusy = false }
            log("CAP throw " + (t.message ?: t.javaClass.simpleName))
            runCatching { bmp.recycle() }
        }
    }

    private fun flushFrames() {
        val d = dir ?: return
        val copy: List<ByteArray>
        synchronized(lock) {
            if (capFrames.isEmpty()) return
            copy = ArrayList(capFrames)
            capFrames.clear()
            capWindow = null
        }
        ioQueue.offer {
            runCatching {
                val f = File(d, "cap_${System.currentTimeMillis()}.bin")
                f.outputStream().buffered().use { os ->
                    os.write("CAP1".toByteArray())
                    writeInt(os, copy.size)
                    writeInt(os, CAP_W)
                    writeInt(os, CAP_H)
                    for (fr in copy) os.write(fr)
                }
            }
        }
    }

    private fun writeInt(os: java.io.OutputStream, v: Int) {
        os.write(v ushr 24 and 0xFF)
        os.write(v ushr 16 and 0xFF)
        os.write(v ushr 8 and 0xFF)
        os.write(v and 0xFF)
    }

    private fun flush() {
        val d = dir ?: return
        flushFrames()
        synchronized(lock) {
            if (lines.isEmpty()) return
            runCatching {
                File(d, "flicker_${System.currentTimeMillis()}.txt")
                    .writeText(lines.joinToString("\n"))
            }
            lines.clear()
        }
    }

    private const val CAP_W = 180
    private const val CAP_H = 120
}

/** 记录该节点每一帧是否执行了 draw，以及当时解析出的颜色。 */
fun Modifier.flickerTraceDraw(tag: String, info: () -> String = { "" }): Modifier =
    this.drawWithContent {
        FlickerTrace.log("DRAW   " + tag + "  " + info())
        drawContent()
    }

/** 记录该节点每次布局后的位置尺寸。 */
fun Modifier.flickerTraceLayout(tag: String): Modifier =
    this.onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        FlickerTrace.log(
            "LAYOUT $tag  x=%.1f y=%.1f w=%.1f h=%.1f".format(r.left, r.top, r.width, r.height)
        )
    }
