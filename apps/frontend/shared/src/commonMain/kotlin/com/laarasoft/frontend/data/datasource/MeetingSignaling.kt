package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.data.dto.ErrorDto
import com.laarasoft.frontend.data.dto.IceCandidateDto
import com.laarasoft.frontend.data.dto.MeetEventDto
import com.laarasoft.frontend.data.dto.SdpDto
import com.laarasoft.frontend.domain.datasource.KtorWsDataSource
import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

sealed interface MeetingSignal {
    data class Offer(val sdp: String) : MeetingSignal
    data class Answer(val sdp: String) : MeetingSignal
    data class IceCandidate(val candidate: Candidate) : MeetingSignal
    data class Failure(val message: String) : MeetingSignal
}

interface MeetingSignaling {
    val incoming: Flow<MeetingSignal>
    suspend fun sendOffer(description: RtcSessionDescription)
    suspend fun sendAnswer(description: RtcSessionDescription)
    suspend fun sendIceCandidate(candidate: Candidate)
}

// WebRTC signaling over the already-joined meeting WebSocket.
class WsMeetingSignaling(
    private val ws: KtorWsDataSource,
    private val json: Json,
) : MeetingSignaling {

    override val incoming: Flow<MeetingSignal>
        get() = ws.events.mapNotNull { event ->
            when (event.type) {
                OFFER -> MeetingSignal.Offer(json.decodeFromJsonElement<SdpDto>(event.data).sdp)
                ANSWER -> MeetingSignal.Answer(json.decodeFromJsonElement<SdpDto>(event.data).sdp)
                ICE_CANDIDATE -> {
                    val dto = json.decodeFromJsonElement<IceCandidateDto>(event.data)
                    MeetingSignal.IceCandidate(
                        Candidate(
                            sdpMid = dto.sdpMid,
                            sdpMLineIndex = dto.sdpMLineIndex,
                            sdpCandidate = dto.candidate
                        )
                    )
                }
                ERROR -> {
                    val dto = json.decodeFromJsonElement<ErrorDto>(event.data)
                    MeetingSignal.Failure("${dto.code}: ${dto.message}")
                }
                else -> null
            }
        }

    override suspend fun sendOffer(description: RtcSessionDescription) =
        send(OFFER, SdpDto(type = "offer", sdp = description.sdp))

    override suspend fun sendAnswer(description: RtcSessionDescription) =
        send(ANSWER, SdpDto(type = "answer", sdp = description.sdp))

    override suspend fun sendIceCandidate(candidate: Candidate) =
        send(
            ICE_CANDIDATE,
            IceCandidateDto(
                candidate = candidate.sdpCandidate,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        )

    private suspend inline fun <reified T> send(type: String, data: T) {
        ws.send(MeetEventDto(type = type, data = json.encodeToJsonElement(data)))
    }

    private companion object {
        const val OFFER = "webrtc.offer"
        const val ANSWER = "webrtc.answer"
        const val ICE_CANDIDATE = "webrtc.ice_candidate"
        const val ERROR = "error"
    }
}
