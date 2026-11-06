package com.kapi.notes.ui.editor

import com.kapi.notes.ui.icons.KapiIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kapi.notes.R
import com.kapi.notes.domain.model.PageLayer

/**
 * "Lớp (layers)" panel: lists a page's drawing layers, each independently toggle-able (visible
 * while editing - export always includes everything regardless), renamable, and selectable as
 * the "active" layer new strokes/images/stickers/text land on. New content goes on top, so the
 * list shows the most-recently-added layer first.
 */
@Composable
fun LayersPanelDialog(
    layers: List<PageLayer>,
    activeLayerId: String,
    onDismiss: () -> Unit,
    onAddLayer: () -> Unit,
    onRenameLayer: (String, String) -> Unit,
    onToggleVisible: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onSelectActive: (String) -> Unit
) {
    var renamingLayerId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layers_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.layers_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height((layers.size.coerceAtMost(5) * 56).dp)) {
                    items(layers.reversed(), key = { it.id }) { layer ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleVisible(layer.id) }) {
                                Icon(
                                    if (layer.visible) KapiIcons.Visibility else KapiIcons.VisibilityOff,
                                    contentDescription = if (layer.visible) stringResource(R.string.layers_hide) else stringResource(R.string.layers_show)
                                )
                            }
                            IconButton(onClick = { onSelectActive(layer.id) }) {
                                Icon(
                                    if (layer.id == activeLayerId) KapiIcons.RadioButtonChecked else KapiIcons.RadioButtonUnchecked,
                                    contentDescription = stringResource(R.string.layers_select_active),
                                    tint = if (layer.id == activeLayerId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                layer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { renamingLayerId = layer.id }) {
                                Icon(KapiIcons.Edit, contentDescription = stringResource(R.string.layers_rename), modifier = Modifier.width(18.dp))
                            }
                            IconButton(onClick = { onDeleteLayer(layer.id) }, enabled = layers.size > 1) {
                                Icon(KapiIcons.Delete, contentDescription = stringResource(R.string.layers_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onAddLayer) {
                        Icon(KapiIcons.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.layers_add))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
    )

    renamingLayerId?.let { layerId ->
        var draft by remember(layerId) { mutableStateOf(layers.firstOrNull { it.id == layerId }?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renamingLayerId = null },
            title = { Text(stringResource(R.string.layers_rename_title)) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameLayer(layerId, draft)
                    renamingLayerId = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renamingLayerId = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
