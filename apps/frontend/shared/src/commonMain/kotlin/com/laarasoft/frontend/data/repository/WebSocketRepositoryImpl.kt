package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.data.mapper.MeetingEventMapper
import com.laarasoft.frontend.domain.datasource.KtorWsDataSource
import com.laarasoft.frontend.domain.models.MeetEvents
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WebSocketRepositoryImpl(
    private val dataSource: KtorWsDataSource,
    private val mapper: MeetingEventMapper,
): WebSocketRepository {
    override suspend fun connect(meetingId: String, name: String) {
        dataSource.connect(meetingId, name)
    }

    override suspend fun observeEvents(): Flow<MeetEvents> {
        return dataSource.events.map(mapper::map)
    }

    override suspend fun leave() {
        dataSource.leave()
    }
}