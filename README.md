# 同阅 (Toread) — Client

一个**仅导入本地**的个人电子书阅读器客户端，覆盖 Android 与 Windows。

你导入自己拥有的 EPUB / PDF / TXT 文件，在本地阅读。客户端可以登录自己的同阅服务端，在多台设备之间同步阅读进度与书籍信息。

> **书籍原文件、正文与封面始终留在你的设备上，不会上传。**
>
> 本客户端不提供在线搜书、在线下载、书源或任何形式的在线内容获取。

## 状态

早期版本。接口、数据库结构与界面仍可能变动。

## 功能

### 已经能用

| 模块 | 说明 |
| --- | --- |
| 导入 | EPUB / PDF / TXT。Android 走系统文件选择器，也可从文件管理器「打开方式」或其它应用分享进来；Windows 支持把文件直接拖进窗口 |
| 书架 | 搜索、分类标签、六种排序、列表 / 网格、置顶、继续阅读卡片 |
| 书籍信息 | 封面、书名、作者、分类标签、简介。可从本地文件自动提取简介并给出分类建议；自动分类为 **Beta**，默认关闭 |
| EPUB 阅读 | 按章加载，不解析全书；章节抽屉、章节间跳转、字号、日夜切换、跟手翻页 |
| PDF 阅读 | Android：横向分页 / 纵向分页 / 连续纵向、缩放、自动翻页、音量键翻页与方向控制。Windows：漫画式双页画廊、跨页原子提交、方向键与滚轮翻页 |
| TXT 阅读 | 分页渲染，不一次性载入大文件 |
| 界面 | Material 3、多套调色板、动态取色、高对比度深色 |
| 同步 | 本地优先；登录后同步阅读进度与书籍信息，离线可用，联网后自动重试 |

### 还没做

- **书签与笔记**：本地表与服务端接口都已就位，但客户端还没有创建与查看的入口，因此当前版本不会产生这类数据。
- **阅读排版**：目前只能调字号；字体族、行距、页边距尚未提供。
- **全文搜索**：书架可以搜书名 / 作者，正文内搜索未实现。
- **应用商店分发**：目前只通过 GitHub Releases 分发。

## 下载

从 [Releases](../../releases) 获取：

- Android：`androidApp-release.apk`（applicationId `cn.toreadapp.reader`）
- Windows：`同阅-<版本>.msi`

Windows 安装包**未做代码签名**，安装时会出现 SmartScreen 提示。

## 从源码构建

需要 JDK 21 与 Android SDK（`compileSdk` 见 [gradle/libs.versions.toml](gradle/libs.versions.toml)）。
在 `local.properties` 里写 `sdk.dir=...`，或用 `ANDROID_HOME` 环境变量。

```powershell
# Android
.\gradlew.bat :app:androidApp:assembleDebug

# Windows 桌面端（直接运行）
.\gradlew.bat :app:desktopApp:run

# Windows 安装包
.\gradlew.bat :app:desktopApp:packageMsi
```

服务端地址是**构建期元数据**，不是应用内设置。构建时按 `Gradle 属性 → 环境变量 → 平台开发默认值` 的顺序解析：

```powershell
.\gradlew.bat :app:androidApp:assembleRelease `
  "-Ptoread.serverBaseUrl=https://your-server.example.com"
```

留空时使用开发默认值（Android 模拟器 `10.0.2.2:8080`，桌面端 `localhost:8080`）。
应用界面里没有、也不会有服务器地址或接口的编辑入口。

## 项目结构

```text
.
├── app/
│   ├── androidApp/   # Android 壳
│   ├── desktopApp/   # Windows 桌面壳
│   ├── shared/       # 共享 domain、存储、同步、阅读器与 UI
│   └── branding/     # 图标源文件与生成脚本
└── gradle/           # 版本目录与 wrapper
```

技术栈：Kotlin Multiplatform、Compose Multiplatform、SQLDelight（本地库）、Ktor Client（网络）、kotlinx.serialization。

客户端**不含任何第三方 SDK**：没有广告、没有统计分析、没有崩溃上报；Android 端仅申请网络权限。

本地阅读功能不需要服务端即可完整使用；登录与同步需要你自行部署同阅服务端（不在本仓库内）。

## 参与贡献

```powershell
# 改动后至少编译通过
.\gradlew.bat :app:shared:compileKotlinJvm :app:androidApp:assembleDebug
```

## 许可证

[MIT](LICENSE)
