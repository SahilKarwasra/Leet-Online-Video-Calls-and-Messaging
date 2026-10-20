package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.onvoid.webrtc.media.video.desktop.PowerManagement

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        val power = runCatching { PowerManagement().apply { enableUserActivity() } }.getOrNull()
        onDispose { runCatching { power?.disableUserActivity() } }
    }
}

actual val PausesCameraInBackground: Boolean = false
