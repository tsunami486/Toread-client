package com.example.toread.ui.text

import kotlin.math.roundToInt

/**
 * Centralized user-facing copy for the shared client UI.
 *
 * Put only text that can be shown to users here: labels, buttons, empty states,
 * snackbar messages, content descriptions, and user-facing fallback errors.
 *
 * Do not put log event names, API/protocol fields, database/sync status values,
 * test fixture text, chapter-detection rules, regular expressions, or character
 * tables here. Compose Preview sample copy may live here when it is part of a
 * reusable visual fixture. Use `const val` for static copy and functions for copy
 * that includes runtime values.
 */
object ToreadStrings {
    // Common
    const val AppName = "同阅"
    const val UnknownError = "未知错误"
    const val BackToBookshelf = "返回书架"
    const val Close = "关闭"
    const val Search = "搜索"
    const val Refresh = "刷新"
    const val Settings = "设置"
    const val Reader = "阅读"
    const val PreviousPage = "上一页"
    const val NextPage = "下一页"
    const val Catalog = "目录"
    const val Confirm = "知道了"
    const val NotStarted = "尚未开始"
    const val UnknownAuthor = "未知作者"

    fun failedWithReason(prefix: String, reason: String?): String =
        "$prefix：${reason?.takeIf { it.isNotBlank() } ?: UnknownError}"

    fun safeUserVisibleMessage(
        message: String?,
        fallback: String = RefreshUnexpectedFailure,
    ): String {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return fallback
        return if (text.containsTechnicalEndpointDetail()) fallback else text
    }

    // App
    const val RemoteProgressSourceUnavailable = "远端进度服务未初始化"
    const val LocalProgressServiceUnavailable = "本地进度服务未初始化"
    const val LoginExpired = "登录状态已过期，请重新登录。"
    const val ServerConnectionMonitorUnavailable = "连接监控未初始化"
    const val ServerOfflineSnackbar = "连接服务器失败，当前为离线状态。"
    const val ServerReconnectedSnackbar = "已重新连接服务器"
    const val RefreshSuccess = "刷新成功"
    const val SyncServiceUnavailable = "同步服务未初始化"
    const val CurrentOffline = "当前为离线状态"
    const val RemoteProgressFetchFailed = "云端进度获取失败"
    const val RefreshUnexpectedFailure = "操作未完成，请稍后重试。"
    const val NoSafeCacheToClear = "当前没有可安全清理的独立缓存。"

    fun refreshAuthFailed(reason: String): String = "刷新登录状态失败：$reason"
    fun refreshFailed(reason: String): String = "刷新失败"
    fun remoteProgressUpdated(count: Int): String = "刷新成功：已更新 $count 本书的云端进度"
    fun databaseInitFailed(reason: String?): String = failedWithReason("本地数据库初始化失败", reason)
    fun readBookshelfFailed(reason: String?): String = failedWithReason("读取书架失败", reason)
    fun offlineBanner(reason: String): String =
        "离线状态，同步将在恢复连接后继续。"

    fun shortReasonSuffix(reason: String): String = reason.toShortReasonSuffix()

    // Auth
    const val LoginTitle = "登录" + AppName
    const val RegisterTitle = "注册" + AppName
    const val AuthDescription = "获得更好的多端同步阅读体验"
    const val EmailLabel = "邮箱"
    const val PasswordLabel = "密码"
    const val EmailPasswordRequired = "请输入邮箱和密码。"
    const val AuthServiceUnavailable = "认证服务未初始化。"
    const val LoginAction = "登录"
    const val RegisterAndEnter = "注册并进入"
    const val GoLogin = "去登录"
    const val GoRegister = "去注册"
    const val GuestMode = "游客进入"
    const val CheckingStartupTitle = "正在启动"
    const val CheckingAuthState = "正在读取本地登录状态。"

