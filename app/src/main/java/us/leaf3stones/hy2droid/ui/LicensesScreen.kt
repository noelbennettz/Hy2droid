package us.leaf3stones.hy2droid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import us.leaf3stones.hy2droid.BuildConfig
import us.leaf3stones.hy2droid.R
import us.leaf3stones.hy2droid.data.CoreVersion
import us.leaf3stones.hy2droid.data.LicenseEntry
import us.leaf3stones.hy2droid.data.ThirdPartyLicenses

/**
 * Everything that ships inside the APK and belongs to someone else.
 *
 * The dependency half is the build's Licensee report, copied into assets at
 * build time; the native half is written down in [ThirdPartyLicenses] because a
 * `.so` and an exec'd Go binary are invisible to a Gradle licence plugin. Both
 * halves are shipped data: the screen never opens a socket, so it looks the same
 * in a tunnel, behind a captive portal, or in aeroplane mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var dependencies by remember { mutableStateOf<List<LicenseEntry>>(emptyList()) }
    var hysteriaVersion by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var texts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        dependencies = runCatching {
            context.assets.open(ThirdPartyLicenses.DEPENDENCIES_ASSET)
                .bufferedReader().use { ThirdPartyLicenses.parseDependencies(it.readText()) }
        }.getOrDefault(emptyList())
        hysteriaVersion = CoreVersion.probe(context)
        texts = listOf(
            ThirdPartyLicenses.HYSTERIA_LICENSE_ASSET,
            ThirdPartyLicenses.TUN2SOCKS_LICENSE_ASSET,
        ).mapNotNull { asset ->
            runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }.getOrNull()?.let { asset to it }
        }.toMap()
    }

    val native = ThirdPartyLicenses.nativeComponents(
        hysteriaVersion = hysteriaVersion,
        tun2socksVersion = BuildConfig.TUN2SOCKS_VERSION,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.licenses_section_native))
            Text(
                text = stringResource(R.string.licenses_section_native_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            native.forEach { entry ->
                val asset = entry.licenseTextAsset
                val open = asset != null && expanded == asset
                LicenseRow(
                    entry = entry,
                    expandable = asset != null && texts.containsKey(asset),
                    expanded = open,
                    onClick = { expanded = if (open) null else asset },
                )
                if (open && asset != null) {
                    Text(
                        text = texts[asset].orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            SectionHeader(stringResource(R.string.licenses_section_libraries))
            Text(
                text = stringResource(
                    R.string.licenses_section_libraries_summary,
                    dependencies.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            dependencies.forEach { entry ->
                LicenseRow(entry = entry, expandable = false, expanded = false, onClick = null)
                HorizontalDivider()
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.licenses_native_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Same metrics as the other list rows, with the licence on the right. */
@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    expandable: Boolean,
    expanded: Boolean,
    onClick: (() -> Unit)?,
) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = entry.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
