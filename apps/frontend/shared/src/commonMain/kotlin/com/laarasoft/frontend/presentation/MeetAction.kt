package com.laarasoft.frontend.presentation

sealed interface MeetAction {
    data object LeaveMeeting : MeetAction
}