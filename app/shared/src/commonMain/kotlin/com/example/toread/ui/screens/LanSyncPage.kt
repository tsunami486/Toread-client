package com.example.toread.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.PlatformKind
import com.example.toread.config.GeneratedToreadBuildConfig
import com.example.toread.importing.BookImportResult
import com.example.toread.lan.LanBookSummary
import com.example.toread.lan.LanClientError
import com.example.toread.lan.LanPairingCode
import com.example.toread.lan.LanPeerAddress
import com.example.toread.lan.LanPeerResult
import com.example.toread.lan.LanServeRequest
import com.example.toread.lan.LanServerHandle
import com.example.toread.lan.LanServerUnsupportedException
import com.example.toread.lan.LanSyncEngine
import com.example.toread.lan.lanIpv4Addresses
import com.example.toread.lan.missingBooks
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch

private const val LanSyncPort = 47901

@Composable
internal fun LanSyncPage(
    engine: LanSyncEngine?,
    platformKind: PlatformKind,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf<LanServerHandle?>(null) }
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var addresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var servedEmpty by remember { mutableStateOf(false) }
    var serveBusy by remember { mutableStateOf(false) }
    var serveError by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var peerAddress by remember { mutableStateOf<LanPeerAddress?>(null) }
    var peerName by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf<List<LanBookSummary>>(emptyList()) }
    var receivingHash by remember { mutableStateOf<String?>(null) }
    var progressLine by remember { mutableStateOf<String?>(null) }
    var resultLine by remember { mutableStateOf<String?>(null) }

    // The engine outlives the page, but an open port must not: leaving closes it even after a crash.
    val openHandle by rememberUpdatedState(handle)
    DisposableEffect(Unit) {
        onDispose { openHandle?.stop() }
    }

    fun startServing() {
        val lanEngine = engine ?: return
        val generated = LanPairingCode.generate()
        serveBusy = true
        serveError = null
        scope.launch {
            val request = LanServeRequest(
                LanSyncPort, generated, "${ToreadStrings.AppName} ${platformKind.name}",
                platformKind.name.lowercase(), GeneratedToreadBuildConfig.appVersion,
            )
            runCatching { lanEngine.startServing(request) }
                .onSuccess { started ->
                    handle = started
                    pairingCode = generated
                    addresses = lanIpv4Addresses()
                    servedEmpty = runCatching { lanEngine.localBooks().isEmpty() }.getOrDefault(false)
                }
                .onFailure { error ->
                    serveError = when (error) {
                        is LanServerUnsupportedException -> ToreadStrings.LanSyncUnsupported
                        else -> ToreadStrings.safeUserVisibleMessage(error.message)
                    }
                }
            serveBusy = false
        }
    }

    fun stopServing() {
        handle?.stop()
        handle = null
        pairingCode = null
        addresses = emptyList()
        servedEmpty = false
        serveError = null
    }

    fun connect() {
        val lanEngine = engine ?: return
        val target = LanPeerAddress.parse(host, code) ?: run {
            connectError = ToreadStrings.LanSyncUnreachable
            return
        }
        connecting = true
        connectError = null
        resultLine = null
        peerAddress = null
        peerName = null
        missing = emptyList()
        scope.launch {
            when (val result = lanEngine.fetchPeer(target)) {
                is LanPeerResult.Success -> {
                    peerAddress = target
                    peerName = result.value.deviceName
                    missing = missingBooks(result.value.books, lanEngine.localHashes())
                }
                is LanPeerResult.Failure -> connectError = lanErrorText(result.error)
            }
            connecting = false
        }
    }

    fun receive(book: LanBookSummary) {
        val lanEngine = engine ?: return
        val target = peerAddress ?: return
        resultLine = null
        receivingHash = book.bookHash
        progressLine = ToreadStrings.lanSyncProgress(0L, book.sizeBytes)
        scope.launch {
            val result = lanEngine.receive(target, book) { received, total ->
                progressLine = ToreadStrings.lanSyncProgress(received, total)
            }
            when (result) {
                is LanPeerResult.Failure ->
                    resultLine = ToreadStrings.lanSyncFailed(lanErrorText(result.error))
                is LanPeerResult.Success -> {
                    // Drop the book so an impatient second tap cannot pull it again.
                    missing = missing.filterNot { it.bookHash == book.bookHash }
                    when (val imported = result.value) {
                        is BookImportResult.Imported -> {
                            resultLine = ToreadStrings.lanSyncImported(imported.title)
                            onImported()
                        }
                        is BookImportResult.Duplicate ->
                            resultLine = ToreadStrings.lanSyncDuplicate(imported.title)
                        is BookImportResult.Failed ->
                            resultLine = ToreadStrings.lanSyncFailed(imported.reason)
                    }
                }
            }
            progressLine = null
            receivingHash = null
        }
    }

    PlatformBackHandler(enabled = true, onBack = onBack)
    val colors = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LanSyncHeader(onBack = onBack)
            if (engine == null) {
                LanText(ToreadStrings.databaseInitFailed(null), fontSize = 15.sp)
                return@Column
            }

            LanSyncCard {
                LanText(ToreadStrings.LanSyncServeTitle, 18.sp, FontWeight.SemiBold, colors.onSurface)
                LanText(ToreadStrings.LanSyncServeDescription)
                val running = handle
                if (running == null) {
                    LanSyncButton(ToreadStrings.LanSyncServeStart, !serveBusy, Modifier.fillMaxWidth()) {
                        startServing()
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LanText(ToreadStrings.LanSyncPairingCode, 13.sp)
                            LanText(pairingCode.orEmpty(), 22.sp, FontWeight.Bold, colors.primary)
                        }
                        LanSyncButton(ToreadStrings.LanSyncServeStop, enabled = true) { stopServing() }
                    }
                    LanText(ToreadStrings.LanSyncAddress, 13.sp)
                    if (addresses.isEmpty()) {
                        LanText(ToreadStrings.LanSyncNoLanAddress)
                    } else {
                        addresses.forEach { LanText("$it:${running.port}", 16.sp, FontWeight.Medium, colors.onSurface) }
                    }
                    if (servedEmpty) LanText(ToreadStrings.LanSyncEmptyShelf)
                }
                serveError?.let { LanText(it, color = colors.error) }
            }

            LanSyncCard {
                LanText(ToreadStrings.LanSyncConnectTitle, 18.sp, FontWeight.SemiBold, colors.onSurface)
                LanSyncField(host, { host = it }, ToreadStrings.LanSyncAddress, ToreadStrings.LanSyncHostHint)
                LanSyncField(code, { code = it }, ToreadStrings.LanSyncPairingCode, ToreadStrings.LanSyncCodeHint)
                LanSyncButton(
                    text = ToreadStrings.LanSyncConnect,
                    enabled = !connecting && receivingHash == null && host.isNotBlank() && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { connect() }
                connectError?.let { LanText(it, color = colors.error) }
                peerName?.let { name ->
                    LanText(name, 16.sp, FontWeight.SemiBold, colors.onSurface, maxLines = 1)
                    LanText(
                        if (missing.isEmpty()) ToreadStrings.LanSyncAllPresent
                        else ToreadStrings.lanSyncMissingCount(missing.size),
                    )
                    missing.forEach { book ->
                        MissingBookRow(book, receivingHash == book.bookHash, receivingHash == null && !connecting) {
                            receive(book)
                        }
                    }
                }
                progressLine?.let { LanText(it, color = colors.primary) }
                resultLine?.let { LanText(it, color = colors.onSurface) }
            }
        }
    }
}

