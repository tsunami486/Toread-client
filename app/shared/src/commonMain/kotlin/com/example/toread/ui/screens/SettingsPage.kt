package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.text.ToreadStrings

private enum class SettingsSection(
    val title: String,
    val imageVector: ImageVector,
) {
    Account(ToreadStrings.AccountInfo, Icons.Outlined.AccountCircle),
    Sync(ToreadStrings.SyncAndNetwork, Icons.Outlined.Sync),
    Appearance(ToreadStrings.AppearanceAndReading, Icons.Outlined.Palette),
    Storage(ToreadStrings.StorageAndSystem, Icons.Outlined.Tune),
}

private val ExpandedSettingsMinWidth = 900.dp

@Composable
internal fun SettingsPage(
    settingsRepository: LocalSettingsRepository?,
    dynamicColorEnabled: Boolean,
    autoSyncEnabled: Boolean,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onClearCache: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

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
    var showAboutDialog by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(SettingsSection.Account) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (maxWidth >= ExpandedSettingsMinWidth) {
            SettingsTwoPane(
                selectedSection = selectedSection,
                onSelectedSectionChange = { selectedSection = it },
                accountTitle = accountTitle,
                accountSubtitle = accountSubtitle,
                logoutEnabled = settingsRepository != null,
                dynamicColorEnabled = dynamicColorEnabled,
                autoSyncEnabled = autoSyncEnabled,
                settingsEnabled = settingsRepository != null,
                onDynamicColorEnabledChange = onDynamicColorEnabledChange,
                onAutoSyncEnabledChange = onAutoSyncEnabledChange,
                onBack = onBack,
                onLogout = onLogout,
                onClearCache = onClearCache,
                onAbout = { showAboutDialog = true },
            )
        } else {
            SettingsSinglePane(
                accountTitle = accountTitle,
                accountSubtitle = accountSubtitle,
                logoutEnabled = settingsRepository != null,
                dynamicColorEnabled = dynamicColorEnabled,
                autoSyncEnabled = autoSyncEnabled,
                settingsEnabled = settingsRepository != null,
                onDynamicColorEnabledChange = onDynamicColorEnabledChange,
                onAutoSyncEnabledChange = onAutoSyncEnabledChange,
                onBack = onBack,
                onLogout = onLogout,
                onClearCache = onClearCache,
                onAbout = { showAboutDialog = true },
            )
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
                    Text(ToreadStrings.Version100)
                    Text(ToreadStrings.AboutDescription)
                }
            },
        )
    }
}

@Composable
private fun SettingsSinglePane(
    accountTitle: String,
    accountSubtitle: String,
    logoutEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    autoSyncEnabled: Boolean,
    settingsEnabled: Boolean,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsHeader(onBack = onBack)
        AccountInfoCard(
            title = accountTitle,
            subtitle = accountSubtitle,
            logoutEnabled = logoutEnabled,
            onLogout = onLogout,
        )
        SettingsSectionCard(
            title = SettingsSection.Sync.title,
            imageVector = SettingsSection.Sync.imageVector,
        ) {
            SyncSettingsContent(
                autoSyncEnabled = autoSyncEnabled,
                settingsEnabled = settingsEnabled,
                onAutoSyncEnabledChange = onAutoSyncEnabledChange,
            )
        }
        SettingsSectionCard(
            title = SettingsSection.Appearance.title,
            imageVector = SettingsSection.Appearance.imageVector,
        ) {
            AppearanceSettingsContent(
                dynamicColorEnabled = dynamicColorEnabled,
                settingsEnabled = settingsEnabled,
                onDynamicColorEnabledChange = onDynamicColorEnabledChange,
            )
        }
        SettingsSectionCard(
            title = SettingsSection.Storage.title,
            imageVector = SettingsSection.Storage.imageVector,
        ) {
            StorageSettingsContent(
                onClearCache = onClearCache,
                onAbout = onAbout,
            )
        }
    }
}

