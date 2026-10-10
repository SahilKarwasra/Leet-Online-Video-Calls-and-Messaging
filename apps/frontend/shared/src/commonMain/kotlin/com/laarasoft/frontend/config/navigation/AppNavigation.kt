package com.laarasoft.frontend.config.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.laarasoft.frontend.core.utils.ObserveAsEvents
import com.laarasoft.frontend.core.utils.ui.AppDialog
import com.laarasoft.frontend.core.utils.ui.UiEvent
import com.laarasoft.frontend.core.utils.ui.UiEventController
import com.laarasoft.frontend.presentation.HomeRoot
import com.laarasoft.frontend.presentation.HomeScreen
import com.laarasoft.frontend.presentation.MeetRoot
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Composable
fun AppNavigation() {

    val appController: LeetController = rememberLeetController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialogEvent by remember { mutableStateOf<UiEvent.Dialog?>(null) }

    ObserveAsEvents(UiEventController.events) { uiEvent ->
        when (uiEvent) {
            is UiEvent.Dialog -> {
                dialogEvent = uiEvent
            }

            is UiEvent.Snackbar -> {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = uiEvent.message,
                        actionLabel = uiEvent.actionLabel,
                        withDismissAction = uiEvent.withDismissAction,
                        duration = uiEvent.duration
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        uiEvent.onAction?.invoke()
                    }
                }
            }

            else -> {}
        }
    }
    dialogEvent?.let { dialog ->
        AppDialog(
            event = dialog,
            onDismiss = { dialogEvent = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) {
                Snackbar(
                    snackbarData = it,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        NavHost(
            navController = appController.navController,
            startDestination = AppDestination.HomeScreen
        ) {
            composable<AppDestination.HomeScreen> {
                HomeRoot(
                    onNavigateToMeet = { meetId ->
                        appController.navigate(AppDestination.MeetScreen(meetId))
                    }
                )
            }
            composable<AppDestination.MeetScreen> {
                val meetId = it.toRoute<AppDestination.MeetScreen>().meetId
                MeetRoot()
            }
        }
    }
}

@Serializable
sealed interface AppDestination {
    @Serializable
    data object HomeScreen : AppDestination

    @Serializable
    data class MeetScreen(val meetId: String) : AppDestination
}