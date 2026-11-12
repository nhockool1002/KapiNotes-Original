@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kapi.notes.ui.dashboard

import com.kapi.notes.ui.icons.KapiIcons

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.ui.res.stringResource
import com.kapi.notes.R
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
import com.kapi.notes.ui.common.UpdateDialog
import com.kapi.notes.ui.common.VersionBadge
import com.kapi.notes.ui.common.formatNoteTimestamp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    quickNoteRequested: Boolean = false,
    onQuickNoteConsumed: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var isManualUpdateDialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Launched from the home-screen widget: skip the Dashboard and its "Tạo ghi chú" dialog
    // entirely and land straight in a brand-new blank note. onQuickNoteConsumed clears the
    // one-shot flag immediately so recomposition (or coming back to Dashboard later) can never
    // fire this a second time.
    LaunchedEffect(quickNoteRequested) {
        if (quickNoteRequested) {
            onQuickNoteConsumed()
            viewModel.createBlankNote("", com.kapi.notes.domain.model.PaperType.LINED) { id -> onOpenNote(id) }
        }
    }

    // Fires every time this screen becomes visible - including navigating back from the Editor -
    // not just once per process, so switching devices (or just finishing an edit) pulls/pushes
    // automatically instead of requiring the user to remember to tap "Đồng bộ ngay".
    LaunchedEffect(Unit) { viewModel.syncOnResume() }

    val localFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        var displayName = context.getString(R.string.dashboard_default_doc_name)
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
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    onClose = { isSidebarOpen = false }
                )
            }

            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                DashboardHeader(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    session = state.session,
                    syncStatus = state.syncStatus,
                    onSync = viewModel::syncNow,
                    onAvatarClick = onOpenSettings,
                    onMenuClick = { isSidebarOpen = !isSidebarOpen }
                )

            Column(Modifier.fillMaxSize().padding(if (isWideScreen) 24.dp else 16.dp)) {
                Text(
                    text = when (state.section) {
                        DashboardSection.MY_NOTES -> stringResource(R.string.dashboard_heading_my_notes)
                        DashboardSection.FAVORITES -> stringResource(DashboardSection.FAVORITES.labelRes)
                        DashboardSection.TRASH -> stringResource(DashboardSection.TRASH.labelRes)
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
                                Icon(KapiIcons.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dashboard_upload_local_file))
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(KapiIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dashboard_default_note_title))
                        }
                    }
                }

                // Only shown once at least one note in this section actually has a label - tapping
                // a chip filters the grid to just that label, tapping it again clears the filter.
                if (state.availableLabels.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableLabels.forEach { label ->
                            LabelFilterChip(
                                label = label,
                                selected = state.selectedLabel == label.text,
                                onClick = { viewModel.toggleLabelFilter(label.text) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (state.notes.isEmpty()) {
                    EmptyState(section = state.section)
                } else {
                    var labelDialogNote by remember { mutableStateOf<Note?>(null) }
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
                                onDuplicate = { viewModel.duplicateNote(note) },
                                onEditLabel = { labelDialogNote = note }
                            )
                        }
                    }
                    labelDialogNote?.let { note ->
                        LabelDialog(
                            note = note,
                            existingLabels = state.availableLabels,
                            onDismiss = { labelDialogNote = null },
                            onSave = { text, color ->
                                viewModel.setNoteLabel(note, text, color)
                                labelDialogNote = null
                            }
                        )
                    }
                }
            }
            }

            // Floats over the content pane only (not the sidebar) so it never gets clipped by a
            // narrow screen's layout and never sits under the sidebar's own items on tablet.
            state.versionStatus?.let { status ->
                VersionBadge(
                    status = status,
                    onClick = { isManualUpdateDialogOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
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
            title = { Text(stringResource(R.string.dashboard_import_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissImportError) { Text(stringResource(R.string.action_got_it)) } }
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


@Composable
private fun DashboardSidebar(
    section: DashboardSection,
    onSectionSelected: (DashboardSection) -> Unit,
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
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(KapiIcons.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }

            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SidebarItem(
                    icon = KapiIcons.Book,
                    label = stringResource(DashboardSection.MY_NOTES.labelRes),
                    selected = section == DashboardSection.MY_NOTES,
                    onClick = { onSectionSelected(DashboardSection.MY_NOTES) }
                )
                SidebarItem(
                    icon = KapiIcons.Star,
                    label = stringResource(DashboardSection.FAVORITES.labelRes),
                    selected = section == DashboardSection.FAVORITES,
                    onClick = { onSectionSelected(DashboardSection.FAVORITES) },
                    tint = Color(0xFFFACC15)
                )
                SidebarItem(
                    icon = KapiIcons.Delete,
                    label = stringResource(DashboardSection.TRASH.labelRes),
                    selected = section == DashboardSection.TRASH,
                    onClick = { onSectionSelected(DashboardSection.TRASH) }
                )
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SidebarItem(icon = KapiIcons.Info, label = stringResource(R.string.dashboard_about), selected = false, onClick = onOpenAbout)
                SidebarItem(icon = KapiIcons.Settings, label = stringResource(R.string.dashboard_sidebar_settings), selected = false, onClick = onOpenSettings)
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
                    Icon(KapiIcons.Menu, contentDescription = stringResource(R.string.dashboard_open_menu))
                }
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.dashboard_search_placeholder), maxLines = 1) },
                    leadingIcon = { Icon(KapiIcons.Search, contentDescription = null) },
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
                if (session != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(
                                    when (syncStatus) {
                                        com.kapi.notes.domain.model.SyncStatus.SYNCING -> stringResource(R.string.dashboard_syncing)
                                        else -> stringResource(R.string.dashboard_sync_tooltip)
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
                                Icon(KapiIcons.Sync, contentDescription = stringResource(R.string.dashboard_sync_now))
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
    onDuplicate: () -> Unit,
    onEditLabel: () -> Unit = {}
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
                            KapiIcons.PictureAsPdf,
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
                        Icon(KapiIcons.MoreVert, contentDescription = stringResource(R.string.dashboard_note_options), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (inTrash) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_restore)) }, onClick = { menuOpen = false; onRestore() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_delete_forever)) }, onClick = { menuOpen = false; onDeleteForever() })
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (note.isFavorite) stringResource(R.string.dashboard_unfavorite) else stringResource(R.string.dashboard_favorite)) },
                                onClick = { menuOpen = false; onToggleFavorite() },
                                leadingIcon = { Icon(KapiIcons.Star, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_duplicate_note)) },
                                onClick = { menuOpen = false; onDuplicate() },
                                leadingIcon = { Icon(KapiIcons.ContentCopy, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.labelText != null) stringResource(R.string.dashboard_edit_label) else stringResource(R.string.dashboard_add_label)) },
                                onClick = { menuOpen = false; onEditLabel() },
                                leadingIcon = { Icon(KapiIcons.Label, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_move_to_trash)) },
                                onClick = { menuOpen = false; onTrash() },
                                leadingIcon = { Icon(KapiIcons.Delete, contentDescription = null) }
                            )
                        }
                    }
                }

                note.labelText?.let { labelText ->
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(note.labelColorArgb ?: 0xFF64748BL))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            labelText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
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
                DashboardSection.MY_NOTES -> stringResource(R.string.dashboard_empty_my_notes)
                DashboardSection.FAVORITES -> stringResource(R.string.dashboard_empty_favorites)
                DashboardSection.TRASH -> stringResource(R.string.dashboard_empty_trash)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaperType.labelVi(): String = when (this) {
        PaperType.BLANK -> stringResource(R.string.paper_type_blank)
        PaperType.LINED -> stringResource(R.string.paper_type_lined)
        PaperType.GRID -> stringResource(R.string.paper_type_grid)
        PaperType.DOT -> stringResource(R.string.paper_type_dot)
        PaperType.CORNELL -> stringResource(R.string.paper_type_cornell)
        PaperType.CHECKLIST -> stringResource(R.string.paper_type_checklist)
        PaperType.TIMETABLE -> stringResource(R.string.paper_type_timetable)
        PaperType.CHINESE_CHARACTER_GRID -> stringResource(R.string.paper_type_chinese_grid)
        PaperType.GENKOU_YOUSHI -> stringResource(R.string.paper_type_genkou_youshi)
        PaperType.MUSIC_STAFF -> stringResource(R.string.paper_type_music_staff)
        PaperType.TABLE -> stringResource(R.string.paper_type_table)
        PaperType.ISOMETRIC_DOT -> stringResource(R.string.paper_type_isometric_dot)
        PaperType.HANDWRITING_GUIDE -> stringResource(R.string.paper_type_handwriting_guide)
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
        title = { Text(stringResource(R.string.dashboard_create_note_dialog_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dashboard_note_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.dashboard_paper_type_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                // Fixed 4-column grid, scrollable now that there are more than 2 rows' worth of
                // paper types - every option stays reachable instead of overflowing the dialog.
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
                        // Pad an incomplete last row with empty weighted slots so its swatches
                        // stay the same size as a full row instead of stretching to fill it.
                        repeat(4 - rowTypes.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, paperType) }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                        KapiIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            type.labelVi(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LabelFilterChip(label: NoteLabelFilter, selected: Boolean, onClick: () -> Unit) {
    val bg = Color(label.colorArgb)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .let {
                if (selected) it.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(50)) else it
            }
            .background(bg)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (selected) Icon(KapiIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(label.text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Curated palette for note labels - deliberately separate from [com.kapi.notes.ui.theme.PenColorSwatches]
 * (those are tuned to read well as thin ink on white paper; a label chip is a solid filled
 * background with white text, so it needs saturated-but-not-neon colors that stay legible). */
private val LabelColorSwatches: List<Long> = listOf(
    0xFFEF4444L, 0xFFF97316L, 0xFFEAB308L, 0xFF22C55EL,
    0xFF06B6D4L, 0xFF3B82F6L, 0xFF8B5CF6L, 0xFFEC4899L, 0xFF64748BL
)

@Composable
private fun LabelDialog(
    note: Note,
    existingLabels: List<NoteLabelFilter>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var text by remember { mutableStateOf(note.labelText.orEmpty()) }
    var color by remember { mutableStateOf(note.labelColorArgb ?: LabelColorSwatches.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.label_dialog_title)) },
        text = {
            Column {
                // Previously this dialog only ever let you TYPE a brand new label - picking one
                // that already exists on another note meant retyping its exact name and re-picking
                // its color by eye, with nothing stopping a typo from silently creating a second,
                // separate label. Tapping a chip here fills in both fields with that label's exact
                // text+color instead.
                if (existingLabels.isNotEmpty()) {
                    Text(stringResource(R.string.label_existing), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existingLabels.forEach { existing ->
                            val isSelected = text == existing.text && color == existing.colorArgb
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(Color(existing.colorArgb))
                                    .border(
                                        BorderStroke(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface),
                                        MaterialTheme.shapes.extraLarge
                                    )
                                    .combinedClickable(onClick = { text = existing.text; color = existing.colorArgb })
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(existing.text, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.label_or_create_new), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.label_name_placeholder)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelColorSwatches.forEach { swatch ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    BorderStroke(if (color == swatch) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface),
                                    CircleShape
                                )
                                .combinedClickable(onClick = { color = swatch })
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text, color) }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (note.labelText != null) {
                    TextButton(onClick = { onSave("", color) }) { Text(stringResource(R.string.label_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
