package com.laarasoft.frontend.presentation

sealed interface HomeAction {
    data object OpenCreateDialog : HomeAction
    data object DismissCreateDialog : HomeAction
    data object CreateMeeting : HomeAction

    data object OpenJoinDialog : HomeAction
    data object DismissJoinDialog : HomeAction
    data class JoinMeeting(val code: String = "", val name: String = "") : HomeAction

    data class OnUserNameChange(val name: String) : HomeAction
    data class OnMeetCodeChange(val code: String) : HomeAction
    data object ClearError : HomeAction
}