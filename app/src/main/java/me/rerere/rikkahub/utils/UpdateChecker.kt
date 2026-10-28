package me.rerere.rikkahub.utils

import java.time.Instant
import kotlinx.serialization.SerialName
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.await
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * RikkaRP 的更新源。
 *
 * 上游用的是自建接口 https://updates.rikka-ai.com/ ，它只发布 RikkaHub 官方版本。
 * fork 改名为 RikkaRP 后必须换成自己的仓库，否则会把用户导向原版。
 *
 * ## 为什么用 `/releases` 列表而不是 `/releases/latest`
 *
 * GitHub 的 `GET /repos/{owner}/{repo}/releases/latest` 定义是
 * 「**the most recent non-prerelease, non-draft release**」——
 * 也就是说它**永远不会返回标记为 Pre-release 的版本**。
 *
 * 而本项目早期只发 alpha（勾了 Pre-release），此时 `/latest` 会返回 404，
 * 检查更新就永远拿不到数据。改用列表端点后：
 *   · 取第一个 `draft == false` 的（**不排除** prerelease），alpha 也能被检到；
 *   · 列表为空（仓库还没发过 Release）时返回空数组 → 走「与当前版本相同」的兜底，
 *     首页不会出现红色「检查失败」卡片。
 *
 * 代价：列表接口比 `/latest` 多传几个字段（约几 KB），一次启动只请求一次，可忽略。
 *
 * 仓库：https://github.com/Flanski/RikkaRP
 * tag 形如 v0.0.1 / v3.1.1-rp.6（前导 v 会被去掉再比较版本）
 */
private const val API_URL = "https://api.github.com/repos/Flanski/RikkaRP/releases?per_page=10"

class UpdateChecker(
    private val client: OkHttpClient,
    appScope: AppScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val updateState: StateFlow<UiState<UpdateInfo>> = checkUpdate().stateIn(
        scope = appScope,
        started = SharingStarted.Lazily,
        initialValue = UiState.Loading,
    )

    private fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        emit(
            UiState.Success(
                data = try {
                    val response = client.newCall(
                        Request.Builder()
                            .url(API_URL)
                            .get()
                            .addHeader("Accept", "application/vnd.github+json")
                            .addHeader(
                                "User-Agent",
                                "RikkaRP ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                            )
                            .build()
                    ).await()
                    when {
                        response.isSuccessful -> {
                            val releases = json.decodeFromString<List<GithubRelease>>(response.body.string())
                            // 取第一个非草稿的。prerelease **不**排除 —— alpha 就是靠这个被检到。
                            val release = releases.firstOrNull { !it.draft }
                            release?.let { toUpdateInfo(it) } ?: noUpdate()
                        }

                        // 仓库还没有任何 Release（或接口返回 404）。
                        // 这不是错误，返回「与当前版本相同」即可，避免首页出现红色「检查失败」卡片。
                        response.code == 404 -> noUpdate()

                        else -> throw Exception(
                            "Failed to fetch update info (HTTP ${response.code})"
                        )
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to fetch update info", e)
                }
            )
        )
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    /**
     * 「没有可用的更新信息」时的占位。
     *
     * 返回 `version = 当前版本` 会让 UpdateCard 判定为「已是最新」，不显示红卡。
     * `publishedAt` 必须是合法 ISO-8601 —— UpdateCard 用 `Instant.parse` 解析它。
     */
    private fun noUpdate(): UpdateInfo = UpdateInfo(
        version = BuildConfig.VERSION_NAME,
        publishedAt = Instant.now().toString(),
        changelog = "",
        downloads = emptyList(),
    )

    /** GitHub Release → 应用内 UpdateInfo。 */
    private fun toUpdateInfo(release: GithubRelease): UpdateInfo = UpdateInfo(
        // tag 可能带前导 v（v0.0.1 / v3.1.1-rp.6）
        version = release.tagName.removePrefix("v"),
        publishedAt = release.publishedAt.ifBlank { Instant.now().toString() },
        changelog = release.body.ifBlank { "No release notes." },
        downloads = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .map {
                UpdateDownload(
                    name = it.name,
                    url = it.downloadUrl,
                    size = formatSize(it.size),
                )
            },
    )

    fun downloadUpdate(context: Context, download: UpdateDownload) {
        runCatching {
            val request = DownloadManager.Request(download.url.toUri()).apply {
                // 设置下载时通知栏的标题和描述
                setTitle(download.name)
                setDescription("正在下载更新包...")
                // 下载完成后通知栏可见
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // 允许在移动网络和WiFi下下载
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                // 设置文件保存路径
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
                // 允许下载的文件类型
                setMimeType("application/vnd.android.package-archive")
            }
            // 获取系统的DownloadManager
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            // 你可以保存返回的downloadId到本地，以便后续查询下载进度或状态
        }.onFailure {
            Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
            context.openUrl(download.url) // 跳转到下载页面
        }
    }
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>
)

/* ---------------- GitHub Releases 的响应模型（字段名照 API 原文） ---------------- */

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    /** 草稿不发布给任何人，必须跳过。 */
    val draft: Boolean = false,
    /** Pre-release（alpha/beta）。**不**跳过 —— 见文件顶部 API_URL 的说明。 */
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

/** 字节数 → 人类可读，供下载列表右侧展示。 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1048576.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
}

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 *
 * 支持完整的 SemVer 规范：MAJOR.MINOR.PATCH[-prerelease][+build]
 * - 预发布版本优先级低于正式版：1.0.0-alpha < 1.0.0
 * - 预发布标识符按段逐个比较：数字按数值比较，字符串按字典序比较
 * - 预发布标识符优先级：alpha < beta < rc（通过字典序自然满足）
 * - build metadata（+号后面的部分）不影响优先级比较
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        // 去掉 build metadata（+号后面的部分）
        val withoutBuild = value.split("+").first()
        // 分离主版本号和预发布标识符
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        // 先比较主版本号
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        // 主版本号相同时比较预发布标识符
        // 有预发布标识符的版本优先级低于没有的：1.0.0-alpha < 1.0.0
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                // 字段少的优先级更低：1.0.0-alpha < 1.0.0-alpha.1
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    // 都是字：按数值比较
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    // 数字优先级低于字符串
                    aNum != null -> -1
                    bNum != null -> 1
                    // 都是字符串：按字典序比较
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
