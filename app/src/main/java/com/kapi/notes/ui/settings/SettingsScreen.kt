@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kapi.notes.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.kapi.notes.domain.model.SyncStatus
import java.io.File

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // The Drive-scope consent step is often still finishing up (silently, at the Play
        // services level) at the exact moment this callback fires: getSignedInAccountFromIntent()
        // can report a failure, or the activity itself can return a non-OK result, even though
        // the account+scopes end up fully granted a moment later - the user's SECOND tap then
        // "just works" because it's reading a cache that's already settled. Checking
        // lastSignedInAccount() as a fallback on ANY failure (not just a non-OK result code)
        // catches that and completes login in one tap instead of needing a retry.
        val signInResult = if (result.resultCode == Activity.RESULT_OK) {
            runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result }
        } else {
            Result.failure(IllegalStateException("non-OK activity result"))
        }
        val account = signInResult.getOrNull() ?: viewModel.driveAuthManager.lastSignedInAccount()
        if (account != null) {
            viewModel.onGoogleSignInResult(account)
        } else {
            val code = (signInResult.exceptionOrNull() as? ApiException)?.statusCode
            val message = when {
                code == CommonStatusCodes.DEVELOPER_ERROR ->
                    "Lỗi cấu hình OAuth (DEVELOPER_ERROR) - xem README.md mục 3"
                code != null -> "Đăng nhập thất bại (mã $code)"
                else -> null
            }
            viewModel.onGoogleSignInResult(null, message)
        }
    }

    LaunchedEffect(state.backupMessage) {
        state.backupMessage?.let { snackbarMessage = it; viewModel.dismissMessage() }
    }

    LaunchedEffect(state.lastExportedFile) {
        state.lastExportedFile?.let { file ->
            // A misconfigured FileProvider path (or any other surprise here) must never crash the
            // whole app over something as recoverable as "couldn't open the share sheet" - the
            // backup file itself was already written successfully at this point.
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Lưu file backup"))
            }.onFailure { snackbarMessage = "Không mở được hộp thoại chia sẻ: ${it.message}" }
            viewModel.consumeExportedFile()
        }
    }

    LaunchedEffect(state.lastSharedLogFile) {
        state.lastSharedLogFile?.let { file ->
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Chia sẻ log đồng bộ"))
            }.onFailure { snackbarMessage = "Không mở được hộp thoại chia sẻ: ${it.message}" }
            viewModel.consumeSharedLogFile()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.kapibackup")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        viewModel.importBackup(tempFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(20.dp)) {
            AccountSection(
                state = state,
                onSignIn = { signInLauncher.launch(viewModel.driveAuthManager.signInIntent()) },
                onLogout = viewModel::logout,
                onSync = viewModel::triggerSync,
                onShareLog = viewModel::shareSyncLog
            )

            Spacer(Modifier.height(24.dp))
            Text("Viết & vẽ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bàn tay nghỉ trên màn hình (palm rejection)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Khi dùng bút cảm ứng, các điểm chạm bằng ngón tay/lòng bàn tay nghỉ trên màn hình sẽ bị bỏ qua để tránh vẽ nhầm.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.palmRejectionEnabled, onCheckedChange = viewModel::setPalmRejectionEnabled)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Dữ liệu trên máy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ngoài đồng bộ qua Drive ở trên, bạn vẫn có thể xuất ghi chú ra một file riêng để sao lưu/chuyển máy thủ công, nhập lại, hoặc xóa toàn bộ dữ liệu trên máy này.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::exportBackup, enabled = !state.isWorkingOnBackup) { Text("Xuất dữ liệu") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !state.isWorkingOnBackup) { Text("Nhập dữ liệu") }
                Button(
                    onClick = viewModel::requestDeleteAllData,
                    enabled = !state.isWorkingOnBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa tất cả") }
            }

            if (state.isWorkingOnBackup) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            }

            snackbarMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteAllData,
            title = { Text("Xóa toàn bộ dữ liệu?") },
            text = { Text("Mọi ghi chú, trang và nét vẽ trên máy sẽ bị xóa vĩnh viễn và không thể khôi phục (kể cả bản đã đồng bộ lên Drive vẫn còn, có thể đồng bộ lại để khôi phục).") },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteAllData) { Text("Xóa", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteAllData) { Text("Hủy") } }
        )
    }
}

@Composable
private fun AccountSection(
    state: SettingsUiState,
    onSignIn: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onShareLog: () -> Unit
) {
    Text("Tài khoản & đồng bộ", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Đăng nhập Google để lưu ghi chú lên Google Drive của chính bạn. Đăng nhập cùng tài khoản trên thiết bị khác sẽ tự động tải ghi chú về - không có máy chủ trung gian nào khác.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (state.session == null) {
                state.loginError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onSignIn, enabled = !state.isLoggingIn, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoggingIn) CircularProgressIndicator(modifier = Modifier.height(16.dp)) else Text("Đăng nhập bằng Google")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(state.session.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(state.session.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onLogout) { Text("Đăng xuất") }
                }
                Divider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Đồng bộ với Google Drive", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (state.syncStatus) {
                                SyncStatus.IDLE -> "Chưa đồng bộ lần nào"
                                SyncStatus.SYNCING -> "Đang đồng bộ..."
                                SyncStatus.SYNCED -> "Đã đồng bộ"
                                SyncStatus.ERROR -> "Lỗi đồng bộ, thử lại"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.syncStatus == SyncStatus.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        OutlinedButton(onClick = onSync) { Text("Đồng bộ ngay") }
                    }
                }
                // Always reachable (not just after an error) so the user can grab the log right
                // after a timeout too, or just to double-check what the last sync actually did.
                TextButton(onClick = onShareLog, modifier = Modifier.fillMaxWidth()) {
                    Text("Chia sẻ log đồng bộ (để điều tra lỗi/timeout)")
                }
            }
        }
    }
}
