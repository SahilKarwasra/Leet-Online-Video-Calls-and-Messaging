package com.laarasoft.frontend.domain.datasource

import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import io.ktor.client.webrtc.WebRtcMedia

interface ScreenCapturer {
    suspend fun createTrack(grant: ScreenCaptureGrant, onEnded: () -> Unit): WebRtcMedia.VideoTrack
}
