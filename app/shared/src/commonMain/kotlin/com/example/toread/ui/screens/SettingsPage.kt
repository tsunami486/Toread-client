package com.example.toread.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.categories.BookCategoryEntry
import com.example.toread.categories.builtInCategoryKeywords
import com.example.toread.categories.removeCategoryMemory
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.settings.ToreadAppearancePreferences
import com.example.toread.settings.ToreadPalette
import com.example.toread.settings.ToreadThemeMode
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadLegalTexts
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.paletteSwatchColors
import com.example.toread.ui.theme.toreadColorScheme
import org.jetbrains.compose.resources.DrawableResource

/**
 * Settings is one flat, grouped list: a section label followed by rows, each row an icon, a title and
 * an optional description, control or chevron. Rows that own more than a single toggle open a sub page
 * instead of growing the list, which is what the 主题与色彩 and 界面 pages are for.
 */
private enum class SettingsSubPage {
    Interface,
    Appearance,
    CategoryLexicon,
    PrivacyPolicy,
    UserAgreement,
}

private val SettingsContentMaxWidth = 720.dp
private val SettingsRowMinHeight = 56.dp

/** 工信部政务服务平台，供公众查询核对 App 备案信息。 */
private const val AppFilingQueryUrl = "https://beian.miit.gov.cn"

@Composable
internal fun SettingsPage(
    settingsRepository: LocalSettingsRepository?,
    appearance: ToreadAppearancePreferences,
    autoSyncEnabled: Boolean,
    bookshelfLayoutMode: BookshelfLayoutMode,
    onAppearanceChange: (ToreadAppearancePreferences) -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onBookshelfLayoutModeChange: (BookshelfLayoutMode) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onClearCache: () -> Unit,
    onOpenLanSync: () -> Unit,
) {
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    PlatformBackHandler(enabled = true) {
        if (subPage != null) subPage = null else onBack()
    }

    val authMode = settingsRepository?.currentAuthMode()
    val userEmail = settingsRepository?.userEmail()
    val accountTitle = when (authMode) {
        AuthMode.LoggedIn -> ToreadStrings.LoggedIn
        AuthMode.Guest -> ToreadStrings.GuestModeStatus
        null -> ToreadStrings.NotLoggedIn
    }
    val accountSubtitle = when {
        authMode == AuthMode.LoggedIn && !userEmail.isNullOrBlank() -> userEmail
        authMode == AuthMode.Guest -> ToreadStrings.LocalReadingOnly
        else -> ToreadStrings.LoginToSync
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsContentMaxWidth)
                .fillMaxWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            SettingsHeader(
                title = when (subPage) {
                    SettingsSubPage.Interface -> ToreadStrings.SettingsInterface
                    SettingsSubPage.Appearance -> ToreadStrings.SettingsAppearance
                    SettingsSubPage.CategoryLexicon -> ToreadStrings.SettingsCategoryLexicon
                    SettingsSubPage.PrivacyPolicy -> ToreadStrings.PrivacyPolicy
                    SettingsSubPage.UserAgreement -> ToreadStrings.UserAgreement
                    null -> ToreadStrings.Settings
                },
                onBack = { if (subPage != null) subPage = null else onBack() },
            )

            when (subPage) {
                SettingsSubPage.Interface -> InterfaceSettingsContent(
                    bookshelfLayoutMode = bookshelfLayoutMode,
                    settingsEnabled = settingsRepository != null,
                    onBookshelfLayoutModeChange = onBookshelfLayoutModeChange,
                )
                SettingsSubPage.Appearance -> AppearanceSettingsContent(
                    appearance = appearance,
                    settingsEnabled = settingsRepository != null,
                    onAppearanceChange = onAppearanceChange,
                )
                SettingsSubPage.CategoryLexicon -> CategoryLexiconSettingsContent(
                    settingsRepository = settingsRepository,
                )
                SettingsSubPage.PrivacyPolicy -> LegalTextSettingsContent(
                    updated = ToreadLegalTexts.PrivacyPolicyUpdated,
                    body = ToreadLegalTexts.PrivacyPolicy,
                )
                SettingsSubPage.UserAgreement -> LegalTextSettingsContent(
                    updated = ToreadLegalTexts.UserAgreementUpdated,
                    body = ToreadLegalTexts.UserAgreement,
                )
                null -> SettingsOverviewContent(
                    accountTitle = accountTitle,
                    accountSubtitle = accountSubtitle,
                    logoutEnabled = settingsRepository != null,
                    autoSyncEnabled = autoSyncEnabled,
                    settingsEnabled = settingsRepository != null,
                    onAutoSyncEnabledChange = onAutoSyncEnabledChange,
                    onOpenInterface = { subPage = SettingsSubPage.Interface },
                    onOpenAppearance = { subPage = SettingsSubPage.Appearance },
                    onOpenCategoryLexicon = { subPage = SettingsSubPage.CategoryLexicon },
                    onOpenLanSync = onOpenLanSync,
                    onClearCache = onClearCache,
                    onAbout = { showAboutDialog = true },
                    onOpenAppFiling = { uriHandler.openUri(AppFilingQueryUrl) },
                    onOpenPrivacyPolicy = { subPage = SettingsSubPage.PrivacyPolicy },
                    onOpenUserAgreement = { subPage = SettingsSubPage.UserAgreement },
                    onLogout = onLogout,
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(ToreadStrings.Confirm)
                }
            },
            title = { Text(ToreadStrings.AboutToread) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ToreadStrings.VersionLabel)
                    Text(ToreadStrings.AboutDescription)
                    // App 备案要求：显著位置标明备案编号，并在其下方链接备案系统供公众核对。
                    Text(ToreadStrings.AppFiling + "：" + ToreadStrings.AppFilingNumber)
                    Text(
                        text = ToreadStrings.AppFilingQueryDescription,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uriHandler.openUri(AppFilingQueryUrl) },
                    )
                }
            },
        )
    }
}

