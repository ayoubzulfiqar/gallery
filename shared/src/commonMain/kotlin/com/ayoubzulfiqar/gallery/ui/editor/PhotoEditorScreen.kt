package com.ayoubzulfiqar.gallery.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.editor.ColorMatrixHelper
import com.ayoubzulfiqar.gallery.editor.FilterPresets
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recipe by viewModel.currentRecipe.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()

    var activeAdjustmentParam by remember { mutableStateOf("Brightness") }

    val colorMatrixArray = remember(recipe) {
        val base = ColorMatrixHelper.buildColorMatrix(recipe.adjustments)
        val filter = recipe.filterPresetId?.let { FilterPresets.getById(it) }
        if (filter != null) {
            ColorMatrixHelper.multiply(base, filter.colorMatrix)
        } else {
            base
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F12))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status-bar safe Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = GalleryIcons.Close,
                        contentDescription = "Cancel",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = GalleryIcons.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = GalleryIcons.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B4D8),
                        contentColor = Color.Black
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = GalleryIcons.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " Save",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real-Time GPU Preview Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val tintHue = (viewModel.mediaItem.id.hashCode() and 0xFF) / 255f
                val crop = recipe.cropRect
                val baseColor = Color.hsv(tintHue * 360f, 0.45f, 0.55f)

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .graphicsLayer {
                            rotationZ = recipe.rotationDegrees
                            scaleX = if (recipe.flipHorizontal) -1f else 1f
                            scaleY = if (recipe.flipVertical) -1f else 1f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            if (crop != null) 2.5.dp else 0.dp,
                            Color(0xFF00B4D8),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val paint = Paint().apply {
                            color = baseColor
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray))
                        }
                        drawIntoCanvas { canvas ->
                            canvas.drawRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                paint = paint
                            )
                        }
                    }

                    Text(
                        text = viewModel.mediaItem.displayName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Tool Tab Selector with custom vector icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    EditorTab.FILTERS to ("Filters" to GalleryIcons.Sparkles),
                    EditorTab.ADJUST to ("Adjust" to GalleryIcons.Sliders),
                    EditorTab.CROP to ("Crop" to GalleryIcons.Crop),
                    EditorTab.ROTATE to ("Rotate" to GalleryIcons.Rotate)
                ).forEach { (tab, meta) ->
                    val isSelected = activeTab == tab
                    val bgColor by animateColorAsState(if (isSelected) Color(0xFF00B4D8) else Color.Transparent)
                    val tintColor by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.6f))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .clickable { viewModel.setTab(tab) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = meta.second,
                            contentDescription = meta.first,
                            tint = tintColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = meta.first,
                            color = tintColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Controls Area with Navigation Bar Inset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                when (activeTab) {
                    EditorTab.FILTERS -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterPresets.ALL.forEach { preset ->
                                val isSelected = recipe.filterPresetId == preset.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.applyFilter(preset.id) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0xFF00B4D8) else Color(0xFF1E262C))
                                            .border(
                                                if (isSelected) 2.5.dp else 1.dp,
                                                if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(10.dp)
                                            )
                                    )
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) Color(0xFF00B4D8) else Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    EditorTab.ADJUST -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Brightness", "Contrast", "Saturation", "Warmth", "Vignette").forEach { param ->
                                    FilterChip(
                                        selected = activeAdjustmentParam == param,
                                        onClick = { activeAdjustmentParam = param },
                                        label = { Text(param, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF00B4D8),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            when (activeAdjustmentParam) {
                                "Contrast" -> {
                                    Slider(
                                        value = recipe.adjustments.contrast,
                                        onValueChange = { viewModel.updateContrast(it) },
                                        valueRange = 0f..2f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00B4D8))
                                    )
                                }
                                "Saturation" -> {
                                    Slider(
                                        value = recipe.adjustments.saturation,
                                        onValueChange = { viewModel.updateSaturation(it) },
                                        valueRange = 0f..2f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00B4D8))
                                    )
                                }
                                "Warmth" -> {
                                    Slider(
                                        value = recipe.adjustments.warmth,
                                        onValueChange = { viewModel.updateWarmth(it) },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00B4D8))
                                    )
                                }
                                "Vignette" -> {
                                    Slider(
                                        value = recipe.adjustments.vignette,
                                        onValueChange = { viewModel.updateVignette(it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00B4D8))
                                    )
                                }
                                else -> {
                                    Slider(
                                        value = recipe.adjustments.brightness,
                                        onValueChange = { viewModel.updateBrightness(it) },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00B4D8))
                                    )
                                }
                            }
                        }
                    }

                    EditorTab.CROP -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { viewModel.setCropPreset(null) }) {
                                Text("Original", color = Color.White, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.setCropPreset(1.0f) }) {
                                Text("1:1", color = Color.White, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.setCropPreset(4f / 3f) }) {
                                Text("4:3", color = Color.White, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.setCropPreset(16f / 9f) }) {
                                Text("16:9", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    EditorTab.ROTATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { viewModel.rotate90() }) {
                                Icon(imageVector = GalleryIcons.Rotate, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(" 90°", color = Color.White, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.toggleFlipHorizontal() }) {
                                Text("⇄ Flip", color = Color.White, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.reset() }) {
                                Icon(imageVector = GalleryIcons.Undo, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                                Text(" Reset", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
