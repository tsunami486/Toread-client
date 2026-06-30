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
 * tables here. Use `const val` for static copy and functions for copy that
 * includes runtime values.
 */
object ToreadStrings {
    // Common
    const val UnknownError = "未知错误"
    const val BackToBookshelf = "返回书架"
    const val Search = "搜索"
    const val Refresh = "刷新"
    const val Settings = "设置"
    const val Reader = "阅读"
    const val PreviousPage = "上一页"
    const val NextPage = "下一页"
    const val Catalog = "目录"
    const val Confirm = "知道了"
    const val NotStarted = "尚未开始"
    const val JustNow = "刚刚"
    const val UnknownAuthor = "未知作者"

    fun failedWithReason(prefix: String, reason: String?): String =
        "$prefix：${reason?.takeIf { it.isNotBlank() } ?: UnknownError}"

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
    const val LoginTitle = "登录 Toread"
    const val RegisterTitle = "注册 Toread"
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

    fun pendingSyncItems(count: Int): String = "$count 项等待同步"
    fun failedSyncItems(count: Int): String = "$count 项同步失败"
    fun syncPendingSummary(pending: Int, failed: Int): String =
        buildList {
            add(pendingSyncItems(pending))
            if (failed > 0) add(failedSyncItems(failed))
        }.joinToString(" · ")

    fun bookshelfSummary(bookCount: Int, syncSummary: String): String =
        "本地书库 · $bookCount 本书 · $syncSummary"

    fun lastReadAt(label: String): String = "上次阅读：$label"
    fun bookProgress(progress: Double): String = "已读 ${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
    fun bookCoverContentDescription(title: String): String = "$title 封面"

    // Import
    const val ImportTitle = "导入书籍"
    const val SupportedImportFormats = "支持 EPUB、PDF、TXT。"
    const val ImportWaiting = "等待选择本地文件。"
    const val OpeningFilePicker = "正在打开文件选择器..."
    const val ChooseLocalFile = "选择本地文件"
    const val ImportStatusTitle = "导入状态"
    const val NoFileSelected = "没有选择文件。"

    fun importFailed(reason: String?): String = failedWithReason("导入失败", reason)
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
    const val SwitchLightMode = "切换浅色模式"
    const val SwitchDarkMode = "切换深色模式"

    fun openBookFailed(reason: String?): String = failedWithReason("打开失败", reason)
    fun readChapterSummary(read: Int, total: Int): String = "已读 $read / 共 $total 章"
    fun chapterPage(index: Int): String = "第 ${index + 1} 页"
    fun chapterFallback(index: Int): String = "第 ${index + 1} 章"
    fun readerPercent(progress: Double): String = "${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

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
    const val AboutToread = "关于 Toread"
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