@Composable
private fun LanSyncHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            ToreadSymbolIcon(
                ToreadSymbols.ArrowBack, ToreadStrings.BackToBookshelf,
                tint = MaterialTheme.colorScheme.onBackground, autoMirror = true,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = ToreadStrings.LanSyncTitle, color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LanSyncCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun LanSyncButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(44.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LanSyncField(value: String, onValueChange: (String) -> Unit, label: String, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        label = { Text(label) }, placeholder = { Text(hint) }, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LanText(
    text: String,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text, color = color, fontSize = fontSize, lineHeight = fontSize * 1.45f,
        fontWeight = fontWeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MissingBookRow(book: LanBookSummary, receiving: Boolean, enabled: Boolean, onReceive: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LanText(book.title, 15.sp, FontWeight.Medium, MaterialTheme.colorScheme.onSurface, maxLines = 2)
            LanText(
                listOf(
                    book.author?.takeIf { it.isNotBlank() } ?: ToreadStrings.UnknownAuthor,
                    book.format.uppercase(), ToreadStrings.bookFileSizeLabel(book.sizeBytes),
                ).joinToString(" · "),
                13.sp, maxLines = 1,
            )
        }
        LanSyncButton(
            text = if (receiving) ToreadStrings.LanSyncReceiving else ToreadStrings.LanSyncReceive,
            enabled = enabled,
            onClick = onReceive,
        )
    }
}

private fun lanErrorText(error: LanClientError): String = when (error) {
    LanClientError.Unauthorized -> ToreadStrings.LanSyncUnauthorized
    is LanClientError.Incompatible -> ToreadStrings.LanSyncIncompatible
    is LanClientError.Unreachable, is LanClientError.TransferFailed -> ToreadStrings.LanSyncUnreachable
}
