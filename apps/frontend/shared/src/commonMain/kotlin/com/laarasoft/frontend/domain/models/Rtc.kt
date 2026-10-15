package com.laarasoft.frontend.domain.models

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

data class RemoteTrack(
    val participantId: String?,
    val trackId: String,
    val kind: String
)

sealed interface RtcEvent {
    data class ConnectionStateChanged(
        val state: RtcConnectionState
    ) : RtcEvent

    data class RemoteTrackAdded(
        val track: RemoteTrack
    ) : RtcEvent

    data class RemoteTrackRemoved(
        val trackId: String
    ) : RtcEvent

    data class Error(
        val message: String
    ) : RtcEvent
}