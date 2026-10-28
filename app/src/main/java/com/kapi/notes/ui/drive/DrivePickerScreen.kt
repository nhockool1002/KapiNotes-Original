@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kapi.notes.ui.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.kapi.notes.domain.model.DriveFile
import com.kapi.notes.domain.model.DriveFileKind

/**
 * Google Sign-In failures here are almost always a Google Cloud Console setup problem, not a
 * bug in this app - see README.md section 3. DEVELOPER_ERROR (10) in particular means the
 * app's package name + signing SHA-1 are not registered as an Android OAuth client yet, even
 * though the device already has a Google/Drive account signed in elsewhere.
 */
private fun googleSignInErrorMessage(e: Throwable): String {
    val code = (e as? ApiException)?.statusCode
    return when (code) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Lỗi cấu hình (DEVELOPER_ERROR): OAuth Client ID cho app này chưa được đăng ký đúng " +
                "trong Google Cloud Console (package name + SHA-1 chưa khớp). Việc máy đã đăng " +
                "nhập Google Drive sẵn không đủ - xem README.md mục 3 để tạo OAuth Client ID Android."
        CommonStatusCodes.NETWORK_ERROR -> "Không có kết nối mạng, thử lại sau."
        CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.CANCELED -> null
        else -> "Đăng nhập Google Drive thất bại" + (code?.let { " (mã lỗi $it)" } ?: "")
    } ?: "Đã hủy đăng nhập"
}

@Composable
fun DrivePickerScreen(
    onBack: () -> Unit,
    onNoteImported: (String) -> Unit,
    viewModel: DrivePickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            }.fold(
                onSuccess = { account -> viewModel.onSignInResult(account) },
                onFailure = { e -> viewModel.onSignInResult(null, googleSignInErrorMessage(e)) }
            )
        } else {
            // The Drive-scope consent step sometimes round-trips through its own activity and
            // reports back a non-OK result even though the OS already finished granting access -
            // fall back to the cached account instead of showing "cancelled" and making the user
            // tap "Kết nối Google Drive" again.
            val account = viewModel.driveAuthManager.lastSignedInAccount()
            if (account != null) {
                viewModel.onSignInResult(account)
            } else {
                // User genuinely backed out of the account picker - not a real error.
                viewModel.onSignInResult(null, errorMessage = null)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.account == null) {
                ConnectDriveState(onConnect = { signInLauncher.launch(viewModel.driveAuthManager.signInIntent()) })
            } else {
                Breadcrumbs(
                    breadcrumb = state.breadcrumb,
                    onRootClick = { viewModel.navigateToBreadcrumb(-1) },
                    onCrumbClick = { viewModel.navigateToBreadcrumb(it) }
                )
                Divider()

                when {
                    state.isLoading -> LoadingBox()
                    state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                    state.files.isEmpty() -> EmptyDriveBox()
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.files, key = { it.id }) { file ->
                            DriveFileRow(
                                file = file,
                                onClick = {
                                    if (file.kind == DriveFileKind.FOLDER) {
                                        viewModel.openFolder(file)
                                    } else {
                                        viewModel.importFile(file, onNoteImported)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }

                if (state.isImporting) {
                    LoadingBox(label = "Đang nhập tài liệu...")
                }
            }
        }
    }
}

@Composable
private fun ConnectDriveState(onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Kết nối Google Drive của bạn để chọn tài liệu cần ghi chú", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onConnect) { Text("Kết nối Google Drive") }
    }
}

@Composable
private fun Breadcrumbs(breadcrumb: List<Pair<String, String>>, onRootClick: () -> Unit, onCrumbClick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Drive của tôi", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRootClick))
        breadcrumb.forEachIndexed { index, crumb ->
            Text(" / ${crumb.second}", modifier = Modifier.clickable { onCrumbClick(index) })
        }
    }
}

@Composable
private fun DriveFileRow(file: DriveFile, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val icon = when (file.kind) {
            DriveFileKind.FOLDER -> Icons.Rounded.Folder
            DriveFileKind.PDF -> Icons.Rounded.PictureAsPdf
            DriveFileKind.IMAGE -> Icons.Rounded.Image
            DriveFileKind.OTHER -> Icons.Rounded.PictureAsPdf
        }
        Icon(icon, contentDescription = null)
        Text(file.name, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoadingBox(label: String = "Đang tải...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) { Text("Thử lại") }
        }
    }
}

@Composable
private fun EmptyDriveBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Không có PDF/ảnh nào trong thư mục này")
    }
}
