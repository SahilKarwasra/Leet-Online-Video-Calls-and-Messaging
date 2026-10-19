package com.laarasoft.frontend.domain.repository

import com.laarasoft.frontend.domain.models.LocalMedia
import com.laarasoft.frontend.domain.models.RtcEvent
import kotlinx.coroutines.flow.Flow

interface MeetingRtcRepository {
    val rtcEvents: Flow<RtcEvent>

    // Requires an already joined meeting WebSocket (see WebSocketRepository.connect).
    suspend fun startCall(): LocalMedia
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    suspend fun endCall()
}