    // Bookshelf
    const val BookshelfTitle = "书架"
    const val BooksSection = "书籍"
    const val ImportEpubPdfTxt = "导入 EPUB / PDF / TXT"
    const val SearchBooksPlaceholder = "搜索书名、作者或文件名"
    const val ContinueReading = "继续阅读"
    const val ImportBooks = "导入书籍"
    const val Open = "打开"
    const val NoMatchingBooks = "没有符合条件的书"
    const val NoReadableBooks = "还没有可阅读的书"
    const val TryAnotherSearch = "换个关键词或筛选项试试。"
    const val EmptyBookshelfHint = "导入文件后会显示在这里。"
    const val Synced = "已同步"
    const val SyncFailed = "同步失败"
    const val WaitingSync = "等待同步"
    const val FilterAll = "全部"
    const val FilterReading = "在读"
    const val FilterUnread = "未读"
    const val FilterFinished = "已读"
    const val HideSearch = "收起搜索"
    const val FilterBooks = "筛选书籍"
    const val SortBooks = "排序"
    const val SortRecentRead = "最近阅读"
    const val SortTitle = "书名"
    const val SortAuthor = "作者"
    const val SortProgress = "阅读进度"
    const val SortFormat = "文件格式"
    const val SortFileSize = "文件大小"
    const val SwitchToGridView = "切换为网格模式"
    const val SwitchToListView = "切换为列表模式"
    const val Unread = "未读"
    const val ReadFinished = "已读完"
    const val FallbackCoverUnknownMark = "?"

    const val BookshelfPreviewCompactLight = "书架 - 360 Compact 浅色"
    const val BookshelfPreviewMediumDark = "书架 - 600 Medium 深色"
    const val BookshelfPreviewExpandedLight = "书架 - 840 Expanded 浅色"
    const val BookshelfPreviewDesktopDark = "书架 - 1280 Desktop 深色"
    const val BookshelfPreviewUltrawideLight = "书架 - 1920 Ultrawide 浅色"
    const val BookshelfPreviewLongTitle = "一段足够长、用于验证窄屏省略效果的示例书名"
    const val BookshelfPreviewAuthor = "示例作者"
    const val BookshelfPreviewLongChapterTitle = "第四十二章 一段足够长、用于验证章节标题省略效果的标题"
    const val BookshelfPreviewDragonTitle = "龙族全套 共七册"
    const val BookshelfPreviewDragonAuthor = "江南"
    const val BookshelfPreviewJavaTitle = "Java面经"
    const val BookshelfPreviewTxtTitle = "蛊无删减错别字修正＋文本读者问答"
    const val BookshelfPreviewFinishedTitle = "已经读完但仍保留在书架中的书"
    const val BookshelfPreviewUnreadTitle = "尚未开始阅读"
    const val BookshelfPreviewDesignTitle = "响应式界面设计手册"
    const val BookshelfPreviewDesignAuthor = "Design Team"
    const val BookshelfPreviewEighthTitle = "第八本书用于验证末行左对齐"

    fun pendingSyncItems(count: Int): String = "$count 项等待同步"
    fun failedSyncItems(count: Int): String = "$count 项同步失败"
    fun syncPendingSummary(pending: Int, failed: Int): String =
        buildList {
            add(pendingSyncItems(pending))
            if (failed > 0) add(failedSyncItems(failed))
        }.joinToString(" · ")

    fun bookshelfSummary(bookCount: Int, syncSummary: String): String =
        "本地书库 · $bookCount 本书 · $syncSummary"

    fun bookProgress(progress: Double): String = "已读 ${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
    fun bookProgressPercent(progress: Double): String = "${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
    fun bookGridProgress(progress: Double): String = "${(progress.coerceIn(0.0, 1.0) * 1000).roundToInt() / 10.0}%"
    fun bookCoverContentDescription(title: String): String = "$title 封面"

