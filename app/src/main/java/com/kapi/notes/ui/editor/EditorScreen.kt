@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kapi.notes.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.ui.common.PaperBackground
import com.kapi.notes.ui.theme.PenColorSwatches
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Shared by both pinch-zoom and the quick-zoom slider: lets the user zoom out a bit below
 * 100% (e.g. to see a whole wide page at once) while still capping how far in they can go. */
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f

/** Only tracks pan/zoom once a SECOND finger goes down, leaving every single-finger gesture
 * (drawing, moving objects...) untouched for the DrawingCanvas beneath to handle. */
private fun Modifier.twoFingerPanZoom(onTransform: (Float, Offset) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zooming = false
        var previousDistance = 0f
        var previousCentroid = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val p0 = pressed[0].position
                val p1 = pressed[1].position
                val distance = (p0 - p1).getDistance().coerceAtLeast(1f)
                val centroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                if (zooming) {
                    val zoomChange = distance / previousDistance
                    onTransform(zoomChange, centroid - previousCentroid)
                    pressed.forEach { it.consume() }
                } else {
                    zooming = true
                }
                previousDistance = distance
                previousCentroid = centroid
            } else {
                zooming = false
            }
        } while (event.changes.any { it.pressed })
    }
}

private val CuratedEmojis = listOf(
    "✅", "❌", "⭐", "📌", "💡", "🔥", "📚", "✏️", "❗", "❓", "🎯", "👍",
    "👎", "✨", "🟢", "🔴", "🟡", "🔵", "⚠️", "💯", "😀", "😅", "😢", "❤️",
    "🧠", "📝", "🔑", "🏆", "⏰", "📅", "🔗", "💬", "🎨", "🧪", "🧮", "🌟",
    "✔️", "➡️", "⬅️", "⬆️", "⬇️", "🙏", "👏", "🤔", "😴", "🚀", "🎓", "📎"
)

/** Saves a picked/captured bitmap under `filesDir/pages` (the same directory used for page
 * background images) and returns its max-1000px-wide, aspect-correct on-page size. */
private fun persistPickedImage(context: android.content.Context, bitmap: Bitmap): Triple<String, Float, Float> {
    val dir = File(context.filesDir, "pages").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    val maxWidth = 360f
    val scale = (maxWidth / bitmap.width).coerceAtMost(1f)
    return Triple(file.absolutePath, bitmap.width * scale, bitmap.height * scale)
}

/** Same idea as [persistPickedImage] but for a full-page background (not a small draggable
 * sticker): keeps native resolution up to 2000px wide so annotated photos/worksheets stay sharp
 * when zoomed in, and returns pixel (not dp) dimensions to become the page's own widthPx/heightPx. */
