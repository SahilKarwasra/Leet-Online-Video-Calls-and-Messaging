package com.laarasoft.frontend.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laarasoft.frontend.core.theme.LeetTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeetRoot(
    viewModel: MeetViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MeetScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun MeetScreen(
    state: MeetState,
    onAction: (MeetAction) -> Unit,
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