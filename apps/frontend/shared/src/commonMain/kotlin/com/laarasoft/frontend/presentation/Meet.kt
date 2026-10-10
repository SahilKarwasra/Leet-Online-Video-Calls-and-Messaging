package com.laarasoft.frontend.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laarasoft.frontend.core.theme.LeetTheme
import com.laarasoft.frontend.core.utils.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeetRoot(
    viewModel: MeetViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MeetEvents.NavigateBack -> onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveMeeting()
        }
    }

    MeetScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = { viewModel.onAction(MeetAction.LeaveMeeting) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetScreen(
    state: MeetState,
    onAction: (MeetAction) -> Unit,
    onNavigateBack: () -> Unit = {}
) {

}

@Preview
@Composable
private fun Preview() {
    LeetTheme {
        MeetScreen(
            state = MeetState(),
            onAction = {}
        )
    }
}