package com.laarasoft.frontend.domain.models

sealed interface MeetEvents {

    data class Joined(val participant: Participant) : MeetEvents

    data class ExistingParticipants(val participants: List<Participant>) : MeetEvents

    data class ParticipantLeft(val participant: Participant) : MeetEvents

    data class ChatMessageReceived(
        val id: String,
        val participantId: String,
        val name: String,
        val text: String,
        val sentAt: Long,
    ) : MeetEvents

    data class ReactionReceived(
        val participantId: String,
        val name: String,
        val emoji: String,
    ) : MeetEvents

    data class HandRaiseChanged(
        val participantId: String,
        val name: String,
        val raised: Boolean,
    ) : MeetEvents

    data class Failure(val code: String, val message: String) : MeetEvents

    data class Unknown(val type: String) : MeetEvents

}
