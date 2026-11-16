# 同阅 (Toread)

同阅是一个只导入本地文件的个人阅读器：把你自己的 EPUB / PDF / TXT 导入 Android 或 Windows，在本地阅读，并在登录后把书籍信息与阅读进度同步到自己的其他设备。

> **书籍原文件、正文与封面不会上传到服务器。** 服务端只保存账号、设备、书籍信息与阅读进度；
> 局域网传书只在同一 Wi-Fi 的设备之间直传。
>
> 不提供在线搜书、在线下载、书源或任何形式的在线内容获取；应用只处理用户主动导入的本地文件。

当前版本 1.0.5。

[下载](https://github.com/tsunami486/Toread-client/releases) ·
[功能](#功能) ·
[安装](#安装) ·
[使用示例](#使用示例) ·
[目录结构](#目录结构)

## 功能

| 能力 | 说明 |
| --- | --- |
| 导入 | EPUB / PDF / TXT。Android 走系统文件选择器，也可从文件管理器「打开方式」或分享菜单导入；Windows 可把多个文件直接拖进窗口 |
| 书架 | 按书名 / 作者搜索，全部、在读、未读、已读筛选，六种排序，网格 / 列表切换，置顶，继续阅读卡片，同步状态标记 |
| 书籍信息 | 修改封面、书名、作者、分类 / 标签与简介；简介从本地书籍文件自动提取，分类建议在本机生成；自动分类为 Beta，默认关闭 |
| EPUB 阅读 | 只建立轻量目录索引、按章加载，不解析整本书；目录抽屉、上下章、字号、日间 / 夜间，Android 支持跟手翻页 |
| PDF 阅读 | Android：水平分页 / 纵向分页 / 连续纵向、缩放、自动翻页、音量键与方向控制；Windows：双页画廊、跨页原子提交、方向键与滚轮翻页 |
| TXT 阅读 | 分页渲染，大文件不会一次性载入内存 |
| 界面 | Material 3、动态取色、多套调色板、高对比度深色；书架按窗口宽度响应式布局 |
| 同步 | 本地优先：先写本地再入队；登录后同步书籍信息与阅读进度，离线可用，联网后自动重试 |
| 局域网传书 | 同一 Wi-Fi 下设备之间直传，不经过服务器；默认关闭、配对码校验、只读分享。Windows 可作为分享端，Android 与 Windows 都可接收 |
| 隐私 | 客户端不含广告、统计或崩溃上报 SDK；没有网络也能完成导入与本地阅读 |

**尚未实现**

- **书签与笔记**：客户端还没有创建 / 查看入口，当前版本不会产生这类数据。
- **阅读排版**：目前只能调整字号；字体族、行距、页边距尚未提供。
- **全文搜索**：书架可搜书名 / 作者，正文内搜索未实现。
- **应用商店分发**：目前只通过 GitHub Releases 发布。

## 你的书和数据在哪里

**你导入的书、正文、封面，只留在你自己的设备里。**

登录同步时，上传的只有：

- 账号与设备信息：邮箱、密码（仅用于服务端校验，服务端只保存加盐哈希）、设备名与平台版本
- 书籍信息：书名、作者、文件哈希，以及你自己填的分类标签和简介
- 阅读进度：读到哪一章、哪一页或哪个字符位置

**不会**上传：书籍原文件、正文内容、封面图片。

另外：

- 不含广告、统计或崩溃上报 SDK
- 没有网络也能完整地导入和阅读
- 局域网传书只在同一 Wi-Fi 的两台设备之间进行，不经过服务器

## 安装

### 安装应用

从 [Releases](https://github.com/tsunami486/Toread-client/releases) 下载对应安装包：

| 平台 | 文件 | 说明 |
| --- | --- | --- |
| Android | `同阅-<版本>.apk` | 需要 Android 14（API 34）及以上；正式签名，applicationId 为 `cn.toreadapp.reader` |
| Windows | `同阅-<版本>.msi` | Windows 10 / 11（64 位），安装包自带运行时；未做代码签名，安装时会出现 SmartScreen 提示 |

如果 Releases 里还没有对应版本，可以按下面的步骤从源码构建。官方安装包已内置同阅的同步服务地址；
自行构建的版本需要指向你自己的服务端，否则登录与跨设备同步会落到本机开发地址。

### 从源码构建

需要 JDK 21 与 Android SDK（`compileSdk` 为 36）。在 `local.properties` 里写 `sdk.dir=...`，或设置 `ANDROID_HOME`。

```powershell
# Android debug APK（applicationId 为 cn.toreadapp.reader.debug，可与正式版共存）
.\gradlew.bat :app:androidApp:assembleDebug

# 桌面端直接运行
.\gradlew.bat :app:desktopApp:run

# Windows 安装包
.\gradlew.bat :app:desktopApp:packageMsi
```

打 MSI 需要 WiX Toolset 3.x 的 `candle.exe` / `light.exe` 在 `PATH` 上。不必安装到系统：解压官方
`wix314-binaries.zip` 到任意目录，把该目录加进 `PATH` 即可。安装包版本来自
[gradle/libs.versions.toml](gradle/libs.versions.toml) 的 `toread-app`（它同时决定 MSI 的 ProductVersion），
发布新包前先改这个值。

正式签名的 Release APK 需要仓库之外的签名文件（默认 `~/.toread/signing/release.properties`），
可以运行 `app/androidApp/scripts/Initialize-ReleaseSigning.ps1` 初始化：

```powershell
.\gradlew.bat :app:androidApp:assembleRelease
```

服务端地址是**构建期元数据**，不是应用内设置。构建时按 `Gradle 属性 → 环境变量 → 平台开发默认值` 解析：

```powershell
.\gradlew.bat :app:androidApp:assembleRelease `
  "-Ptoread.serverBaseUrl=https://your-server.example.com"
```

留空时使用开发默认值（Android 模拟器 `10.0.2.2:8080`，桌面端 `localhost:8080`）。应用界面里没有服务器地址或接口的编辑入口。

## 使用示例

### 导入、阅读与同步

1. 首次启动进入登录页：可以登录、注册，或选择游客模式。游客只使用本地功能。
2. 在书架点「导入书籍」选择一个本地文件；Android 也可从文件管理器「打开方式」或分享菜单导入，
   Windows 可把多个文件直接拖进窗口。
3. 点书籍卡片开始阅读：
   - **EPUB / TXT**：目录抽屉跳章、调字号、切换日间 / 夜间；Android 上点页面左 / 右区域或拖动翻页，
     点中间呼出控制面板。
   - **PDF**：Android 可切换水平分页、纵向分页、连续纵向并缩放；Windows 使用双页画廊，支持方向键、
     滚轮和跨页切换。
4. 阅读位置在退出时写回本地。登录并开启自动同步后会上传，书架上的刷新按钮也可手动同步。
   在另一台设备登录同一账号、导入同一个文件（SHA-256 相同），即可接着上次的位置读。

### 局域网传书（不经过服务器）

1. 在 Windows 上打开 `设置 → 局域网传输`，在「让其他设备读取本机书架」处点「打开」，记下地址
   （例如 `192.168.1.7:47901`）与配对码。
2. 在另一台设备上打开同页，输入地址与配对码，点「查看对方书架」。
3. 接收本机没有的书。文件会先校验 SHA-256，再走正常导入流程（去重、入书架、提取封面与简介）。
4. 传完在分享端点「关掉」即断开，端口不会继续监听。

Android 目前只能作为接收端，作为分享端会在后续版本补上。

### 常见问题

**支持哪些格式？**

EPUB、PDF、TXT。

**需要注册账号吗？**

不需要。不登录也能完整地导入和阅读，只是不能多设备同步。

**换设备了，阅读进度还在吗？**

登录同一个账号会自动同步。官方安装包已内置同阅的同步服务地址；自行构建的版本需要按上面的方式
指定服务端。

**为什么 Windows 提示「已保护你的电脑」？**

因为安装包没有做代码签名。这是提示，不是病毒。

**有书签和笔记吗？**

暂时没有。客户端里还没有创建和查看它们的界面，所以当前版本不会产生这类数据。

**能在线找书或者下载吗？**

不能，也不会加。同阅只处理你自己导入的本地文件。

**书读一半关掉，下次能接着读吗？**

可以，阅读位置会自动保存。

**为什么 Android 会用到相册权限？**

只在你主动更换书籍封面时使用。系统照片选择器可用的设备不需要这项权限；没有照片选择器的旧系统
才会请求读取图片。

## 目录结构

```text
Toread-client/
├─ app/
│  ├─ shared/                # 共享代码（Gradle: :app:shared）
│  │  └─ src/commonMain/kotlin/com/example/toread/
│  │     ├─ importing/       # 导入、去重、封面与简介提取
│  │     ├─ library/         # 书架数据、书籍信息与置顶
│  │     ├─ reader/          # 阅读定位、分页与内容加载
│  │     ├─ progress/        # 进度保存、合并与远端比较
│  │     ├─ sync/            # 同步队列、上传与补偿拉取
│  │     ├─ auth/            # 登录、令牌刷新与会话
│  │     ├─ lan/             # 局域网传书协议与传输
│  │     ├─ metadata/        # 书籍元数据模型
│  │     ├─ categories/      # 本地分类词典与建议
│  │     ├─ settings/        # 设备级设置
│  │     └─ ui/              # 页面、阅读器组件与主题
│  ├─ androidApp/            # Android 入口（MainActivity、清单、图标）
│  ├─ desktopApp/            # 桌面入口与 MSI 打包（DesktopMain）
│  └─ branding/              # 品牌图标母版与导出脚本
├─ gradle/                   # 版本目录 libs.versions.toml、Gradle wrapper
├─ settings.gradle.kts       # 聚合 :app:androidApp、:app:desktopApp、:app:shared
├─ build.gradle.kts
├─ LICENSE
└─ README.md
```

**技术栈**：Kotlin Multiplatform、Compose Multiplatform、SQLDelight、Ktor Client、kotlinx.serialization、
kotlinx.coroutines。构建使用 Gradle 9 与版本目录，JDK 21。

## 遇到问题

到 [Issues](https://github.com/tsunami486/Toread-client/issues) 反馈，请说明设备型号、系统版本和复现步骤。

## 许可证

[MIT](LICENSE)
