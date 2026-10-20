package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.laarasoft.frontend.config.media.IosBroadcastGrant
import com.laarasoft.frontend.config.media.IosScreenShare
import com.laarasoft.frontend.config.media.IosScreenShareSession
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant

// Opens the system broadcast picker. Sharing starts once the user taps
// "Start Broadcast" and the extension connects; cancelling does nothing.
@Composable
actual fun rememberScreenShareLauncher(onGranted: (ScreenCaptureGrant) -> Unit): ScreenShareLauncher {
    val currentOnGranted by rememberUpdatedState(onGranted)
    return remember {
        object : ScreenShareLauncher {
            override val isSupported = IosScreenShare.broadcast?.isAvailable == true
            override fun launch() {
                val broadcast = IosScreenShare.broadcast ?: return
                IosScreenShareSession.onStartedHandler = { currentOnGranted(IosBroadcastGrant) }
                broadcast.startListening(IosScreenShareSession)
                broadcast.showPicker()
            }
        }
    }
}
