# AGENTS.md — anlibv 开发指南

本文件描述**当前仓库的实际状态**，不是上游 newBV 的历史文档。改动架构或功能边界时，必须同步更新本文件。

## 1. 项目定位

anlibv 把 [anlibili](https://anlibili-site-bxlxh4ai.teamily.run/) 的「网友推荐 B 站视频」列表做成 Android TV 客户端；播放、弹幕、字幕、评论沿用上游 newBV 的实现。

- 上游：`Frost819/newBV`（fork 自 `aaa1115910/bv`），仅作播放器/基础设施来源。
- 本仓库：`dakai/anlibv`。
- 同步策略：**手工挑补丁，禁止 `git merge upstream/main`**（见 §9）。

## 2. 身份与包名

| 项 | 值 | 文件 |
|---|---|---|
| 安装包标识 | `dev.dakai.anlibv` | `buildSrc/src/main/kotlin/AppConfiguration.kt` `applicationId` |
| namespace / Kotlin 包 | `dev.frost819.newbv.*` | 同文件 `appId` |
| 应用名 | `anlibv` | `app/src/main/res/values/strings.xml` |
| 图标源 | `icon.svg`（512²）、`banner.svg`（320×180） | 生成规则写在两个 SVG 的文件头注释 |

`applicationId` 与 `appId` 故意解耦：改 `applicationId` 就能与上游 newBV 并存安装；改 `appId` 会牵动全部模块 namespace 与 400+ 文件的包声明，**除非有明确需求，不要动**。

## 3. 功能边界

保留：

- anlibili 列表：11 个频道（推荐 / 新近好评 / AI / 知识 / 编程 / 科技 / 影视 / 运动 / 生活 / 游戏 / 动画）。
- 搜索：按标题 / UP 名在已拉取的站点条目里过滤，带标题联想；纯内存，不发搜索请求（见 §5.1）。
- 播放器：Media3 + 弹幕（`danmaku-engine`）+ 字幕 + 评论弹窗 + 一键三连、点赞、投币、收藏。
- 个人页：稍后再看 / 历史 / 收藏（需登录）。
- 设置：播放设置、界面设置、存储管理、设备信息、编解码、日志、关于。
- 扫码登录。

已删除，不要"顺手"加回：

| 功能 | 删除原因 |
|---|---|
| 首页 推荐/热门/动态 | 列表数据全部来自 anlibili |
| 分区 UGC、影视 PGC、直播 | 同上 |
| 视频详情页、UP 主页 | 卡片直接进播放器 |
| 追番 | 用户明确只要 收藏/历史/稍后再看 |
| 用户切换面板、关注列表 | 依赖已删页面 |

删掉的路由：`VideoDetailRoute`、`SearchResultRoute`、`LivePlayerRoute`、`LiveAreaRoute`、`LiveFollowRoute`、`UserSpaceRoute`、`FollowRoute`、`PgcFeatureRoute`、`UserSwitchRoute`、`SeasonPlayerRoute`。见 `app/ui/navigation/Routes.kt`。

## 4. 模块

| 模块 | 职责 |
|---|---|
| `app` | UI（Compose TV）、ViewModel、Hilt 装配、anlibili 数据源、播放器页面 |
| `core` | 日志、崩溃、主题、焦点工具、互动追踪 |
| `data` | DataStore 偏好（`Prefs` / `PrefKeys` / `PrefEnums`）、Room 实体 |
| `player` | Media3 封装、CDN 测速、播放内核接口 |
| `danmaku` | 弹幕 UI 层（Compose） |
| `danmaku-engine` | 自研弹幕渲染引擎（vendored，第三方代码，detekt 豁免） |
| `bili-subtitle` | 字幕解析（纯 JVM） |
| `bili-api` / `bili-api-grpc` | B 站 HTTP / gRPC 接口与 repository（纯 JVM） |
| `buildSrc` | `AppConfiguration`（包名、SDK、版本号） |

## 5. anlibili 数据流

```
BVApplication.onCreate (IO)
  └─ AnlibiliRepository.videos()          # 预取；当天已拉取则只读磁盘
AnlibiliViewModel.loadIfNeeded()          # 非强制，命中当天缓存
AnlibiliViewModel.reload()                # 菜单键：force = true
AnlibiliContent                           # 频道 + 4 列网格
```

数据源：`https://anlibili-site-bxlxh4ai.teamily.run/videos.json`（整表，约 6.8 MB、1900 条，无分页无鉴权）。

过滤链（顺序不可变）：`parse → isVisible() → filterNot(AnlibiliBlocklist::blocks) → 写缓存`。

关键文件：

- `app/data/anlibili/AnlibiliVideo.kt` — `@Serializable` 模型（`ignoreUnknownKeys`）。
- `app/data/anlibili/AnlibiliRepository.kt` — 拉取、缓存、频道规则（`AnlibiliChannel`、`inChannel`）。
- `app/data/anlibili/AnlibiliBlocklist.kt` — 内容屏蔽表（账号 + 关键词）。
- `app/viewmodel/anlibili/AnlibiliViewModel.kt` — 状态、频道切换、`bv → aid` 解析。
- `app/ui/screen/anlibili/AnlibiliContent.kt` — `TopNav` 频道 + `TvLazyVerticalGrid` + `SmallVideoCard`。

不变量：

1. **缓存文件名带版本号**（`AnlibiliRepository.CACHE_FILE = "anlibili-videos-v2.json"`）。改动过滤规则、屏蔽表或数据模型后必须 `+1`，否则当天会继续读旧规则生成的缓存。
2. **缓存按文件修改日期判断"今天"**，跨天自动重拉；`force` 用于菜单键刷新。
3. **顺序保持站点语义**：推荐流 = 社区条目原序 + 每 6 条插 1 条新近好评（`recommendOrder()`）。
4. 屏蔽表采用「宁缺勿滥」：只收明确的政治宣传账号与话术，不收「央视 / 香港 / 台湾 / 社会 / 美国」这类高频通用词，避免误杀纪录片、音乐与科技内容。新增条目只加进 `AUTHORS` / `KEYWORDS` 两个列表。
5. 命中数打印在日志：`Filtered anlibili videos: N visible, M blocked`。

### 5.1 搜索（内存过滤）

`SearchViewModel.loadIfNeeded()` → `AnlibiliRepository.videos()` → 内存里用 `AnlibiliSearch.kt` 的两个纯函数：

- `searchVideos(query)`：匹配标题或 UP 名，排序「标题前缀 > 标题包含 > UP 名包含」，同档保持站点顺序；空关键词返回空列表。
- `suggestTitles(query, limit = 6)`：标题去重后按命中位置排序，排除与关键词完全相同的标题。

归一化口径（小写 + 去空白）与屏蔽表一致。UI 状态机：输入 → 联想可见（`submitted = false`）；IME 搜索键 / 遥控器确定 / 点选联想 → `submitted = true` 显示结果网格；再次输入回到联想态。

焦点 key 前缀：anlibili 页 `ANLIBILI_FOCUS_PREFIX`、搜索页 `SEARCH_FOCUS_PREFIX`，两者共用 `AnlibiliVideoGrid` 与 `anlibiliFocusKey()`；新增页面复用网格时必须给新前缀，否则焦点恢复会串页。

## 6. 播放链路

```
卡片点击 → AnlibiliViewModel.play(video)
        → BiliHttpApi.getVideoInfo(bv = video.bv)           # 站点只有 BV 号
        → 效果 AnlibiliEffect.Play(aid, bvid, title, cover)
        → navController.navigateFromVideoCard(VideoCardData)
        → VideoPlayerRoute（popUpTo 保证播放器单例）
        → PlayerViewModel.init / loadVideoDetail / loadDanmaku / loadSubtitleList
```

- `app/ui/navigation/VideoNavigation.kt#navigateFromVideoCard` **只进播放器**，没有详情页分支；新页面不要往这里加路由，除非同时恢复详情页。
- `cid` 传 0 让播放器按历史恢复分 P；`PlayerScreens.kt` 里 7 步初始化顺序不可乱（`init → setCurrentAid → initVideoPlayer → danmaku.init → loadVideoDetail → 弹幕/字幕 → loadVideoWithResources`）。
- 弹幕设置：`Prefs.defaultDanmakuTypes` 为**空列表即弹幕关闭**。这个语义是反直觉点：`enabledTypes.isEmpty()` = 关（见 `VideoPlayerScreen` 的 `alpha`、`VideoPlayerController` 的图标与快捷键提示）。开启时写入具体类型，下次进入沿用。

## 7. 个人页与登录

- `MainScreen` 用 `MainSection` 切换内容区：`Anlibili` / `Search` / `Personal`；左侧栏 `LeftNaviContent` 只有 账号 / 精选 / 搜索 / 我的 / 设置 五项。
- `PersonalContent` = `TopNav`（`PersonalTopNavItem`：稍后再看 / 历史 / 收藏）+ 三个列表页；菜单键刷新当前 Tab。
- 数据层 `PersonalViewModel` 只在 `Prefs.isLogin` 为真时加载；未登录显示空态文案（上游行为，没有登录引导）。
- 接口：稍后再看 `/x/v2/history/toview*`、历史 `/x/v3/history`、收藏 `/x/v3/fav/folder*`（仓库在 `bili-api`，不要复制实现）。

## 8. 构建、签名、安装、验证

环境：

- JDK 21（AGP 9.1.1；本机 `/usr/lib/jvm/java-21-openjdk`）。
- Android SDK：compileSdk/targetSdk 36，minSdk 21；`local.properties` 写 `sdk.dir`（已 gitignore）。
- 签名：`signing.properties` + `key.jks`（均 gitignore）。缺签名时 release APK 装不上。

命令（仓库根目录）：

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/Sdk \
  ./gradlew :app:assembleRelease :app:testDebugUnitTest :data:testDebugUnitTest \
             :app:ktlintCheck :data:ktlintCheck :app:detekt
```

- 产物：`app/build/outputs/apk/release/anlibv_<versionCode>_<versionName>_release.apk`（命名在 `app/build.gradle.kts`）。
- `versionCode` = `git rev-list --count HEAD`，`versionName` = 最近 `v*` tag + 提交号（`AppConfiguration`）。仓库没有 tag 时回退 `0.0.0`。
- ktlint 会改写文件：先 `ktlintFormat`，再 `ktlintCheck`。

安装与验证（TV 走网络 ADB）：

```bash
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/release/anlibv_*_release.apk
adb shell am force-stop dev.dakai.anlibv
adb logcat -c && adb shell monkey -p dev.dakai.anlibv -c android.intent.category.LEANBACK_LAUNCHER 1
adb logcat -d | grep -i anlibili          # 期望：Filtered ... / Loaded ... from cache
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/s.png
```

- 清数据重来：`adb shell pm clear dev.dakai.anlibv`（同时清掉当天缓存与登录态）。
- D-Pad 验证顺序：列表 → 频道左右切换 → 卡片 CENTER 进播放器 → BACK 回列表；焦点状态用 `uiautomator dump` 读 `focused="true"`，不要靠截图猜。

## 9. 上游同步：手工挑补丁（禁止 merge）

`upstream` remote 指向 `https://github.com/Frost819/newBV.git`。合并上游会与「已删除文件」大面积冲突，因此按补丁处理：

```bash
git fetch upstream
git log --oneline HEAD..upstream/main -- app/src/main/kotlin/dev/frost819/newbv/app/player   # 先看范围
git show <sha> -- <paths> | git apply -3          # 首选：只取需要的 hunk
# 或者：git cherry-pick -n <sha> 后手工解冲突，再 git restore --staged 丢弃无关文件
```

规则：

1. 一次只挑一个主题（一个 fix 或一个 feat），挑完立即跑 §8 的四道闸（assemble / 单测 / ktlint / detekt）。
2. 我们改动过、冲突概率最高的文件：`MainScreen.kt`、`LeftNaviContent.kt`、`Routes.kt`、`AppNavHost.kt`、`VideoNavigation.kt`、`SmallVideoCard.kt`、`PlayerScreens.kt`、`VideoPlayerController.kt`、`ControllerVideoInfo.kt`、`GithubApi.kt`、`UpdateDialog.kt`、`AboutSetting.kt`、`SettingsScreen.kt`/`UISetting.kt`、`Prefs.kt`/`PrefKeys.kt`/`PrefEnums.kt`、`AppConfiguration.kt`、`strings.xml`、图标资源。
3. 上游若改了 anlibili 相关字段或过滤语义：同步后必须 bump `CACHE_FILE` 版本号。
4. 上游新增页面默认**不引入**，先判断是否属于 §3 的"已删除"清单。
5. 不需要把改动回推上游；上游 PR 模板与 `release.yml` 都绑定在 `Frost819/newBV`（CI 有 `if: github.repository == 'Frost819/newBV'`，在本仓库不会触发）。

## 10. 已知缺口

- 追番 Tab、视频详情页、UP 主页不存在；卡片长按只剩"加/移稍后再看"。
- 关于页「最新版本」显示 `Error`：更新检查指向 `dakai/anlibv`，该仓库无 Release；且 CI 不在此仓库触发。
- `docs/` 仍是上游架构的历史文档（PRD、开发计划），与本仓库现状不符，仅作背景资料。
- 屏蔽表是启发式（账号 + 关键词），不做语义判定；新出现的同类账号需手工加入。

## 11. 代码规范（精简版）

- Kotlin：4 空格缩进、上行尾逗号、`when` 分支穷举、`val` 优先；公共 API 写 KDoc（中文）。
- 注释解释 **why**，禁止无意义注释与注释掉的代码；临时妥协写成 `// shortcut: <限制>, <何时升级>`。
- 命名：类 `PascalCase`、函数/变量 `camelCase`、常量 `SCREAMING_SNAKE_CASE`、资源 `snake_case`、枚举值 `PascalCase`。
- 协程：`viewModelScope` + `StateFlow`；UI 层 `collectAsState()`（与本仓库既有写法一致）。
- Compose TV：列表项必须带 `focusSaverItem`，跨页返回用 `FocusSaver.RestoreFocus`；不要用 `FocusRequester` 指向不可聚焦容器（会"请求成功但焦点没动"）。
- 日志：模块名用 `Loggers.get("XxxViewModel")`；异常先判 `CancellationException` 再记录。
- 禁止：`!!`、`lateinit`（用构造注入/`by lazy`）、字符串拼接路由、在 UI 层直接发网络请求。

## 12. 排错速查

| 现象 | 检查 |
|---|---|
| TV 提示 `No route to host` | TV 待机/掉网：先 `ping`，再 `adb connect` |
| 列表为空 | `logcat | grep anlibili`：拉取失败（网络）或缓存文件损坏（会自动重拉） |
| 列表内容变少/变多 | 是否命中 `AnlibiliBlocklist`；`Filtered ... blocked` 数字变化 |
| 装不上 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不同）或 `VERSION_DOWNGRADE`（versionCode 回退） |
| 首帧无焦点 | `AnlibiliContent` 的首项聚焦 `LaunchedEffect` 是否被 `FocusSaver` 的旧 key 短路 |
| 搜索无结果 | 站点数据是否已加载（`loading`）；关键词是否被归一化后变空；确认没有把「搜索」误当成全表浏览（空关键词返回空列表） |
| 弹幕不显示 | `Prefs.defaultDanmakuTypes` 是否为空（默认关闭）；播放器中是否手动开启 |
| 个人页全空 | 是否已登录（`Prefs.isLogin`）；未登录不会请求接口 |
