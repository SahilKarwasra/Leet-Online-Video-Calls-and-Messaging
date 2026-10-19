package com.laarasoft.frontend.presentation

sealed interface MeetAction {
    data object StartCall : MeetAction
    data object LeaveMeeting : MeetAction
    data object ToggleMic : MeetAction
    data object ToggleCamera : MeetAction
    data object ToggleScreenShare : MeetAction
    data object ToggleHand : MeetAction
    data class SendReaction(val emoji: String) : MeetAction
    data class SendMessage(val text: String) : MeetAction
    data class DismissReaction(val id: Long) : MeetAction
    data class OpenPanel(val panel: MeetPanel) : MeetAction
    data class TogglePanel(val panel: MeetPanel) : MeetAction
    data object ClosePanel : MeetAction
}