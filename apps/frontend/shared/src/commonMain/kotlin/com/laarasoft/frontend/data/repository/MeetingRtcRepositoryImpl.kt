package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.config.media.CallAudioSession
import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.data.datasource.MeetingSignal
import com.laarasoft.frontend.data.datasource.MeetingSignaling
import com.laarasoft.frontend.domain.datasource.WebRTCDataSource
import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RtcConnectionState
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import com.laarasoft.frontend.domain.repository.MeetingRtcRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The server is an SFU: we send one offer with our tracks, it answers, and whenever
// other participants publish/unpublish tracks it sends us a new offer to answer.
// We only offer again to add a video sender we didn't have at join time, or to
// restart ICE after the connection failed.
class MeetingRtcRepositoryImpl(
    private val rtc: WebRTCDataSource,
    private val signaling: MeetingSignaling,
    private val audioSession: CallAudioSession,
) : MeetingRtcRepository {

    private var callScope: CoroutineScope? = null
    private val negotiation = Mutex()
    private var iceRestartJob: Job? = null

    override val rtcEvents: Flow<RtcEvent> = rtc.events

    override suspend fun startCall(): LocalMedia {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        callScope = scope
        // The server rejects candidates that arrive before our first offer.
        val offerSent = CompletableDeferred<Unit>()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            signaling.incoming.collect { signal ->
                try {
                    negotiation.withLock { handleSignal(signal) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    platformHttpLogger.log("[RTC] Failed to handle $signal: ${e.message}")
                }
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            rtc.localIceCandidates.collect { candidate ->
                offerSent.await()
                signaling.sendIceCandidate(candidate)
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            rtc.events.collect { event ->
                if (event is RtcEvent.ConnectionStateChanged) onConnectionState(event.state)
            }
        }

        audioSession.start()
        val local = rtc.open()
        negotiation.withLock { signaling.sendOffer(rtc.createOffer()) }
        offerSent.complete(Unit)
        return local
    }

    private suspend fun handleSignal(signal: MeetingSignal) {
        when (signal) {
            is MeetingSignal.Offer -> rtc.createAnswer(signal.sdp)?.let { signaling.sendAnswer(it) }
            is MeetingSignal.Answer -> rtc.setRemoteAnswer(signal.sdp)
            is MeetingSignal.IceCandidate -> rtc.addIceCandidate(signal.candidate)
            is MeetingSignal.Failure -> platformHttpLogger.log("[RTC] Server error ${signal.message}")
        }
    }

    // A failed connection (network switch, NAT timeout) is retried with an ICE
    // restart a few times before giving up.
    private fun onConnectionState(state: RtcConnectionState) {
        when (state) {
            RtcConnectionState.CONNECTED -> {
                iceRestartJob?.cancel()
                iceRestartJob = null
            }

            RtcConnectionState.FAILED -> {
                if (iceRestartJob?.isActive == true) return
                iceRestartJob = callScope?.launch {
                    repeat(MAX_ICE_RESTARTS) { attempt ->
                        platformHttpLogger.log("[RTC] Connection failed, ICE restart ${attempt + 1}")
                        try {
                            renegotiate(iceRestart = true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            platformHttpLogger.log("[RTC] ICE restart failed: ${e.message}")
                        }
                        delay(ICE_RESTART_WAIT_MS * (attempt + 1))
                    }
                }
            }

            else -> Unit
        }
    }

    private suspend fun renegotiate(iceRestart: Boolean = false) {
        negotiation.withLock { signaling.sendOffer(rtc.createOffer(iceRestart)) }
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        rtc.setMicrophoneEnabled(enabled)
    }

    override suspend fun setCameraEnabled(enabled: Boolean) {
        if (rtc.setCameraEnabled(enabled)) renegotiate()
    }

    override suspend fun startScreenShare(grant: ScreenCaptureGrant) {
        if (rtc.startScreenShare(grant)) renegotiate()
    }

    override suspend fun stopScreenShare() {
        rtc.stopScreenShare()
    }

    override suspend fun endCall() {
        callScope?.cancel()
        callScope = null
        iceRestartJob = null
        rtc.close()
        audioSession.stop()
    }

    private companion object {
        const val MAX_ICE_RESTARTS = 3
        const val ICE_RESTART_WAIT_MS = 5_000L
    }
}
