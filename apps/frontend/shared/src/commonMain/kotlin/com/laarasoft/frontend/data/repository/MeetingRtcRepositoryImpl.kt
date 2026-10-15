package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.data.datasource.KtorWebRtcDataSource
import com.laarasoft.frontend.data.datasource.MeetingSignal
import com.laarasoft.frontend.data.datasource.MeetingSignaling
import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.repository.MeetingRtcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class MeetingRtcRepositoryImpl(
    private val rtc: KtorWebRtcDataSource,
    private val signaling: MeetingSignaling
) : MeetingRtcRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val rtcEvents: Flow<RtcEvent> = rtc.events

    init {
        scope.launch {
            rtc.localIceCandidates.collect { candidate ->
                signaling.sendIceCandidate(candidate)
            }
        }

        scope.launch {
            signaling.incoming.collect { signal ->
                when (signal) {
                    is MeetingSignal.Offer -> {
                        rtc.startLocalMedia()
                        val answer = rtc.createAnswer(signal.sdp)
                        signaling.sendAnswer(answer)
                    }

                    is MeetingSignal.Answer -> {
                        rtc.setRemoteAnswer(signal.sdp)
                    }

                    is MeetingSignal.IceCandidate -> {
                        rtc.addIceCandidate(signal.candidate)
                    }

                    is MeetingSignal.Failure -> {
                    }
                }
            }
        }
    }

    override suspend fun joinCall(
        meetingCode: String,
        participantId: String
    ) {
        rtc.createConnection()
        rtc.startLocalMedia()
        signaling.join(meetingCode, participantId)
    }

    override suspend fun handleRemoteOffer(sdp: String) {
        rtc.startLocalMedia()
        signaling.sendAnswer(rtc.createAnswer(sdp))
    }

    override suspend fun handleRemoteAnswer(sdp: String) {
        rtc.setRemoteAnswer(sdp)
    }

    override suspend fun handleRemoteIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?
    ) {
        rtc.addIceCandidate(
            Candidate(
                sdpCandidate = candidate,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex
            )
        )
    }

//    override suspend fun setMicrophoneEnabled(enabled: Boolean) {
//        rtc.setMicrophoneEnabled(enabled)
//    }
//
//    override suspend fun setCameraEnabled(enabled: Boolean) {
//        rtc.setCameraEnabled(enabled)
//    }

    override suspend fun leaveCall() {
        signaling.leave()
        rtc.close()
    }

    fun dispose() {
        scope.cancel()
        signaling.disconnect()
        rtc.dispose()
    }
}
