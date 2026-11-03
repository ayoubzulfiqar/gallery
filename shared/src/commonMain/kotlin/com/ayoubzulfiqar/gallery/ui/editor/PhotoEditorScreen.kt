package com.ayoubzulfiqar.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.editor.ColorMatrixHelper
import com.ayoubzulfiqar.gallery.editor.FilterPresets

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

    // Compute combined color matrix (adjustments + preset filter if any)
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
            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Cancel", fontSize = 14.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.DarkGray)
                    ) {
                        Text("↶ Undo", fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.DarkGray)
                    ) {
                        Text("↷ Redo", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8), contentColor = Color.Black)
                ) {
                    Text("Save", fontSize = 14.sp)
                }
            }

            // Real-Time GPU Preview Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val tintHue = (viewModel.mediaItem.id.hashCode() and 0xFF) / 255f
                val crop = recipe.cropRect
                val baseColor = Color.hsv(tintHue * 360f, 0.45f, 0.55f)

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .graphicsLayer {
                            rotationZ = recipe.rotationDegrees
                            scaleX = if (recipe.flipHorizontal) -1f else 1f
                            scaleY = if (recipe.flipVertical) -1f else 1f
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            if (crop != null) 2.dp else 0.dp,
                            Color(0xFF80D4FF),
                            RoundedCornerShape(8.dp)
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
                        fontSize = 18.sp
                    )
                }
            }

            // Tab Selector (Filters, Adjust, Crop, Rotate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EditorTab.values().forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Tab Controls Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (activeTab) {
                    EditorTab.FILTERS -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterPresets.ALL.forEach { preset ->
                                val isSelected = recipe.filterPresetId == preset.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.applyFilter(preset.id) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF00B4D8) else Color(0xFF1E262C))
                                            .border(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) Color.White else Color.Gray,
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) Color(0xFF80D4FF) else Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    EditorTab.ADJUST -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Sub-selector for parameter
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
                                        label = { Text(param, fontSize = 11.sp) }
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
                            TextButton(onClick = { viewModel.setCropPreset(null) }) { Text("Original") }
                            TextButton(onClick = { viewModel.setCropPreset(1.0f) }) { Text("1:1") }
                            TextButton(onClick = { viewModel.setCropPreset(4f / 3f) }) { Text("4:3") }
                            TextButton(onClick = { viewModel.setCropPreset(16f / 9f) }) { Text("16:9") }
                        }
                    }
                    EditorTab.ROTATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { viewModel.rotate90() }) { Text("⟳ Rotate 90°") }
                            TextButton(onClick = { viewModel.toggleFlipHorizontal() }) { Text("⇄ Flip Horizontal") }
                            TextButton(onClick = { viewModel.reset() }) { Text("↺ Reset All") }
                        }
                    }
                }
            }
        }
    }
}
