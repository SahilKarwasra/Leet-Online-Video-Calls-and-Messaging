package com.laarasoft.frontend.presentation

data class HomeState(
    val meetCode: String = "",
    val isJoinDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)