private fun persistPageBackgroundImage(context: android.content.Context, bitmap: Bitmap): Triple<String, Int, Int> {
    val dir = File(context.filesDir, "pages").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    val maxWidth = 2000f
    val scale = (maxWidth / bitmap.width).coerceAtMost(1f)
    val out = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap
    FileOutputStream(file).use { stream -> out.compress(Bitmap.CompressFormat.JPEG, 92, stream) }
    return Triple(file.absolutePath, out.width, out.height)
}

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Hardware/gesture back also needs to refresh the dashboard thumbnail, not just the top
    // bar's arrow button.
    androidx.activity.compose.BackHandler { viewModel.saveThumbnailThenBack(onBack) }

    state.exportedFile?.let { file ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = state.exportedFileMimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Xuất ghi chú"))
        viewModel.consumeExportedFile()
    }

    var pendingImagePoint by remember { mutableStateOf<Offset?>(null) }
    var isImageSourceDialogOpen by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun handlePickedBitmap(bitmap: Bitmap?) {
        val point = pendingImagePoint
        if (bitmap != null && point != null) {
            val (path, w, h) = persistPickedImage(context, bitmap)
            viewModel.placeImage(path, point, w, h)
        }
        pendingImagePoint = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        isImageSourceDialogOpen = false
        if (uri != null) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            handlePickedBitmap(bitmap)
        } else {
            pendingImagePoint = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        isImageSourceDialogOpen = false
        val file = pendingCameraFile
        if (success && file != null) {
            handlePickedBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            pendingImagePoint = null
        }
        pendingCameraFile = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }

    // "Nền trang": pick a local image to annotate (sets the current page's background) or a local
    // PDF to import (rendered one page per PDF page, inserted right after the current page).
    val backgroundImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                val (path, w, h) = persistPageBackgroundImage(context, bitmap)
                viewModel.setPageBackgroundImage(path, w, h)
            }
        }
    }
    val backgroundPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tempFile = File(context.cacheDir, "bg_import_${UUID.randomUUID()}.pdf")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                }
            }.onSuccess { viewModel.importPdfPagesAsBackground(tempFile) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        if (!state.isFullscreen) {
            EditorTopBar(
                title = state.note?.title.orEmpty(),
                onBack = { viewModel.saveThumbnailThenBack(onBack) },
                onRenameConfirmed = viewModel::renameNote,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                isPaperMenuOpen = state.isPaperMenuOpen,
                onPaperMenuToggle = viewModel::togglePaperMenu,
                currentPaperType = state.currentPage?.paperType ?: PaperType.LINED,
                onPaperTypeSelected = viewModel::changePaperType,
                onRotate = viewModel::rotatePage,
                onFlip = viewModel::flipPage,
                currentPageTagColor = state.currentPage?.tagColorArgb,
                onPageTagColorSelected = viewModel::setPageTagColor,
                hasPageBackground = state.currentPage?.backgroundImagePath != null,
                onInsertBackgroundImage = { backgroundImageLauncher.launch("image/*") },
                onInsertBackgroundPdf = { backgroundPdfLauncher.launch("application/pdf") },
                onClearPageBackground = viewModel::clearPageBackground,
                isImportingBackground = state.isImportingBackground,
                isCurrentPageBookmarked = state.currentPage?.isBookmarked ?: false,
                onToggleBookmark = viewModel::toggleCurrentPageBookmark,
                bookmarkedPages = state.pages.mapIndexedNotNull { index, p -> if (p.isBookmarked) index else null },
                onJumpToPage = viewModel::selectPage,
                isSharingLink = state.isSharingLink,
                onShareViewOnlyLink = viewModel::shareNoteViewOnlyLink,
                isRecording = state.isRecording,
                onToggleRecording = {
                    if (state.isRecording) {
                        viewModel.stopRecording()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onFullscreenToggle = viewModel::toggleFullscreen,
                isExporting = state.isExporting,
                onExportPdf = viewModel::exportNote,
                onExportPng = viewModel::exportCurrentPageAsPng
            )

            SecondaryToolbar(
                tool = state.tool,
                onToolSelected = viewModel::selectTool,
                colorArgb = state.colorArgb,
                customColors = state.customColors,
                onColorSelected = viewModel::selectColor,
                onCustomColorAdded = viewModel::addCustomColor,
                strokeWidthDp = state.strokeWidthDp,
                onStrokeWidthChange = viewModel::setStrokeWidth,
                isShapeFillEnabled = state.isShapeFillEnabled,
                onToggleShapeFill = viewModel::toggleShapeFill,
                lassoRectMode = state.lassoRectMode,
                onToggleLassoRectMode = viewModel::toggleLassoRectMode,
                rulerMode = state.rulerMode,
                onToggleRulerMode = viewModel::toggleRulerMode,
                compassMode = state.compassMode,
                onToggleCompassMode = viewModel::toggleCompassMode,
                eraseMode = state.eraseMode,
                onEraseModeChange = viewModel::setEraseMode,
                hasSelection = state.selectedStrokeIds.isNotEmpty(),
                isRecognizingHandwriting = state.isRecognizingHandwriting,
                onRecognizeHandwriting = viewModel::recognizeSelectedHandwriting,
                recognitionLanguage = state.effectiveRecognitionLanguage,
                onRecognitionLanguageChange = viewModel::setRecognitionLanguage
            )

            if (state.audioClips.isNotEmpty()) {
                AudioClipsBar(
                    clips = state.audioClips,
                    playingClipId = state.playingClipId,
                    onTogglePlay = viewModel::togglePlayback,
                    onRemove = viewModel::removeAudioClip
                )
            }
        }

        // Deliberately NOT remember(state.currentPageIndex): Modifier.pointerInput(Unit) inside
        // twoFingerPanZoom only launches its gesture-reading coroutine once and keeps running that
        // same closure for the composable's lifetime, so if `zoom`/`pan` were swapped for a new
        // MutableState object on every page change, the running gesture handler would keep
        // mutating the OLD (now-orphaned) state forever - pinch-zoom would silently stop doing
        // anything the moment the user left page 1 (e.g. right after inserting a new page). Keep
        // one stable state instance for the whole screen and just reset its value on page change.
        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        // The zoom panel starts docked/hidden at the screen edge so it doesn't sit over the page
        // by default - only its small tab handle shows until the user explicitly asks for it.
        var isZoomPanelExpanded by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(state.currentPageIndex) {
            zoom = 1f
            pan = Offset.Zero
        }

        // Outer, unpadded wrapper: the inner Box below keeps the 24dp inset + clip that stops the
        // zoomed page from bleeding into the toolbars, but the floating zoom handle needs to sit
        // flush against the TRUE screen edge, outside that inset - so it lives in this outer Box
        // instead, as a sibling of the (padded/clipped) page box rather than a child of it.
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (state.isFullscreen) 4.dp else 24.dp)
                // Clips the zoomed/panned page content to this box's own bounds, which sit strictly
                // below the top bars and above the bottom dock/page switcher - without it, Compose's
                // graphicsLayer scale draws past its layout bounds and a pinch-zoomed page visibly
                // overlapped the toolbar above it (reported on both phone and tablet sizes).
                .clipToBounds()
                .twoFingerPanZoom(
                    onTransform = { zoomChange, panChange ->
                        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        pan += panChange
                    }
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            val page = state.currentPage
            if (page != null) {
                val widthDp = (page.widthPx / 2).dp
                val heightDp = (page.heightPx / 2).dp

                val backgroundBitmap = remember(page.backgroundImagePath) {
                    page.backgroundImagePath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
                }

                Box(
                    Modifier
                        .width(widthDp)
                        .height(heightDp)
                        .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
                        .background(Color.White)
                ) {
                    if (backgroundBitmap == null) {
                        PaperBackground(paperType = page.paperType)
                    }
                    DrawingCanvas(
                        page = page,
                        strokes = state.strokes,
                        stickers = state.stickers,
                        images = state.images,
                        textBoxes = state.textBoxes,
                        tool = state.tool,
                        colorArgb = state.colorArgb,
                        strokeWidthDp = state.strokeWidthDp,
                        isShapeFillEnabled = state.isShapeFillEnabled,
                        selectedStrokeIds = state.selectedStrokeIds,
                        lassoRectMode = state.lassoRectMode,
                        rulerMode = state.rulerMode,
                        compassMode = state.compassMode,
                        backgroundBitmap = backgroundBitmap,
                        onStrokeFinished = viewModel::commitStroke,
                        onEraseAt = { point -> viewModel.eraseAt(point, radiusDp = 10f) },
                        onEraseFinished = viewModel::finishEraseGesture,
                        onLassoFinished = viewModel::finishLasso,
                        onSelectionMoved = viewModel::moveSelection,
                        onSelectionRotated = viewModel::rotateSelection,
                        onEmptyTapForEmoji = viewModel::requestPlaceEmojiAt,
                        onStickerTapped = viewModel::removeStickerTap,
                        onStickerDragged = viewModel::moveSticker,
                        onStickerTransformed = viewModel::transformSticker,
                        onEmptyTapForImage = { point ->
                            pendingImagePoint = point
                            isImageSourceDialogOpen = true
                        },
                        onImageTapped = viewModel::removeImageTap,
                        onImageDragged = viewModel::moveImage,
                        onImageTransformed = viewModel::transformImage,
                        onEmptyTapForText = viewModel::requestAddTextBoxAt,
                        onTextBoxTapped = viewModel::requestEditTextBox,
                        onTextBoxDragged = viewModel::moveTextBox,
                        onTextBoxTransformed = viewModel::transformTextBox,
                        onLaserPoint = viewModel::setLaserPoint
                    )

                    state.laserPoint?.let { point ->
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color(0xFFEF4444).copy(alpha = 0.35f), radius = 36f, center = point)
                            drawCircle(color = Color(0xFFEF4444), radius = 14f, center = point)
                        }
                    }
                }

                if (state.isFullscreen) {
                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
                        TooltipIconButton(icon = Icons.Rounded.FullscreenExit, tooltip = "Thoát toàn màn hình", onClick = viewModel::toggleFullscreen)
                    }
                }
            }
        }

            // Quick zoom control alongside pinch-to-zoom: bottom-end on both phone and tablet
            // keeps it reachable with a thumb without sitting over the page centre where drawing
            // actually happens. Lives in the outer (unpadded) box so it can sit flush against the
            // real screen edge instead of floating 24dp away from it. One single capsule shape
            // (not two separate floating pieces) that smoothly grows/shrinks in place, with the
            // magnifier toggle always anchored at its bottom - docked/collapsed by default so it
            // doesn't sit over the page the whole time.
            Box(Modifier.fillMaxSize().padding(bottom = 12.dp), contentAlignment = Alignment.BottomEnd) {
                ZoomControl(
                    zoom = zoom,
                    onZoomChange = { newZoom ->
                        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                        if (zoom <= 1f) pan = Offset.Zero
                    },
                    expanded = isZoomPanelExpanded,
                    onToggleExpanded = { isZoomPanelExpanded = !isZoomPanelExpanded }
                )
            }

            // Same reasoning as the zoom control above - lives in the outer unpadded box so it
            // can sit flush against the real screen edge, and this Box's own bounds already stop
            // above the bottom toolbar/page switcher so it can never cover them.
            state.versionStatus?.let { status ->
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                    com.kapi.notes.ui.common.VersionBadge(
                        status = status,
                        onClick = { viewModel.toggleManualUpdateDialog(true) }
                    )
                }
            }
        }

        if (!state.isFullscreen) {
            BottomToolDock(
                tool = state.tool,
                onToolSelected = viewModel::selectTool,
                isShapeMenuOpen = state.isShapeMenuOpen,
                onShapeMenuToggle = viewModel::toggleShapeMenu
            )
            PageSwitcher(
                tagColors = state.pages.map { it.tagColorArgb },
                currentIndex = state.currentPageIndex,
                onPageSelected = viewModel::selectPage,
                onAddPage = viewModel::addPage,
                onDuplicatePage = viewModel::duplicateCurrentPage,
                onDeletePage = viewModel::deleteCurrentPage,
                onMovePage = viewModel::movePageBy
            )
        }
    }

    if (state.isManualUpdateDialogOpen) {
        state.versionStatus?.let { status ->
            com.kapi.notes.ui.common.UpdateDialog(
                status = status,
                mandatory = false,
                onDismiss = { viewModel.toggleManualUpdateDialog(false) }
            )
        }
    }

    if (state.isEmojiPickerOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEmojiPicker,
            title = { Text("Chọn Emoji") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.height(260.dp)) {
                    items(CuratedEmojis) { emoji ->
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { viewModel.placeEmoji(emoji) }) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissEmojiPicker) { Text("Hủy") } }
        )
    }

    if (isImageSourceDialogOpen) {
        AlertDialog(
            onDismissRequest = { isImageSourceDialogOpen = false; pendingImagePoint = null },
            title = { Text("Chèn ảnh") },
            text = { Text("Chọn nguồn ảnh để chèn vào trang.") },
            confirmButton = {
                TextButton(onClick = { galleryLauncher.launch("image/*") }) { Text("Thư viện") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    pendingCameraFile = file
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraLauncher.launch(uri)
                }) { Text("Chụp ảnh") }
            }
        )
    }

    val pendingTextPoint = state.pendingTextBoxPlacement
    val editingTextBox = state.editingTextBox
    if (pendingTextPoint != null || editingTextBox != null) {
        var draft by remember(editingTextBox?.id) { mutableStateOf(editingTextBox?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = viewModel::dismissTextBoxDialog,
            title = { Text(if (editingTextBox != null) "Sửa văn bản" else "Chèn văn bản") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nhập ghi chú...") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StyleToggleChip(label = "B", active = state.textDialogBold, onClick = { viewModel.setTextDialogBold(!state.textDialogBold) })
                        StyleToggleChip(label = "I", active = state.textDialogItalic, onClick = { viewModel.setTextDialogItalic(!state.textDialogItalic) })
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.setTextDialogFontSize((state.textDialogFontSize - 4f).coerceAtLeast(12f)) }) { Text("A-") }
                        Text("${state.textDialogFontSize.toInt()}px", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { viewModel.setTextDialogFontSize((state.textDialogFontSize + 4f).coerceAtMost(96f)) }) { Text("A+") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            com.kapi.notes.domain.model.TextFontFamily.DEFAULT to "Mặc định",
                            com.kapi.notes.domain.model.TextFontFamily.SERIF to "Chữ in",
                            com.kapi.notes.domain.model.TextFontFamily.MONOSPACE to "Đều nét",
                            com.kapi.notes.domain.model.TextFontFamily.HANDWRITING to "Viết tay"
                        ).forEach { (family, label) ->
                            StyleToggleChip(label = label, active = state.textDialogFontFamily == family, onClick = { viewModel.setTextDialogFontFamily(family) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTextBox(draft) }) { Text("Lưu") }
            },
            dismissButton = {
                Row {
                    if (editingTextBox != null) {
                        TextButton(onClick = viewModel::removeEditingTextBox) { Text("Xóa", color = Color(0xFFDC2626)) }
                    }
                    TextButton(onClick = viewModel::dismissTextBoxDialog) { Text("Hủy") }
                }
            }
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Thông báo") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") } }
        )
    }

    state.shareLink?.let { link ->
        AlertDialog(
            onDismissRequest = viewModel::consumeShareLink,
            title = { Text("Link xem-only đã sẵn sàng") },
            text = {
                Column {
                    Text("Bất kỳ ai có link này đều xem được ghi chú (không chỉnh sửa được):", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(link, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2563EB))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Link ghi chú", link))
                    viewModel.consumeShareLink()
                }) { Text("Sao chép") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    }
                    context.startActivity(Intent.createChooser(intent, "Chia sẻ link"))
                    viewModel.consumeShareLink()
                }) { Text("Chia sẻ") }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onRenameConfirmed: (String) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isPaperMenuOpen: Boolean,
    onPaperMenuToggle: (Boolean) -> Unit,
    currentPaperType: PaperType,
    onPaperTypeSelected: (PaperType) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    currentPageTagColor: Long?,
    onPageTagColorSelected: (Long?) -> Unit,
    hasPageBackground: Boolean = false,
    onInsertBackgroundImage: () -> Unit = {},
    onInsertBackgroundPdf: () -> Unit = {},
    onClearPageBackground: () -> Unit = {},
    isImportingBackground: Boolean = false,
    isCurrentPageBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    bookmarkedPages: List<Int> = emptyList(),
    onJumpToPage: (Int) -> Unit = {},
    isSharingLink: Boolean = false,
    onShareViewOnlyLink: () -> Unit = {},
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onFullscreenToggle: () -> Unit,
    isExporting: Boolean,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit
) {
    var editingTitle by remember { mutableStateOf(false) }
    var titleDraft by remember(title) { mutableStateOf(title) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var isBookmarksMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFD1D5DB)))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, tooltip = "Quay lại", onClick = onBack, size = 44.dp)
            if (editingTitle) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    singleLine = true,
                    modifier = Modifier.widthIn(max = 180.dp),
                    textStyle = TextStyle(fontWeight = FontWeight.SemiBold)
                )
                TooltipIconButton(icon = Icons.Rounded.Check, tooltip = "Xác nhận đổi tên", onClick = { editingTitle = false; onRenameConfirmed(titleDraft) })
            } else {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 84.dp).padding(horizontal = 8.dp)
                )
                TooltipIconButton(icon = Icons.Rounded.Edit, tooltip = "Đổi tên ghi chú", onClick = { editingTitle = true }, size = 44.dp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipIconButton(icon = Icons.AutoMirrored.Rounded.Undo, tooltip = "Hoàn tác", onClick = onUndo, enabled = canUndo, size = 44.dp)
            TooltipIconButton(icon = Icons.AutoMirrored.Rounded.Redo, tooltip = "Làm lại", onClick = onRedo, enabled = canRedo, size = 44.dp)

            TooltipIconButton(
                icon = if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                tooltip = if (isCurrentPageBookmarked) "Bỏ đánh dấu trang" else "Đánh dấu trang (để nhảy nhanh)",
                onClick = onToggleBookmark,
                tint = if (isCurrentPageBookmarked) Color(0xFFF59E0B) else Color(0xFF334155),
                size = 44.dp
            )

            Box {
                TooltipIconButton(icon = Icons.Rounded.Bookmarks, tooltip = "Trang đã đánh dấu", onClick = { isBookmarksMenuOpen = true }, enabled = bookmarkedPages.isNotEmpty(), size = 44.dp)
                DropdownMenu(expanded = isBookmarksMenuOpen, onDismissRequest = { isBookmarksMenuOpen = false }) {
                    if (bookmarkedPages.isEmpty()) {
                        DropdownMenuItem(text = { Text("Chưa có trang nào được đánh dấu") }, onClick = {}, enabled = false)
                    } else {
                        bookmarkedPages.forEach { pageIndex ->
                            DropdownMenuItem(
                                text = { Text("Trang ${pageIndex + 1}") },
                                leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                onClick = { isBookmarksMenuOpen = false; onJumpToPage(pageIndex) }
                            )
                        }
                    }
                }
            }

            Box {
                TooltipIconButton(icon = Icons.Rounded.MoreVert, tooltip = "Thêm", onClick = { isMoreMenuOpen = true }, size = 44.dp)
                if (isRecording) {
                    Box(
                        Modifier
                            .padding(top = 6.dp, end = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .align(Alignment.TopEnd)
                    )
                }
                DropdownMenu(expanded = isMoreMenuOpen, onDismissRequest = { isMoreMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isRecording) "Dừng ghi âm" else "Ghi âm bài giảng") },
                        leadingIcon = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = if (isRecording) Color(0xFFDC2626) else LocalContentColor.current) },
                        onClick = { isMoreMenuOpen = false; onToggleRecording() }
                    )
                    DropdownMenuItem(
                        text = { Text("Toàn màn hình") },
                        leadingIcon = { Icon(Icons.Rounded.Fullscreen, contentDescription = null) },
                        onClick = { isMoreMenuOpen = false; onFullscreenToggle() }
                    )
                    DropdownMenuItem(
                        text = { Text("Xuất ghi chú ra PDF") },
                        leadingIcon = {
                            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Share, contentDescription = null)
                        },
                        enabled = !isExporting,
                        onClick = { isMoreMenuOpen = false; onExportPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text("Xuất ảnh PNG (trang hiện tại)") },
                        leadingIcon = {
                            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Image, contentDescription = null)
                        },
                        enabled = !isExporting,
                        onClick = { isMoreMenuOpen = false; onExportPng() }
                    )
                    DropdownMenuItem(
                        text = { Text("Chia sẻ link xem-only (Drive)") },
                        leadingIcon = {
                            if (isSharingLink) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Link, contentDescription = null)
                        },
                        enabled = !isSharingLink,
                        onClick = { isMoreMenuOpen = false; onShareViewOnlyLink() }
                    )
                }
            }

            Box {
                TooltipIconButton(icon = Icons.Rounded.Description, tooltip = "Cài đặt giấy: loại giấy, xoay, lật", onClick = { onPaperMenuToggle(true) }, size = 44.dp)
                DropdownMenu(expanded = isPaperMenuOpen, onDismissRequest = { onPaperMenuToggle(false) }) {
                    Column(Modifier.padding(12.dp).width(260.dp)) {
                        Text("Loại giấy", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PaperType.BLANK, PaperType.LINED, PaperType.GRID, PaperType.DOT).forEach { type ->
                                PaperSwatchButton(type = type, selected = type == currentPaperType, onClick = { onPaperTypeSelected(type); onPaperMenuToggle(false) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Mẫu giấy", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PaperType.CORNELL, PaperType.CHECKLIST, PaperType.TIMETABLE, PaperType.CHINESE_CHARACTER_GRID).forEach { type ->
                                PaperSwatchButton(type = type, selected = type == currentPaperType, onClick = { onPaperTypeSelected(type); onPaperMenuToggle(false) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Định dạng", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRotate(); onPaperMenuToggle(false) }) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Xoay")
                            }
                            OutlinedButton(onClick = { onFlip(); onPaperMenuToggle(false) }) {
                                Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Lật")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Nhãn màu trang", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                                    .clickable { onPageTagColorSelected(null) },
                                contentAlignment = Alignment.Center
                            ) { if (currentPageTagColor == null) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            listOf(0xFFEF4444L, 0xFFF59E0BL, 0xFF22C55EL, 0xFF3B82F6L, 0xFFA855F7L).forEach { c ->
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF000000 or c))
                                        .border(if (currentPageTagColor == (0xFF000000L or c)) 2.dp else 0.dp, Color.Black, CircleShape)
                                        .clickable { onPageTagColorSelected(0xFF000000L or c) }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Nền trang (chèn ảnh/PDF để chú thích đè lên)", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onInsertBackgroundImage(); onPaperMenuToggle(false) }, enabled = !isImportingBackground) {
                                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ảnh nền")
                            }
                            OutlinedButton(onClick = { onInsertBackgroundPdf(); onPaperMenuToggle(false) }, enabled = !isImportingBackground) {
                                if (isImportingBackground) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF làm nền")
                            }
                        }
                        if (hasPageBackground) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onClearPageBackground(); onPaperMenuToggle(false) }) {
                                Text("Xóa nền trang này", color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomToolDock(
    tool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    isShapeMenuOpen: Boolean,
    onShapeMenuToggle: (Boolean) -> Unit
) {
    var isInsertMenuOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    val insertTools = setOf(EditorTool.IMAGE, EditorTool.TEXT, EditorTool.EMOJI)

    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE5E7EB))),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tapping "Bút" always starts (or returns to) the ink pen directly - no dropdown. To
        // switch pen type (pencil/brush/highlighter) once drawing, use the picker that appears
        // in the secondary toolbar above the page whenever an ink tool is active.
        DockToolButton(icon = inkToolIcon(tool), label = "Bút", selected = tool in InkTools, onClick = { onToolSelected(EditorTool.PEN) })

        DockToolButton(icon = Icons.Rounded.Backspace, label = "Tẩy", selected = tool == EditorTool.ERASER, onClick = { onToolSelected(EditorTool.ERASER) })

        Box {
            DockToolButton(icon = shapeToolIcon(tool), label = "Hình", selected = tool in ShapeTools, onClick = { onShapeMenuToggle(true) })
            DropdownMenu(expanded = isShapeMenuOpen, onDismissRequest = { onShapeMenuToggle(false) }) {
                DropdownMenuItem(
                    text = { Text("Đường thẳng") },
                    leadingIcon = { Icon(Icons.Rounded.Remove, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.LINE); onShapeMenuToggle(false) }
                )
                DropdownMenuItem(
                    text = { Text("Hình chữ nhật") },
                    leadingIcon = { Icon(Icons.Rounded.CropSquare, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.RECTANGLE); onShapeMenuToggle(false) }
                )
                DropdownMenuItem(
                    text = { Text("Hình tròn") },
                    leadingIcon = { Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.CIRCLE); onShapeMenuToggle(false) }
                )
                DropdownMenuItem(
                    text = { Text("Mũi tên") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.ARROW); onShapeMenuToggle(false) }
                )
                DropdownMenuItem(
                    text = { Text("Tam giác") },
                    leadingIcon = { Icon(Icons.Rounded.ChangeHistory, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.TRIANGLE); onShapeMenuToggle(false) }
                )
            }
        }

        DockToolButton(icon = Icons.Rounded.CropFree, label = "Chọn", selected = tool == EditorTool.LASSO, onClick = { onToolSelected(EditorTool.LASSO) })

        Box {
            DockToolButton(
                icon = when (tool) {
                    EditorTool.IMAGE -> Icons.Rounded.Image
                    EditorTool.TEXT -> Icons.Rounded.TextFields
                    EditorTool.EMOJI -> Icons.Rounded.EmojiEmotions
                    else -> Icons.Rounded.Add
                },
                label = "Chèn",
                selected = tool in insertTools,
                onClick = { isInsertMenuOpen = true }
            )
            DropdownMenu(expanded = isInsertMenuOpen, onDismissRequest = { isInsertMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ảnh") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.IMAGE); isInsertMenuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Văn bản") },
                    leadingIcon = { Icon(Icons.Rounded.TextFields, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.TEXT); isInsertMenuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Emoji") },
                    leadingIcon = { Icon(Icons.Rounded.EmojiEmotions, contentDescription = null) },
                    onClick = { onToolSelected(EditorTool.EMOJI); isInsertMenuOpen = false }
                )
            }
        }

        Box {
            DockToolButton(icon = Icons.Rounded.MoreHoriz, label = "Thêm", selected = tool == EditorTool.LASER, onClick = { isMoreMenuOpen = true })
            DropdownMenu(expanded = isMoreMenuOpen, onDismissRequest = { isMoreMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (tool == EditorTool.LASER) "Tắt con trỏ laser" else "Con trỏ laser (trình chiếu)") },
                    leadingIcon = { Icon(Icons.Rounded.Adjust, contentDescription = null, tint = Color(0xFFEF4444)) },
                    onClick = {
                        onToolSelected(if (tool == EditorTool.LASER) EditorTool.PEN else EditorTool.LASER)
                        isMoreMenuOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DockToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFEFF6FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color(0xFF2563EB) else Color(0xFF475569),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(0xFF2563EB) else Color(0xFF475569),
            maxLines = 1
        )
    }
}

