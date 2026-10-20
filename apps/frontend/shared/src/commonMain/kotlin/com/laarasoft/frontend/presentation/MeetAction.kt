package com.laarasoft.frontend.presentation

import com.laarasoft.frontend.domain.models.ScreenCaptureGrant

sealed interface MeetAction {
    data object StartCall : MeetAction
    data object LeaveMeeting : MeetAction
    data object ToggleMic : MeetAction
    data object ToggleCamera : MeetAction
    data class StartScreenShare(val grant: ScreenCaptureGrant) : MeetAction
    data object StopScreenShare : MeetAction
    data object AppBackgrounded : MeetAction
    data object AppForegrounded : MeetAction
    data object ToggleHand : MeetAction
    data class SendReaction(val emoji: String) : MeetAction
    data class SendMessage(val text: String) : MeetAction
    data class DismissReaction(val id: Long) : MeetAction
    data class OpenPanel(val panel: MeetPanel) : MeetAction
    data class TogglePanel(val panel: MeetPanel) : MeetAction
    data object ClosePanel : MeetAction
}