package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laarasoft.frontend.core.utils.ui.UiEvent
import com.laarasoft.frontend.core.utils.ui.UiEventController
import com.laarasoft.frontend.domain.models.RtcConnectionState
import com.laarasoft.frontend.domain.models.RtcEvent
import com.laarasoft.frontend.domain.models.MeetEvents as DomainMeetEvents
import com.laarasoft.frontend.domain.repository.MeetingRtcRepository
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import com.laarasoft.frontend.presentation.MeetEvents as UiMeetEvents
import io.ktor.client.webrtc.WebRtcMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MeetViewModel(
    private val wsRepository: WebSocketRepository,
    private val rtcRepository: MeetingRtcRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MeetState())
    val state = _state
        .map { it.withMediaFlags() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = _state.value
        )

    private var startCallJob: Job? = null
    private var hasLeft = false
    private var nextReactionId = 0L

    private val _events = Channel<UiMeetEvents>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeWebSocketEvents()
        observeRtcEvents()
        startTimer()
    }

    // Tile flags are derived from the actual media so they can't drift out of sync.
    private fun MeetState.withMediaFlags() = copy(
        participants = participants.map { participant ->
            if (participant.isLocal) {
                participant.copy(isMuted = !isMicOn, isVideoOn = isCameraOn && localVideoTrack != null)
            } else {
                participant.copy(isVideoOn = remoteVideoTracks.containsKey(participant.id))
            }
        }
    )

    private fun startCall() {
        if (startCallJob != null || hasLeft) return
        startCallJob = viewModelScope.launch {
            try {
                val local = rtcRepository.startCall()
                rtcRepository.setMicrophoneEnabled(_state.value.isMicOn)
                rtcRepository.setCameraEnabled(_state.value.isCameraOn)
                _state.update {
                    it.copy(
                        localVideoTrack = local.videoTrack,
                        isMicOn = it.isMicOn && local.audioTrack != null,
                        isCameraOn = it.isCameraOn && local.videoTrack != null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiEventController.send(UiEvent.Snackbar(e.message ?: "Could not start the call"))
            }
        }
    }

    private fun observeRtcEvents() {
        viewModelScope.launch {
            rtcRepository.rtcEvents.collect { event ->
                when (event) {
                    is RtcEvent.RemoteTrackAdded -> {
                        val track = event.track.track as? WebRtcMedia.VideoTrack ?: return@collect
                        _state.update {
                            it.copy(remoteVideoTracks = it.remoteVideoTracks + (event.track.participantId to track))
                        }
                    }

                    is RtcEvent.RemoteTrackRemoved -> {
                        _state.update {
                            val current = it.remoteVideoTracks[event.track.participantId]
                            if (current?.id != event.track.track.id) it
                            else it.copy(remoteVideoTracks = it.remoteVideoTracks - event.track.participantId)
                        }
                    }

                    is RtcEvent.ConnectionStateChanged -> {
                        if (event.state == RtcConnectionState.FAILED) {
                            UiEventController.send(UiEvent.Snackbar("Media connection lost"))
                        }
                    }

                    is RtcEvent.Error -> UiEventController.send(UiEvent.Snackbar(event.message))
                }
            }
        }
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            try {
                wsRepository.observeEvents().collect { event ->
                    handleWebSocketEvent(event)
                }
            } catch (_: Exception) {
                // Connection closed or errored
            }
        }
    }

    private fun handleWebSocketEvent(event: DomainMeetEvents) {
        when (event) {
            is DomainMeetEvents.Joined -> {
                _state.update { state ->
                    if (event.participant.isLocal) {
                        val updatedLocal = event.participant.copy(
                            isLocal = true,
                            isMuted = !state.isMicOn,
                            isVideoOn = state.isCameraOn,
                            isHandRaised = state.isHandRaised
                        )
                        val others = state.participants.filterNot { it.isLocal || it.id == event.participant.id }
                        state.copy(participants = listOf(updatedLocal) + others)
                    } else {
                        val alreadyPresent = state.participants.any { it.id == event.participant.id }
                        val updatedList = if (alreadyPresent) {
                            state.participants.map {
                                if (it.id == event.participant.id) event.participant.copy(isLocal = false) else it
                            }
                        } else {
                            state.participants + event.participant.copy(isLocal = false)
                        }
                        state.copy(participants = updatedList)
                    }
                }
            }

            is DomainMeetEvents.ExistingParticipants -> {
                _state.update { state ->
                    val local = state.participants.find { it.isLocal }
                    val remote = event.participants.map { it.copy(isLocal = false) }
                    val filteredRemote = if (local != null) {
                        remote.filterNot { it.id == local.id }
                    } else {
                        remote
                    }
                    val combined = if (local != null) {
                        listOf(local) + filteredRemote
                    } else {
                        filteredRemote
                    }
                    state.copy(participants = combined)
                }
            }

            is DomainMeetEvents.ParticipantLeft -> {
                _state.update { state ->
                    state.copy(
                        participants = state.participants.filterNot { it.id == event.participant.id },
                        remoteVideoTracks = state.remoteVideoTracks - event.participant.id
                    )
                }
            }

            is DomainMeetEvents.ChatMessageReceived -> {
                _state.update { state ->
                    val localId = state.participants.find { it.isLocal }?.id
                    val isMine = event.participantId == localId
                    val message = ChatMessage(
                        id = event.id,
                        sender = event.name,
                        text = event.text,
                        isMine = isMine,
                    )
                    state.copy(
                        messages = state.messages + message,
                        unreadCount = if (isMine || state.panel == MeetPanel.Chat) state.unreadCount else state.unreadCount + 1
                    )
                }
            }

            is DomainMeetEvents.ReactionReceived -> addReaction(event.emoji, event.name)

            is DomainMeetEvents.HandRaiseChanged -> {
                _state.update { state ->
                    state.copy(
                        participants = state.participants.map {
                            if (it.id == event.participantId) it.copy(isHandRaised = event.raised) else it
                        }
                    )
                }
                if (event.raised) {
                    UiEventController.trySend(UiEvent.Snackbar("${event.name} raised their hand"))
                }
            }

            is DomainMeetEvents.Failure -> {
                // WebRTC errors are reported by the RTC repository.
                if (event.code in setOf("INVALID_CHAT_MESSAGE", "INVALID_REACTION", "INVALID_HAND_RAISE")) {
                    UiEventController.trySend(UiEvent.Snackbar(event.message))
                }
            }
            is DomainMeetEvents.Unknown -> {}
        }
    }

    private fun addReaction(emoji: String, sender: String) {
        val reaction = FloatingReaction(id = nextReactionId++, emoji = emoji, sender = sender)
        _state.update { it.copy(reactions = (it.reactions + reaction).takeLast(MAX_FLOATING_REACTIONS)) }
    }

    private fun startTimer() {
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000L)
                seconds++
                val m = (seconds / 60).toString().padStart(2, '0')
                val s = (seconds % 60).toString().padStart(2, '0')
                _state.update { it.copy(elapsed = "$m:$s") }
            }
        }
    }

    fun onAction(action: MeetAction) {
        when (action) {
            is MeetAction.StartCall -> startCall()

            is MeetAction.LeaveMeeting -> leaveMeeting()

            is MeetAction.ToggleMic -> {
                _state.update { state ->
                    val newMic = !state.isMicOn
                    rtcRepository.setMicrophoneEnabled(newMic)
                    state.copy(
                        isMicOn = newMic,
                        participants = state.participants.map {
                            if (it.isLocal) it.copy(isMuted = !newMic) else it
                        }
                    )
                }
            }

            is MeetAction.ToggleCamera -> {
                _state.update { state ->
                    val newCamera = !state.isCameraOn
                    rtcRepository.setCameraEnabled(newCamera)
                    state.copy(
                        isCameraOn = newCamera,
                        participants = state.participants.map {
                            if (it.isLocal) it.copy(isVideoOn = newCamera) else it
                        }
                    )
                }
            }

            is MeetAction.ToggleScreenShare -> {
                _state.update { it.copy(isScreenSharing = !it.isScreenSharing) }
            }

            is MeetAction.ToggleHand -> {
                val newHand = !_state.value.isHandRaised
                _state.update { state ->
                    state.copy(
                        isHandRaised = newHand,
                        participants = state.participants.map {
                            if (it.isLocal) it.copy(isHandRaised = newHand) else it
                        }
                    )
                }
                viewModelScope.launch {
                    try {
                        wsRepository.sendHandRaise(newHand)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        UiEventController.send(UiEvent.Snackbar("Could not update raised hand"))
                    }
                }
            }

            is MeetAction.SendReaction -> {
                addReaction(action.emoji, sender = "You")
                viewModelScope.launch {
                    try {
                        wsRepository.sendReaction(action.emoji)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        UiEventController.send(UiEvent.Snackbar("Could not send reaction"))
                    }
                }
            }

            is MeetAction.DismissReaction -> {
                _state.update { it.copy(reactions = it.reactions.filterNot { r -> r.id == action.id }) }
            }

            // The server echoes the message back, which is when it gets added to the list.
            is MeetAction.SendMessage -> {
                val text = action.text.trim()
                if (text.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            wsRepository.sendChatMessage(text)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            UiEventController.send(UiEvent.Snackbar("Message not sent"))
                        }
                    }
                }
            }

            is MeetAction.OpenPanel -> {
                _state.update {
                    it.copy(
                        panel = action.panel,
                        unreadCount = if (action.panel == MeetPanel.Chat) 0 else it.unreadCount
                    )
                }
            }

            is MeetAction.TogglePanel -> {
                _state.update {
                    val nextPanel = if (it.panel == action.panel) MeetPanel.None else action.panel
                    it.copy(
                        panel = nextPanel,
                        unreadCount = if (nextPanel == MeetPanel.Chat) 0 else it.unreadCount
                    )
                }
            }

            is MeetAction.ClosePanel -> {
                _state.update { it.copy(panel = MeetPanel.None) }
            }
        }
    }

    private fun leaveMeeting() {
        viewModelScope.launch {
            endSession()
            _events.send(UiMeetEvents.NavigateBack)
        }
    }

    private suspend fun endSession() {
        if (hasLeft) return
        hasLeft = true
        startCallJob?.cancelAndJoin()
        try {
            rtcRepository.endCall()
        } catch (_: Exception) {}
        try {
            wsRepository.leave()
        } catch (_: Exception) {}
    }

    // Covers system back / navigation pops, which don't go through LeaveMeeting.
    override fun onCleared() {
        super.onCleared()
        val pendingStart = startCallJob
        startCallJob = null
        CoroutineScope(Dispatchers.Default).launch {
            pendingStart?.join()
            endSession()
        }
    }

    private companion object {
        const val MAX_FLOATING_REACTIONS = 30
    }
}
