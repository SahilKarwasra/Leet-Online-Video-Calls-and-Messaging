package com.laarasoft.frontend.domain.models

sealed interface MeetEvents {

    data class Joined(val participant: Participant) : MeetEvents

    data class ExistingParticipants(val participants: List<Participant>) : MeetEvents

    data class ParticipantLeft(val participant: Participant) : MeetEvents

    data class Failure(val code: String, val message: String) : MeetEvents

    data class Unknown(val type: String) : MeetEvents

}
