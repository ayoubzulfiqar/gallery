package com.ayoubzulfiqar.gallery.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.PinPolicy
import com.ayoubzulfiqar.gallery.domain.model.VaultItem
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import com.ayoubzulfiqar.gallery.domain.model.formatDuration
import com.ayoubzulfiqar.gallery.domain.model.formatFileSize
import com.ayoubzulfiqar.gallery.ui.common.ConfirmDialog
import com.ayoubzulfiqar.gallery.ui.common.EmptyState
import com.ayoubzulfiqar.gallery.ui.common.EncryptedImage
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.common.TransferProgressBar
import com.ayoubzulfiqar.gallery.ui.common.formatDateTime
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.timeline.MarqueeSelectionBar
import com.ayoubzulfiqar.gallery.ui.timeline.SelectionActions

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val vaultState = uiState.vaultState

    GalleryBackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }
    GalleryBackHandler(enabled = uiState.pinMode in setOf(PinMode.CHANGE_CURRENT, PinMode.CHANGE_NEW, PinMode.CHANGE_CONFIRM)) {
        viewModel.cancelPinFlow()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            VaultHeader(
                showActions = vaultState is VaultState.Unlocked && !uiState.showsPinPad,
                onChangePin = viewModel::startChangePin,
                onLock = viewModel::lock
            )
            when {
                vaultState is VaultState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryColors.Accent)
                }
                vaultState is VaultState.Unavailable -> EmptyState(
                    icon = GalleryIcons.Warning,
                    title = "Vault can't be opened",
                    message = "${vaultState.reason}. The encrypted files can only be opened on the device that created them.",
                    actionLabel = "Erase vault",
                    onAction = { confirmReset = true }
                )
                uiState.showsPinPad -> PinPad(
                    state = uiState,
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    onSubmit = viewModel::onSubmit,
                    onCancel = if (uiState.pinMode in setOf(PinMode.CHANGE_CURRENT, PinMode.CHANGE_NEW, PinMode.CHANGE_CONFIRM, PinMode.SETUP_CONFIRM)) {
                        viewModel::cancelPinFlow
                    } else {
                        null
                    },
                    onForgot = if (vaultState is VaultState.Locked) ({ confirmReset = true }) else null,
                    onCancelPendingMove = viewModel::cancelPendingMove
                )
                else -> UnlockedContent(
                    state = uiState,
                    viewModel = viewModel,
                    onOpen = { viewModel.openPreview(it.id) }
                )
            }
        }

        MarqueeSelectionBar(
            selectedCount = uiState.selectedIds.size,
            totalCount = uiState.items.size,
            visible = uiState.isSelectionMode && vaultState is VaultState.Unlocked,
            actions = SelectionActions(
                onRestore = { viewModel.restore(uiState.selectedIds) },
                onDelete = { pendingDelete = uiState.selectedIds },
                onSelectAll = viewModel::selectAll,
                onClose = viewModel::clearSelection
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        )

        // The ViewModel drops the preview as soon as the vault locks, so decrypted pixels never outlive the session.
        uiState.previewItem?.takeIf { vaultState is VaultState.Unlocked }?.let { item ->
            VaultItemPreview(
                item = item,
                loadImage = { viewModel.readImage(item.id) },
                loadThumbnail = { viewModel.readThumbnail(item.id) },
                onRestore = { viewModel.restore(listOf(item.id)) },
                onDelete = { pendingDelete = setOf(item.id) },
                onClose = viewModel::closePreview
            )
        }
    }

    pendingDelete?.let { ids ->
        ConfirmDialog(
            title = if (ids.size == 1) "Delete permanently?" else "Delete ${ids.size} items permanently?",
            message = "Vaulted items are the only copy. Deleting them can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(ids)
            },
            onDismiss = { pendingDelete = null }
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Erase the vault?",
            message = "Without the PIN the vault can't be decrypted. Erasing permanently destroys every item in it and lets you create a new PIN.",
            confirmLabel = "Erase everything",
            destructive = true,
            onConfirm = {
                confirmReset = false
                viewModel.resetVault()
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun VaultHeader(showActions: Boolean, onChangePin: () -> Unit, onLock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(GalleryIcons.Vault, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(22.dp))
        Text(
            text = "Vault",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        if (showActions) {
            IconButton(onClick = onChangePin, modifier = Modifier.size(44.dp)) {
                Icon(GalleryIcons.Key, contentDescription = "Change PIN", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
            TextButton(onClick = onLock) {
                Icon(GalleryIcons.Lock, contentDescription = null, tint = GalleryColors.Danger, modifier = Modifier.size(18.dp))
                Text(" Lock", color = GalleryColors.Danger, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PinPad(
    state: VaultUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: (() -> Unit)?,
    onForgot: (() -> Unit)?,
    onCancelPendingMove: () -> Unit
) {
    val isLockedOut = state.lockoutSecondsRemaining > 0
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        if (state.pendingMoveCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GalleryColors.Accent.copy(alpha = 0.12f))
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (state.vaultState is VaultState.NotSetUp) "Create a PIN" else "Unlock"} to move ${state.pendingMoveCount} item${if (state.pendingMoveCount == 1) "" else "s"} into the vault",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancelPendingMove) { Text("Cancel", color = GalleryColors.Accent) }
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GalleryColors.Accent.copy(alpha = 0.12f))
                .border(1.5.dp, GalleryColors.Accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(GalleryIcons.Lock, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(30.dp))
        }
        Text(
            text = state.pinMode.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = when (state.pinMode) {
                PinMode.SETUP_ENTER, PinMode.CHANGE_NEW -> "Use ${PinPolicy.MIN_LENGTH}–${PinPolicy.MAX_LENGTH} digits. It can't be recovered if you forget it."
                PinMode.SETUP_CONFIRM, PinMode.CHANGE_CONFIRM -> "Enter the same PIN again"
                else -> if (state.isDeviceBound) "AES-256-GCM · key bound to this device" else "AES-256-GCM encrypted"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        val dotCount = state.expectedPinLength ?: maxOf(PinPolicy.MIN_LENGTH, state.pinLength)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(top = 24.dp, bottom = 12.dp)
                .semantics { contentDescription = "${state.pinLength} digits entered" }
        ) {
            repeat(dotCount) { i ->
                val filled = i < state.pinLength
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (filled) GalleryColors.Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                        .border(1.dp, if (filled) GalleryColors.Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
        Text(
            text = state.pinMessage ?: "",
            color = if (state.isPinError) GalleryColors.Danger else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(40.dp)
        )

        val submitEnabled = state.pinMode.requiresSubmit && state.pinLength >= PinPolicy.MIN_LENGTH && !state.isBusy
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('S', '0', 'D'))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    when (key) {
                        'D' -> KeypadKey(icon = GalleryIcons.Backspace, label = "Delete digit", enabled = state.pinLength > 0, onClick = onBackspace)
                        'S' -> if (state.pinMode.requiresSubmit) {
                            KeypadKey(icon = GalleryIcons.Check, label = "Confirm PIN", enabled = submitEnabled, highlighted = true, onClick = onSubmit)
                        } else {
                            Spacer(Modifier.size(68.dp))
                        }
                        else -> KeypadKey(text = key.toString(), label = key.toString(), enabled = !isLockedOut && !state.isBusy, onClick = { onDigit(key) })
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isBusy) CircularProgressIndicator(color = GalleryColors.Accent, modifier = Modifier.size(24.dp))
            onCancel?.let { TextButton(onClick = it) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) } }
            onForgot?.let { TextButton(onClick = it) { Text("Forgot PIN?", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) } }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    text: String? = null,
    icon: ImageVector? = null,
    highlighted: Boolean = false
) {
    val background = when {
        highlighted && enabled -> GalleryColors.Accent
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        highlighted && enabled -> Color.White
        enabled -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    }
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        } else if (text != null) {
            Text(text, color = content, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun UnlockedContent(state: VaultUiState, viewModel: VaultViewModel, onOpen: (VaultItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.transfer?.let {
            TransferProgressBar(it.label, it.completed, it.total, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Text(
            text = "${state.items.size} ${if (state.items.size == 1) "item" else "items"} · " +
                if (state.isDeviceBound) "AES-256-GCM, device-bound key" else "AES-256-GCM",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (state.items.isEmpty()) {
            EmptyState(
                icon = GalleryIcons.Vault,
                title = "Vault is empty",
                message = "Long-press photos in your gallery and choose the vault icon to encrypt and hide them here."
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = (maxWidth / 118.dp).toInt().coerceIn(3, 7)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 150.dp
                )
            ) {
                items(state.items, key = { it.id }) { item ->
                    VaultThumbnail(
                        item = item,
                        isSelected = item.id in state.selectedIds,
                        isSelectionMode = state.isSelectionMode,
                        loadThumbnail = { viewModel.readThumbnail(item.id) },
                        onClick = { if (state.isSelectionMode) viewModel.toggleSelection(item.id) else onOpen(item) },
                        onLongClick = { viewModel.toggleSelection(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultThumbnail(
    item: VaultItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    loadThumbnail: suspend () -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .clip(shape)
            .background(Color(0xFF161B20))
            .then(if (isSelected) Modifier.border(2.dp, GalleryColors.Accent, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "Vaulted ${if (item.isVideo) "video" else "photo"} ${item.displayName}" }
    ) {
        if (item.hasThumbnail) {
            EncryptedImage(key = "vault-thumb-${item.id}", contentDescription = null, loadBytes = loadThumbnail, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = if (item.isVideo) GalleryIcons.Play else GalleryIcons.Photos,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(26.dp)
            )
        }
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(GalleryIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                item.durationMs?.let { Text(" ${formatDuration(it)}", color = Color.White, fontSize = 10.sp) }
            }
        }
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) GalleryColors.Accent else Color.Black.copy(alpha = 0.45f))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(GalleryIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun VaultItemPreview(
    item: VaultItem,
    loadImage: suspend () -> ByteArray?,
    loadThumbnail: suspend () -> ByteArray?,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    GalleryBackHandler(enabled = true, onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = true, onClick = {}) // swallow touches meant for the grid below
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(GalleryIcons.Close, contentDescription = "Close", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatFileSize(item.sizeBytes)} · vaulted ${formatDateTime(item.vaultedAtMillis)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val viewableInApp = !item.isVideo && item.sizeBytes <= VaultViewModel.MAX_IN_APP_IMAGE_BYTES
                if (viewableInApp) {
                    EncryptedImage(
                        key = "vault-full-${item.id}",
                        contentDescription = item.displayName,
                        loadBytes = loadImage,
                        maxDimension = 2048,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (item.hasThumbnail) {
                            EncryptedImage(
                                key = "vault-thumb-${item.id}",
                                contentDescription = item.displayName,
                                loadBytes = loadThumbnail,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(220.dp)
                            )
                        }
                        Text(
                            text = if (item.isVideo) "Restore this video to play it." else "This file is too large to preview securely. Restore it to view.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GalleryColors.Accent, contentColor = Color.White)
                ) {
                    Icon(GalleryIcons.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Restore to gallery", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete", color = GalleryColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
