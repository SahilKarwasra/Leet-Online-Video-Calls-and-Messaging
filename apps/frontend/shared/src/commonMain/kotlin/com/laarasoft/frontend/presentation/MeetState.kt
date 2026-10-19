package com.laarasoft.frontend.presentation

import com.laarasoft.frontend.domain.models.Participant
import io.ktor.client.webrtc.WebRtcMedia

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val isMine: Boolean,
)

data class FloatingReaction(
    val id: Long,
    val emoji: String,
)

enum class MeetPanel { None, Chat, People }

data class MeetState(
    val participants: List<Participant> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val reactions: List<FloatingReaction> = emptyList(),
    val isMicOn: Boolean = true,
    val isCameraOn: Boolean = true,
    val isScreenSharing: Boolean = false,
    val isHandRaised: Boolean = false,
    val panel: MeetPanel = MeetPanel.None,
    val unreadCount: Int = 0,
    val elapsed: String = "00:00",
    val localVideoTrack: WebRtcMedia.VideoTrack? = null,
    // Keyed by participant id.
    val remoteVideoTracks: Map<String, WebRtcMedia.VideoTrack> = emptyMap(),
) {
    fun videoTrackFor(participant: Participant): WebRtcMedia.VideoTrack? =
        if (participant.isLocal) localVideoTrack else remoteVideoTracks[participant.id]
}