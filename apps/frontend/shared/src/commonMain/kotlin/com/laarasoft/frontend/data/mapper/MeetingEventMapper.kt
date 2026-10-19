package com.laarasoft.frontend.data.mapper

import com.laarasoft.frontend.data.dto.ChatMessageDto
import com.laarasoft.frontend.data.dto.ErrorDto
import com.laarasoft.frontend.data.dto.HandRaiseDto
import com.laarasoft.frontend.data.dto.MeetEventDto
import com.laarasoft.frontend.data.dto.ParticipantDto
import com.laarasoft.frontend.data.dto.ReactionDto
import com.laarasoft.frontend.domain.models.MeetEvents
import com.laarasoft.frontend.domain.models.Participant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class MeetingEventMapper(
    private val json: Json,
) {
    private fun ParticipantDto.toDomain() = Participant(
        id = participantId,
        name = name,
    )
    // A malformed frame must not throw, or it would end the whole event stream.
    fun map(events: MeetEventDto) : MeetEvents = try {
        decode(events)
    } catch (_: IllegalArgumentException) {
        MeetEvents.Unknown(events.type)
    }

    private fun decode(events: MeetEventDto) : MeetEvents {
        return when (events.type) {
            "meeting.joined" -> {
                val data = json.decodeFromJsonElement<ParticipantDto>(events.data)
                MeetEvents.Joined(data.toDomain().copy(isLocal = true))
            }

            "meeting.already_exists_participants" -> {
                val data = json.decodeFromJsonElement<List<ParticipantDto>>(events.data)
                MeetEvents.ExistingParticipants(data.map { it.toDomain().copy(isLocal = false) })
            }

            "meeting.participant_joined" -> {
                val data = json.decodeFromJsonElement<ParticipantDto>(events.data)
                MeetEvents.Joined(data.toDomain().copy(isLocal = false))
            }

            "meeting.left" -> {
                val data = json.decodeFromJsonElement<ParticipantDto>(events.data)
                MeetEvents.ParticipantLeft(data.toDomain())
            }

            "chat.message" -> {
                val data = json.decodeFromJsonElement<ChatMessageDto>(events.data)
                MeetEvents.ChatMessageReceived(
                    id = data.id,
                    participantId = data.participantId,
                    name = data.name,
                    text = data.message,
                    sentAt = data.sentAt,
                )
            }

            "meeting.reaction" -> {
                val data = json.decodeFromJsonElement<ReactionDto>(events.data)
                MeetEvents.ReactionReceived(
                    participantId = data.participantId,
                    name = data.name,
                    emoji = data.emoji,
                )
            }

            "meeting.hand_raise" -> {
                val data = json.decodeFromJsonElement<HandRaiseDto>(events.data)
                MeetEvents.HandRaiseChanged(
                    participantId = data.participantId,
                    name = data.name,
                    raised = data.raised,
                )
            }

            "error" -> {
                val data = json.decodeFromJsonElement<ErrorDto>(events.data)
                MeetEvents.Failure(code = data.code, message = data.message)
            }

            else -> MeetEvents.Unknown(events.type)

        }
    }
}