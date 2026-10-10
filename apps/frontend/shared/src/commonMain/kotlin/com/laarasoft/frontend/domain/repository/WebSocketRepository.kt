package com.laarasoft.frontend.domain.repository

import com.laarasoft.frontend.domain.models.MeetEvents
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    suspend fun connect(meetingId: String, name: String)
    suspend fun observeEvents(): Flow<MeetEvents>
    suspend fun leave()
}