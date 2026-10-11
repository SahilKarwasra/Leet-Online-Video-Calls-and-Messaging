package com.laarasoft.frontend.data.datasource

import com.laarasoft.frontend.config.network.platformHttpLogger
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

    private var mutableEvent = MutableSharedFlow<MeetEventDto>(replay = 64, extraBufferCapacity = 64)
    override val events: Flow<MeetEventDto> get() = mutableEvent.asSharedFlow()

    private var sessions: DefaultWebSocketSession? = null
    private var receiveJob: Job? = null
    private var connectionScope: CoroutineScope? = null

    private val lifecycleMutex = Mutex()

    override suspend fun connect(meetingId: String, name: String) {
        lifecycleMutex.withLock {
            check(sessions == null) {
                "A meeting WebSocket session is already active"
            }
            // Reset the replay cache so events from a prior session aren't replayed to new subscribers
            mutableEvent = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
            val wsUrl = "${baseUrl.trimEnd('/')}/ws/meetings/$meetingId"
            platformHttpLogger.log("[WS_CONNECT] Connecting to $wsUrl with name '$name'")
            val socket = httpClient.webSocketSession {
                url(wsUrl)
            }
            sessions = socket

            try {
                val joinEvent = MeetEventDto(
                    type = "meeting.join",
                    data = buildJsonObject {
                        put("name", name)
                    }
                )
                val joinJson = json.encodeToString(joinEvent)
                platformHttpLogger.log("[WS_OUTGOING] Sending: $joinJson")
                socket.send(Frame.Text(joinJson))
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                connectionScope = scope

                receiveJob = scope.launch {
                    try {
                        socket.incoming
                            .consumeAsFlow()
                            .filterIsInstance<Frame.Text>()
                            .collect { frame ->
                                val rawText = frame.readText()
                                platformHttpLogger.log("[WS_INCOMING] Raw frame received: $rawText")
                                try {
                                    val event = json.decodeFromString<MeetEventDto>(rawText)
                                    platformHttpLogger.log("[WS_INCOMING] Parsed MeetEventDto: type='${event.type}', data=${event.data}")
                                    mutableEvent.emit(event)
                                } catch (e: Exception) {
                                    platformHttpLogger.log("[WS_INCOMING] Error parsing frame: $rawText | Error: ${e.message}")
                                }
                            }
                    } catch (e: Exception) {
                        platformHttpLogger.log("[WS_INCOMING] Receive loop error: ${e.message}")
                        throw e
                    }
                }

            } catch (e: Exception) {
                platformHttpLogger.log("[WS_CONNECT] Connection failed: ${e.message}")
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

            // Clear the replay cache so stale events don't leak into the next session
            mutableEvent = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
        }
    }

}