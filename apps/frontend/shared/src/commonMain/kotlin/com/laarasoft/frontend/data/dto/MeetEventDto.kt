package com.laarasoft.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class MeetEventDto(
    val type: String,
    val data: JsonElement = JsonNull,
)

@Serializable
data class ParticipantDto(
    @SerialName("participant_id")
    val participantId: String,
    val name: String,
)

@Serializable
data class JoinPayloadDto(
    val name: String,
)

@Serializable
data class SendChatMessageDto(
    val message: String,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("participant_id")
    val participantId: String,
    val name: String,
    val message: String,
    @SerialName("sent_at")
    val sentAt: Long = 0,
)

@Serializable
data class SendReactionDto(
    val emoji: String,
)

@Serializable
data class ReactionDto(
    @SerialName("participant_id")
    val participantId: String,
    val name: String,
    val emoji: String,
)

@Serializable
data class SendHandRaiseDto(
    val raised: Boolean,
)

@Serializable
data class HandRaiseDto(
    @SerialName("participant_id")
    val participantId: String,
    val name: String,
    val raised: Boolean,
)

@Serializable
data class SendMediaStateDto(
    val audio: Boolean,
    val video: Boolean,
    val screen: Boolean,
)

@Serializable
data class MediaStateDto(
    @SerialName("participant_id")
    val participantId: String,
    val audio: Boolean,
    val video: Boolean,
    val screen: Boolean = false,
)

@Serializable
data class SdpDto(
    val type: String,
    val sdp: String,
)

@Serializable
data class IceCandidateDto(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
)

@Serializable
data class ErrorDto(
    val code: String = "",
    val message: String = "",
)
