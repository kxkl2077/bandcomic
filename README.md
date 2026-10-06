<div align="center">
   <img src="wearos/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="132" alt="腕上漫画">
   <h3>腕上漫画 · Wear OS 版</h3>
</div>

## 项目简介

腕上漫画是一个手表上的漫画阅读工具。本仓库是
[腕上漫画（小米 Vela OS 快应用版）](https://github.com/sf-yuzifu/bandcomic) 的
**Wear OS 原生移植**：把原来跑在快应用框架里的 `.ux` 页面重写为
Kotlin + Jetpack Compose for Wear OS 的 Android 应用，在保持原版
界面密度、交互逻辑与漫画源协议一致的前提下，跑在标准 Wear OS / Android 手表上。

本项目本身不提供任何漫画内容，所有内容均来自用户自行配置的 API 漫画源。

## 主要特性

- **原生 Wear OS 应用**：Kotlin + Compose for Wear OS，`minSdk 30`，独立运行，不依赖手机端配套 App。
- **在线阅读**：通过自定义漫画源搜索漫画、查看详情并在线阅读。
- **圆屏与方屏适配**：界面尺寸按原版快应用的 CSS 数值等比换算（原版 `designWidth = device-width`，
  即 CSS px 与设备像素 1:1，故 `dp = 原版 px ÷ 2`），圆屏下角标按钮自动让位给标题点击。
- **表冠支持**：整页模式用 Wear 的 `HorizontalPager`（自带表冠吸附翻页），
  列表页显式接 `onRotaryScrollEvent`。
- **阅读器**：圆屏进度圆弧、点按显隐工具栏、左右边缘翻页、**双指捏合缩放**与单指平移、
  亮度调节、进度自动记录。
- **章节/页码直达**：阅读中点头部的章节号或页码即可弹出全量网格，点哪个去哪个，
  不用一章一章地按（百来章的漫画尤其明显）。
- **自定义漫画源**：通过 `/config` 配置接口接入第三方或自建 API 漫画源，可添加、切换、删除。
- **图片参数调节**：支持设置图片宽度、质量、搜索封面显示、相邻页预加载等选项。
- **多语言支持**：简体中文与英文，跟随系统语言自动切换。
- **PNG 图片解析**：针对部分图片服务，可请求 API 返回 PNG 图片。
- **阅读历史**：按最近阅读时间排序，支持横滑删除（带二次确认）与分页浏览。
- **下载与离线阅读**：详情页可直接进入下载页，按章节选择（支持全选/反选）后串行下载，
  带实时进度；下载完成后阅读器优先读本地文件，断网也能看。
  下载在详情页与首页均有明确入口，退出下载页即取消（已下好的页不会丢）。
- **本地漫画管理**：首页底部的「本地漫画」入口列出全部已下内容，
  显示真实磁盘占用与整体大小，可单独删除某一部（带二次确认），
  未下完的章节会标记出来。

## 使用说明

1. 打开应用，首页就是搜索页。
2. 输入漫画 ID 或关键词，按确认键提交。是否按 ID 直连详情**由当前源决定**：
   MangaDex 认 UUID、E-Hentai 认 `gid_token`、拷贝漫画认 slug，其余按纯数字判定；
   不符合的输入会自动转成关键词搜索。源也可以用 `/config` 里的 `idType` 显式声明。
3. 在搜索结果中点选漫画，进入详情页。
4. 点封面开始阅读，左滑/右滑或转动表冠翻页。
5. 要离线看，在详情页点「下载漫画」，勾选需要的章节（可「全选」）后点「开始下载」。
   下载完成的章节会缓存到本地，之后阅读会直接读本地文件。
6. 首页底部的「本地漫画」可以看到已下载的全部内容与磁盘占用，并可删除；
   「阅读历史」是另一处入口，两者互不影响。

首页顶部显示当前漫画源，点「✎」进入漫画源编辑页，点「⟳」在已配置的源之间切换。
点应用名进入「关于」页，其中包含设置项。

## 自定义漫画源

本项目支持配置自定义漫画源。漫画源需要提供：

- `/config` 配置接口
- 漫画详情接口
- 搜索接口
- 图片列表接口
- 可直接请求或可代理处理的图片 URL

图片接口建议支持以下参数：

| 参数 | 说明 |
|------|------|
| `width` | 图片目标宽度 |
| `quality` | 图片质量 |
| `ifPNG=1` | 请求返回 PNG 图片 |

封面图片会固定请求 `width=80`，质量跟随应用设置。

详细接入方式见：[自定义漫画源配置指南](docs/CUSTOM_SOURCE.md)。

## 图片处理说明

腕上漫画会根据用户设置自动给图片 URL 添加参数。

### 正文图片

正文图片会追加：

```text
width=<图片尺寸>&quality=<图片质量>
```

开启 PNG 图片解析后追加：

```text
ifPNG=1
```

### 封面图片

封面图片会追加：

```text
width=80&quality=<图片质量>
```

开启 PNG 图片解析后追加：

```text
ifPNG=1
```

## 数据说明

### 设备信息收集

本应用会读取以下设备信息用于生成 User-Agent，以便 API 服务器根据设备能力调整返回内容：

- 设备型号
- 设备品牌
- 操作系统类型和版本
- 语言和地区

User-Agent 格式：

```text
packageName(versionName(versionCode))/product/brand/osType/osVersionName/osVersionCode/language/region
```

重要说明：

- 这些信息仅用于 API 请求的 User-Agent 生成。
- 不会上传到任何非用户配置的第三方服务器。
- 不收集任何个人身份信息。

### 本地存储

本应用会在设备本地存储以下文件（均在应用私有目录 `filesDir/` 下，其他应用无法读取）：

| 路径 | 内容 |
|------|------|
| `store/sources.json` | 用户配置的漫画源列表 |
| `store/cookies.json` | 漫画源的认证 Cookie |
| `store/library.json` | 阅读历史与阅读进度 |
| `store/search_history.json` | 搜索历史 |
| `datastore/comic_wear_settings.preferences_pb` | 应用设置 |
| `store/comic_cache.json` | 已下载漫画的索引（章节目录、页数、占用大小） |
| `comics/{源}_{漫画id}/cover` | 下载时缓存的封面 |
| `comics/{源}_{漫画id}/{章节号}/{页码}` | 下载的正文页 |
| `image_cache/` | 图片磁盘缓存（Coil 管理，上限 256 MB，位于 `cacheDir`，系统可回收） |

重要说明：

- 所有数据均存储在本地设备。
- 不会上传到任何服务器。
- 开发者无法访问用户设备上的数据。
- 下载内容可用应用内的「本地漫画」逐个删除，或直接清除应用数据一并移除。

## 运行环境

- **系统要求**：Android 11（API 30）及以上，声明 `android.hardware.type.watch`。
- **构建目标**：`compileSdk 37` / `targetSdk 37`（AGP 9 起 compileSdk 为「主.次」版本，见移植说明）。
- **架构**：同时包含 `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64`。
- **实测设备**：OPPO OWW231（Android 11 / 466×466 圆屏 / armeabi-v7a）。
  该机型并非标准 Wear OS，而是 ColorOS Watch，无 GMS；应用不依赖 GMS，可正常运行。

应用不依赖 Google Play 服务，也不需要手机端配套应用即可独立使用。

## 构建与安装

本仓库的快应用源码（`src/`）与 Wear OS 工程（`wearos/`）并存，二者互不影响。

### 1. 环境准备

- JDK 17 及以上（实测 Zulu JDK 25）
- Android SDK，需安装 `platforms;android-37.1` 与 `build-tools;37.0.0`
- Gradle 9.8（工程自带 wrapper，无需单独安装）

### 2. 构建

```bash
cd wearos
./gradlew :app:assembleDebug
```

产物位于 `wearos/app/build/outputs/apk/debug/app-debug.apk`。

### 3. 单元测试

```bash
cd wearos
./gradlew :app:testDebugUnitTest
```

测试全部跑在 JVM 上（Robolectric），无需真机或模拟器。

### 4. 安装到手表

```bash
adb install -r wearos/app/build/outputs/apk/debug/app-debug.apk
```

## 与原 Vela 快应用版的差异

移植过程中数据层与协议层完全对齐，以下能力依赖原平台的专属运行时或手机端插件，
**未纳入本期范围**：

- **自定义全屏输入法**：原版自绘了一套输入法页面；Wear OS 侧改用系统输入法，
  保留了独立的输入页与「确认 / 取消」回传语义。
- **图片预解码**：原版可请求服务端返回预解码的二进制图片，这是原固件的专属能力。
- **本地漫画导入**：原版支持从手机端插件推送本地漫画文件；Wear OS 版只管理应用内下载的内容。
- **手机端插件联动**：网络桥接、漫画源同步、Cookie 上传、本地漫画管理等均依赖
  桌面端插件与私有互联协议；Wear OS 使用标准网络栈，不需要额外桥接。
- **更新检查**：原版通过 `docs/update.json` 做版本检查。

下载相关已对齐的功能：章节网格（18 章一屏、每行 3 格）、三态配色（已完成/部分/选中）、
全选与反选、`开始下载(N)`、`第 {page}/{total_page} 页` 进度、`MAX_RETRY = 3`、
退出页面即取消。唯一的有意偏离是章节目录名只存章节号（原版是「章节号　章节名」），
因为不需要与 Vela 侧的下载产物互操作，详见移植说明第 10 节。

因此本仓库的 `docs/` 中，与本移植直接相关的是 `CUSTOM_SOURCE.md`（漫画源协议），
其余文档描述的是上述未纳入范围的能力。

完整的移植说明、工具链决策、协议保真度对照与实测记录见
[Wear OS 移植说明](docs/WEAROS_PORT.md)。

## 技术栈

- **平台**：Android / Wear OS
- **语言**：Kotlin
- **UI**：Jetpack Compose for Wear OS（Material 3）
- **网络**：OkHttp
- **图片**：Coil 3
- **序列化**：kotlinx.serialization（不使用 Room / KSP）
- **设置存储**：DataStore Preferences

## 致谢

- 原项目 [腕上漫画](https://github.com/sf-yuzifu/bandcomic) 作者 **小鱼yuzifu**，
  本项目的数据结构、漫画源协议与界面设计均源自其快应用版。
- 原项目致谢 **OrPudding**、**NEORUAA**、**无源流沙**。

## 开发工具声明

本项目 **Wear OS 移植部分**（`wearos/` 目录、相关文档与 CI 工作流）的开发过程中，
使用了 **DeepSeek Harness** 辅助完成，包括代码实现、重构、单元测试编写，
以及 GitHub Actions 工作流的编写与排障。

所有 AI 参与产出的内容均经过人工审阅与实测验证：本地 Gradle 构建、
JVM 单元测试（以 Robolectric 代替真机）以及真机端到端联调。

原 Vela 快应用版（`src/` 目录）为上游原创作品，不属于上述范围。

## License

本项目基于 [AGPL-3.0 License](https://www.gnu.org/licenses/agpl-3.0.html) 开源，请遵守相关协议规定。
原项目版权归原作者所有，本移植版同样以 AGPL-3.0 发布。

### 开源协议说明

AGPL-3.0 协议意味着：

- 您可以自由使用、修改和分发本应用。
- 如果您修改了本应用并在网络服务器上运行，需要开源您的修改。
- 请保留原始版权声明和许可证信息。

## 免责声明

1. **项目性质**：本应用仅为开源技术工具，不提供任何漫画内容。

2. **用户责任**：
   - 用户需自行输入 API 地址，并对其合法性负责。
   - 用户应遵守当地法律法规，尊重内容创作者的知识产权。
   - 建议用户仅使用合法授权的 API 服务。

3. **数据安全**：
   - 所有数据存储在用户设备，开发者无法访问。
   - 不进行任何数据上传或收集。

4. **技术支持**：
   - 本应用不提供任何内容，仅作为阅读工具使用。
   - 不对 API 提供的内容负责。

一旦使用本项目，即视为您已完全理解并同意以上声明内容。

---

**注意**：请遵守相关法律法规，合理使用本项目。如有版权问题，请及时联系处理。
