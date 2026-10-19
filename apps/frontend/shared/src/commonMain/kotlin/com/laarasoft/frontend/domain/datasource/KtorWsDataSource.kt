package com.laarasoft.frontend.domain.datasource

import com.laarasoft.frontend.data.dto.MeetEventDto
import kotlinx.coroutines.flow.Flow

interface KtorWsDataSource {
    suspend fun connect(meetingId: String, name: String)
    val events: Flow<MeetEventDto>
    suspend fun send(event: MeetEventDto)
    suspend fun leave()
}