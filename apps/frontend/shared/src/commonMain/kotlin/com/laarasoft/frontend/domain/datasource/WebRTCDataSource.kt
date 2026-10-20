package com.laarasoft.frontend.domain.datasource

import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import kotlinx.coroutines.flow.Flow

interface WebRTCDataSource {
    val events: Flow<RtcEvent>
    val localIceCandidates: Flow<Candidate>

    // Creates the peer connection and captures local camera/mic.
    suspend fun open(): LocalMedia
    suspend fun createOffer(iceRestart: Boolean = false): RtcSessionDescription
    suspend fun createAnswer(remoteOfferSdp: String): RtcSessionDescription?
    suspend fun setRemoteAnswer(sdp: String)
    suspend fun addIceCandidate(candidate: Candidate)
    fun setMicrophoneEnabled(enabled: Boolean)
    suspend fun setCameraEnabled(enabled: Boolean): Boolean
    suspend fun startScreenShare(grant: ScreenCaptureGrant): Boolean
    suspend fun stopScreenShare()
    suspend fun close()
}