    // Import
    const val ImportTitle = "导入书籍"
    const val SupportedImportFormats = "支持 EPUB、PDF、TXT。"
    const val ImportWaiting = "等待选择本地文件。"
    const val OpeningFilePicker = "正在打开文件选择器..."
    const val ChooseLocalFile = "选择本地文件"
    const val ImportStatusTitle = "导入状态"
    const val NoFileSelected = "没有选择文件。"
    const val ImportHeroTitle = "导入你的本地书籍"
    const val ImportHeroDescription = "支持 EPUB、PDF、TXT等常见电子书格式"
    const val ImportChooseTitle = "选择本地书籍文件"
    const val ImportChooseDescription = "支持 EPUB、PDF、TXT等常见电子书格式"
    const val ImportChooseHint = "也可从其他app分享文件到本应用"
    const val ImportWaitingTitle = "等待选择文件"
    const val ImportWaitingDescription = "请选择一个本地电子书文件。"
    const val ImportingTitle = "正在导入本地书籍"
    const val ImportingDescription = "正在解析文件并生成书籍信息..."
    const val ImportNoSelectionTitle = "未选择文件"
    const val ImportNoSelectionDescription = "你还没有选择任何文件。"
    const val ImportSuccessTitle = "导入成功"
    const val ImportDuplicateTitle = "书籍已存在"
    const val ImportFailedTitle = "导入失败"
    const val ImportFailedGeneric = "暂不支持该文件格式，或文件可能已损坏。"
    const val ImportServiceUnavailable = "导入服务暂不可用，请稍后重试。"
    const val ImportingAction = "正在导入..."
    const val RechooseLocalFile = "重新选择"
    const val ContinueChooseFile = "继续选择"
    const val ImportTipTitle = "提示"
    const val ImportTipDescription = "请确保你拥有导入文件的合法使用权。本应用仅提供本地阅读和个人同步功能。"

    fun importFailed(reason: String?): String = failedWithReason("导入失败", reason)
    fun importSuccessMessage(title: String?, count: Int): String =
        if (count == 1 && !title.isNullOrBlank()) "《$title》已加入书架。" else "$count 本书已加入书架。"

    fun importDuplicateMessage(title: String?, count: Int): String =
        if (count == 1 && !title.isNullOrBlank()) "《$title》已经在你的书架中。" else "$count 本书已经在你的书架中。"

    fun importFailedMessage(reason: String?): String =
        reason?.takeIf { it.isNotBlank() } ?: ImportFailedGeneric

    fun importedSummary(imported: Int, duplicates: Int, failed: Int): String =
        buildString {
            append("导入成功：$imported")
            if (duplicates > 0) append("，已在书架中：$duplicates")
            if (failed > 0) append("，失败：$failed。")
        }

    fun unsupportedFileExtension(extension: String): String =
        "不支持的文件类型：$extension"

    // Reader
    const val SelectBookBeforeReading = "请先从书架打开一本书。"
    const val UnsupportedReadableFormat = "当前格式暂不能直接阅读。"
    const val Font = "字体"
    const val DecreaseFontSize = "字号减小"
    const val IncreaseFontSize = "字号增大"
    const val NightMode = "夜间"
    const val ReaderSettings = "设置"
    const val EmptyChapterList = "尚未识别到章节"
    const val ImageUnavailable = "图片无法显示"
    const val SwitchSinglePageReading = "切换单页阅读"
    const val SwitchDoublePageReading = "切换双页阅读"
    const val SinglePage = "单页"
    const val DoublePage = "双页"
    const val LeftToRight = "从左到右"
    const val RightToLeft = "从右到左"
    const val CoverAlone = "封面单页"
    const val CoverPaired = "封面并排"
    const val LandscapeSinglePage = "横屏：单页"
    const val LandscapeDoublePage = "横屏：双页"
    const val OrientationSystem = "方向：跟随系统"
    const val OrientationPortrait = "方向：竖屏"
    const val OrientationLandscape = "方向：横屏"
    const val VolumeKeysOn = "音量键：开"
    const val VolumeKeysOff = "音量键：关"
    const val FirstPage = "首页"
    const val LastPage = "末页"
    const val SwitchLightMode = "切换浅色模式"
    const val SwitchDarkMode = "切换深色模式"
    const val PdfRendering = "正在渲染 PDF 页面"
    const val Retry = "重试"
    const val PdfReaderSettings = "PDF 阅读设置"
    const val ReadingMode = "阅读模式"
    const val PdfSpreadSettings = "分页与跨页"
    const val ScreenOrientation = "屏幕方向"
    const val ReaderInteraction = "阅读交互"
    const val ReaderDisplay = "阅读信息"
    const val RightToLeftReading = "从右向左阅读"
    const val TapToTurnPages = "点击翻页"
    const val ReverseTapToTurnPages = "反转点击翻页方向"
    const val PageAnimation = "翻页动画"
    const val DoubleTapToZoom = "双击缩放"
    const val LongPressToZoom = "长按临时放大"
    const val TurnPagesWithVolumeKeys = "音量键翻页"
    const val VolumeKeysDefaultOffHint = "仅在阅读页前台生效，默认关闭"
    const val ShowPageNumber = "显示页码"
    const val ShowReaderStatusInfo = "显示时间与电量"
    const val AutoPageTurning = "自动翻页"
    const val AutoPageTurningOn = "自动翻页中"
    const val AutoPageTurningInterval = "自动翻页间隔"
    const val PdfReaderModePagedHorizontal = "横向分页"
    const val PdfReaderModePagedVertical = "纵向分页"
    const val PdfReaderModeContinuousVertical = "纵向连续"

