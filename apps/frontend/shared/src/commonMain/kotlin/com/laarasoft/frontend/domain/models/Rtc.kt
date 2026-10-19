package com.laarasoft.frontend.domain.models

import io.ktor.client.webrtc.WebRtcMedia

data class RtcSessionDescription(
    val type: String,
    val sdp: String
)

data class Candidate(
    val sdpMid: String?,
    val sdpMLineIndex: Int?,
    val sdpCandidate: String
)

enum class RtcConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED
}

// Tracks we publish. Either can be null if the device or permission is unavailable.
data class LocalMedia(
    val audioTrack: WebRtcMedia.AudioTrack?,
    val videoTrack: WebRtcMedia.VideoTrack?,
)

// The SFU names forwarded tracks "<participantId>:<originalTrackId>".
data class RemoteTrack(
    val participantId: String,
    val track: WebRtcMedia.Track,
)

sealed interface RtcEvent {
    data class ConnectionStateChanged(
        val state: RtcConnectionState
    ) : RtcEvent

    data class RemoteTrackAdded(
        val track: RemoteTrack
    ) : RtcEvent

    data class RemoteTrackRemoved(
        val track: RemoteTrack
    ) : RtcEvent

    data class Error(
        val message: String
    ) : RtcEvent
}
