package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import kotlinx.coroutines.flow.Flow

sealed interface MeetingSignal {
    data class Offer(val sdp: String) : MeetingSignal
    data class Answer(val sdp: String) : MeetingSignal
    data class IceCandidate(val candidate: com.laarasoft.frontend.domain.models.Candidate) : MeetingSignal
    data class Failure(val message: String) : MeetingSignal
}

interface MeetingSignaling {
    val incoming: Flow<MeetingSignal>
    suspend fun join(meetingCode: String, participantId: String)
    suspend fun sendOffer(description: RtcSessionDescription)
    suspend fun sendAnswer(description: RtcSessionDescription)
    suspend fun sendIceCandidate(candidate: Candidate)
    suspend fun leave()
    fun disconnect()
}