    fun openBookFailed(reason: String?): String = failedWithReason("打开失败", reason)
    fun readChapterSummary(read: Int, total: Int): String = "已读 $read / 共 $total 章"
    fun chapterPage(index: Int): String = "第 ${index + 1} 页"
    fun pagePosition(index: Int, total: Int): String = "${index + 1} / $total"
    fun pdfPageRange(pageIndices: List<Int>, total: Int): String {
        val first = pageIndices.minOrNull() ?: 0
        val last = pageIndices.maxOrNull() ?: first
        return if (first == last) {
            "${first + 1} / $total"
        } else {
            "${first + 1}–${last + 1} / $total"
        }
    }
    fun chapterFallback(index: Int): String = "第 ${index + 1} 章"
    fun readerPercent(progress: Double): String = "${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
    fun seconds(value: Int): String = "$value 秒"

    // Settings
    const val AccountInfo = "账号信息"
    const val SyncAndNetwork = "同步与网络"
    const val AppearanceAndReading = "外观与阅读"
    const val StorageAndSystem = "存储与系统"
    const val Account = "账号"
    const val SyncAndReading = "同步与阅读"
    const val LoggedIn = "已登录"
    const val GuestModeStatus = "游客模式"
    const val NotLoggedIn = "未登录"
    const val LocalReadingOnly = "当前仅使用本地阅读数据"
    const val LoginToSync = "登录后可进行跨设备同步"
    const val AboutToread = "关于" + AppName
    const val Version100 = "版本 1.0"
    const val AboutDescription = "导入型个人阅读器。本地阅读 EPUB、PDF 和 TXT，同步阅读进度、书签与笔记。"
    const val LogoutState = "退出登录状态"
    const val LogoutLocalOnlyHint = "退出后仅清除本地 token，本地书籍与同步数据将保留。"
    const val AutoSync = "自动同步"
    const val AutoSyncDescription = "联网后自动同步阅读进度、书签与笔记。"
    const val DynamicColor = "动态取色"
    const val DynamicColorDescription = "根据阅读内容调整主题氛围。"
    const val LocalStorage = "本地存储"
    const val AppPrivateDirectory = "应用私有目录"
    const val ClearCache = "清理缓存"
    const val About = "关于"
}

private fun String.toShortReasonSuffix(): String {
    val reason = trim()
    if (reason.isBlank()) return ""
    return " 原因：${reason.take(80)}"
}

private fun String.containsTechnicalEndpointDetail(): Boolean {
    val text = lowercase()
    return listOf(
        "http://",
        "https://",
        "localhost",
        "10.0.2.2",
        ":8080",
        "/health",
        "/auth",
        "/books",
        "/progress",
        "/bookmarks",
        "/notes",
        "server_base_url",
        "baseurl",
        "serverbaseurl",
        "http ",
        "http:",
    ).any { marker -> marker in text }
}
