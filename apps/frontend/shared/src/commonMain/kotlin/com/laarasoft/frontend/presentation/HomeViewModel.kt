package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laarasoft.frontend.config.network.onError
import com.laarasoft.frontend.config.network.onSuccess
import com.laarasoft.frontend.config.network.sendSnackbarOnError
import com.laarasoft.frontend.domain.repository.HomeRepository
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository,
    private val wsRepository: WebSocketRepository
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
            is HomeAction.OpenCreateDialog -> {
                _state.update { it.copy(isCreateDialogOpen = true, errorMessage = null) }
            }
            is HomeAction.DismissCreateDialog -> {
                _state.update { it.copy(isCreateDialogOpen = false, errorMessage = null) }
            }
            is HomeAction.CreateMeeting -> {
                val name = _state.value.userName.trim()
                if (name.isBlank()) {
                    _state.update { it.copy(isCreateDialogOpen = true) }
                } else {
                    createNewMeet(name)
                }
            }
            is HomeAction.OpenJoinDialog -> {
                _state.update { it.copy(isJoinDialogOpen = true, errorMessage = null) }
            }
            is HomeAction.DismissJoinDialog -> {
                _state.update { it.copy(isJoinDialogOpen = false, errorMessage = null) }
            }
            is HomeAction.OnUserNameChange -> {
                _state.update { it.copy(userName = action.name, errorMessage = null) }
            }
            is HomeAction.OnMeetCodeChange -> {
                _state.update { it.copy(meetCode = action.code, errorMessage = null) }
            }
            is HomeAction.JoinMeeting -> {
                val code = action.code.ifBlank { _state.value.meetCode }.trim()
                val name = action.name.ifBlank { _state.value.userName }.trim()
                if (name.isBlank() || code.isBlank()) {
                    _state.update {
                        it.copy(
                            isJoinDialogOpen = true,
                            meetCode = code.ifBlank { it.meetCode },
                            userName = name.ifBlank { it.userName }
                        )
                    }
                } else {
                    joinMeeting(code, name)
                }
            }
            is HomeAction.ClearError -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun joinMeeting(code: String, name: String) {
        val cleanCode = if (code.contains("/")) code.substringAfterLast("/").trim() else code.trim()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                wsRepository.connect(cleanCode, name)
                _state.update { it.copy(isLoading = false, isJoinDialogOpen = false, errorMessage = null) }
                _events.send(HomeEvents.NavigateToMeet(cleanCode))
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to join meeting"
                    )
                }
            }
        }
    }

    private fun createNewMeet(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.createMeet()
                .sendSnackbarOnError()
                .onSuccess {
                    try {
                        wsRepository.connect(it.meetId, name)
                        _state.update { state ->
                            state.copy(isLoading = false, isCreateDialogOpen = false, errorMessage = null)
                        }
                        _events.send(HomeEvents.NavigateToMeet(it.meetId))
                    } catch (e: Exception) {
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Failed to connect to meeting"
                            )
                        }
                    }
                }
                .onError { error ->
                    _state.update { state ->
                        state.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }
}