<!-- RIKKARP-FORK-BANNER -->
> [!IMPORTANT]
> **RikkaRP 是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的非官方分支（unofficial fork）。**
> 与原项目作者无隶属、无背书、无支持关系。所有问题请提交到**本仓库**，不要打扰上游。
> 本项目以 **AGPL-3.0** 授权 —— 见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。
> 对应源码：https://github.com/Flanski/RikkaRP

## 版本血缘与版本号说明

| 项 | 值 |
|---|---|
| **fork 基线** | [rikkahub](https://github.com/rikkahub/rikkahub) **3.1.1**（官方 versionCode **194**，tag `3.1.1` / 提交 `ad2c443`）|
| 本仓库早期内部版本 | `3.1.1-rp.1` … `3.1.1-rp.25`（versionCode 195–219）|
| **正式对外版本起点** | **`0.0.1`**（versionCode **1**）—— 自本版起重置版本号，与上游编号脱钩 |

> **为什么重置成 0.0.1**
>
> 早期沿用的 `3.1.1-rp.X` 是「上游版本号 + 内部修订号」，容易让人误以为是上游 3.1.1 的等价物。
> 本分支已经有大量与上游不一致的改动（界面层、导航、弹窗、TTS 等），
> 继续挂着 3.1.1 会误导版本预期，因此从 `0.0.1` 起独立编号。
>
> ⚠️ **从 `3.1.1-rp.25`(versionCode 219) 升级到 `0.0.1`(versionCode 1) 必须卸载重装** ——
> Android 不允许 versionCode 降级。卸载会清空应用数据，请先备份。
> 从 `0.0.1` 往后每次发版 versionCode 严格递增，不会再回退。


<div align="center">
  <h1>RikkaRP</h1>
  <p><b>专注 LLM 驱动的角色扮演与视觉小说客户端</b></p>
  <p><i>An LLM-driven role-play &amp; visual-novel client for Android</i></p>
</div>

---

## 这是什么

RikkaRP 是一个 Android 端的大语言模型客户端，方向是**角色扮演（Role-Play）**与**视觉小说（Visual Novel）**。

它以内核成熟的 RikkaHub 为底座 —— 多提供商接入、流式对话、工具调用、记忆系统这些基础设施不重造，
把力气花在角色扮演与叙事交互这一层。

## 当前状态

本项目处于**早期演进阶段**，如实说明：

| 部分 | 状态 |
|---|---|
| 底层能力（多提供商 / 流式 / 工具 / 记忆） | ✅ 继承自 RikkaHub 3.1.1，可用 |
| 角色卡导入（SillyTavern V2 / V3） | ✅ 继承自上游 |
| 世界书 / 正则输出 / 宏 / 历史摘要 | ✅ 继承自上游 |
| 界面层重构 | 🚧 进行中（Sharp 设计系统） |
| 角色扮演专属功能 | 🚧 规划中 |
| 视觉小说表现层（立绘 / 台词框 / 分支） | 🚧 规划中，尚未实现 |

> 上面写「规划中」的部分**目前代码里没有**。不在 README 里把没做的东西写成已完成。

## 相对上游的改动

### 界面

- 摒弃 Material 3 Expressive 的浮动与圆角，改用**直角 Sharp 设计系统**：
  方角、细边框、线性动画、按压果冻回弹
- 原「抽屉式」会话列表改为**独立消息页并作为启动首页**
- 消息页底部改为**导航栏**；顶栏承载头像与昵称
- 会话列表**左滑菜单**（置顶 / 更多 / 删除）
- 会话项的**长按菜单**保留上游全部功能（重新生成标题、移动到助手、移动到文件夹、删除）
- 设置类页面统一为单行顶栏，去除大标题占位

### 工程

- 包名 `io.github.flanski.rikkarp`，与上游并存安装
- 移除 Firebase / 分析 / 内购等上游的商业化组件
- `:terminal` 模块复用预编译 `libtermux.so`，无需 NDK 交叉编译

## 构建

```bash
./gradlew :app:assembleGithubDebug
```

产物：`app/build/outputs/apk/github/debug/app-github-arm64-v8a-debug.apk`

需要注意两点：

1. **`google-services.json`**：`applicationId` 已改为 `io.github.flanski.rikkarp`，
   若沿用上游的配置文件，其中的 `package_name` 必须同步修改，否则
   `processReleaseGoogleServices` 会报 `No matching client`。
2. **构建工具链**：`compileSdk 37` + AGP 9.x（内置 Kotlin 编译器，
   不要再额外加 `org.jetbrains.kotlin.android` 插件；Compose 仍需
   `org.jetbrains.kotlin.plugin.compose`）。

## 技术栈

继承自上游：[Kotlin](https://kotlinlang.org/)、
[Jetpack Compose](https://developer.android.com/jetpack/compose)、
[Koin](https://insert-koin.io/)、[Room](https://developer.android.com/training/data-storage/room)、
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)、
[OkHttp](https://square.github.io/okhttp/)、
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)、
[Navigation 3](https://developer.android.com/guide/navigation/navigation-3)、
[Coil](https://coil-kt.github.io/coil/)

## 许可与合规

本项目以 **GNU Affero General Public License v3.0** 授权，完整条款见 [LICENSE](LICENSE)。

- 本项目是 RikkaHub 的修改版本，修改内容按 AGPL-3.0 第 5(a) 条以 git 历史标注
- 版权与来源声明见 [NOTICE](NOTICE)
- 分发本项目的二进制产物时，须同时提供对应源码
- 本项目**不声称**拥有上游项目的任何商标权利；「Rikka」仅用于标识代码来源

## 致谢

本项目建立在 [RikkaHub](https://github.com/rikkahub/rikkahub)（作者 re-ovo）之上，
感谢原作者与所有贡献者。

角色卡 V2/V3 格式、世界书等设计参考自
[SillyTavern](https://github.com/SillyTavern/SillyTavern) 社区（AGPL-3.0）。