private fun shapeToolIcon(tool: EditorTool): ImageVector = when (tool) {
    EditorTool.RECTANGLE -> Icons.Rounded.CropSquare
    EditorTool.CIRCLE -> Icons.Rounded.RadioButtonUnchecked
    EditorTool.ARROW -> Icons.AutoMirrored.Rounded.ArrowForward
    EditorTool.TRIANGLE -> Icons.Rounded.ChangeHistory
    else -> Icons.Rounded.Remove
}

private fun inkToolIcon(tool: EditorTool): ImageVector = when (tool) {
    // Create/Edit (pen/pencil) render as near-identical pencil glyphs in Material - Draw's
    // dip-pen-and-ink-line shape is visually distinct from Edit's classic pencil at a glance.
    EditorTool.PEN -> Icons.Rounded.Draw
    EditorTool.PENCIL -> Icons.Rounded.Edit
    EditorTool.BRUSH -> Icons.Rounded.Brush
    EditorTool.HIGHLIGHTER -> Icons.Rounded.BorderColor
    else -> Icons.Rounded.Draw
}

private fun inkToolLabel(tool: EditorTool): String = when (tool) {
    EditorTool.PEN -> "Bút mực"
    EditorTool.PENCIL -> "Bút chì"
    EditorTool.BRUSH -> "Cọ lông"
    EditorTool.HIGHLIGHTER -> "Bút dạ quang"
    else -> "Bút mực"
}

