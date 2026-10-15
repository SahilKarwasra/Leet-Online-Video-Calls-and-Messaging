package com.laarasoft.frontend.domain.repository

import com.laarasoft.frontend.domain.models.RtcEvent
import kotlinx.coroutines.flow.Flow

interface MeetingRtcRepository {
    val rtcEvents: Flow<RtcEvent>
    suspend fun joinCall(meetingCode: String, participantId: String)
    suspend fun handleRemoteOffer(sdp: String)
    suspend fun handleRemoteAnswer(sdp: String)
    suspend fun handleRemoteIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?)
//    suspend fun setMicrophoneEnabled(enabled: Boolean)
//    suspend fun setCameraEnabled(enabled: Boolean)
    suspend fun leaveCall()
}