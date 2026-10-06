# BandComic Wear OS 移植说明

本目录（`wearos/`）是 [bandcomic](https://github.com/kxkl2077/bandcomic) 的 **Wear OS 原生移植版**。
仓库根目录下的原始快应用源码**未做任何改动**，仍然可以按原方式用 `aiot-toolkit` 构建。

---

## 1. 为什么这是「移植」而不是「改配置」

原仓库是**小米 Vela OS 快应用**，不是 Android 应用：

| 原仓库 | 说明 |
| --- | --- |
| `src/**/*.ux` | 13 个 `.ux` 单文件组件（9132 行），Vela 私有 UI 框架 |
| `src/**/*.js` | 27 个 JS 模块（6167 行），`@system.*` 系统接口 |
| `src/manifest.json` | 快应用清单（`aiot-toolkit` 消费） |
| `docs/CUSTOM_SOURCE.md` | 漫画源 HTTP 协议（660 行） |

`.ux` 的模板语法、`@system.storage` / `@system.fetch` / `@system.file` 等接口在 Android 上都不存在，
因此不存在「把配置文件改一改就能跑在手表上」的路径。本次移植的做法是：

- **保留**：源协议（`docs/CUSTOM_SOURCE.md` 定义的 HTTP 契约）、数据模型语义、交互流程、URL 参数规则。
- **重写**：UI 层（`.ux` → Compose for Wear OS）、系统接口层（`@system.*` → Android API）。
- **丢弃**：依赖 Vela 固件的能力（LVGL 预解码、`ifLVGL`、网桥互联、快应用 OOBE 等），见第 6 节。

---

## 2. 工具链与版本决策

全部版本号都是 2026-09 从 Google Maven / Maven Central 的 **元数据实测得到**，不使用猜测版本。

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| Gradle | 9.8.0 | wrapper 已生成 |
| AGP | 9.4.1 | |
| Kotlin | 2.4.20 | |
| JDK | 25（Zulu） | |
| compileSdk | **37 + `compileSdkMinor = 1`** | |
| minSdk / targetSdk | 30 / 37 | |
| Compose BOM | 2026.09.00 | |
| Wear Compose | 1.7.0 | material3 + foundation + navigation |
| Coil | 3.6.3 | |
| OkHttp | 5.5.0 | |
| kotlinx-serialization | 1.11.0 | |
| DataStore Preferences | 1.2.1 | |
| Robolectric | 4.17 | 仅测试；在 JVM 上跑真实 Android 运行时 |
| androidx.test ext-junit / core | 1.3.0 / 1.7.0 | 仅测试 |

### 2.1 AGP 9 起 Kotlin 编译内置

AGP 9.0 之后**不允许**再申请 `org.jetbrains.kotlin.android`，否则 `gradle wrapper` 会直接报错：

```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```

因此 `gradle/libs.versions.toml` 的 `[plugins]` 只有三项：

```toml
android-application  = "com.android.application"
kotlin-compose       = "org.jetbrains.kotlin.plugin.compose"        # Compose 编译器插件仍需单独申请
kotlin-serialization = "org.jetbrains.kotlin.plugin.serialization"  # 同上
```

同时 `app/build.gradle.kts` 里**没有** `kotlin { compilerOptions { } }` 块：
内置 Kotlin 下 `jvmTarget` 默认跟随 `android.compileOptions.targetCompatibility`，
本工程已设为 17，再显式声明属于冗余。

### 2.2 compileSdk 必须写成「主.次」

Android 37 起平台按 `主.次` 版本发布。本机 SDK 里的目录是 `platforms/android-37.1`
（其 `source.properties` 中 `AndroidVersion.ApiLevel=37.1`），**并不存在** `android-37`。
若只写 `compileSdk = 37`，`checkDebugAarMetadata` 会以如下理由失败 28 项（每个 2026-09 版库一条）：

```
Dependency 'androidx.wear.compose:compose-material3:1.7.0' requires libraries and applications
that depend on it to compile against version 37 or later of the Android APIs.
:app is currently compiled against android-36.
```

AGP 9.4.1 的 `CommonExtension` 提供了 `compileSdkMinor`，因此必须写成：

```kotlin
compileSdk = 37
compileSdkMinor = 1
```

### 2.3 刻意不引入 Room / KSP

持久化**没有**使用 Room。原因是版本约束：当前 KSP 线（2.3.12）与 Kotlin 2.4.20 不匹配，
强行引入会把工具链钉死在一个别扭的组合上。本应用的数据形态也支撑这个选择：

| 数据 | 方案 | 位置 |
| --- | --- | --- |
| 漫画源列表 / Cookie | `kotlinx.serialization` + 原子 JSON 文件 | `filesDir/store/sources.json`、`cookies.json` |
| 阅读历史与进度（BookEntry） | 同上 | `filesDir/store/library.json` |
| 搜索历史 | 同上，上限 10 条 | `filesDir/store/search_history.json` |
| 应用设置 | DataStore Preferences | `comic_wear_settings` |

全部数据量都是「几十条记录」级别，且都是**整体读写**（没有按字段查询的需求），
文件级原子写比数据库更简单也更不容易出错。`JsonFileStore` 的写入是
`.tmp` + rename，并在读到损坏文件时把原文备份成 `<name>.corrupt-<时间戳>` 后回落默认值。

---

## 3. 目录结构

```
wearos/
├─ settings.gradle.kts          rootProject.name = "bandcomic-wear"
├─ build.gradle.kts
├─ gradle.properties            org.gradle.configuration-cache=false
├─ gradle/libs.versions.toml    版本目录（唯一版本来源）
├─ gradlew.bat / gradle/        wrapper（gradle-9.8.0-bin）
├─ local.properties             sdk.dir
└─ app/
   ├─ build.gradle.kts
   └─ src/
      ├─ main/AndroidManifest.xml
      ├─ main/res/              themes / colors / 图标 / strings(en) / strings-zh-rCN
      ├─ main/java/moe/yzf/comic/wear/
      │  ├─ ComicWearApp.kt               Application，持有 AppContainer
      │  ├─ MainActivity.kt               ComponentActivity + NavHost
      │  ├─ di/AppContainer.kt            手写依赖容器
      │  ├─ data/
      │  │  ├─ model/Models.kt            ComicSource / BookEntry / ComicDetail / SearchPage / ChapterImages
      │  │  ├─ net/JsonLite.kt            宽松 JSON 取值
      │  │  ├─ net/ImageUrls.kt           addUrlParam / addImageParams / addCoverParams
      │  │  ├─ net/ComicApi.kt            URL 构造 + 请求 + 解析 + 错误分类
      │  │  ├─ source/SourceConfig.kt     /config 解析与校验
      │  │  ├─ store/                     JsonFileStore / SourceStore / LibraryStore
      │  │  │                             / SearchHistoryStore / SettingsStore
      │  │  └─ repo/ComicRepository.kt    面向 UI 的用例层
      │  └─ ui/
      │     ├─ theme/Theme.kt
      │     ├─ common/Design.kt           视觉令牌（调色板/尺寸）+ Canvas 自绘图标
      │     ├─ common/Chrome.kt           页头（.time/.title）、输入胶囊、列表卡片、分页
      │     ├─ common/Rotary.kt           表冠 → 任意可滚动容器
      │     ├─ common/Toast.kt            对应原版 prompt.showToast 的瞬时提示
      │     ├─ common/ErrorText.kt         错误文案映射
      │     ├─ AppViewModel.kt
      │     ├─ nav/AppNav.kt               Routes + SwipeDismissableNavHost
      │     ├─ home/HomeScreen.kt          首页（index.ux：搜索优先）
      │     ├─ input/InputScreen.kt        输入页（对应原版 ime 页，改用系统输入法）
      │     ├─ search/SearchScreen.kt
      │     ├─ detail/DetailScreen.kt
      │     ├─ reader/ReaderScreen.kt
      │     ├─ history/HistoryScreen.kt    阅读历史（原版在 offline.ux 的其中一个标签页）
      │     ├─ cache/CacheScreen.kt        本地漫画（原版 offline.ux 的另一个标签页）
      │     ├─ download/DownloadScreen.kt  章节选择 + 下载进度（原版 download.ux）
      │     ├─ sources/SourcesScreen.kt
      │     └─ about/AboutScreen.kt        关于 + 设置（原版同页）
      └─ test/java/moe/yzf/comic/wear/
            ├─ TestHttpServer.kt           零依赖本地 HTTP 服务（下载用例的靶子）
            ├─ ProtocolContractTest.kt
            ├─ StorePersistenceTest.kt
            ├─ CacheStoreTest.kt
            └─ DownloaderTest.kt
```

规模：36 个 Kotlin 文件 / 约 5400 行（不含测试；测试另有 11 个文件 / 约 1650 行）。

---

## 4. 分层与数据流

```
Compose Screen
   ↓ 只读 StateFlow / 调 suspend 用例
AppViewModel            settings, shelf, currentSource, sources, searchHistory
   ↓
ComicRepository         授权校验 + Dispatchers.IO + 异常收敛为 ApiException
   ↓
ComicApi                URL 构造 / OkHttp 请求 / JSON 解析 / 错误分类
   ↓
SourceStore · LibraryStore · SettingsStore
```

`AppContainer` 在 `Application.onCreate` 里装配全部单例，并**同步**加载三个小 JSON 文件
（避免首次网络请求时在 OkHttp 线程里做文件 IO）。全程没有 `runBlocking`，
DataStore 的读取走 `flow`，不存在主线程阻塞死锁。

### 4.1 错误分类

`ApiErrorType` 区分 `TIMEOUT / SSL / DOMAIN / CONNECTION / HTTP / PARSE / UNKNOWN`，
由 `ComicApi.classify` 归类（注意 `SSLException` 必须先于 `IOException` 判断，
否则会被父类吃掉）。`ErrorText.kt` 把它映射成本地化文案，
所以界面拿到的是「网络超时」而不是一串英文异常。

### 4.2 协议补全与 HTTPS → HTTP 回落

用户输入页沿用原版 `edit.ux` 的 `normalizeApiUrl`，会把 `https?://` 前缀和尾部 `/`
一并剥掉。原版在请求时用 `protocol + "://" + context + "/config"` 把协议拼回来，
移植时漏掉了这一步，`buildConfigUrl` 只做纯字符串拼接，于是
「在源管理页输入 `comic.kxkl2024.cn`」会拼出没有 scheme 的
`comic.kxkl2024.cn/config`。OkHttp 在 `Request.Builder().url()` 阶段直接抛
`IllegalArgumentException`，而它既不是 `IOException` 也不是 `ApiException`，
被 `classify` 归入 `else -> UNKNOWN`，用户只看到一句「未知错误」。

修复后 `buildConfigUrl` 补默认 `https://`（输入自带协议则原样保留），
单测除了断言字符串，还用 `toHttpUrlOrNull()` 断言**每个结果都能被 OkHttp 解析**——
回归点正是上面那个异常抛出的位置。

`fetchText` 在 HTTPS 失败时会尝试同一地址的 HTTP 版本。回落条件对齐原版
`fetchSourceConfig`：仅当失败类型是 **SSL（证书）或 CONNECTION（连接）** 时才降级；
`DOMAIN` / `TIMEOUT` 原样上报，避免把「域名打错了」误判成「需要降级」。
回落一旦发生，`AddSourceResult.insecure` 置真，源管理页会**显式提示**
导入的源降级到了明文 HTTP，而不是静默接受。

> 该缺陷是实测暴露的：`https://comic.kxkl2024.cn` 这类自带源的 `/config` 正常返回
> 200，但图片接口 `/photo/...` 返回 **503** 且带官方反破解文案
> （`{"code":210,"message":"請到官網更新最新APP…"}`）。也就是说，
> **添加失败是客户端的协议缺陷，看图失败是服务端的按 IP 限制**，两者互不相关，
> 排查时不要混为一谈。

---

## 5. 协议保真度

`docs/CUSTOM_SOURCE.md` 是本次移植的黄金契约。以下行为逐条对齐（并有单测覆盖）：

| 协议要求 | 实现 |
| --- | --- |
| `{apiUrl}{detailPath}`，`<id>` 替换 | `ComicApi.buildDetailUrl` |
| `{apiUrl}{searchPath}`，`<text>`/`<page>` 替换，`<text>` 走 `encodeURIComponent` | `buildSearchUrl`（空格编码为 `%20` 而非 `+`） |
| `{apiUrl}{photoPath}`，`<id>`/`<chapter>` 替换 | `buildPhotoUrl` |
| `GET {api}/config` 返回 `{key: {...}}` 映射，`key` 取自外层 map 键 | `parseSourceConfig` |
| 输入地址剥掉协议后，请求前补回默认 `https://` | `buildConfigUrl`（见 §4.2） |
| `using` / `type` / `__proto__` / `prototype` / `constructor` 为保留键 | `RESERVED_KEYS`（含原型污染键） |
| 源 key：1–80 字符、首尾无空白、不含路径分隔符/尖括号/控制字符 | `isSourceKey` |
| `apiUrl` 为 http(s) 基地址；端口 1–65535；IPv4 段 ≤255；禁 `/./` 与 `/../` | `isBaseUrl` |
| 路径必须以**单个** `/` 开头，禁空白、`#`、`\`，禁目录穿越 | `checkPath` |
| 占位符白名单：`<id>` `<chapter>` `<text>` `<page>`，且必填项齐全 | `checkPath` |
| `idType` ∈ `numeric` / `uuid` / `gid_token` / `slug` / `string` | `validateEntry` → `ComicSource.idType` |
| 输入是否为漫画 ID：按 `idType`，否则按 `type`/`key` 推断（MangaDex→UUID、E-Hentai→`gid_token`、拷贝漫画→slug），都不匹配则 `^\d{1,20}$` | `isComicId` |
| 图片参数 `width` / `quality` / `ifPNG` | `addImageParams(url, width, quality, usePng)` |
| 封面固定 `width=80` | `addCoverParams(url, quality, usePng)` |
| 参数原位替换、不重复追加、保留 `#fragment` | `addUrlParam` |
| 请求带 `User-Agent` 与当前源 `Cookie` | OkHttp 拦截器统一注入 |
| `item_id` / `page_count` / `views` / `rate` 允许 number 或 string | `JsonLite` 一律按文本读取 |
| 章节图片为空视为失败 | `parseChapterImages` 抛 `ApiException(PARSE)` |

### 5.1 相对快应用版的**有意偏离**

1. **修复了 URL 以 `?` 结尾时的拼接缺陷。** 快应用版 `imageUrl.js` 的 `addUrlParam`
   在地址已带 `?` 时会多插一个分隔符，产生 `...jpg?&width=480`。移植版修正为单分隔符。
2. **丢弃 `ifLVGL` 与 `.bin` 后缀。** 这是 Vela 固件用 LVGL 预解码的私有约定，
   Wear OS 侧由 Coil 解码，无对应能力（详见第 6 节）。
3. **ID 判定改为按源判定，并让显式 `idType` 真正生效。** 快应用版 `submitSearch`
   用 `/^\d{1,20}$/` 一刀切判断输入是不是漫画 ID，于是 MangaDex 的 UUID、
   E-Hentai 的 `gid_token`、拷贝漫画的 slug 全被错当成关键词丢去搜索。
   移植版跟随上游 78919b9 改为按源的 `idType` / `type` / `key` 判定。
   但上游 `isComicId` 把 key 的通配判断与 `idType` 并排放在同一个 `||` 链里，
   `key` 含 `mangadex` 时会盖掉显式声明的 `idType`，与它自己文档写的
   「`idType` 可选…**默认识别**：MangaDex 匹配 UUID」矛盾。移植版按文档语义实现，
   让显式声明优先——有单测钉住这条分歧（`显式_idType_优先于_key_推断`）。
   副作用：选中 MangaDex 后输入 `12345` 不再直连一个必然 404 的详情，而是走搜索。

---

## 6. 丢弃的 Vela 专属能力

| 能力 | 原实现 | 丢弃原因 |
| --- | --- | --- |
| LVGL 预解码与 `.bin` 图片 | `ifLVGL` 参数 | Vela 固件私有；Wear OS 用 Coil |
| 网桥 / AstroBox 互联 | `@system.interconnect` | 无对应 Android API |
| 快应用 OOBE 引导 | 快应用平台流程 | Wear OS 无此概念 |
| 应用内检查更新 | 下载快应用 `.rpk` | 由应用商店负责 |
| 下载 / 离线漫画库 | `@system.file` 分片写 | 本期范围外（见第 8 节） |
| 系统级分享/互传 | 快应用能力 | 非核心闭环 |

---

## 7. 关键实现取舍

### 7.1 Wear Material3 的 API 现实

Wear Compose Material3 1.7.0 与手机版差异很大，以下都是**从 AAR 的 `classes.jar`
实测**得到的结论（不是猜的）：

- **没有 `Chip`**。源码里任何 `Chip` 用法都必须换成 `Button` / `Card`。
- **没有 `TextField`**。输入框用 `androidx.compose.foundation.text.BasicTextField` 自绘
  `decorationBox`（搜索框、源地址输入框都走这条）。
- 实验性标记名是 **`ExperimentalWearComposeMaterial3Api`**，不是 `ExperimentalWearMaterial3Api`。
- `ColorScheme` **没有 `surfaceDim`**。
- `Button` 没有 `secondaryLabel`；设置项的状态值直接拼进 `label`。
- `rememberPickerState` 的第二个参数**不能**用 `initiallySelectedOption` 命名传参，按位置传。
- 翻页指示器是 `HorizontalPageIndicator(pagerState)`，**不存在** `PositionIndicator`。
- `AlertDialog` 的正文内容槽是 `ScalingLazyListScope`，因此 Picker 要包在 `item { }` 里。

### 7.2 量表冠（rotary）

- **整页模式**：用 `androidx.wear.compose.foundation.pager.HorizontalPager`，
  它自带表冠吸附翻页，比在通用 pager 上手工接旋转事件可靠得多。
- **连续模式**：`LazyColumn` 不认表冠，显式接 `Modifier.onRotaryScrollEvent`
  并用 `focusRequester` + `focusable()` + `onPlaced { requestFocus() }` 把焦点交给列表——
  少了这一步旋转事件根本不会派发到列表上。

### 7.3 缩放：双指捏合 + 面板滑杆

两条路都能缩放，取值 1.0x..3.0x：

- **双指捏合**（本轮新增）。原版只有滑杆，Wear 上没有捏合。
- **设置面板里的步进滑杆**，对齐原版（`photo_size_change`）。

手势分工必须分得很清，否则会和 Pager 抢事件：

| 手指 | 行为 | 是否消费事件 |
| --- | --- | --- |
| 两根 | 捏合缩放 + 按两指质心平移 | 全部吞掉 |
| 一根且已放大 | 平移 | 吞掉（此时 Pager 滑动已被 `userScrollEnabled = !zoomed` 关掉） |
| 一根且未放大 | 什么都不做 | **不吞**，把左右滑动让给 Pager 翻页 |

三个必须守住的实现细节：

1. **`pointerInput` 的 key 必须是 `Unit`，绝不能 key 在缩放值上。** 缩放实时在变，
   一旦 key 变化，手势识别器会在捏合过程中被重启，手感直接断掉。
   最新的缩放与回调通过 `rememberUpdatedState` 取。
2. **缩放只在 `graphicsLayer` 里读，不在组合里读。** 缩放值放进 `MutableFloatState`，
   握在 `ReaderScreen` 里向下传的是 state 对象而不是 Float；`graphicsLayer` 属于绘制阶段，
   于是捏合只触发重绘。否则每一帧都会重组整屏（含 Pager 与所有页），这是捏合卡顿的主因。
   只有「是否已放大」这个布尔量经 `derivedStateOf` 参与组合，
   跨越 1x 边界时才重组 Pager。
3. **相邻页必须读另一个恒为 1x 的状态，不能共用当前页的缩放状态。**
   `beyondViewportPageCount` 会把相邻页一起组合，它们和当前页**渲染在同一个坐标系里**，
   共用状态就等于共用缩放——现象是「横向排开的所有页一起放大」。
   所以每页按 `index == pagerState.currentPage` 取 `zoomState` 或 `flatZoom`。
   （这个 bug 其实一直存在：以前缩放只能靠面板滑杆调，而面板挡着画面所以看不出来；
   换成捏合后第一下就暴露了。）

翻页时还会先把缩放收回 1x（`goToPage`）：放大时 Pager 滑动被关掉，翻页只能走动画，
而相邻页是 1x、落位后当前页却会吃缩放值，不收回的话动画结束会跳一下。
注意这只处理**翻页**；换章的缩放由 `keepDefaultZoom` 决定（该设置的文案就是
「切换章节保持缩放比例」），两者互不干扰。

平移会被夹在 `容器尺寸 × (zoom-1)/2` 之内，防止把图片拖到看不见。
（`ContentScale.Fit` 下图片未必铺满容器，所以这是个略宽松的近似。）

圆屏上那条滑杆用「‹ 数值 ›」步进代替细条 `Slider`——细条在 466px 圆屏上
可点面积太小，步进既好点按又与滑杆语义等价；显示的是真实倍率（`1.0x`）而不是
0..100 的刻度值。

### 7.4 图片适配

正文与封面都走 `AsyncImage(ContentScale.Fit/Crop)`，高度由容器约束，
不依赖图片原始像素高，避免页面之间出现空隙。
另外 coil3 的 `AsyncImage` **没有** `painter` 重载，
需要画笔状态时用 Compose 的 `Image(painter = ...)`。

Coil 侧刻意**关掉 crossfade**：漫画翻页要的是「立刻看到」，淡入那一下反而像卡了一帧；
相邻页由 `beyondViewportPageCount` 预取，不需要靠淡入掩盖加载。

### 7.5 进度落盘：去抖

阅读进度**不是**每翻一页写一次盘。翻页会不断重启
`LaunchedEffect(comicId, chapter, page, urlCount)`，只有停下来 600ms 才真正调一次
`upsertShelf`；离开当前章（换章或退出阅读器）时再由 `DisposableEffect(comicId, chapter)`
的 `onDispose` 补一次最终值，保证进度不丢。

这样 40 页的章节最多写几次，而不是 40 次。写盘本身走 `Dispatchers.IO`，
但仍会与图片加载抢 I/O，所以去抖是有效果的。

> 注：文档早先版本写的是「`onDispose` 落盘从而避免每页写一次」，那是错的——
> 当时的 `DisposableEffect` key 里含 `page`，翻页即 dispose，等于每页都写。

### 7.6 章节与页码直达

原版的章节切换只有左右箭头 ±1，页码是数字滚轮。
这里保留「‹ › 逐格微调」，**并把中间的数字做成可点**：
点章节号弹出全量章节网格，点页码弹出全量页码网格，点哪个去哪个。

133 章的漫画原来要点 132 次，现在是两次点击。浮层与下载页的章节网格同一套视觉
（3 列、圆角、选中态），接表冠滚动（`Modifier.rotaryScroll`），
打开时自动落到当前值所在行，右上角有关闭键、点面板外也可关闭。

### 7.7 界面保真：整轮重做（本轮）

上一版的数据层（协议、存储、错误分类）是对齐的，但**视觉与交互是凭空设计的**，
与原版快应用无关，因此被判定为「丑」。本轮把 UI 完全改为从 `.ux` 源码反推：

- **换算依据**：原版 `manifest.json` 的 `config.designWidth = "device-width"`，
  即 CSS px 与设备像素 1:1；目标手表 466x466 @320dpi（密度 2.0），
  所以 **dp = 原版 px / 2**。全部尺寸令牌集中在 `ui/common/Design.kt`，
  逐条标注来源（如 `.result-item height: 120px` → `Dim.cardH = 60.dp`）。
- **首页**：原版首页**不是书架，而是搜索优先的落地页**。已按 `index.ux` 重建：
  时钟 → 应用名（圆屏点击进「关于」）→「当前源: X」+ 换源(`toggleSource` 循环)
  + 编辑 → 居中输入胶囊（256x54px → 128x27dp）→ 横向滚动搜索历史词条
  （最多 5 条 + 二次确认清空）→ 底部居中「更多」进阅读历史。
- **列表卡片**：搜索/历史统一为高 60dp、圆角 12dp、`#262626` 底、
  封面 30x40dp、标题 12sp 最多三行、副标题 10sp 白色 60%。
- **详情页**：封面宽 = 屏宽 x 3/4、比例 3:4、圆角 12dp；「点击封面开始阅读」提示；
  资料行是 18dp 圆角、`#262626` 底、1.5dp `rgba(255,255,255,.24)` 描边的胶囊。
- **阅读器**：圆屏整屏进度圆弧（`#4fc3f7`，底环 `rgba(255,255,255,.15)`）、
  点图显隐顶栏、左右边缘 `‹ ›` 翻页、底部居中 `✓` 展开设置面板
  （面板宽 67%、`rgba(38,38,38,.6)`、圆角 18dp）。
- **关于页**：与原版一致，把「关于信息」和「设置」放同一页；设置项高 56dp、
  圆角 18dp、标题 16sp 加粗 + 说明 10sp 白色 60%，右侧为控件。
- **编辑漫画源**：列表项高 42dp、圆角 18dp、`rgba(38,38,38,.8)` 底，
  内置源不显示删除图标；底部居中加号进输入页，域名/IP 校验后拉取 `/config`。
- **阅读历史**：原版这段列表在 `offline.ux` 的「阅读历史」标签下
  （本地漫画部分不在 v1 范围），因此单独成页，但沿用其交互：
  卡片横滑露出删除按钮、删除需二次点击、底部分页。
- **图标**：全部用 `Canvas` 自绘（`Design.kt`），不引入 material-icons 字体，
  尺寸完全可控，也避免圆屏上字体图标裁切。
- **文案**：`values/values-zh-rCN` 的键值逐条对齐原版 `i18n/zh-CN.json`
  与 `defaults.json`（如 `elsePlaceholder = 输入漫画ID/关键词`、
  `homeTip = 试试搜索关键词；到「关于」页可运行快速检查`）。

与原版的**有意偏离**（都是平台能力差异，语义不变）：
滑杆/滚轮改为圆屏友好的步进；原版自绘的全屏输入法改为系统输入法
（Wear OS 侧 `com.sogou.ime.wear` 可用），因此保留了独立的输入页与
「确认/取消」回传语义（对应原版 `global.__imeResult`）。

---

## 8. 实测验证记录

### 8.1 线上协议实测（2026-10）

对内置源发起真实请求。`https://mangadex.yzf.moe/config` 返回 **308** 跳转到
`https://mangadex.yuzifu.top/config`，后者返回 200：

```json
{"MangaDex":{"apiUrl":"https://mangadex.yuzifu.top","detailPath":"/comic/<id>",
"name":"MangaDex","photoPath":"/photo/<id>/ch/<chapter>",
"searchPath":"/search/<text>/<page>","type":"mangadex"}}
```

**关键确认**：条目内**没有 `key` 字段**，源 key 来自外层 map 键——移植版的
`parseSourceConfig` 正是这样实现的。

搜索 `GET /search/one/1`：

```json
{"has_more":true,"page":1,"results":[{"comic_id":"595e3a7a-...","cover_url":".../cover",
"pages":0,"title":"Tennis no Oujisama - Onecoin Reserve (Doujinshi)"}]}
```

详情 `GET /comic/{id}`：

```json
{"cover":".../cover","item_id":"595e3a7a-...","name":"...","page_count":30,
"rate":8.07,"tags":["Boys' Love","Doujinshi"],"total_chapters":1}
```

**注意两点**：`rate` 是 JSON **number**（`8.07`）而不是字符串；响应里**没有 `views`**。
两者都被 `JsonLite` 的文本读取方式正确处理。

章节图片 `GET /photo/{id}/ch/1` 返回 `{"images":[{"url":".../1.jpg"}, ...]}`；
封面 `GET /comic/{id}/cover` 返回 `200 image/jpeg`。

### 8.2 自动化测试总览

10 个测试套件 / 91 个用例，全部跑在 JVM 上（不需要真机或模拟器）：

| 套件 | 用例 | 覆盖内容 |
| --- | --- | --- |
| `CacheStoreTest` | 19 | 缓存目录布局、索引跨实例持久化、按源隔离、真实文件数重扫、完整/部分判定、目录占用统计 |
| `SourceConfigTest` | 17 | `/config` 校验规则（key、apiUrl、路径、占位符、保留键）与 `idType` 五种形态的 ID 判定 |
| `DownloaderTest` | 13 | **起真实 HTTP 服务 + 落真实文件**：整章下载、断点续传、单页重试、确定性 4xx 不重试、整章失败隔离、取消语义 |
| `ProtocolContractTest` | 11 | 真实报文的协议解析、URL 构造（含补协议回归）、图片参数边界 |
| `StorePersistenceTest` | 10 | 真实文件读写、跨实例持久化、原子写、损坏自愈 |
| `AppLaunchTest` | 6 | 真实 Application 启动、容器装配、默认值、UA 形状 |
| `AppViewModelTest` | 6 | 状态流接线、书架进度、搜索历史、源切换、按源判定 ID |
| `MainActivityRenderTest` | 5 | 真实 Activity 组合渲染（zh-rCN 227dp 圆屏）、首页标题/输入占位/当前源/引导文案/底部双入口 |
| `SettingsStoreTest` | 3 | DataStore 往返、越界钳制、默认值 |
| `RobolectricSmokeTest` | 1 | Robolectric 在最简路径上可用 |

```
:app:testDebugUnitTest  →  tests=91  failures=0  errors=0  skipped=0
```

### 8.3 运行时验证（Robolectric）

**这是本次移植最强的验证手段**：Robolectric 4.17 能在 JVM 上加载**真实的
`ComicWearApp` Application 与 `MainActivity`**，因此下列断言等价于
「手表上点开图标会不会崩」，而不是对着 mock 自说自话。

- `AppLaunchTest` 走的是清单里声明的 Application，验证 `AppContainer` 装配、
  三个 JSON 存储加载、DataStore 初始化、OkHttp 拦截器构建、Coil 单例安装
  全链路不抛异常，且内置 MangaDex 源被正确植入、`using` 不悬空。
- `MainActivityRenderTest` 启动**真实 `MainActivity`**，跑通
  `ComicWearTheme` → `AppViewModel` → `SwipeDismissableNavHost` → 首页
  的完整启动链路，并断言首页标题、输入占位、当前源与引导文案确实渲染出来。屏幕按 zh-rCN
  454×454 圆屏（≈227dp）配置，用来确认圆形屏下组合不炸。
- `StorePersistenceTest` 用真实临时目录，因此「新建实例能读到上一个实例写的
  数据」才真正证明落盘成功；损坏文件的备份与回落也在这里被钉死。

#### 8.3.1 跑起来需要两个环境开关

**模块开放**（JDK 17+ 的模块封装会挡住 Robolectric 反射访问 JDK 内部 API）：

```
IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets
  (in module java.base) because module java.base does not export jdk.internal.access
```

已在 `app/build.gradle.kts` 里给 `Test` 任务加好 `--add-opens` / `--add-exports`
（`java.base` 的 `java.lang`、`java.io`、`java.nio`、`jdk.internal.access`、`jdk.internal.ref`）。

**Compose 渲染测试降到 API 34**：Compose 测试在 Robolectric 下会走 Espresso 的
`onIdle`，而它反射调用 `android.hardware.input.InputManager.getInstance()`——
这个方法在 API 37 上已不存在：

```
NoSuchMethodException: android.hardware.input.InputManager.getInstance()
  at androidx.test.espresso.Espresso.onIdle
  at androidx.compose.ui.test.RobolectricIdlingStrategy.runUntilIdle
```

渲染验证不依赖 API 37 特性，因此 `MainActivityRenderTest` 用 `@Config(sdk = [34])`
运行；**其余套件仍跑在 API 37**（`robolectric.properties` 里 `sdk=37`），
与应用 targetSdk 一致。

### 8.4 构建与产物校验

```
:app:assembleDebug  →  BUILD SUCCESSFUL
```

`aapt2 dump badging` 对最终 APK 的校验结果：

```
package: name='moe.yzf.comic.wear' versionCode='1' versionName='1.0.0'
compileSdkVersion='37'
minSdkVersion:'30'   targetSdkVersion:'37'
uses-permission: android.permission.INTERNET
uses-permission: android.permission.ACCESS_NETWORK_STATE
uses-feature: android.hardware.type.watch
launchable-activity: moe.yzf.comic.wear.MainActivity
```

清单里同时声明了 `com.google.android.wearable.standalone = true`
（独立运行，不依赖手机端 App）与 `uses-library com.google.android.wearable`（`required=false`）。
中英文字符串各 89 键，键集合完全一致，无缺失。

### 8.5 仍然未能验证的部分

**真机上已装、已启动，但界面截不到。** 可用设备只有一台 OPPO OWW231 手表
（Android 11 / SDK 30 / armeabi-v7a / 466x466 @320dpi，ColorOS Watch 而非 Wear OS）。
`adb install -r` 成功，`am start` 后 `dumpsys window` 显示
`mFocusedApp=...moe.yzf.comic.wear/.MainActivity`、`pidof` 有进程、crash buffer 为空——
**应用确实在前台运行且未崩溃**。

但该手表此刻放在充电座上，ColorOS 的充电界面
（`SysUI.Charging`，package `com.heytap.wearable.systemui`）**长期占着窗口焦点**，
盖住了应用窗口。实测以下手段都无效：`KEYCODE_BACK`、点按、上滑、
屏幕关开、再次 `am start`、`CLOSE_SYSTEM_DIALOGS` 广播，
`mCurrentFocus` 始终是 `SysUI.Charging`。因此**这一轮的视觉验证只能停在安装与启动**，
要看实际画面需把表从充电座上取下。

本机也无法用模拟器替代：`HypervisorPresent = False`（i7-4770，未启用 WHPX/HAXM），
SDK 里没有可用的 Wear 系统镜像。

因此以下项目**只能在取下手表后确认**：

- **GPU 实际渲染结果**。Robolectric 不真正光栅化，圆形屏裁切是否美观、
  文字是否被圆边切掉，这类视觉问题它看不出来。
- **表冠（rotary）手感**。旋转事件派发、`HorizontalPager` 的吸附行为、
  连续模式下 `onRotaryScrollEvent` 与焦点抢不抢得到，都依赖真实输入设备。
- **系统输入法是否真的弹出**。首页输入胶囊会跳到输入页并请求焦点，
  但该表上的输入法是 `com.sogou.ime.wear`，是否正常上屏需要在设备上点一次确认。
- **真机网络与 Coil 磁盘缓存命中**。该表当前**无网络连通性**
  （`ping 8.8.8.8` 全丢包，需经 `com.heytap.wearable.bluetooth.net.proxy` 走手机代理），
  所以搜索/详情/正文的真实请求尚未在设备上跑通，协议保真度依据的是主机侧实测（见 8.1）。
- **功耗与内存**。整话几十页图片在手表上的表现。

### 8.6 真机端到端实测（缓存 / 下载）

手表仍无法联网，因此这一轮把 APK 装到一台**手机**（OPPO PKG110 / Android 17 / SDK 37）
上做真实链路验证——链路本身与屏幕尺寸无关，手机能跑通即证明代码可用。

**下载闭环已跑通，用的是用户自己的源**（`comic.kxkl2024.cn` / CopyManga）：

```
files/comics/CopyManga_biedangounijiangle/1/  →  1 … 14（14 个真实 JPEG，30–70 KB）
files/comics/CopyManga_biedangounijiangle/cover
```

索引 `files/store/comic_cache.json` 与之严格一致：

```json
{"id":"biedangounijiangle","sourceKey":"CopyManga","name":"別當歐尼醬了！",
 "totalChapters":133,
 "chapters":[{"num":1,"name":"第01话","pageCount":14,"downloaded":14}],
 "size":662903}
```

即：**「页数 14 / 已下载 14」与磁盘上 14 个文件完全对应**，`size` 是真实递归统计值
（不是估算）。手工走了一遍 UI：首页 →「下载漫画」入口 → 详情页「下载漫画」按钮 →
章节网格 → 全选（格子变绿、按钮翻成「反选」、右侧变「开始下载(1)」）→ 开始下载。

**MangaDex 侧的对照观察**：拿搜索结果里的 `ONE`（id `5b0a8d2f-…`）试同样流程，
只落了 `cover`，章节目录没建。查索引是
`{"num":1,"name":"","pageCount":0,"downloaded":0}`——该条目在**详情页本身的「页数」就是 0**，
即 MangaDex 这条记录没有可下的图片章节，属于源数据特性而非下载器缺陷。已确认源码/取不到图时
`Downloader` 会正常走「整章失败」分支并在 `Finished.failed` 里回报，不会留下半截脏数据。

**顺带说清一个误判**：`/photo/...` 对 `comic.kxkl2024.cn` 返回 503 的问题（见 4.2）
只出现在**主机侧**探测时；设备侧用真实 UA/Referer/Cookie 请求同一源可以正常取图。
即该 503 是服务端按请求特征做的风控，不是协议实现错误。

---

## 9. 构建与安装

```powershell
$env:JAVA_HOME   = "C:\Program Files\Zulu\zulu-25"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd wearos
.\gradlew.bat :app:assembleDebug
```

产物：`wearos/app/build/outputs/apk/debug/app-debug.apk`

安装到手表（需先开启开发者选项与 ADB 调试，并配对好 ADB）：

```powershell
adb install -r wearos\app\build\outputs\apk\debug\app-debug.apk
```

单测（91 个用例，JVM 上跑，不需要设备）：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests "*ProtocolContractTest*"
.\gradlew.bat :app:testDebugUnitTest --tests "*MainActivityRenderTest*"
```

测试需要 `--add-opens` / `--add-exports` 才能在 JDK 17+ 上跑（原因见 8.3.1），
这些参数已经写在 `app/build.gradle.kts` 的 `Test` 任务里，直接执行即可。

---

## 10. 下载与离线库

原版把「下载」和「离线」拆成 `pages/download` 与 `pages/offline` 两个页面，
本移植沿用同一分工：`download/DownloadScreen.kt`（选章 + 进度）与
`cache/CacheScreen.kt`（已下内容的管理）。核心逻辑在
`data/download/Downloader.kt` 与 `data/store/CacheStore.kt`，两者都不依赖 Android 框架，
因此能在 JVM 上被真实地测（见 8.2）。

### 10.1 与原版逐条对齐的行为

- **章节窗口 18 章**、每行 3 格（`LazyVerticalGrid(GridCells.Fixed(3))`），
  格子圆角与描边取自原版 `download.ux` 的 `.chapter-item`。
- **三态配色**（且 `dl-full`/`dl-partial` 必须排在 `selected` 之前，否则选中态压不住）：
  完成 `#17394D` + 边框 `#4FC3F7`；部分 `#4D3A17` + 边框 `#FFA726`；选中 `#4CAF50`。
- **全选 / 反选两态按钮** + `开始下载(N)`，两枚叠放在圆屏底部。
- **进度文案**复用原版键：`第 {page}/{total_page} 页`、百分比由 `page/page_count` 取整。
- **串行下载**，`MAX_RETRY = 3`；HTTP 4xx（408/429 除外）视为确定性错误立即放弃重试。
- **退出页面即取消**：`DisposableEffect` 在 `onDispose` 调 `cancelDownload()`。
- **封面单独存**（`{comicDir}/cover`），不随章节目录走。

### 10.2 存储布局

```
files/comics/{sourceKey}_{comicId}/cover          封面
files/comics/{sourceKey}_{comicId}/{章节号}/{页码}  正文页
files/store/comic_cache.json                      索引
```

`sourceKey` 参与目录名，因此同名 id 在不同源下互不覆盖（有专门用例钉死）。

**与快应用版的一处有意偏离**：原版章节目录名是 `{num}　{name}`（全角空格分隔）,
本移植只用 `{num}`，章节名存索引里。理由是 Wear 侧不需要与 Vela 的下载产物互操作，
少一层名字清洗就少一类跨文件系统非法字符问题（`sanitize` 仍然作用于目录名）。

### 10.3 取消语义（本轮修掉的两个真问题）

写测试时暴露了两个**看起来能跑、实际不对**的缺陷，都已修复并被用例覆盖：

1. **取消不生效**。`OkHttp` 的 `execute()` 是阻塞调用，协程取消打不断它；
   原先取消只在整批结束后才被观察到，等于「离开页面」这个用户契约是假的。
   现已在**每一章、每一页的边界**插入 `currentCoroutineContext().ensureActive()`，
   最坏情况是当前页请求返回后立即停下，而不是继续下完整批。
2. **取消后索引与磁盘不一致**。原先 `finalize()` 只回写占用大小，不重扫页数，
   于是取消时「磁盘上已有 1 页、索引写 0 页」——界面会显示未缓存，下次下载还会重下这页。
   现在 `finalize()` 会**按磁盘真实文件数重扫每一章**再回写，因此无论正常结束还是取消，
   索引都等于磁盘真相。这同时让「断点续传」不依赖运行期内存的计数，另一个进程写过的文件也认。

### 10.4 本轮未做（仍属 v1 范围外）

按约定，以下能力不在本次移植范围内：

- AstroBox / 网桥互联
- OOBE 引导流程
- 应用内检查更新
- 后台/定时下载、仅 Wi-Fi 下载、并发多章下载、下载队列续传（关闭 App 后重启继续）

### 10.5 已知限制

- **Wear OS 手表上未跑过界面**（原因见 8.5）。逻辑与组合层已由 Robolectric 覆盖，
  下载链路已在手机真机上端到端跑通（见 8.6），但 **GPU 实际渲染、表冠手感、
  圆屏裁切**仍是未知项。
- 阅读器浮层在圆形表盘上最多 6 个按钮，小屏机型可能需要滑动才能看全。
- 连续模式只做「向前预加载 2 页」，不做整话批量预取——
  手表的带宽和存储都不适合一次性拉几十页。
- 书架封面取的是**进入阅读时抓到的详情封面**；若书籍是从搜索页直接开读的，
  封面字段可能为空（搜索结果的 `cover_url` 与详情 `cover` 是同一地址，
  但当前实现只在详情请求成功后回填）。

---

## 11. 上游同步

### 11.1 仓库拓扑

本仓库 `kxkl2077/bandcomic` 是 [`sf-yuzifu/bandcomic`](https://github.com/sf-yuzifu/bandcomic)
（Vela OS 快应用原版）的 **fork**，定位改为 Wear OS 原生版。因此 `src/`（快应用源码）
在本仓库里是**参考实现**，不参与构建；产品代码在 `wearos/`。

首次同步需要挂上上游远程：

```powershell
git remote add upstream https://github.com/sf-yuzifu/bandcomic.git
git fetch upstream
git merge upstream/main
```

### 11.2 已同步（2026-10，4 个提交）

| 提交 | 内容 | 对本移植的影响 |
| --- | --- | --- |
| `78919b9` | [feat(source)] 加固八源运行规则与错误诊断 | **已跟随**，见 11.3 |
| `4098323` | [style] 重写主要说明文档 | 冲突，按 fork 定位保留本仓库的 Wear OS 版 README |
| `cf3e4a2` | [ci] 添加 GitHub Actions 自动化发版工作流 | **已重写**，见 11.4 |
| `86ffc63` | [ci] 完善工作流 release tag 传参支持 | **已重写**，见 11.4 |

冲突面只有 `README.md` 一个文件：上游把它重写成面向 Vela 设备的产品说明，
而本仓库的 README 描述 Wear OS 移植（且被要求删去 AstroBox 等 Wear OS 用不上的能力）。
其余 29 个文件（`src/`、`docs/`、`tools/`、`.github/`）与 `wearos/` 完全不相交，自动合并。

### 11.3 已移植的协议加固（`78919b9`）

上游新增 `src/components/sourceConfig.js`，把源配置校验与 ID 形态判定收成一处。
本移植在 `wearos/.../data/source/SourceConfig.kt` 里逐条对齐，并由
`SourceConfigTest`（17 条）覆盖：

- **保留键扩容**：`using`、`type` 之外补上原型污染键 `__proto__`、`prototype`、`constructor`。
- **key 规则**：1–80 字符、首尾无空白、不含路径分隔符/尖括号/控制字符
  （key 会进文件系统目录名与 JSON 键位）。
- **apiUrl 规则**：必须为 http(s) 基地址，端口 1–65535，IPv4 段 ≤255，
  拒绝 `/./` 与 `/../`。此前只检查了「是不是 http(s) 开头」。
- **路径规则**：必须以**单个** `/` 开头（`//` 是协议相对地址，会绕开基地址）、
  禁空白/`#`/`\`、禁目录穿越、占位符限定在 `<id>` `<chapter>` `<text>` `<page>` 且必填项齐全。
- **`idType`**：`numeric` / `uuid` / `gid_token` / `slug` / `string`，
  解析进 `ComicSource.idType`（此前该字段被静默丢弃），并驱动 `isComicId`。

**仍未跟随的两项**（属上游文档 `docs/SOURCE_RUNTIME.md` 的运行时约定，实现代价较大）：

1. **429/503 的 `Retry-After` 冷却退避**。上游 `formatApiError` 已解析并回显
   `Retry-After`；本移植的下载器目前是固定 `1s × 3` 重试，未读取该响应头。
   要做得先把响应头从 `fetchBytes` 里透出来。
2. **结构化的错误呈现**。上游会给出 `[源名] [状态码] 描述`（含 HTTP 语义表与 curl 错误码表）；
   本移植的 `errorText` 仍只映射到「网络连接失败 / 请求失败 / 未知错误」三档。
   两者都需要新增 `error.http.*` / `error.curl.*` 文案键。

### 11.4 CI 工作流重写（`.github/workflows/release.yml`）

上游带来的 workflow 是给**快应用**用的，在本 fork 上必然失败，原因有三条：

1. 它跑 `yarn install` + `yarn run release`（`node tools/build-release.mjs --enable-jsc`），
   打的是 Vela 的 `.rpk`；本仓库的产物是 Gradle 出的 APK。
2. 它**强制要求** `SIGN_CERTIFICATE` / `SIGN_PRIVATE_KEY` 两个 secret，缺失就 `exit 1`。
   这两个是 Vela 签名证书，Wear OS 侧既不需要也没有。
3. Gradle 工程根在 `wearos/`，而它在仓库根执行——根目录没有 `gradlew`。

重写后的要点：

- 工作目录固定 `wearos/`，用 `./gradlew`；
- JDK 取 25（与本地验证过的工具链一致）。依据：`com.android.tools.build:gradle:9.4.1`
  的模块元数据声明 `org.gradle.jvm.version=17`，即最低 JDK 17；
- 装 SDK 组件必须写 `platforms;android-37.1`——因为 `compileSdk = 37` 配
  `compileSdkMinor = 1`，只装 `platforms;android-37` 定位不到；
- 不再依赖任何 secret，默认交付**可安装的 debug APK**。
  注：`release` 变体没有配置 signingConfig，`assembleRelease` 产出的是 unsigned 包（装不上），
  所以这里刻意不发它；
- `gradlew` 曾被提交成 `100644`（无执行位），Linux runner 上 `./gradlew` 会 Permission denied。
  已用 `git update-index --chmod=+x` 修正，workflow 里另有 `chmod +x` 兜底；
- 发布条件的写法是 `startsWith(github.ref, 'refs/tags/') || (github.event_name == 'workflow_dispatch' && ...)`。
  **不能**简写成 `inputs.tag_name != ''`：push 事件下该值为 null，而 `null != ''` 成立，
  会导致每次 push 都发版。

#### 11.4.1 真实排障过程与最终修法

上线后连挂三次，每次都得靠**公开信息**定位——因为 job 日志需要仓库 admin 权限
（匿名拉 `/actions/jobs/{id}/logs` 返回 403），而 **annotations 与 job 的逐步结论是公开的**。
这两条就是当时唯一的观测通道。

**第 1 次（run #2，18 秒）**：第 4 步「安装 Android SDK 命令行工具」失败，其后全 skipped，
说明 JDK/SDK/Gradle 逻辑根本没跑到。对比两个 tag 的 `action.yml`：
v3 的 `packages` 默认值是 `'tools platform-tools'`，v4 是 `'platform-tools'`。
**Google 已移除旧的 `tools` 包**，v3 于是卡在安装一个不存在的包上直接失败
（v4.0.2 的发布说明即「Fix for removed tools package.」）。→ 改用 `@v4`。
顺带排除一个误判：node20 **不是**原因，同一次运行里 `checkout@v4`、`setup-java@v4`
都是 node20 且成功。

**第 2 次（run #3，27 秒）**：`@v4` 生效，第 4 步转绿；改成第 5 步「安装 SDK 组件」失败，
且只有 2 秒。→ 又踩一坑：包 id 的分号形式 `platforms;android-37.1` 会被外壳按分号拆开，
sdkmanager 实际收到 `platforms`、`android-37.1`、`build-tools`、`37.0.0` 四个参数，
逐个报 `Package ... not found`。改用斜杠形式（也正是 `sdkmanager --list` 打印的形式）。

**第 3 次（run #4，26 秒）**：仍失败在第 5 步。**这里的关键判断是：sdkmanager 的退出码不可信。**
本机实测——哪怕是完全不存在的包名，它也只打印 `Package ... not found.` 然后**返回 0**。
既然读不到日志，就换一个不依赖日志的观测手段：在 workflow 里用 `::notice::` 把环境事实
打成 annotation（公开可读）。结果直接推翻了原先的假设：

| 探针 | 实测值 |
| --- | --- |
| `SDKROOT` | `/usr/local/lib/android/sdk` |
| `CMDLINE` | `Pkg.Revision=22.0`（另有 12.0） |
| `SDKMANAGER_PATH` | `…/cmdline-tools/22.0/bin/sdkmanager` —— **在 PATH 上，不是命令找不到** |
| `INSTALLED_PLATFORMS` | 含 **`android-37.1`**（另有 `android-37.0`、`android-37.2`） |
| `INSTALLED_BUILDTOOLS` | 含 **`37.0.0`** |
| `SM_INSTALL_TAIL` | 只有废弃警告 + `Loading package information...`，**没有任何报错** |

即：**runner 镜像本来就自带 `platforms/android-37.1` 与 `build-tools/37.0.0`**，
那一步纯属多余，而 sdkmanager 偏偏在「无事可做」时返回了非零。

**最终修法**：不再无条件调用 sdkmanager，改为

1. 先按**目录**判断三件套是否齐备，齐备就直接跳过安装（当前 runner 走的就是这条路）；
2. 真的缺了才装，且**安装结果同样以目标目录是否存在为准**，不看 sdkmanager 的退出码。

**结果**：run #5 全绿，11 个步骤全 success，耗时 4 分钟，产物 `comic-wear-apk`
（15,336,214 字节）上传成功。同一轮里顺手把这些 action 升到当前大版本：
`checkout` / `setup-java` / `cache` / `upload-artifact` / `download-artifact` → `v5`
（`setup-java@v4` 已被明确标记废弃，且 v4 系列跑在 node20 上，GitHub 会强制迁移到 node24）。

顺带记录一个环境事实：本机 SDK 的 `sdkmanager` 会警告
「The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead」，
替代品是 cmdline-tools 目录下的 `android sdk`。当前仍以警告方式可用，故未切换。



