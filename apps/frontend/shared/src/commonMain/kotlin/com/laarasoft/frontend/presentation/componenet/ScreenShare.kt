package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant

interface ScreenShareLauncher {
    val isSupported: Boolean
    // Runs the platform's consent flow; [rememberScreenShareLauncher]'s
    // onGranted is called only if the user agrees.
    fun launch()
}

// Android: the MediaProjection "Start recording or casting?" dialog.
// iOS: the ReplayKit broadcast picker. Desktop: a screen/window chooser.
@Composable
expect fun rememberScreenShareLauncher(onGranted: (ScreenCaptureGrant) -> Unit): ScreenShareLauncher