/** Floating quick-zoom control shown over the page (bottom-end) as an alternative to two-finger
 * pinch - a vertical slider plus +/- buttons so a one-handed drag zooms without needing a second
 * finger, and a tap on the percentage label resets back to 100%. A SINGLE capsule the whole time
 * (never two separate floating pieces with a gap between them): the magnifier toggle is always
 * anchored at its bottom, and the zoom controls above it grow/shrink in place with
 * [Modifier.animateContentSize], so collapsing it leaves just a small circle-ish pill docked at
 * the edge and expanding it smoothly grows the very same shape upward - one continuous shadow,
 * one continuous outline, not two blobs side by side. */
@Composable
private fun ZoomControl(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier
                .animateContentSize(animationSpec = androidx.compose.animation.core.tween(220))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 80)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onZoomChange(zoom + 0.25f) },
                        enabled = zoom < MAX_ZOOM,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Phóng to", modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    // Rotating a real Material3 Slider (via Modifier.rotate(), and even via the
                    // "correct" layout{}-swap technique for vertical sliders) turned out unreliable
                    // in practice here: taps jumped to the right value, but continuous dragging did
                    // not track the finger at all. Drawing the track/thumb by hand and reading drag
                    // position directly off the Y axis sidesteps rotation entirely, so there is no
                    // transform for hit-testing to get wrong - it is just "where is the finger" the
                    // whole way through the gesture.
                    VerticalZoomSlider(
                        zoom = zoom,
                        onZoomChange = onZoomChange,
                        modifier = Modifier.width(30.dp).height(84.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    IconButton(
                        onClick = { onZoomChange(zoom - 0.25f) },
                        enabled = zoom > MIN_ZOOM,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Thu nhỏ", modifier = Modifier.size(15.dp))
                    }
                    Text(
                        "${(zoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 6.dp)
                            .clickable { onZoomChange(1f) }
                    )
                    androidx.compose.material3.Divider(
                        modifier = Modifier.width(24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            IconButton(onClick = onToggleExpanded, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = if (expanded) "Thu gọn thanh zoom" else "Mở thanh zoom",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A vertical slider drawn by hand (track + thumb on a Canvas) instead of a rotated Material3
 * Slider - top of [modifier]'s bounds is [MAX_ZOOM], bottom is [MIN_ZOOM], matching the ＋/－
 * buttons placed above/below it. Both an initial tap and every subsequent drag position map
 * straight to a Y fraction of the track, so there is no gesture-recognition ambiguity. */
@Composable
private fun VerticalZoomSlider(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outline
    val activeColor = MaterialTheme.colorScheme.primary
    var heightPx by remember { mutableStateOf(1f) }

    fun updateFromY(y: Float) {
        val fraction = (1f - (y / heightPx)).coerceIn(0f, 1f)
        onZoomChange(MIN_ZOOM + fraction * (MAX_ZOOM - MIN_ZOOM))
    }

    Canvas(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            // detectDragGestures + detectTapGestures as two separate pointerInput blocks on the
            // same node turned out to interfere with each other once the value reached an edge
            // (MIN/MAX) - dragging back off the edge stopped registering, even though a plain tap
            // and the +/- buttons still worked. A single hand-rolled gesture loop (down, then every
            // move until up, all in one place) removes the ambiguity between two competing
            // detectors entirely.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromY(down.position.y)
                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        change.consume()
                        updateFromY(change.position.y)
                        pointerId = change.id
                    }
                }
            }
    ) {
        val trackX = size.width / 2f
        drawLine(
            color = trackColor,
            start = Offset(trackX, 0f),
            end = Offset(trackX, size.height),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        val fraction = ((zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)).coerceIn(0f, 1f)
        val thumbY = size.height * (1f - fraction)
        drawLine(
            color = activeColor,
            start = Offset(trackX, thumbY),
            end = Offset(trackX, size.height),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = activeColor, radius = 9.dp.toPx(), center = Offset(trackX, thumbY))
        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(trackX, thumbY))
    }
}

@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color(0xFF334155),
    size: androidx.compose.ui.unit.Dp? = null
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (size != null) Modifier.size(size) else Modifier
        ) {
            Icon(icon, contentDescription = tooltip, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        }
    }
}

