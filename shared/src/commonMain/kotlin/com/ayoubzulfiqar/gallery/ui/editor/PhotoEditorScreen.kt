package com.ayoubzulfiqar.gallery.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.editor.EditRenderMath
import com.ayoubzulfiqar.gallery.editor.FilterPresets
import com.ayoubzulfiqar.gallery.ui.common.ConfirmDialog
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.common.rememberMediaBitmap
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val EDITOR_PREVIEW_DIMENSION = 2048

@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val recipe = state.recipe
    val bitmap = rememberMediaBitmap(viewModel.mediaItem.uri, EDITOR_PREVIEW_DIMENSION)
    var confirmDiscard by remember { mutableStateOf(false) }

    val requestClose = { if (state.isModified && !state.isSaving) confirmDiscard = true else onClose() }
    GalleryBackHandler(enabled = true) { requestClose() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> {
                    onMessage(event.message)
                    onClose()
                }
                is EditorEvent.Error -> onMessage(event.message)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0F12))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = requestClose, modifier = Modifier.size(44.dp)) {
                    Icon(GalleryIcons.Close, contentDescription = "Close editor", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Row {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo, modifier = Modifier.size(44.dp)) {
                        Icon(GalleryIcons.Undo, contentDescription = "Undo", tint = Color.White.copy(alpha = if (state.canUndo) 1f else 0.25f), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo, modifier = Modifier.size(44.dp)) {
                        Icon(GalleryIcons.Redo, contentDescription = "Redo", tint = Color.White.copy(alpha = if (state.canRedo) 1f else 0.25f), modifier = Modifier.size(20.dp))
                    }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave && state.isModified && !state.isSaving && bitmap != null,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GalleryColors.Accent, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("  Saving", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(GalleryIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Save copy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap == null) {
                    CircularProgressIndicator(color = GalleryColors.Accent)
                } else {
                    EditedImageCanvas(bitmap = bitmap, recipe = recipe, modifier = Modifier.fillMaxSize())
                }
            }

            EditorTabs(state.activeTab, viewModel::setTab)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (state.activeTab) {
                    EditorTab.FILTERS -> FilterStrip(bitmap, recipe, viewModel::applyFilter)
                    EditorTab.ADJUST -> AdjustPanel(state, viewModel)
                    EditorTab.CROP -> Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CropPreset.entries.forEach { preset ->
                            ToolChip(preset.label, selected = state.activeCrop == preset) { viewModel.setCropPreset(preset) }
                        }
                    }
                    EditorTab.ROTATE -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToolButton(GalleryIcons.Rotate, "Rotate") { viewModel.rotate(clockwise = true) }
                        ToolButton(GalleryIcons.FlipHorizontal, "Flip") { viewModel.toggleFlipHorizontal() }
                        ToolButton(GalleryIcons.FlipVertical, "Flip vertical") { viewModel.toggleFlipVertical() }
                        ToolButton(GalleryIcons.Undo, "Reset all", tint = Color(0xFFFF6B6B)) { viewModel.reset() }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard edits?",
            message = "Your changes haven't been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

/** Renders crop → flip → rotation → colour matrix → vignette exactly like the exporter. */
@Composable
fun EditedImageCanvas(bitmap: ImageBitmap, recipe: EditRecipe, modifier: Modifier = Modifier) {
    val colorFilter = remember(recipe.adjustments, recipe.filterPresetId) {
        EditRenderMath.colorMatrix(recipe)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    Canvas(modifier = modifier) { drawEdited(bitmap, recipe, colorFilter) }
}

private fun DrawScope.drawEdited(bitmap: ImageBitmap, recipe: EditRecipe, colorFilter: ColorFilter?) {
    val crop = recipe.cropRect
    val srcLeft = ((crop?.left ?: 0f) * bitmap.width).roundToInt()
    val srcTop = ((crop?.top ?: 0f) * bitmap.height).roundToInt()
    val srcWidth = (((crop?.right ?: 1f) * bitmap.width).roundToInt() - srcLeft).coerceAtLeast(1)
    val srcHeight = (((crop?.bottom ?: 1f) * bitmap.height).roundToInt() - srcTop).coerceAtLeast(1)

    val sideways = EditRenderMath.isSideways(recipe.rotationDegrees)
    val displayWidth = if (sideways) srcHeight else srcWidth
    val displayHeight = if (sideways) srcWidth else srcHeight
    val fit = min(size.width / displayWidth, size.height / displayHeight)
    val center = Offset(size.width / 2f, size.height / 2f)

    translate(center.x, center.y) {
        rotate(recipe.rotationDegrees, pivot = Offset.Zero) {
            scale(
                scaleX = fit * if (recipe.flipHorizontal) -1f else 1f,
                scaleY = fit * if (recipe.flipVertical) -1f else 1f,
                pivot = Offset.Zero
            ) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcWidth, srcHeight),
                    dstOffset = IntOffset(-srcWidth / 2, -srcHeight / 2),
                    dstSize = IntSize(srcWidth, srcHeight),
                    colorFilter = colorFilter
                )
            }
        }
    }

    val edgeAlpha = EditRenderMath.vignetteEdgeAlpha(recipe)
    if (edgeAlpha > 0f) {
        val w = displayWidth * fit
        val h = displayHeight * fit
        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
        drawRect(
            brush = Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = edgeAlpha),
                center = center,
                radius = sqrt(w * w + h * h) / 2f
            ),
            topLeft = topLeft,
            size = Size(w, h)
        )
    }
}

@Composable
private fun EditorTabs(active: EditorTab, onSelect: (EditorTab) -> Unit) {
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
            Triple(EditorTab.FILTERS, "Filters", GalleryIcons.Sparkles),
            Triple(EditorTab.ADJUST, "Adjust", GalleryIcons.Sliders),
            Triple(EditorTab.CROP, "Crop", GalleryIcons.Crop),
            Triple(EditorTab.ROTATE, "Rotate", GalleryIcons.Rotate)
        ).forEach { (tab, label, icon) ->
            val isSelected = active == tab
            val bg by animateColorAsState(if (isSelected) GalleryColors.Accent else Color.Transparent)
            val tint by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(label, color = tint, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun FilterStrip(bitmap: ImageBitmap?, recipe: EditRecipe, onApply: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        (listOf<Pair<String?, String>>(null to "Original") + FilterPresets.ALL.map { it.id to it.name }).forEach { (id, name) ->
            val isSelected = recipe.filterPresetId == id
            val previewFilter = remember(id) {
                id?.let { FilterPresets.getById(it) }?.let { ColorFilter.colorMatrix(ColorMatrix(it.colorMatrix)) }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.RadioButton) { onApply(id) }.padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E262C))
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) GalleryColors.Accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = previewFilter, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(
                    text = name,
                    color = if (isSelected) GalleryColors.Accent else Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AdjustPanel(state: EditorUiState, viewModel: PhotoEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdjustmentParam.entries.forEach { param ->
                ToolChip(param.label, selected = state.activeParam == param) { viewModel.selectParam(param) }
            }
        }
        val param = state.activeParam
        val value = param.read(state.recipe.adjustments)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = { viewModel.previewAdjustment(param, it) },
                onValueChangeFinished = viewModel::commitAdjustment,
                valueRange = param.range,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = GalleryColors.Accent)
            )
            TextButton(onClick = { viewModel.resetAdjustment(param) }, enabled = value != param.neutral) {
                Text("Reset", color = if (value != param.neutral) GalleryColors.Accent else Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GalleryColors.Accent else Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
