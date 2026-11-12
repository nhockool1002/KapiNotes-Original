package com.kapi.notes.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kapi.notes.R
import com.kapi.notes.ui.icons.KapiIcons
import com.kapi.notes.update.PlayUpdateViewModel

/**
 * Checks Google Play for a newer published version once per app session and, if found, offers
 * it through a dialog - entirely separate from [UpdateDialog] (the GitHub-releases check used
 * while this app isn't on Play Store yet): this one only ever has something to show once the
 * installed build actually came from Play Store. Mount once near the top of the nav graph so it
 * persists across screen navigation instead of re-checking on every screen.
 */
@Composable
fun PlayUpdateHost(viewModel: PlayUpdateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // Ignored: whether the user completes, cancels, or backs out of Play's own update
        // sheet, the InstallStateUpdatedListener (wired up in the ViewModel) is what tells us
        // when a download actually finishes - not this result callback.
    }

    when {
        state.isDownloadReady && !state.readyDismissed -> PlayUpdateReadyDialog(
            onRestart = viewModel::completeUpdate,
            onDismiss = viewModel::dismissReady
        )
        state.availableUpdate != null && !state.dismissed -> PlayUpdateAvailableDialog(
            onUpdate = { viewModel.requestUpdate(launcher) },
            onDismiss = viewModel::dismiss
        )
    }
}

@Composable
private fun PlayUpdateAvailableDialog(onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KapiIcons.CloudDownload, contentDescription = null) },
        title = { Text(stringResource(R.string.play_update_available_title)) },
        text = { Text(stringResource(R.string.play_update_available_body)) },
        confirmButton = { Button(onClick = onUpdate) { Text(stringResource(R.string.play_update_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.play_update_later)) } }
    )
}

@Composable
private fun PlayUpdateReadyDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KapiIcons.CloudDownload, contentDescription = null) },
        title = { Text(stringResource(R.string.play_update_ready_title)) },
        text = { Text(stringResource(R.string.play_update_ready_body)) },
        confirmButton = { Button(onClick = onRestart) { Text(stringResource(R.string.play_update_restart_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.play_update_later)) } }
    )
}
