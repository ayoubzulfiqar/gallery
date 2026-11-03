package com.ayoubzulfiqar.gallery.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.timeline.MediaThumbnailItem

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F12))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("← Back", fontSize = 14.sp)
                }

                Text(
                    text = "🔒 Encrypted Vault",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (uiState.isUnlocked) {
                    TextButton(
                        onClick = { viewModel.lockVault() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Text("Lock", fontSize = 14.sp)
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            }

            if (!uiState.isUnlocked) {
                // PIN Entry Keypad
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Enter Vault PIN",
                        style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Protected by AES-256-GCM authenticated encryption",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

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
                                    .background(if (filled) Color(0xFF00B4D8) else Color(0xFF1E262C))
                            )
                        }
                    }

                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Keypad grid
                    val keypad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "⌫")
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
                                        .background(if (key.isNotEmpty()) Color(0xFF1E262C) else Color.Transparent)
                                        .clickable(enabled = key.isNotEmpty()) {
                                            if (key == "⌫") viewModel.onPinBackspace()
                                            else viewModel.onPinDigit(key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Unlocked Vault Items Grid
                if (uiState.items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Vault is empty\nMove photos here to encrypt them securely",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            MediaThumbnailItem(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                onClick = { /* View vaulted item */ },
                                onLongClick = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