@Composable
private fun SettingsTwoPane(
    selectedSection: SettingsSection,
    onSelectedSectionChange: (SettingsSection) -> Unit,
    accountTitle: String,
    accountSubtitle: String,
    logoutEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    autoSyncEnabled: Boolean,
    settingsEnabled: Boolean,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSidebar(
            selectedSection = selectedSection,
            onSelectedSectionChange = onSelectedSectionChange,
            accountSubtitle = accountSubtitle,
            onBack = onBack,
        )
        SettingsDetailPane(
            selectedSection = selectedSection,
            accountTitle = accountTitle,
            accountSubtitle = accountSubtitle,
            logoutEnabled = logoutEnabled,
            dynamicColorEnabled = dynamicColorEnabled,
            autoSyncEnabled = autoSyncEnabled,
            settingsEnabled = settingsEnabled,
            onDynamicColorEnabledChange = onDynamicColorEnabledChange,
            onAutoSyncEnabledChange = onAutoSyncEnabledChange,
            onLogout = onLogout,
            onClearCache = onClearCache,
            onAbout = onAbout,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SettingsSidebar(
    selectedSection: SettingsSection,
    onSelectedSectionChange: (SettingsSection) -> Unit,
    accountSubtitle: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsHeader(onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionIcon(imageVector = Icons.Outlined.AccountCircle, modifier = Modifier.size(56.dp))
            Text(
                text = accountSubtitle,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SettingsSidebarGroup(title = ToreadStrings.Account) {
            SettingsSidebarItem(
                section = SettingsSection.Account,
                selected = selectedSection == SettingsSection.Account,
                onClick = { onSelectedSectionChange(SettingsSection.Account) },
            )
        }
        SettingsSidebarGroup(title = ToreadStrings.SyncAndReading) {
            SettingsSidebarItem(
                section = SettingsSection.Sync,
                selected = selectedSection == SettingsSection.Sync,
                onClick = { onSelectedSectionChange(SettingsSection.Sync) },
            )
            SettingsSidebarItem(
                section = SettingsSection.Appearance,
                selected = selectedSection == SettingsSection.Appearance,
                onClick = { onSelectedSectionChange(SettingsSection.Appearance) },
            )
        }
        SettingsSidebarGroup(title = ToreadStrings.StorageAndSystem) {
            SettingsSidebarItem(
                section = SettingsSection.Storage,
                selected = selectedSection == SettingsSection.Storage,
                onClick = { onSelectedSectionChange(SettingsSection.Storage) },
            )
        }
    }
}

@Composable
private fun SettingsSidebarGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        content()
    }
}

@Composable
private fun SettingsSidebarItem(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.imageVector,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = section.title,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingsDetailPane(
    selectedSection: SettingsSection,
    accountTitle: String,
    accountSubtitle: String,
    logoutEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    autoSyncEnabled: Boolean,
    settingsEnabled: Boolean,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionIcon(imageVector = selectedSection.imageVector)
                Text(
                    text = selectedSection.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            when (selectedSection) {
                SettingsSection.Account -> AccountInfoContent(
                    title = accountTitle,
                    subtitle = accountSubtitle,
                    logoutEnabled = logoutEnabled,
                    onLogout = onLogout,
                )
                SettingsSection.Sync -> SyncSettingsContent(
                    autoSyncEnabled = autoSyncEnabled,
                    settingsEnabled = settingsEnabled,
                    onAutoSyncEnabledChange = onAutoSyncEnabledChange,
                )
                SettingsSection.Appearance -> AppearanceSettingsContent(
                    dynamicColorEnabled = dynamicColorEnabled,
                    settingsEnabled = settingsEnabled,
                    onDynamicColorEnabledChange = onDynamicColorEnabledChange,
                )
                SettingsSection.Storage -> StorageSettingsContent(
                    onClearCache = onClearCache,
                    onAbout = onAbout,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = ToreadStrings.Settings,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AccountInfoCard(
    title: String,
    subtitle: String,
    logoutEnabled: Boolean,
    onLogout: () -> Unit,
) {
    SettingsSectionCard(
        title = ToreadStrings.AccountInfo,
        imageVector = Icons.Outlined.AccountCircle,
    ) {
        AccountInfoContent(
            title = title,
            subtitle = subtitle,
            logoutEnabled = logoutEnabled,
            onLogout = onLogout,
        )
    }
}

@Composable
private fun AccountInfoContent(
    title: String,
    subtitle: String,
    logoutEnabled: Boolean,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionIcon(
                imageVector = Icons.Outlined.AccountCircle,
                modifier = Modifier.size(72.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = onLogout,
            enabled = logoutEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(ToreadStrings.LogoutState, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = ToreadStrings.LogoutLocalOnlyHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SyncSettingsContent(
    autoSyncEnabled: Boolean,
    settingsEnabled: Boolean,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = ToreadStrings.AutoSync,
        description = ToreadStrings.AutoSyncDescription,
        checked = autoSyncEnabled,
        enabled = settingsEnabled,
        onCheckedChange = onAutoSyncEnabledChange,
    )
}

@Composable
private fun AppearanceSettingsContent(
    dynamicColorEnabled: Boolean,
    settingsEnabled: Boolean,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = ToreadStrings.DynamicColor,
        description = ToreadStrings.DynamicColorDescription,
        checked = dynamicColorEnabled,
        enabled = settingsEnabled,
        onCheckedChange = onDynamicColorEnabledChange,
    )
}

@Composable
private fun StorageSettingsContent(
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
//        SettingsInfoRow(
//            imageVector = Icons.Outlined.Folder,
//            title = ToreadStrings.LocalStorage,
//            trailingText = ToreadStrings.AppPrivateDirectory,
//        )
//        SettingsDivider()
        SettingsActionRow(
            imageVector = Icons.Outlined.DeleteSweep,
            title = ToreadStrings.ClearCache,
            onClick = onClearCache,
        )
        SettingsDivider()
        SettingsActionRow(
            imageVector = Icons.Outlined.Info,
            title = ToreadStrings.About,
            trailingText = ToreadStrings.Version100,
            onClick = onAbout,
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    imageVector: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionIcon(imageVector = imageVector)
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
private fun SectionIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(40.dp),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsInfoRow(
    imageVector: ImageVector,
    title: String,
    trailingText: String,
) {
    SettingsBaseRow(imageVector = imageVector, title = title, trailingText = trailingText)
}

@Composable
private fun SettingsActionRow(
    imageVector: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        imageVector = imageVector,
        title = title,
        trailingText = trailingText,
        modifier = Modifier.clickable(onClick = onClick),
        showChevron = true,
    )
}

@Composable
private fun SettingsBaseRow(
    imageVector: ImageVector,
    title: String,
    trailingText: String?,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        trailingText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
