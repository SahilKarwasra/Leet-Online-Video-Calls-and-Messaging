package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.client.webrtc.WebRtcMedia

// Renders a local or remote WebRTC video track using the platform's native renderer.
@Composable
expect fun VideoContent(
    track: WebRtcMedia.VideoTrack,
    mirror: Boolean,
    modifier: Modifier = Modifier,
)
