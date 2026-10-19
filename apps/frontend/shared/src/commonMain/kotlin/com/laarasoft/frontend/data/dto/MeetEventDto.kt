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
data class ChatMessagePayloadDto(
    @SerialName("participant_id")
    val participantId: String,
    val name: String,
    val message: String,
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
