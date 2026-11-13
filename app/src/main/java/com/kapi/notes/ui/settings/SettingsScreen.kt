@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kapi.notes.ui.settings

import com.kapi.notes.ui.icons.KapiIcons

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kapi.notes.R
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.kapi.notes.domain.model.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.logSignInEvent("Kết quả màn hình chọn tài khoản: resultCode=${result.resultCode} (OK=${Activity.RESULT_OK}, CANCELED=${Activity.RESULT_CANCELED})")
        val signInResult = if (result.resultCode == Activity.RESULT_OK) {
            runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result }
                .onFailure { viewModel.logSignInEvent("getSignedInAccountFromIntent lỗi: ${it::class.java.simpleName} - ${it.message}") }
                .onSuccess { viewModel.logSignInEvent("getSignedInAccountFromIntent OK: ${it.email}") }
        } else {
            viewModel.logSignInEvent("Không phải RESULT_OK - người dùng có thể đã huỷ, hoặc Play Services trả về lỗi trước khi mở được màn hình chọn tài khoản")
            Result.failure(IllegalStateException("non-OK activity result"))
        }
        val immediateAccount = signInResult.getOrNull()
        if (immediateAccount != null) {
            viewModel.logSignInEvent("Đăng nhập thành công, đang lưu phiên + đồng bộ lần đầu")
            viewModel.onGoogleSignInResult(immediateAccount)
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            // Play Services can still be settling the signed-in session (silently) for a moment
            // right after the picker returns: getSignedInAccountFromIntent() can fail even though
            // lastSignedInAccount() would succeed a beat later. A same-tick check misses that
            // window entirely, so retry a few times with a short delay before giving up - this is
            // what makes a single tap actually complete instead of silently doing nothing.
            var fallbackAccount: GoogleSignInAccount? = null
            repeat(3) { attempt ->
                if (fallbackAccount == null) {
                    delay(400L)
                    fallbackAccount = viewModel.googleAuthManager.lastSignedInAccount()
                    if (fallbackAccount != null) {
                        viewModel.logSignInEvent("Fallback lastSignedInAccount() tìm thấy sau thử lại #${attempt + 1}: ${fallbackAccount?.email}")
                    }
                }
            }
            val account = fallbackAccount
            if (account != null) {
                viewModel.logSignInEvent("Đăng nhập thành công (qua fallback), đang lưu phiên + đồng bộ lần đầu")
                viewModel.onGoogleSignInResult(account)
            } else {
                val exception = signInResult.exceptionOrNull()
                val code = (exception as? ApiException)?.statusCode
                val message = when {
                    // DEVELOPER_ERROR almost always means no OAuth Android client in Google Cloud
                    // Console matches THIS exact (package name, signing SHA-1) pair. Naming the
                    // actual package here saves a guessing round-trip into Cloud Console.
                    code == CommonStatusCodes.DEVELOPER_ERROR ->
                        context.getString(R.string.settings_oauth_config_error, context.packageName)
                    code != null -> context.getString(R.string.settings_signin_failed_code, code)
                    // RESULT_OK (the picker genuinely returned) but still no account after retrying
                    // is a real failure, not a cancel - show something instead of leaving the user
                    // staring at a Sign-in button that silently did nothing.
                    result.resultCode == Activity.RESULT_OK ->
                        context.getString(R.string.settings_signin_failed_generic)
                    // Non-OK with no exception usually just means the user backed out of the
                    // picker - showing a scary error for a plain cancel would be wrong, but it's
                    // still logged above so a real failure can be told apart from a cancel by
                    // reading the shared log.
                    else -> null
                }
                viewModel.logSignInEvent("Đăng nhập KHÔNG thành công. code=$code, exception=${exception?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "không có"}, thông báo hiển thị cho người dùng=${message ?: "(không hiển thị gì - có thể do người dùng huỷ)"}")
                viewModel.onGoogleSignInResult(null, message)
            }
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
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.settings_save_backup_chooser)))
            }.onFailure { snackbarMessage = context.getString(R.string.settings_share_sheet_failed, it.message) }
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
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.settings_share_sync_log_chooser)))
            }.onFailure { snackbarMessage = context.getString(R.string.settings_share_sheet_failed, it.message) }
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(KapiIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(20.dp)) {
            AccountSection(
                state = state,
                onSignIn = {
                    viewModel.logSignInEvent("Bấm 'Đăng nhập bằng Google'")
                    runCatching { signInLauncher.launch(viewModel.googleAuthManager.signInIntent()) }
                        .onFailure { viewModel.logSignInEvent("Không mở được màn hình chọn tài khoản: ${it::class.java.simpleName} - ${it.message}") }
                },
                onLogout = viewModel::logout,
                onSync = viewModel::triggerSync,
                onShareLog = viewModel::shareSyncLog
            )

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_section_writing), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_palm_rejection_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_palm_rejection_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.palmRejectionEnabled, onCheckedChange = viewModel::setPalmRejectionEnabled)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_stylus_only_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_stylus_only_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.stylusOnlyEnabled, onCheckedChange = viewModel::setStylusOnlyEnabled)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_section_data), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_data_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::exportBackup, enabled = !state.isWorkingOnBackup) { Text(stringResource(R.string.settings_export_data)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !state.isWorkingOnBackup) { Text(stringResource(R.string.settings_import_data)) }
                Button(
                    onClick = viewModel::requestDeleteAllData,
                    enabled = !state.isWorkingOnBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_delete_all)) }
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
            title = { Text(stringResource(R.string.settings_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteAllData) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteAllData) { Text(stringResource(R.string.action_cancel)) } }
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
    Text(stringResource(R.string.settings_section_account), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_account_desc),
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
                    if (state.isLoggingIn) CircularProgressIndicator(modifier = Modifier.height(16.dp)) else Text(stringResource(R.string.settings_sign_in_google))
                }
                Spacer(Modifier.height(8.dp))
                // Every step of a sign-in attempt (tap, activity result, exception if any) is now
                // logged into the same file as sync - reachable here even before ever signing in
                // successfully, since that's exactly when this is needed most: "I tapped sign-in
                // and nothing visibly happened" has no other way to investigate otherwise.
                TextButton(onClick = onShareLog, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_share_signin_log))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(state.session.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(state.session.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onLogout) { Text(stringResource(R.string.settings_sign_out)) }
                }
                Divider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.settings_sync_with_drive), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (state.syncStatus) {
                                SyncStatus.IDLE -> stringResource(R.string.settings_sync_status_idle)
                                SyncStatus.SYNCING -> stringResource(R.string.dashboard_syncing)
                                SyncStatus.SYNCED -> stringResource(R.string.settings_sync_status_synced)
                                SyncStatus.ERROR -> stringResource(R.string.settings_sync_status_error)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.syncStatus == SyncStatus.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        OutlinedButton(onClick = onSync) { Text(stringResource(R.string.dashboard_sync_now)) }
                    }
                }
                // Always reachable (not just after an error) so the user can grab the log right
                // after a timeout too, or just to double-check what the last sync actually did.
                TextButton(onClick = onShareLog, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_share_sync_log))
                }
            }
        }
    }
}
