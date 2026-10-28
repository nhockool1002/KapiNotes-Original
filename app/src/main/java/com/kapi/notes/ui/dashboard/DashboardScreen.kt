@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kapi.notes.ui.dashboard

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.NoteSource
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.ui.common.PaperBackground
import com.kapi.notes.ui.common.formatNoteTimestamp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onOpenNote: (String) -> Unit,
    onOpenDrivePicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var isManualUpdateDialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Fires every time this screen becomes visible - including navigating back from the Editor -
    // not just once per process, so switching devices (or just finishing an edit) pulls/pushes
    // automatically instead of requiring the user to remember to tap "Đồng bộ ngay".
    LaunchedEffect(Unit) { viewModel.syncOnResume() }

    val localFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        var displayName = "Tài liệu"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) displayName = cursor.getString(nameIndex)
        }
        viewModel.importLocalDocument(uri, displayName, mimeType) { id -> onOpenNote(id) }
    }

    // Tablet (>=600dp, e.g. Xiaomi Pad Pro): sidebar is inline and pushes the content over.
    // Phone (<600dp): sidebar is an overlay drawer on top of the content, closed by default.
    val isWideScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    var isSidebarOpen by remember(isWideScreen) { mutableStateOf(isWideScreen) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(Modifier.fillMaxSize()) {
            if (isWideScreen && isSidebarOpen) {
                DashboardSidebar(
                    section = state.section,
                    onSectionSelected = viewModel::selectSection,
                    onOpenDrive = onOpenDrivePicker,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    onClose = { isSidebarOpen = false }
                )
            }

            Column(Modifier.fillMaxSize()) {
                DashboardHeader(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    session = state.session,
                    syncStatus = state.syncStatus,
                    versionStatus = state.versionStatus,
                    onVersionBadgeClick = { isManualUpdateDialogOpen = true },
                    onSync = viewModel::syncNow,
                    onAvatarClick = onOpenSettings,
                    onMenuClick = { isSidebarOpen = !isSidebarOpen }
                )

            Column(Modifier.fillMaxSize().padding(if (isWideScreen) 24.dp else 16.dp)) {
                Text(
                    text = when (state.section) {
                        DashboardSection.MY_NOTES -> "Tài liệu gần đây"
                        DashboardSection.FAVORITES -> "Mục yêu thích"
                        DashboardSection.TRASH -> "Thùng rác"
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                if (state.section == DashboardSection.MY_NOTES) {
                    Spacer(Modifier.height(14.dp))
                    // Wraps onto a second line instead of forcing a horizontal-scroll strip -
                    // every action stays visible at once, on both phone and tablet widths.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { localFilePicker.launch(arrayOf("application/pdf", "image/*")) },
                            enabled = !state.isImportingLocalFile,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (state.isImportingLocalFile) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Tải ảnh/PDF từ máy")
                        }
                        OutlinedButton(onClick = onOpenDrivePicker, shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nhập từ Drive")
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tạo ghi chú")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (state.notes.isEmpty()) {
                    EmptyState(section = state.section)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                inTrash = state.section == DashboardSection.TRASH,
                                onClick = { if (state.section != DashboardSection.TRASH) onOpenNote(note.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(note) },
                                onTrash = { viewModel.moveToTrash(note) },
                                onRestore = { viewModel.restoreFromTrash(note) },
                                onDeleteForever = { viewModel.deletePermanently(note) },
                                onDuplicate = { viewModel.duplicateNote(note) }
                            )
                        }
                    }
                }
            }
            }
        }

        // Phone layout: sidebar floats over the content as a drawer with a dismissible scrim,
        // instead of permanently stealing width the way it does on a tablet.
        if (!isWideScreen) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarOpen,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .combinedClickable(onClick = { isSidebarOpen = false })
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarOpen,
                enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
                exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it })
            ) {
                DashboardSidebar(
                    section = state.section,
                    onSectionSelected = { viewModel.selectSection(it); isSidebarOpen = false },
                    onOpenDrive = { isSidebarOpen = false; onOpenDrivePicker() },
                    onOpenSettings = { isSidebarOpen = false; onOpenSettings() },
                    onOpenAbout = { isSidebarOpen = false; onOpenAbout() },
                    onClose = { isSidebarOpen = false }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, paper ->
                showCreateDialog = false
                viewModel.createBlankNote(title, paper) { id -> onOpenNote(id) }
            }
        )
    }

    state.importError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportError,
            title = { Text("Không nhập được tệp") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissImportError) { Text("Đã hiểu") } }
        )
    }

    // App isn't on Play Store yet, so there is no store update prompt - whenever GitHub has a
    // release newer than this build, show a MANDATORY, non-dismissible dialog (no Cancel/skip,
    // back press and tapping outside both do nothing) automatically. The same dialog content is
    // also reachable any time by tapping the version badge in the header - there it is a normal,
    // dismissible dialog instead (useful mainly to check "what's new" while already up to date).
    state.versionStatus?.let { status ->
        if (!status.isUpToDate) {
            UpdateDialog(status = status, mandatory = true, onDismiss = {})
        } else if (isManualUpdateDialogOpen) {
            UpdateDialog(status = status, mandatory = false, onDismiss = { isManualUpdateDialogOpen = false })
        }
    }
}

