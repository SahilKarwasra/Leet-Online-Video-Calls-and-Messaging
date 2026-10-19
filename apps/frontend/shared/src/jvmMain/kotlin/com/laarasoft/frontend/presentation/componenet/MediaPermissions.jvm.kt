package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// Desktop has no runtime permission API; the OS prompts when the camera/mic is first opened.
@Composable
actual fun RequestMediaPermissions(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { onFinished() }
}
