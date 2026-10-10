package com.laarasoft.frontend.presentation

data class HomeState(
    val userName: String = "",
    val meetCode: String = "",
    val isCreateDialogOpen: Boolean = false,
    val isJoinDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)