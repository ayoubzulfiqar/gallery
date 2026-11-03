package com.ayoubzulfiqar.gallery.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.timeline.MediaThumbnailItem

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onRestoreItem: (MediaItem) -> Unit = {},
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedVaultItem by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val canvasBackground = if (isDark) Color(0xFF0B0F12) else Color(0xFFF8FAFC)
    val surfaceColor = if (isDark) Color(0xFF161B20) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
    val keyBg = if (isDark) Color(0xFF161B20) else Color.White
    val keyBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val dotEmptyBg = if (isDark) Color(0xFF1E262C) else Color(0xFFE2E8F0)
    val dotEmptyBorder = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
    val accentCyan = Color(0xFF00B4D8)
    val dangerRed = Color(0xFFEF4444)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBackground)
    ) {
        val screenWidth = maxWidth
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar safe Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = GalleryIcons.ChevronBack,
                        contentDescription = "Back",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = GalleryIcons.Vault,
                        contentDescription = null,
                        tint = accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Encrypted Vault",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                if (uiState.isUnlocked) {
                    TextButton(
                        onClick = { viewModel.lockVault() },
                        colors = ButtonDefaults.textButtonColors(contentColor = dangerRed)
                    ) {
                        Text("Lock", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Box(modifier = Modifier.size(38.dp))
                }
            }

            if (!uiState.isUnlocked) {
                // PIN Entry Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Biometric / Shield Glowing Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accentCyan.copy(alpha = if (isDark) 0.12f else 0.10f))
                            .border(1.5.dp, accentCyan.copy(alpha = if (isDark) 0.35f else 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = GalleryIcons.Vault,
                            contentDescription = "Vault Locked",
                            tint = accentCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Enter Vault PIN",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Protected by AES-256-GCM authenticated encryption",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Default PIN hint badge
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 28.dp)
                            .background(accentCyan.copy(alpha = if (isDark) 0.12f else 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Default PIN: 1234",
                            color = accentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        for (i in 0 until 4) {
                            val filled = i < uiState.pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) accentCyan else dotEmptyBg)
                                    .border(
                                        1.dp,
                                        if (filled) accentCyan else dotEmptyBorder,
                                        CircleShape
                                    )
                            )
                        }
                    }

                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = dangerRed,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Keypad grid
                    val keypad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "DEL")
                    )

                    keypad.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(if (key.isNotEmpty()) keyBg else Color.Transparent)
                                        .border(
                                            if (key.isNotEmpty()) 1.dp else 0.dp,
                                            if (key.isNotEmpty()) keyBorder else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable(enabled = key.isNotEmpty()) {
                                            if (key == "DEL") viewModel.onPinBackspace()
                                             else viewModel.onPinDigit(key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "DEL") {
                                        Icon(
                                            imageVector = GalleryIcons.Backspace,
                                            contentDescription = "Backspace",
                                            tint = textPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                color = textPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 26.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Unlocked Vault Items Screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.items.size} ${if (uiState.items.size == 1) "item" else "items"} securely vaulted",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "AES-256-GCM Sandbox",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                if (uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                imageVector = GalleryIcons.Vault,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "Vault is empty",
                                color = textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Move photos here from the viewer or albums to encrypt and hide them securely from your gallery.",
                                color = textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val vaultColumns = (screenWidth / 115.dp).toInt().coerceIn(2, 6)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(vaultColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 4.dp,
                            end = 4.dp,
                            top = 4.dp,
                            bottom = navBarBottom + 96.dp
                        )
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            MediaThumbnailItem(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                onClick = { selectedVaultItem = item },
                                onLongClick = { selectedVaultItem = item }
                            )
                        }
                    }
                }
            }
        }

        // Vault Item Detail & Actions Modal Dialog
        if (selectedVaultItem != null) {
            val item = selectedVaultItem!!
            Dialog(onDismissRequest = { selectedVaultItem = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AsyncMediaImage(
                            uri = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            isThumbnail = false,
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val kbSize = item.sizeBytes / 1024
                            val sizeText = if (kbSize > 1024) "${kbSize / 1024} MB" else "$kbSize KB"
                            Text(
                                text = "$sizeText • Protected with AES-256-GCM",
                                style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.restoreItem(item) { restored ->
                                        onRestoreItem(restored)
                                    }
                                    selectedVaultItem = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentCyan)
                            ) {
                                Text("Restore to Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteItem(item)
                                    selectedVaultItem = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, dangerRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerRed)
                            ) {
                                Text("Delete", color = dangerRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        TextButton(
                            onClick = { selectedVaultItem = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
