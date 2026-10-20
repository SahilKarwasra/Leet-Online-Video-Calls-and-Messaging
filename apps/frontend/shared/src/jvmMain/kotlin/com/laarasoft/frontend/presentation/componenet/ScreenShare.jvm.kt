package com.laarasoft.frontend.presentation.componenet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laarasoft.frontend.config.media.DesktopShareSource
import com.laarasoft.frontend.config.media.JvmScreenCapturer
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberScreenShareLauncher(onGranted: (ScreenCaptureGrant) -> Unit): ScreenShareLauncher {
    val currentOnGranted by rememberUpdatedState(onGranted)
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<DesktopShareSource>?>(null) }

    sources?.let { list ->
        SourcePicker(
            sources = list,
            onPick = {
                sources = null
                currentOnGranted(it.grant)
            },
            onDismiss = { sources = null }
        )
    }

    return remember {
        object : ScreenShareLauncher {
            override val isSupported = true
            override fun launch() {
                scope.launch {
                    // An empty list shows the "allow Screen Recording" hint
                    // rather than crashing the window.
                    sources = withContext(Dispatchers.IO) {
                        runCatching { JvmScreenCapturer.listSources() }.getOrDefault(emptyList())
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePicker(
    sources: List<DesktopShareSource>,
    onPick: (DesktopShareSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose what to share") },
        text = {
            if (sources.isEmpty()) {
                Text("Nothing to share was found. On macOS, allow Screen Recording for this app in System Settings → Privacy & Security, then try again.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sources, key = { "${it.grant.isWindow}:${it.grant.sourceId}" }) { source ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(source) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (source.grant.isWindow) Icons.Rounded.DesktopWindows else Icons.Rounded.Monitor,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (source.grant.isWindow) source.title else "Entire screen · ${source.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