/** A small, label-less icon toggle for inline rows (the secondary toolbar's pen-type picker) -
 * distinct from the labeled [DockToolButton] used in the bottom dock. */
@Composable
private fun InlineToolIconButton(icon: ImageVector, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState()
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color(0xFFEFF6FF) else Color.Transparent)
                .then(if (selected) Modifier.border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick) {
                Icon(icon, contentDescription = null, tint = if (selected) Color(0xFF2563EB) else Color(0xFF475569), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PaperSwatchButton(type: PaperType, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF3B82F6) else Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
            .background(Color.White)
    ) {
        PaperBackground(paperType = type, modifier = Modifier.clip(RoundedCornerShape(6.dp)))
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {}
    }
}

@Composable
private fun SecondaryToolbar(
    tool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    colorArgb: Long,
    customColors: List<Long>,
    onColorSelected: (Long) -> Unit,
    onCustomColorAdded: (Long) -> Unit,
    strokeWidthDp: Float,
    onStrokeWidthChange: (Float) -> Unit,
    isShapeFillEnabled: Boolean,
    onToggleShapeFill: () -> Unit,
    lassoRectMode: Boolean,
    onToggleLassoRectMode: () -> Unit,
    rulerMode: Boolean = false,
    onToggleRulerMode: () -> Unit = {},
    compassMode: Boolean = false,
    onToggleCompassMode: () -> Unit = {},
    eraseMode: EraseMode,
    onEraseModeChange: (EraseMode) -> Unit,
    hasSelection: Boolean = false,
    isRecognizingHandwriting: Boolean = false,
    onRecognizeHandwriting: () -> Unit = {},
    recognitionLanguage: com.kapi.notes.ocr.RecognitionLanguage = com.kapi.notes.ocr.RecognitionLanguage.ENGLISH,
    onRecognitionLanguageChange: (com.kapi.notes.ocr.RecognitionLanguage) -> Unit = {}
) {
    val fillableShapes = setOf(EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.TRIANGLE)
    val showInkOptions = tool in InkTools || tool in ShapeTools
    val showLassoOptions = tool == EditorTool.LASSO
    val showEraserOptions = tool == EditorTool.ERASER
    if (!showInkOptions && !showLassoOptions && !showEraserOptions) return

    var isCustomColorDialogOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showEraserOptions) {
            Text("Chế độ tẩy:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            LassoModeChip(label = "Xóa theo nét", icon = Icons.Rounded.Layers, selected = eraseMode == EraseMode.STROKE, onClick = { onEraseModeChange(EraseMode.STROKE) })
            Spacer(Modifier.width(6.dp))
            LassoModeChip(label = "Xóa phẳng", icon = Icons.Rounded.Remove, selected = eraseMode == EraseMode.FLAT, onClick = { onEraseModeChange(EraseMode.FLAT) })
        }

        if (tool in InkTools) {
            InkTools.forEach { inkTool ->
                InlineToolIconButton(icon = inkToolIcon(inkTool), selected = tool == inkTool, contentDescription = inkToolLabel(inkTool), onClick = { onToolSelected(inkTool) })
            }
            Spacer(Modifier.width(10.dp))
            // "Thước"/"Compa": constrain the next stroke to a perfectly straight line or a true
            // center+radius circle, while keeping the currently selected pen's own look (unlike
            // just switching to the generic Line/Circle shape tools).
            LassoModeChip(label = "Thước", icon = Icons.Rounded.Remove, selected = rulerMode, onClick = onToggleRulerMode)
            Spacer(Modifier.width(6.dp))
            LassoModeChip(label = "Compa", icon = Icons.Rounded.RadioButtonUnchecked, selected = compassMode, onClick = onToggleCompassMode)
            Spacer(Modifier.width(10.dp))
        }

        if (showInkOptions) {
            (PenColorSwatches + customColors).distinct().forEach { swatch ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(if (colorArgb == swatch) 2.dp else 0.dp, Color(0xFF3B82F6), CircleShape)
                ) {
                    IconButton(onClick = { onColorSelected(swatch) }, modifier = Modifier.fillMaxSize()) {}
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF94A3B8), CircleShape)
                    .clickable { isCustomColorDialogOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Thêm màu tùy chỉnh", modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(140.dp)) {
                Slider(value = strokeWidthDp, onValueChange = onStrokeWidthChange, valueRange = 1f..20f)
            }
            if (tool in fillableShapes) {
                Spacer(Modifier.width(12.dp))
                LassoModeChip(label = "Tô màu", icon = Icons.Rounded.FormatColorFill, selected = isShapeFillEnabled, onClick = onToggleShapeFill)
            }
        }

        if (showLassoOptions) {
            Text("Kiểu chọn vùng:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            LassoModeChip(label = "Tự do", icon = Icons.Rounded.Gesture, selected = !lassoRectMode, onClick = { if (lassoRectMode) onToggleLassoRectMode() })
            Spacer(Modifier.width(6.dp))
            LassoModeChip(label = "Hình chữ nhật", icon = Icons.Rounded.CropSquare, selected = lassoRectMode, onClick = { if (!lassoRectMode) onToggleLassoRectMode() })
            if (hasSelection) {
                Spacer(Modifier.width(12.dp))
                com.kapi.notes.ocr.RecognitionLanguage.entries.forEach { lang ->
                    LassoModeChip(
                        label = lang.labelVi,
                        icon = Icons.Rounded.Translate,
                        selected = recognitionLanguage == lang,
                        onClick = { onRecognitionLanguageChange(lang) }
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (isRecognizingHandwriting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    LassoModeChip(label = "Nhận dạng chữ", icon = Icons.Rounded.AutoAwesome, selected = false, onClick = onRecognizeHandwriting)
                }
            }
        }
    }

    if (isCustomColorDialogOpen) {
        var hex by remember { mutableStateOf("FF0000") }
        AlertDialog(
            onDismissRequest = { isCustomColorDialogOpen = false },
            title = { Text("Màu tùy chỉnh") },
            text = {
                Column {
                    Text("Nhập mã màu (RRGGBB)", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = hex, onValueChange = { hex = it.take(6) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = hex.toLongOrNull(16)
                    if (parsed != null) onCustomColorAdded(0xFF000000L or parsed)
                    isCustomColorDialogOpen = false
                }) { Text("Thêm") }
            },
            dismissButton = { TextButton(onClick = { isCustomColorDialogOpen = false }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun LassoModeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
            .border(1.dp, if (selected) Color(0xFF3B82F6) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selected) Color(0xFF3B82F6) else Color(0xFF475569))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color(0xFF3B82F6) else Color(0xFF475569))
    }
}

@Composable
private fun StyleToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF3B82F6) else Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else Color(0xFF475569))
    }
}

