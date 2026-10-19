package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.domain.datasource.WebRTCDataSource
import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.LocalMedia
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
import kotlinx.coroutines.CancellationException
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
) : WebRTCDataSource {

    private val _events = MutableSharedFlow<RtcEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val _localCandidates = MutableSharedFlow<Candidate>(extraBufferCapacity = 64)
    override val localIceCandidates = _localCandidates.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var peerConnection: WebRtcPeerConnection? = null
    private var audioTrack: WebRtcMedia.AudioTrack? = null
    private var videoTrack: WebRtcMedia.VideoTrack? = null

    // Remote candidates can arrive before the remote description is applied.
    private val pendingCandidates = mutableListOf<Candidate>()

    override suspend fun open(): LocalMedia {
        check(peerConnection == null) { "A call is already in progress" }

        val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = callScope

        val pc = client.createPeerConnection {
            iceServers = listOf(WebRtc.IceServer(urls = listOf("stun:stun.l.google.com:19302")))
            bundlePolicy = WebRtc.BundlePolicy.MAX_BUNDLE
        }
        peerConnection = pc

        callScope.launch {
            pc.iceCandidates.collect { candidate ->
                if (candidate.candidate.isBlank()) return@collect
                _localCandidates.emit(
                    Candidate(
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdpCandidate = candidate.candidate
                    )
                )
            }
        }

        callScope.launch {
            pc.state.collect { state ->
                _events.emit(RtcEvent.ConnectionStateChanged(state.toDomain()))
            }
        }

        callScope.launch {
            pc.trackEvents.collect { event ->
                val remote = RemoteTrack(
                    participantId = event.track.id.substringBefore(':'),
                    track = event.track
                )
                when (event) {
                    is TrackEvent.Add -> _events.emit(RtcEvent.RemoteTrackAdded(remote))
                    is TrackEvent.Remove -> _events.emit(RtcEvent.RemoteTrackRemoved(remote))
                }
            }
        }

        audioTrack = createTrackOrNull("microphone") {
            client.createAudioTrack {
                echoCancellation = true
                noiseSuppression = true
                autoGainControl = true
            }
        }?.also { pc.addTrack(it) }

        videoTrack = createTrackOrNull("camera") {
            client.createVideoTrack {
                width = 640
                height = 480
                frameRate = 24
            }
        }?.also { pc.addTrack(it) }

        return LocalMedia(audioTrack = audioTrack, videoTrack = videoTrack)
    }

    override suspend fun createOffer(): RtcSessionDescription {
        val pc = requireConnection()
        val offer = pc.createOffer()
        pc.setLocalDescription(offer)
        return RtcSessionDescription(type = "offer", sdp = offer.sdp)
    }

    override suspend fun createAnswer(remoteOfferSdp: String): RtcSessionDescription {
        val pc = requireConnection()
        pc.setRemoteDescription(
            WebRtc.SessionDescription(WebRtc.SessionDescriptionType.OFFER, remoteOfferSdp)
        )
        flushPendingCandidates()
        val answer = pc.createAnswer()
        pc.setLocalDescription(answer)
        return RtcSessionDescription(type = "answer", sdp = answer.sdp)
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        requireConnection().setRemoteDescription(
            WebRtc.SessionDescription(WebRtc.SessionDescriptionType.ANSWER, sdp)
        )
        flushPendingCandidates()
    }

    override suspend fun addIceCandidate(candidate: Candidate) {
        val pc = requireConnection()
        if (pc.remoteDescription == null) {
            pendingCandidates += candidate
        } else {
            pc.addIceCandidate(candidate.toKtor())
        }
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.enable(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        videoTrack?.enable(enabled)
    }

    override suspend fun close() {
        scope?.cancel()
        scope = null

        peerConnection?.close()
        peerConnection = null

        audioTrack?.close()
        audioTrack = null

        videoTrack?.close()
        videoTrack = null

        pendingCandidates.clear()
        _events.emit(RtcEvent.ConnectionStateChanged(RtcConnectionState.CLOSED))
    }

    private suspend fun flushPendingCandidates() {
        val pc = requireConnection()
        val pending = pendingCandidates.toList()
        pendingCandidates.clear()
        pending.forEach { pc.addIceCandidate(it.toKtor()) }
    }

    private fun requireConnection(): WebRtcPeerConnection =
        checkNotNull(peerConnection) { "Peer connection has not been created" }

    private suspend fun <T> createTrackOrNull(name: String, create: suspend () -> T): T? =
        try {
            create()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            platformHttpLogger.log("[RTC] Could not start $name: ${e.message}")
            _events.emit(RtcEvent.Error("Could not start $name"))
            null
        }

    private fun Candidate.toKtor() = WebRtc.IceCandidate(
        candidate = sdpCandidate,
        sdpMid = sdpMid ?: "0",
        sdpMLineIndex = sdpMLineIndex ?: 0
    )

    private fun WebRtc.ConnectionState.toDomain(): RtcConnectionState = when (this) {
        WebRtc.ConnectionState.NEW -> RtcConnectionState.NEW
        WebRtc.ConnectionState.CONNECTING -> RtcConnectionState.CONNECTING
        WebRtc.ConnectionState.CONNECTED -> RtcConnectionState.CONNECTED
        WebRtc.ConnectionState.DISCONNECTED -> RtcConnectionState.DISCONNECTED
        WebRtc.ConnectionState.FAILED -> RtcConnectionState.FAILED
        WebRtc.ConnectionState.CLOSED -> RtcConnectionState.CLOSED
    }
}