@Composable
private fun SettingsOverviewContent(
    accountTitle: String,
    accountSubtitle: String,
    logoutEnabled: Boolean,
    autoSyncEnabled: Boolean,
    settingsEnabled: Boolean,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onOpenInterface: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCategoryLexicon: () -> Unit,
    onOpenLanSync: () -> Unit,
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
    onOpenAppFiling: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onLogout: () -> Unit,
) {
    SettingsAccountRow(title = accountTitle, subtitle = accountSubtitle)
    SettingsGroup(title = ToreadStrings.SettingsGroupAppAndInterface) {
        SettingsNavigationRow(
            symbol = ToreadSymbols.ViewColumn,
            title = ToreadStrings.SettingsInterface,
            description = ToreadStrings.SettingsInterfaceDescription,
            onClick = onOpenInterface,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Palette,
            title = ToreadStrings.SettingsAppearance,
            description = ToreadStrings.SettingsAppearanceDescription,
            onClick = onOpenAppearance,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Tune,
            title = ToreadStrings.SettingsCategoryLexicon,
            description = ToreadStrings.SettingsCategoryLexiconDescription,
            onClick = onOpenCategoryLexicon,
        )
    }
    SettingsGroup(title = ToreadStrings.SettingsGroupDataAndSync) {
        SettingsSwitchRow(
            symbol = ToreadSymbols.Sync,
            title = ToreadStrings.AutoSync,
            description = ToreadStrings.AutoSyncDescription,
            checked = autoSyncEnabled,
            enabled = settingsEnabled,
            onCheckedChange = onAutoSyncEnabledChange,
        )
        // Device-to-device only: nothing here touches the server or a cloud copy.
        SettingsNavigationRow(
            symbol = ToreadSymbols.CloudSync,
            title = ToreadStrings.SettingsLanSync,
            description = ToreadStrings.SettingsLanSyncDescription,
            onClick = onOpenLanSync,
        )
    }
    SettingsGroup(title = ToreadStrings.SettingsGroupStorageAndSystem) {
        SettingsNavigationRow(
            symbol = ToreadSymbols.DeleteSweep,
            title = ToreadStrings.ClearCache,
            onClick = onClearCache,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Info,
            title = ToreadStrings.About,
            trailingText = ToreadStrings.VersionLabel,
            onClick = onAbout,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Article,
            title = ToreadStrings.AppFiling,
            description = ToreadStrings.AppFilingNumber,
            onClick = onOpenAppFiling,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Article,
            title = ToreadStrings.PrivacyPolicy,
            onClick = onOpenPrivacyPolicy,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Article,
            title = ToreadStrings.UserAgreement,
            onClick = onOpenUserAgreement,
        )
        SettingsNavigationRow(
            symbol = ToreadSymbols.Logout,
            title = ToreadStrings.LogoutState,
            description = ToreadStrings.LogoutLocalOnlyHint,
            enabled = logoutEnabled,
            onClick = onLogout,
        )
    }
}

