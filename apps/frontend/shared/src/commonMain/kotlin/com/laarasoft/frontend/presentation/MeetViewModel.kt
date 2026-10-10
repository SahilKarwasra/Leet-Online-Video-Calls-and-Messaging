package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetViewModel(
    private val wsRepository: WebSocketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MeetState())
    val state = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeetState()
        )

    private val _events = Channel<MeetEvents>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: MeetAction) {
        when (action) {
            is MeetAction.LeaveMeeting -> leaveMeeting()
        }
    }

    fun leaveMeeting() {
        viewModelScope.launch {
            try {
                wsRepository.leave()
            } catch (_: Exception) {}
            _events.send(MeetEvents.NavigateBack)
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