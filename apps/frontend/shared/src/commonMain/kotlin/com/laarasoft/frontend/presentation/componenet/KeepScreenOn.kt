package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable

// Keeps the display awake while it is in the composition (i.e. during a meeting).
@Composable
expect fun KeepScreenOn()

// Phones lose the camera in the background, so the meeting pauses it there
// and resumes it on return. Desktop windows keep the camera when minimized.
expect val PausesCameraInBackground: Boolean
