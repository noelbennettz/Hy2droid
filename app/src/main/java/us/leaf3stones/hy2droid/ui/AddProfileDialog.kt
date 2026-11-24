package us.leaf3stones.hy2droid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import us.leaf3stones.hy2droid.R

/**
 * The menu behind the home screen's `+` button: the three ways a profile arrives.
 *
 * It exists because the editor used to be the first screen behind `+`, which put
 * a ten-field form in front of someone who only had a link to paste. Each row is
 * now one path: the link comes from the clipboard, the scanner keeps its camera
 * screen, and only *Enter manually* opens the editor.
 */
@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onImportLink: () -> Unit,
    onScan: () -> Unit,
    onManual: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_title)) },
        text = {
            Column {
                Choice(
                    icon = Icons.Filled.Link,
                    title = stringResource(R.string.add_from_link),
                    subtitle = stringResource(R.string.add_from_link_summary),
                    onClick = onImportLink,
                )
                Choice(
                    icon = Icons.Outlined.QrCode,
                    title = stringResource(R.string.add_scan_qr),
                    subtitle = stringResource(R.string.add_scan_qr_summary),
                    onClick = onScan,
                )
                Choice(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.add_manual),
                    subtitle = stringResource(R.string.add_manual_summary),
                    onClick = onManual,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** One row of the menu: icon, name and a line saying where it leads. */
@Composable
private fun Choice(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
