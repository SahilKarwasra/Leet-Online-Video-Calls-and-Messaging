package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laarasoft.frontend.config.network.onError
import com.laarasoft.frontend.config.network.onSuccess
import com.laarasoft.frontend.config.network.sendSnackbarOnError
import com.laarasoft.frontend.domain.repository.HomeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeState()
        )

    private val _events = Channel<HomeEvents>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.CreateMeeting -> createNewMeet()
            is HomeAction.OpenJoinDialog -> {
                _state.update { it.copy(isJoinDialogOpen = true, errorMessage = null) }
            }
            is HomeAction.DismissJoinDialog -> {
                _state.update { it.copy(isJoinDialogOpen = false, errorMessage = null) }
            }
            is HomeAction.OnMeetCodeChange -> {
                _state.update { it.copy(meetCode = action.code, errorMessage = null) }
            }
            is HomeAction.JoinMeeting -> joinMeeting(action.code)
            is HomeAction.ClearError -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun joinMeeting(code: String) {

    }

    private fun createNewMeet() {
        viewModelScope.launch {
            repository.createMeet()
                .sendSnackbarOnError()
                .onSuccess {
                    _events.send(HomeEvents.NavigateToMeet(it.meetId))
                }
                .onError { error ->
                    _state.update { it.copy(errorMessage = error.message) }
                }
        }
    }
}