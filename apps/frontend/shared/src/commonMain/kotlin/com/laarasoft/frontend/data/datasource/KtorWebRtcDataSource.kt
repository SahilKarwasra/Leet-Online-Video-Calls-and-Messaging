package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.RemoteTrack
import com.laarasoft.frontend.domain.models.RtcConnectionState
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import io.ktor.client.webrtc.TrackEvent
import io.ktor.client.webrtc.WebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.WebRtcPeerConnection
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalKtorApi::class)
class KtorWebRtcDataSource(
    private val client: WebRtcClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var peerConnection: WebRtcPeerConnection? = null
    private var audioTrack: WebRtcMedia.AudioTrack? = null
    private var videoTrack: WebRtcMedia.VideoTrack? = null

    private val _events = MutableSharedFlow<RtcEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()
    private val _localCandidates = MutableSharedFlow<Candidate>(extraBufferCapacity = 32)

    val localIceCandidates = _localCandidates.asSharedFlow()


    suspend fun createConnection() {
        if (peerConnection != null) return
        val pc = client.createPeerConnection()
        peerConnection = pc


        scope.launch {
            pc.iceCandidates.collect { candidate ->
                _localCandidates.tryEmit(
                    Candidate(
                        sdpCandidate = candidate.candidate,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            }
        }

        scope.launch {
            pc.trackEvents.collect {
                when (it) {
                    is TrackEvent.Add -> {
                        _events.emit(
                            RtcEvent.RemoteTrackAdded(
                                RemoteTrack(
                                    participantId = null,
                                    trackId = it.track.id,
                                    kind = "unknown"
                                )
                            )
                        )
                    }

                    is TrackEvent.Remove -> {
                        _events.emit(
                            RtcEvent.RemoteTrackRemoved(
                                it.track.id
                            )
                        )
                    }
                }
            }
        }
    }

    private fun requireConnection(): WebRtcPeerConnection =
        checkNotNull(peerConnection) {
            "Peer connection has not been created"
        }

    suspend fun startLocalMedia() {
        val pc = requireConnection()

        if (audioTrack == null) {
            audioTrack = client.createAudioTrack {
                echoCancellation = true
            }
            pc.addTrack(audioTrack!!)
        }

        if (videoTrack == null) {
            videoTrack = client.createVideoTrack {
                width = 1280
                height = 720
            }
            pc.addTrack(videoTrack!!)
        }
    }

    suspend fun createOffer(): RtcSessionDescription {
        val pc = requireConnection()
        val offer = pc.createOffer()
        pc.setLocalDescription(offer)

        return RtcSessionDescription(
            type = "offer",
            sdp = offer.sdp
        )
    }

    suspend fun createAnswer(remoteSdp: String): RtcSessionDescription {
        val pc = requireConnection()
        pc.setRemoteDescription(
            WebRtc.SessionDescription(
                type = WebRtc.SessionDescriptionType.ANSWER,
                sdp = remoteSdp
            )
        )
        val answer = pc.createAnswer()
        pc.setLocalDescription(answer)
        return RtcSessionDescription(
            type = "answer",
            sdp = answer.sdp
        )
    }

    suspend fun setRemoteAnswer(sdp: String) {
        requireConnection().setRemoteDescription(
            WebRtc.SessionDescription(
                WebRtc.SessionDescriptionType.ANSWER,
                sdp
            )
        )
    }

    suspend fun addIceCandidate(candidate: Candidate) {
        requireConnection().addIceCandidate(
            WebRtc.IceCandidate(
                candidate.sdpCandidate,
                candidate.sdpMid!!,
                candidate.sdpMLineIndex!!
            )
        )
    }

//    fun setMicrophoneEnabled(enabled: Boolean) {
//        audioTrack?.enabled = enabled
//    }
//
//    fun setCameraEnabled(enabled: Boolean) {
//        videoTrack?.enabled = enabled
//    }

    suspend fun close() {
        peerConnection?.close()
        peerConnection = null

        audioTrack?.close()
        audioTrack = null

        videoTrack?.close()
        videoTrack = null

        _events.emit(
            RtcEvent.ConnectionStateChanged(RtcConnectionState.CLOSED)
        )
    }

    fun dispose() {
        scope.cancel()
        client.close()
    }

}