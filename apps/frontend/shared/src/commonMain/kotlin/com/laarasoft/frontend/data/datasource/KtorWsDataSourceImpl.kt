package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.data.dto.MeetEventDto
import com.laarasoft.frontend.domain.datasource.KtorWsDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class KtorWsDataSourceImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val baseUrl: String,
) : KtorWsDataSource {

    private val mutableEvent = MutableSharedFlow<MeetEventDto>(extraBufferCapacity = 64)
    override val events: Flow<MeetEventDto> = mutableEvent.asSharedFlow()

    private var sessions: DefaultWebSocketSession? = null
    private var receiveJob: Job? = null
    private var connectionScope: CoroutineScope? = null

    private val lifecycleMutex = Mutex()

    override suspend fun connect(meetingId: String, name: String) {
        lifecycleMutex.withLock {
            check(sessions == null) {
                "A meeting WebSocket session is already active"
            }
            val socket = httpClient.webSocketSession {
                url("${baseUrl.trimEnd('/')}/ws/meetings/$meetingId")
            }
            sessions = socket

            try {
                val joinEvent = MeetEventDto(
                    type = "meeting.join",
                    data = buildJsonObject {
                        put("name", name)
                    }
                )
                socket.send(Frame.Text(json.encodeToString(joinEvent)))
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                connectionScope = scope

                receiveJob = scope.launch {
                    try {
                        socket.incoming
                            .consumeAsFlow()
                            .filterIsInstance<Frame.Text>()
                            .collect { frame ->
                                val event = json.decodeFromString<
                                        MeetEventDto
                                        >(frame.readText())

                                mutableEvent.emit(event)
                            }
                    } catch (e: Exception) {
                        throw e
                    }
                }

            } catch (e: Exception) {
                sessions = null
                socket.close()
                throw e
            }
        }
    }


    override suspend fun leave() {
        lifecycleMutex.withLock {
            val socket = sessions ?: return

            sessions = null

            receiveJob?.cancel()
            receiveJob = null

            connectionScope?.cancel()
            connectionScope = null

            socket.close()
        }
    }

}