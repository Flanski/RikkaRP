package me.rerere.rikkahub.ui.pages.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.context.Navigator

/**
 * 启动页（Splash）。
 *
 * ## 为什么需要它
 *
 * 主页（消息页）首帧拿到的其实是 [me.rerere.rikkahub.data.datastore.Settings.dummy] ——
 * 一个 `init = true` 的占位设置（零助手、零提供商）。真实设置由 `PreferencesStore`
 * 在 `Dispatchers.Default` 上从磁盘异步读出来之后，才替换进 `settingsFlow`。
 * 所以以前会先渲染一帧占位画面、再被真实数据顶掉。这里把这段时间挡在启动页后面。
 *
 * ## 等什么（按用户要求：进去就是完整画面）
 *
 * 1. [SettingsStore.awaitLoaded] —— 真实设置就绪；
 * 2. **最近一周**的会话（轻量字段）—— [ConversationRepository.preloadRecentConversations]；
 * 3. 当前助手的第一页 —— [ConversationRepository.getConversationsOfAssistantPage]，
 *    这一步把 Paging 的首次 SQL 也预热一遍。
 *
 * ## 这一步能保证什么、不能保证什么（如实说明）
 *
 * 能保证：启动页放行时，设置已是真实值、DB 已打开、迁移已跑完、首条会话 SQL 已执行过一次。
 * 因此主页第一帧渲染的就是真实数据驱动的 UI（顶栏昵称、文件夹栏、助手信息都是真的）。
 *
 * 不能保证：Paging 自己的 `PagingSource` 会再加载一次（`cachedIn` 缓存的是 PagingData
 * 事件流，而 PagingData 无法在页面之外可靠地"读出是否已有条目"）。
 * 但那条 SQL 已在同一张表上跑过、页缓存是热的，通常在一个 DB 往返内就位。
 *
 * ## 转场
 *
 * 本页是 Nav 的一个 entry，开始时 backStack 是 [Splash]。就绪后
 * `navigate(Screen.Messages)` 让 backStack 变成 [Splash, Messages] —— size 变 2，
 * 于是 `NavDisplay.transitionSpec` 自动走 rikkahub 原版那条分支
 * （新页从右滑入 + 旧页向左滑出 1/2 + 缩到 0.7 + 淡出）。
 * **动画代码一行都没复制**，用的就是 NavDisplay 自己的。
 *
 * ## 安全网
 *
 * · 整体 3 秒超时 —— 宁可早点进主页，也不能卡在启动页；
 * · 任何异常都放行（设置读取本身失败时 PreferencesStore 会 halt(1)，这里主要防 DB 卡死）；
 * · 最短显示 100ms —— 避免数据本来就绪时启动页一闪而过、观感更差。
 */
private const val MIN_SHOW_MS = 100L

/**
 * 预载窗口的等待上限。
 *
 * 从 3000 提到 6000：现在启动页要做「最近一周会话」的预读，一周的量在低速存储上
 * 可能超过 3 秒。仍然必须有上限 —— 宁可少预热，也不能卡在启动页。
 */
private const val MAX_WAIT_MS = 6_000L

/** 最近一周的毫秒数（7 天）。 */
private const val RECENT_WEEK_MS = 7L * 24 * 60 * 60 * 1000

@Composable
fun SplashPage(
    navigator: Navigator,
    settingsStore: SettingsStore,
    conversationRepository: ConversationRepository,
    /** 启动页仍是返回栈顶时才跳主页 —— 防止深链/通知点击已经把我们顶掉还硬跳。 */
    isStillTop: () -> Boolean,
) {
    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()

        withTimeoutOrNull(MAX_WAIT_MS) {
            // ★ 必须切到 IO：
            //   AppDatabase 在 Koin 里是 single（懒创建），首次解析会 build + open + 跑 Room 迁移。
            //   如果这段留在主线程，可能正好卡在 Koin 的创建锁上（Application 那边也在 IO 上
            //   并发解析 WorkspaceRepository），主线程会一直等到 DB 建完 —— 启动页会像死掉一样。
            withContext(Dispatchers.IO) {
                runCatching {
                    // 1) 等真实设置（加载完成前 settingsFlow 是 dummy）
                    val settings = settingsStore.awaitLoaded()
                    val assistantId = settings.getCurrentAssistant().id

                    // 2) 预热「最近一周」的会话（用户要求：启动页预载最近一周的所有对话）。
                    //    只读轻量字段，不碰消息体 —— 消息 JSON 全量解析会明显拖慢启动。
                    val since = System.currentTimeMillis() - RECENT_WEEK_MS
                    conversationRepository.preloadRecentConversations(
                        assistantId = assistantId,
                        sinceEpochMillis = since,
                    )

                    // 3) 再预取一次首页第一页。
                    //    该函数内部就是 conversationDAO.getConversationsOfAssistantPaging(...)
                    //    的 PagingSource.load(Refresh)，与主页 Paging 的首次加载是同一条 SQL；
                    //    走它能把「Paging 首次查询」这条路径也预热一遍。
                    conversationRepository.getConversationsOfAssistantPage(
                        assistantId = assistantId,
                        offset = 0,
                        limit = 20,
                    )
                }
            }
        }

        // 最短显示时长
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < MIN_SHOW_MS) delay(MIN_SHOW_MS - elapsed)

        if (isStillTop()) {
            navigator.navigate(Screen.Messages)
        }
    }

    SplashLogo()
}

/**
 * 纯 logo + 背景色。
 *
 * 不加转圈/进度条 —— 这段等待通常只有几十到几百毫秒，转圈还没转满一圈就消失，反而显得卡。
 * 背景取 `splash_background`（浅色 #EEF1F5 / 深色 #101418），与 SharpUI 页面底色一致，
 * 且 `Theme.Rikkahub` 的 windowBackground 也指向同一个颜色，
 * 所以从进程启动到 Composable 完成首帧，中间不会出现色差跳变。
 */
@Composable
fun SplashLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
    }
}
