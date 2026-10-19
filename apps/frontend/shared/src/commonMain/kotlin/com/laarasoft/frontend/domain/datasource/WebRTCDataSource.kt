package com.laarasoft.frontend.domain.datasource

import com.laarasoft.frontend.domain.models.Candidate
import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.RtcSessionDescription
import kotlinx.coroutines.flow.Flow

interface WebRTCDataSource {
    val events: Flow<RtcEvent>
    val localIceCandidates: Flow<Candidate>

    // Creates the peer connection and captures local camera/mic.
    suspend fun open(): LocalMedia
    suspend fun createOffer(): RtcSessionDescription
    suspend fun createAnswer(remoteOfferSdp: String): RtcSessionDescription
    suspend fun setRemoteAnswer(sdp: String)
    suspend fun addIceCandidate(candidate: Candidate)
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    suspend fun close()
}
