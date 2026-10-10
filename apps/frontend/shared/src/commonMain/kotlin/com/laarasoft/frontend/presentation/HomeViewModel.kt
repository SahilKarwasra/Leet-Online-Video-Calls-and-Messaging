package com.laarasoft.frontend.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class HomeViewModel : ViewModel() {

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
            is HomeAction.CreateMeeting -> createNewMeeting()
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

    private fun createNewMeeting() {
        val newMeetId = generateMeetingCode()
        viewModelScope.launch {
            _events.send(HomeEvents.NavigateToMeet(newMeetId))
        }
    }

    private fun joinMeeting(code: String) {
        val trimmed = code.trim().filter { it.isLetterOrDigit() || it == '-' }
        if (trimmed.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter a valid meeting code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isJoinDialogOpen = false, meetCode = "") }
            _events.send(HomeEvents.NavigateToMeet(trimmed))
        }
    }

    private fun generateMeetingCode(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        fun randomSegment(length: Int) = (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
        return "${randomSegment(3)}-${randomSegment(4)}-${randomSegment(3)}"
    }
}