@Composable
private fun AudioClipsBar(
    clips: List<com.kapi.notes.domain.model.AudioClip>,
    playingClipId: String?,
    onTogglePlay: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFFF8FAFC))
            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lazyRowItems(clips) { clip ->
                val isPlaying = playingClipId == clip.id
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isPlaying) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onTogglePlay(clip.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text("${clip.durationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { onRemove(clip.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Xóa", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSwitcher(
    tagColors: List<Long?>,
    currentIndex: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onDeletePage: () -> Unit,
    onMovePage: (Int) -> Unit
) {
    val pageCount = tagColors.size
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, tooltip = "Chuyển trang này lên trước", onClick = { onMovePage(-1) }, enabled = currentIndex > 0)
            TooltipIconButton(icon = Icons.AutoMirrored.Rounded.ArrowForward, tooltip = "Chuyển trang này ra sau", onClick = { onMovePage(1) }, enabled = currentIndex < pageCount - 1)
        }

        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until pageCount) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (i == currentIndex) Color(0xFF3B82F6) else Color(0xFFE2E8F0))
                    ) {
                        IconButton(onClick = { onPageSelected(i) }, modifier = Modifier.fillMaxSize()) {
                            Text("${i + 1}", color = if (i == currentIndex) Color.White else Color.Black)
                        }
                    }
                    tagColors.getOrNull(i)?.let { tag ->
                        Box(Modifier.padding(top = 2.dp).size(6.dp).clip(CircleShape).background(Color(tag)))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(icon = Icons.Rounded.Add, tooltip = "Thêm trang mới", onClick = onAddPage)
            TooltipIconButton(icon = Icons.Rounded.ContentCopy, tooltip = "Nhân đôi trang này", onClick = onDuplicatePage)
            TooltipIconButton(icon = Icons.Rounded.Delete, tooltip = "Xóa trang này", onClick = onDeletePage, enabled = pageCount > 1)
        }
    }
}