/** Small colored pill in the header showing the installed version - green when it matches
 * GitHub's latest release, orange when 1-2 releases behind, red when further behind than that.
 * Tapping it re-opens the update dialog even when already up to date, so the user can always
 * check "what's new" voluntarily, not only when forced to update. */
@Composable
private fun VersionBadge(status: com.kapi.notes.update.VersionStatus, onClick: () -> Unit) {
    val color = when {
        status.newerCount <= 0 -> Color(0xFF22C55E)
        status.newerCount <= 2 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "v${status.localVersion}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun UpdateDialog(
    status: com.kapi.notes.update.VersionStatus,
    mandatory: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !mandatory,
            dismissOnClickOutside = !mandatory
        ),
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text(if (status.isUpToDate) "Bạn đang dùng bản mới nhất" else "Đã có bản cập nhật mới") },
        text = {
            Column {
                Text(
                    if (status.isUpToDate) {
                        "Phiên bản ${status.localVersion} là phiên bản mới nhất hiện có trên GitHub."
                    } else {
                        "Phiên bản ${status.latestVersion} đã sẵn sàng trên GitHub." +
                            (if (mandatory) " Vui lòng cập nhật để tiếp tục sử dụng KapiNotes." else "")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                status.latestReleaseNotes?.let { notes ->
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (!status.isUpToDate && status.latestReleaseUrl != null) {
                Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(status.latestReleaseUrl))
                    context.startActivity(intent)
                }) { Text("Tải bản cập nhật") }
            } else {
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    )
}

@Composable
private fun DashboardSidebar(
    section: DashboardSection,
    onSectionSelected: (DashboardSection) -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.width(240.dp).fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = 20.dp, end = if (onClose != null) 4.dp else 20.dp, top = 20.dp, bottom = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.kapi.notes.ui.common.KapiLogo(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp))
                    Text("KapiNotes", style = MaterialTheme.typography.titleMedium)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Đóng")
                    }
                }
            }

            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SidebarItem(
                    icon = Icons.Rounded.Book,
                    label = DashboardSection.MY_NOTES.labelVi,
                    selected = section == DashboardSection.MY_NOTES,
                    onClick = { onSectionSelected(DashboardSection.MY_NOTES) }
                )
                SidebarItem(
                    icon = Icons.Rounded.InsertDriveFile,
                    label = "Google Drive",
                    selected = false,
                    onClick = onOpenDrive,
                    tint = Color(0xFF22C55E)
                )
                SidebarItem(
                    icon = Icons.Rounded.Star,
                    label = DashboardSection.FAVORITES.labelVi,
                    selected = section == DashboardSection.FAVORITES,
                    onClick = { onSectionSelected(DashboardSection.FAVORITES) },
                    tint = Color(0xFFFACC15)
                )
                SidebarItem(
                    icon = Icons.Rounded.Delete,
                    label = DashboardSection.TRASH.labelVi,
                    selected = section == DashboardSection.TRASH,
                    onClick = { onSectionSelected(DashboardSection.TRASH) }
                )
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SidebarItem(icon = Icons.Rounded.Info, label = "Giới thiệu", selected = false, onClick = onOpenAbout)
                SidebarItem(icon = Icons.Rounded.Settings, label = "Cài đặt", selected = false, onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else tint)
        Text(label, color = textColor, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun DashboardHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    session: com.kapi.notes.domain.model.UserSession?,
    syncStatus: com.kapi.notes.domain.model.SyncStatus,
    versionStatus: com.kapi.notes.update.VersionStatus?,
    onVersionBadgeClick: () -> Unit,
    onSync: () -> Unit,
    onAvatarClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Mở menu")
                }
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Tìm kiếm tài liệu...", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 360.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                versionStatus?.let { status ->
                    VersionBadge(status = status, onClick = onVersionBadgeClick)
                }

                if (session != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(
                                    when (syncStatus) {
                                        com.kapi.notes.domain.model.SyncStatus.SYNCING -> "Đang đồng bộ..."
                                        else -> "Đồng bộ ghi chú với Google Drive"
                                    }
                                )
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onSync, enabled = syncStatus != com.kapi.notes.domain.model.SyncStatus.SYNCING) {
                            if (syncStatus == com.kapi.notes.domain.model.SyncStatus.SYNCING) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = "Đồng bộ ngay")
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .combinedClickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (session?.displayName?.firstOrNull() ?: 'U').uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(
    note: Note,
    inTrash: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onDuplicate: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.combinedClickable(onClick = onClick)) {
        Card(
            modifier = Modifier.aspectRatio(3f / 4f),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (note.thumbnailPath != null) {
                    // Real snapshot of the note's first page, regenerated every time the editor
                    // is left - keyed on updatedAt so Coil reloads it instead of showing a stale
                    // cached bitmap from before the edit.
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(note.thumbnailPath)
                            .memoryCacheKey("${note.id}_${note.updatedAt}")
                            .diskCacheKey("${note.id}_${note.updatedAt}")
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (note.source == NoteSource.DRIVE_IMPORT) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFF87171)
                        )
                    }
                } else {
                    PaperBackground(paperType = PaperType.LINED)
                }

                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Tùy chọn", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (inTrash) {
                            DropdownMenuItem(text = { Text("Khôi phục") }, onClick = { menuOpen = false; onRestore() })
                            DropdownMenuItem(text = { Text("Xóa vĩnh viễn") }, onClick = { menuOpen = false; onDeleteForever() })
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (note.isFavorite) "Bỏ yêu thích" else "Yêu thích") },
                                onClick = { menuOpen = false; onToggleFavorite() },
                                leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Nhân đôi ghi chú") },
                                onClick = { menuOpen = false; onDuplicate() },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Chuyển vào thùng rác") },
                                onClick = { menuOpen = false; onTrash() },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            note.title,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            formatNoteTimestamp(note.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(section: DashboardSection) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            when (section) {
                DashboardSection.MY_NOTES -> "Chưa có ghi chú nào. Nhấn \"Tạo ghi chú\" để bắt đầu."
                DashboardSection.FAVORITES -> "Chưa có mục yêu thích."
                DashboardSection.TRASH -> "Thùng rác trống."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val PaperType.labelVi: String
    get() = when (this) {
        PaperType.BLANK -> "Trơn"
        PaperType.LINED -> "Kẻ dòng"
        PaperType.GRID -> "Ô vuông"
        PaperType.DOT -> "Chấm bi"
        PaperType.CORNELL -> "Cornell"
        PaperType.CHECKLIST -> "Việc cần làm"
        PaperType.TIMETABLE -> "TKB"
        PaperType.CHINESE_CHARACTER_GRID -> "Ô chữ Hán"
    }

@Composable
private fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (String, PaperType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var paperType by remember { mutableStateOf(PaperType.LINED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Tạo ghi chú mới", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên ghi chú") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Loại giấy",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                // Fixed 4-column grid: 8 paper types lay out as two perfectly even rows instead
                // of a ragged scrolling strip, so every option is visible and easy to compare.
                PaperType.entries.chunked(4).forEach { rowTypes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTypes.forEach { type ->
                            PaperChoiceSwatch(
                                type = type,
                                selected = paperType == type,
                                onClick = { paperType = type },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, paperType) }, shape = MaterialTheme.shapes.medium) { Text("Tạo") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun PaperChoiceSwatch(type: PaperType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(10.dp)
                )
        ) {
            PaperBackground(paperType = type)
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            type.labelVi,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
