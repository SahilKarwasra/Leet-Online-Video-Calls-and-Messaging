package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.data.dto.MeetEventDto
import com.laarasoft.frontend.data.dto.SendChatMessageDto
import com.laarasoft.frontend.data.dto.SendHandRaiseDto
import com.laarasoft.frontend.data.dto.SendMediaStateDto
import com.laarasoft.frontend.data.dto.SendReactionDto
import com.laarasoft.frontend.data.mapper.MeetingEventMapper
import com.laarasoft.frontend.domain.datasource.KtorWsDataSource
import com.laarasoft.frontend.domain.models.MediaState
import com.laarasoft.frontend.domain.models.MeetEvents
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class WebSocketRepositoryImpl(
    private val dataSource: KtorWsDataSource,
    private val mapper: MeetingEventMapper,
    private val json: Json,
): WebSocketRepository {
    override suspend fun connect(meetingId: String, name: String) {
        dataSource.connect(meetingId, name)
    }

    override suspend fun observeEvents(): Flow<MeetEvents> {
        return dataSource.events.map(mapper::map)
    }

    override suspend fun sendChatMessage(text: String) {
        dataSource.send(
            MeetEventDto(type = "chat.message", data = json.encodeToJsonElement(SendChatMessageDto(text)))
        )
    }

    override suspend fun sendReaction(emoji: String) {
        dataSource.send(
            MeetEventDto(type = "meeting.reaction", data = json.encodeToJsonElement(SendReactionDto(emoji)))
        )
    }

    override suspend fun sendHandRaise(raised: Boolean) {
        dataSource.send(
            MeetEventDto(type = "meeting.hand_raise", data = json.encodeToJsonElement(SendHandRaiseDto(raised)))
        )
    }

    override suspend fun sendMediaState(state: MediaState) {
        dataSource.send(
            MeetEventDto(
                type = "meeting.media_state",
                data = json.encodeToJsonElement(SendMediaStateDto(state.audio, state.video, state.screen))
            )
        )
    }

    override suspend fun leave() {
        dataSource.leave()
    }
}
