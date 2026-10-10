package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laarasoft.frontend.domain.models.Participant
import com.laarasoft.frontend.domain.models.MeetEvents as DomainMeetEvents
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import com.laarasoft.frontend.presentation.MeetEvents as UiMeetEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MeetViewModel(
    private val wsRepository: WebSocketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        MeetState(
            participants = listOf(
                Participant(
                    id = "LOCAL_ID",
                    name = "You",
                    isLocal = true,
                    isMuted = false,
                    isVideoOn = true,
                )
            )
        )
    )
    val state = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = _state.value
        )

    private val _events = Channel<UiMeetEvents>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeWebSocketEvents()
        startTimer()
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
                        participants = state.participants.filterNot { it.id == event.participant.id }
                    )
                }
            }

            is DomainMeetEvents.Failure -> {}
            is DomainMeetEvents.Unknown -> {}
        }
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
            is MeetAction.LeaveMeeting -> leaveMeeting()

            is MeetAction.ToggleMic -> {
                _state.update { state ->
                    val newMic = !state.isMicOn
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
                _state.update { state ->
                    val newHand = !state.isHandRaised
                    state.copy(
                        isHandRaised = newHand,
                        participants = state.participants.map {
                            if (it.isLocal) it.copy(isHandRaised = newHand) else it
                        }
                    )
                }
            }

            is MeetAction.SendReaction -> {
                val reaction = FloatingReaction(
                    id = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    emoji = action.emoji
                )
                _state.update { it.copy(reactions = it.reactions + reaction) }
            }

            is MeetAction.DismissReaction -> {
                _state.update { it.copy(reactions = it.reactions.filterNot { r -> r.id == action.id }) }
            }

            is MeetAction.SendMessage -> {
                if (action.text.isNotBlank()) {
                    val localName = _state.value.participants.find { it.isLocal }?.name ?: "You"
                    val msg = ChatMessage(
                        id = kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                        sender = localName,
                        text = action.text.trim(),
                        isMine = true
                    )
                    _state.update { it.copy(messages = it.messages + msg) }
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

    fun leaveMeeting() {
        viewModelScope.launch {
            try {
                wsRepository.leave()
            } catch (_: Exception) {}
            _events.send(UiMeetEvents.NavigateBack)
        }
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                wsRepository.leave()
            } catch (_: Exception) {}
        }
    }
}