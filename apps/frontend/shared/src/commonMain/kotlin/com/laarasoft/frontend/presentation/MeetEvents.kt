package com.laarasoft.frontend.presentation

sealed interface MeetEvents {
    data object NavigateBack : MeetEvents
}