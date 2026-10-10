package com.laarasoft.frontend.data.mapper

import com.laarasoft.frontend.data.dto.MeetEventDto
import com.laarasoft.frontend.data.dto.ParticipantDto
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
    fun map(events: MeetEventDto) : MeetEvents {
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

            else -> MeetEvents.Unknown(events.type)

        }
    }
}