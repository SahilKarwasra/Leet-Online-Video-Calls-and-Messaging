package com.laarasoft.frontend.presentation

sealed interface HomeAction {
    data object CreateMeeting : HomeAction
    data object OpenJoinDialog : HomeAction
    data object DismissJoinDialog : HomeAction
    data class OnMeetCodeChange(val code: String) : HomeAction
    data class JoinMeeting(val code: String) : HomeAction
    data object ClearError : HomeAction
}