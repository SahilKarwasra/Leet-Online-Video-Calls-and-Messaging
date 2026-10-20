package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import com.laarasoft.frontend.domain.datasource.WebRTCDataSource
import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RemoteTrack
import com.laarasoft.frontend.domain.models.RtcConnectionState
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import io.ktor.client.webrtc.TrackEvent
import io.ktor.client.webrtc.WebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.WebRtcPeerConnection
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class)
class KtorWebRtcDataSource(
    private val client: WebRtcClient,
    private val screenCapturer: ScreenCapturer,
) : WebRTCDataSource {

    private val _events = MutableSharedFlow<RtcEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val _localCandidates = MutableSharedFlow<Candidate>(extraBufferCapacity = 256)
    override val localIceCandidates = _localCandidates.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var peerConnection: WebRtcPeerConnection? = null
    private var audioTrack: WebRtcMedia.AudioTrack? = null

    private var videoSender: WebRtc.RtpSender? = null
    private var cameraTrack: WebRtcMedia.VideoTrack? = null
    private var screenTrack: WebRtcMedia.VideoTrack? = null
    private var cameraWanted = true
    private val videoMutex = Mutex()

    // Remote candidates can arrive before the remote description is applied.
    private val pendingCandidates = mutableListOf<Candidate>()
    private var awaitingAnswer = false

    override suspend fun open(): LocalMedia {
        check(peerConnection == null) { "A call is already in progress" }

        val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = callScope

        val pc = client.createPeerConnection {
            iceServers = listOf(WebRtc.IceServer(urls = listOf("stun:stun.l.google.com:19302")))
            bundlePolicy = WebRtc.BundlePolicy.MAX_BUNDLE
            iceCandidatesReplay = 100
            remoteTracksReplay = 50
        }
        peerConnection = pc
        callScope.launch(start = CoroutineStart.UNDISPATCHED) {
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

        callScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pc.state.collect { state ->
                _events.emit(RtcEvent.ConnectionStateChanged(state.toDomain()))
            }
        }

        callScope.launch(start = CoroutineStart.UNDISPATCHED) {
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

        cameraTrack = createCameraTrack()
        cameraTrack?.let { videoSender = pc.addTrack(it) }

        return LocalMedia(audioTrack = audioTrack, videoTrack = cameraTrack)
    }

    override suspend fun createOffer(iceRestart: Boolean): RtcSessionDescription {
        val pc = requireConnection()
        if (iceRestart) pc.restartIce()
        val offer = pc.createOffer()
        pc.setLocalDescription(offer)
        awaitingAnswer = true
        return RtcSessionDescription(type = "offer", sdp = offer.sdp)
    }

    override suspend fun createAnswer(remoteOfferSdp: String): RtcSessionDescription? {
        val pc = requireConnection()
        if (awaitingAnswer) {
            platformHttpLogger.log("[RTC] Ignoring server offer while our own offer is pending")
            return null
        }
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
        awaitingAnswer = false
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

    override suspend fun setCameraEnabled(enabled: Boolean): Boolean = videoMutex.withLock {
        cameraWanted = enabled

        if (screenTrack != null) return false
        if (enabled) startCamera() else stopCamera()
    }

    override suspend fun startScreenShare(grant: ScreenCaptureGrant): Boolean = videoMutex.withLock {
        val track = screenCapturer.createTrack(grant) {
            scope?.launch {
                if (stopScreenShareInternal()) _events.emit(RtcEvent.ScreenShareEnded)
            }
        }
        val previous = screenTrack
        val camera = cameraTrack
        val renegotiate = publishVideo(track)
        screenTrack = track
        cameraTrack = null
        _events.emit(RtcEvent.LocalVideoChanged(track))
        previous?.closeQuietly()
        camera?.closeQuietly()
        renegotiate
    }

    override suspend fun stopScreenShare() {
        stopScreenShareInternal()
    }

    private suspend fun stopScreenShareInternal(): Boolean = videoMutex.withLock {
        val track = screenTrack ?: return false
        screenTrack = null
        if (cameraWanted) {
            // The sender already exists, so this never needs renegotiation.
            startCamera()
        }
        if (cameraTrack == null) {
            videoSender?.replaceTrack(null)
            _events.emit(RtcEvent.LocalVideoChanged(null))
        }
        track.closeQuietly()
        true
    }

    private suspend fun startCamera(): Boolean {
        if (cameraTrack != null) return false
        val track = createCameraTrack() ?: return false
        val renegotiate = publishVideo(track)
        cameraTrack = track
        _events.emit(RtcEvent.LocalVideoChanged(track))
        return renegotiate
    }

    private suspend fun stopCamera(): Boolean {
        val track = cameraTrack ?: return false
        cameraTrack = null
        videoSender?.replaceTrack(null)
        _events.emit(RtcEvent.LocalVideoChanged(null))
        track.closeQuietly()
        return false
    }

    private suspend fun publishVideo(track: WebRtcMedia.VideoTrack): Boolean {
        val sender = videoSender
        if (sender != null) {
            sender.replaceTrack(track)
            return false
        }
        videoSender = requireConnection().addTrack(track)
        return true
    }

    override suspend fun close() {
        scope?.cancel()
        scope = null

        peerConnection?.close()
        peerConnection = null
        videoSender = null

        audioTrack?.closeQuietly()
        audioTrack = null

        cameraTrack?.closeQuietly()
        cameraTrack = null

        screenTrack?.closeQuietly()
        screenTrack = null

        cameraWanted = true
        awaitingAnswer = false
        pendingCandidates.clear()
        _events.emit(RtcEvent.ConnectionStateChanged(RtcConnectionState.CLOSED))
    }

    private suspend fun createCameraTrack(): WebRtcMedia.VideoTrack? =
        createTrackOrNull("camera") {
            withTimeoutOrNull(CAMERA_START_TIMEOUT) {
                client.createVideoTrack {
                    width = 640
                    height = 480
                    frameRate = 24
                }
            } ?: error("camera did not start in $CAMERA_START_TIMEOUT")
        }

    private suspend fun flushPendingCandidates() {
        val pc = requireConnection()
        val pending = pendingCandidates.toList()
        pendingCandidates.clear()
        pending.forEach { pc.addIceCandidate(it.toKtor()) }
    }

    private fun WebRtcMedia.Track.closeQuietly() {
        try {
            close()
        } catch (e: Throwable) {
            platformHttpLogger.log("[RTC] Could not release $kind track: ${e.message}")
        }
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

    private companion object {
        val CAMERA_START_TIMEOUT = 10.seconds
    }
}
