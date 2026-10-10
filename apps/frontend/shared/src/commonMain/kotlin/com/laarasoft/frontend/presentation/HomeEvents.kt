package com.laarasoft.frontend.presentation

sealed interface HomeEvents {
    data class NavigateToMeet(val meetId: String) : HomeEvents
    data class ShowSnackbar(val message: String) : HomeEvents
}