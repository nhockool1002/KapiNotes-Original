@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kapi.notes.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kapi.notes.update.VersionStatus

/** Small colored pill showing the installed version - green when it matches GitHub's latest
 * release, orange when 1-2 releases behind, red when further behind than that. Tapping it opens
 * [UpdateDialog] even when already up to date, so the user can always check "what's new"
 * voluntarily, not only when forced to update. Used on both the Dashboard and the Editor (there,
 * floating above the bottom toolbar instead of in a header). */
@Composable
fun VersionBadge(status: VersionStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = when {
        status.newerCount <= 0 -> Color(0xFF22C55E)
        status.newerCount <= 2 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    Box(
        modifier
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

/** App isn't on Play Store yet, so there is no store update prompt - this is the dialog behind
 * both the mandatory "you must update" flow (non-dismissible, shown automatically when outdated)
 * and the voluntary "what's new" flow reachable from [VersionBadge] at any time. */
@Composable
fun UpdateDialog(
    status: VersionStatus,
    mandatory: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
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
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (!status.isUpToDate && status.latestReleaseUrl != null) {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.latestReleaseUrl))
                    context.startActivity(intent)
                }) { Text("Tải bản cập nhật") }
            } else {
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    )
}