/**
 * 隐私政策与用户协议共用的正文页。
 *
 * 正文较长，直接跟随设置页自身的滚动容器，不再嵌套一层滚动，避免出现双滚动条。
 */
@Composable
private fun LegalTextSettingsContent(
    updated: String,
    body: String,
) {
    Text(
        text = updated,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
    )
    Text(
        text = body,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * The reader's own category rules and what the app has learned from them.
 *
 * Rules are plain `关键词 -> 分类` pairs applied to the title, series or description of a book; the
 * memory records the categories the reader already chose for an author or series. Both are
 * device-level, both stay local, and both only ever produce suggestions on the book info page.
 */
@Composable
private fun CategoryLexiconSettingsContent(
    settingsRepository: LocalSettingsRepository?,
) {
    val settingsEnabled = settingsRepository != null
    var customEntries by remember(settingsRepository) {
        mutableStateOf(settingsRepository?.categoryDictionary().orEmpty())
    }
    var memoryEntries by remember(settingsRepository) {
        mutableStateOf(settingsRepository?.categoryMemory().orEmpty())
    }
    var addDialogVisible by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var autoCategorize by remember(settingsRepository) {
        mutableStateOf(settingsRepository?.autoCategorizeEnabled() == true)
    }

    SettingsGroup(title = ToreadStrings.SettingsCategoryLexicon) {
        Text(
            text = ToreadStrings.CategoryLexiconIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        SettingsSwitchRow(
            symbol = ToreadSymbols.Tune,
            title = ToreadStrings.CategoryLexiconAutoTitle,
            description = ToreadStrings.CategoryLexiconAutoDescription,
            checked = autoCategorize,
            enabled = settingsEnabled,
            onCheckedChange = { enabled ->
                settingsRepository?.setAutoCategorizeEnabled(enabled)
                autoCategorize = enabled
            },
        )
        Text(
            text = ToreadStrings.CategoryLexiconBodySampleNote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    // The shipped dictionary, one row per category, read-only: the reader can see exactly what the
    // app already knows before adding a rule of their own.
    SettingsGroup(title = ToreadStrings.CategoryLexiconBuiltInTitle) {
        Text(
            text = ToreadStrings.categoryLexiconBuiltInHint(
                categoryCount = builtInCategoryKeywords.size,
                keywordCount = builtInCategoryKeywords.sumOf { (_, keywords) -> keywords.size },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        builtInCategoryKeywords.forEach { (category, keywords) ->
            CategoryEntryRow(
                symbol = ToreadSymbols.MenuBook,
                title = category,
                description = keywords.joinToString("、"),
                enabled = true,
            )
        }
    }

    SettingsGroup(title = ToreadStrings.CategoryLexiconCustomTitle) {
        if (customEntries.isEmpty()) {
            Text(
                text = ToreadStrings.CategoryLexiconCustomEmpty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        customEntries.forEach { entry ->
            CategoryEntryRow(
                symbol = ToreadSymbols.Tune,
                title = entry.keyword,
                description = entry.category,
                enabled = settingsEnabled,
                onRemove = {
                    val updated = customEntries.filterNot { it == entry }
                    settingsRepository?.setCategoryDictionary(updated)
                    customEntries = updated
                },
            )
        }
        SettingsNavigationRow(
            symbol = ToreadSymbols.Add,
            title = ToreadStrings.CategoryLexiconAdd,
            enabled = settingsEnabled,
            onClick = {
                keyword = ""
                category = ""
                error = null
                addDialogVisible = true
            },
        )
    }

    SettingsGroup(title = ToreadStrings.CategoryLexiconMemoryTitle) {
        if (memoryEntries.isEmpty()) {
            Text(
                text = ToreadStrings.CategoryLexiconMemoryEmpty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            memoryEntries.take(MaximumShownMemoryEntries).forEach { entry ->
                CategoryEntryRow(
                    symbol = ToreadSymbols.MenuBook,
                    title = entry.keyword,
                    description = entry.category,
                    enabled = settingsEnabled,
                    onRemove = {
                        val updated = removeCategoryMemory(memoryEntries, entry)
                        settingsRepository?.setCategoryMemory(updated)
                        memoryEntries = updated
                    },
                )
            }
            SettingsNavigationRow(
                symbol = ToreadSymbols.DeleteSweep,
                title = ToreadStrings.CategoryLexiconMemoryClear,
                enabled = settingsEnabled,
                onClick = {
                    settingsRepository?.setCategoryMemory(emptyList())
                    memoryEntries = emptyList()
                },
            )
        }
    }

    if (addDialogVisible) {
        AlertDialog(
            onDismissRequest = { addDialogVisible = false },
            title = { Text(ToreadStrings.CategoryLexiconAddTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        singleLine = true,
                        label = { Text(ToreadStrings.CategoryLexiconKeywordLabel) },
                        placeholder = { Text(ToreadStrings.CategoryLexiconKeywordPlaceholder) },
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        singleLine = true,
                        label = { Text(ToreadStrings.CategoryLexiconCategoryLabel) },
                        placeholder = { Text(ToreadStrings.CategoryLexiconCategoryPlaceholder) },
                    )
                    error?.let { text ->
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = BookCategoryEntry(
                            keyword = keyword.trim(),
                            category = category.trim(),
                        )
                        if (entry.keyword.length < 2 || entry.category.isBlank()) {
                            error = ToreadStrings.CategoryLexiconInvalid
                            return@TextButton
                        }
                        val updated = (listOf(entry) + customEntries).distinct()
                        settingsRepository?.setCategoryDictionary(updated)
                        customEntries = updated
                        addDialogVisible = false
                    },
                ) {
                    Text(ToreadStrings.CategoryLexiconSave)
                }
            },
            dismissButton = {
                TextButton(onClick = { addDialogVisible = false }) {
                    Text(ToreadStrings.DialogCancel)
                }
            },
        )
    }
}

/** One dictionary or memory rule: the keyword, the category it maps to, and a delete action. */
@Composable
private fun CategoryEntryRow(
    symbol: DrawableResource,
    title: String,
    description: String,
    enabled: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    SettingsRow(
        symbol = symbol,
        enabled = enabled,
        modifier = Modifier,
        trailing = {
            // Built-in rules have no delete action: they are shown so the reader knows what already
            // applies, and their own rules take precedence over them.
            onRemove?.let { remove ->
                IconButton(onClick = remove, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    ToreadSymbolIcon(
                        symbol = ToreadSymbols.Remove,
                        contentDescription = ToreadStrings.CategoryLexiconRemoveHint,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        title = title,
        description = description,
    )
}

/** The settings list never grows past this many memory rows; the rest stay stored. */
private const val MaximumShownMemoryEntries = 20

@Composable
private fun InterfaceSettingsContent(    bookshelfLayoutMode: BookshelfLayoutMode,
    settingsEnabled: Boolean,
    onBookshelfLayoutModeChange: (BookshelfLayoutMode) -> Unit,
) {
    SettingsGroup(title = ToreadStrings.BookshelfLayout) {
        Text(
            text = ToreadStrings.BookshelfLayoutDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SettingsChoiceRow(
            title = ToreadStrings.BookshelfLayoutGrid,
            selected = bookshelfLayoutMode == BookshelfLayoutMode.Grid,
            enabled = settingsEnabled,
            onClick = { onBookshelfLayoutModeChange(BookshelfLayoutMode.Grid) },
        )
        SettingsChoiceRow(
            title = ToreadStrings.BookshelfLayoutList,
            selected = bookshelfLayoutMode == BookshelfLayoutMode.List,
            enabled = settingsEnabled,
            onClick = { onBookshelfLayoutModeChange(BookshelfLayoutMode.List) },
        )
    }
}

@Composable
private fun AppearanceSettingsContent(
    appearance: ToreadAppearancePreferences,
    settingsEnabled: Boolean,
    onAppearanceChange: (ToreadAppearancePreferences) -> Unit,
) {
    SettingsGroup(title = ToreadStrings.SettingsSectionTheme) {
        ThemeModePicker(
            selected = appearance.themeMode,
            enabled = settingsEnabled,
            palette = appearance.palette,
            onSelect = { mode ->
                onAppearanceChange(appearance.copy(themeMode = mode))
            },
        )
        Text(
            text = ToreadStrings.ThemeModeDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }

    SettingsGroup {
        SettingsSwitchRow(
            symbol = ToreadSymbols.Palette,
            title = ToreadStrings.DynamicColor,
            description = ToreadStrings.DynamicColorDescription,
            checked = appearance.dynamicColorEnabled,
            enabled = settingsEnabled,
            onCheckedChange = { enabled ->
                onAppearanceChange(appearance.copy(dynamicColorEnabled = enabled))
            },
        )
        SettingsSwitchRow(
            symbol = ToreadSymbols.DarkMode,
            title = ToreadStrings.HighContrastDarkTheme,
            description = ToreadStrings.HighContrastDarkThemeDescription,
            checked = appearance.highContrastDarkTheme,
            enabled = settingsEnabled,
            onCheckedChange = { enabled ->
                onAppearanceChange(appearance.copy(highContrastDarkTheme = enabled))
            },
        )
        SettingsSwitchRow(
            symbol = ToreadSymbols.LightMode,
            title = ToreadStrings.ReaderNightFollowSystem,
            description = ToreadStrings.ReaderNightFollowSystemDescription,
            checked = appearance.readerNightModeFollowSystem,
            enabled = settingsEnabled,
            onCheckedChange = { enabled ->
                onAppearanceChange(appearance.copy(readerNightModeFollowSystem = enabled))
            },
        )
    }

    SettingsGroup(title = ToreadStrings.SettingsSectionPalette) {
        Text(
            text = if (appearance.dynamicColorEnabled) {
                ToreadStrings.PaletteDynamicColorHint
            } else {
                ToreadStrings.PaletteDescription
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        PaletteGrid(
            selected = appearance.palette,
            enabled = settingsEnabled,
            onSelect = { palette ->
                // Choosing a palette is a choice about the accent, so it takes over from dynamic color.
                onAppearanceChange(
                    appearance.copy(palette = palette, dynamicColorEnabled = false),
                )
            },
        )
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                tint = MaterialTheme.colorScheme.onBackground,
                autoMirror = true,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsAccountRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One section of the list: a muted label followed by its rows, without cards or dividers. */
@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        content()
    }
}

@Composable
private fun SettingsNavigationRow(
    symbol: DrawableResource,
    title: String,
    description: String? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(
        symbol = symbol,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        trailing = {
            trailingText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            ToreadSymbolIcon(
                symbol = ToreadSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
                autoMirror = true,
            )
        },
        title = title,
        description = description,
    )
}

@Composable
private fun SettingsSwitchRow(
    symbol: DrawableResource,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        symbol = symbol,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        title = title,
        description = description,
    )
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsRowMinHeight)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SettingsRow(
    symbol: DrawableResource,
    enabled: Boolean,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
    title: String,
    description: String?,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                fontSize = 17.sp,
            )
            description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun ThemeModePicker(
    selected: ToreadThemeMode,
    enabled: Boolean,
    palette: ToreadPalette,
    onSelect: (ToreadThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ToreadThemeMode.entries.forEach { mode ->
            ThemeModeOption(
                mode = mode,
                palette = palette,
                selected = mode == selected,
                enabled = enabled,
                onSelect = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ToreadThemeMode,
    palette: ToreadPalette,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.74f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            if (mode == ToreadThemeMode.System) {
                ThemeModePreview(palette = palette, darkTheme = false)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            val diagonal = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            clipPath(diagonal) { this@drawWithContent.drawContent() }
                        },
                ) {
                    ThemeModePreview(palette = palette, darkTheme = true)
                }
            } else {
                ThemeModePreview(palette = palette, darkTheme = mode == ToreadThemeMode.Dark)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = mode.label(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Miniature of the app painted with the scheme a mode would produce, used as the theme preview. */
@Composable
private fun ThemeModePreview(palette: ToreadPalette, darkTheme: Boolean) {
    val scheme = remember(palette, darkTheme) {
        toreadColorScheme(
            palette = palette,
            darkTheme = darkTheme,
            highContrastDark = false,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.primaryContainer),
            )
            PreviewLine(color = scheme.primary.copy(alpha = 0.85f), widthFraction = 0.86f)
            PreviewLine(color = scheme.onSurfaceVariant.copy(alpha = 0.5f), widthFraction = 0.7f)
            PreviewLine(color = scheme.onSurfaceVariant.copy(alpha = 0.5f), widthFraction = 0.5f)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.primaryContainer),
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(color: Color, widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** Palette swatches: the light tone a palette produces next to the accent it paints with. */
@Composable
private fun PaletteGrid(
    selected: ToreadPalette,
    enabled: Boolean,
    onSelect: (ToreadPalette) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToreadPalette.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = palette == selected,
                        enabled = enabled,
                        onSelect = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ToreadPalette,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (lightTone, darkTone) = remember(palette) { paletteSwatchColors(palette) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(18.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = lightTone)
                    // Right half in the accent tone, so one swatch shows both ends of the palette.
                    drawArc(
                        color = darkTone,
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = true,
                    )
                }
            }
        }
        Text(
            text = palette.label(),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun ToreadThemeMode.label(): String = when (this) {
    ToreadThemeMode.Light -> ToreadStrings.ThemeModeLight
    ToreadThemeMode.Dark -> ToreadStrings.ThemeModeDark
    ToreadThemeMode.System -> ToreadStrings.ThemeModeSystem
}

internal fun ToreadPalette.label(): String = when (this) {
    ToreadPalette.Indigo -> ToreadStrings.PaletteIndigo
    ToreadPalette.Green -> ToreadStrings.PaletteGreen
    ToreadPalette.Teal -> ToreadStrings.PaletteTeal
    ToreadPalette.Cyan -> ToreadStrings.PaletteCyan
    ToreadPalette.Sky -> ToreadStrings.PaletteSky
    ToreadPalette.Violet -> ToreadStrings.PaletteViolet
    ToreadPalette.Purple -> ToreadStrings.PalettePurple
    ToreadPalette.Pink -> ToreadStrings.PalettePink
    ToreadPalette.Peach -> ToreadStrings.PalettePeach
    ToreadPalette.Orange -> ToreadStrings.PaletteOrange
    ToreadPalette.Amber -> ToreadStrings.PaletteAmber
    ToreadPalette.Slate -> ToreadStrings.PaletteSlate
}
