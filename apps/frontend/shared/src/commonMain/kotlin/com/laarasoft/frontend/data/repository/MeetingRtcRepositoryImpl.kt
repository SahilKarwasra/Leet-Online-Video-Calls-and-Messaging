package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.config.media.CallAudioSession
import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.data.datasource.MeetingSignal
import com.laarasoft.frontend.data.datasource.MeetingSignaling
import com.laarasoft.frontend.domain.datasource.WebRTCDataSource
import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.repository.MeetingRtcRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// The server is an SFU: we send one offer with our tracks, it answers, and whenever
// other participants publish/unpublish tracks it sends us a new offer to answer.
class MeetingRtcRepositoryImpl(
    private val rtc: WebRTCDataSource,
    private val signaling: MeetingSignaling,
    private val audioSession: CallAudioSession,
) : MeetingRtcRepository {

    private var callScope: CoroutineScope? = null

    override val rtcEvents: Flow<RtcEvent> = rtc.events

    override suspend fun startCall(): LocalMedia {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        callScope = scope
        // The server rejects candidates that arrive before our first offer.
        val offerSent = CompletableDeferred<Unit>()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            signaling.incoming.collect { signal ->
                try {
                    handleSignal(signal)
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

        audioSession.start()
        val local = rtc.open()
        signaling.sendOffer(rtc.createOffer())
        offerSent.complete(Unit)
        return local
    }

    private suspend fun handleSignal(signal: MeetingSignal) {
        when (signal) {
            is MeetingSignal.Offer -> signaling.sendAnswer(rtc.createAnswer(signal.sdp))
            is MeetingSignal.Answer -> rtc.setRemoteAnswer(signal.sdp)
            is MeetingSignal.IceCandidate -> rtc.addIceCandidate(signal.candidate)
            is MeetingSignal.Failure -> platformHttpLogger.log("[RTC] Server error ${signal.message}")
        }
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        rtc.setMicrophoneEnabled(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        rtc.setCameraEnabled(enabled)
    }

    override suspend fun endCall() {
        callScope?.cancel()
        callScope = null
        rtc.close()
        audioSession.stop()
    }
}
