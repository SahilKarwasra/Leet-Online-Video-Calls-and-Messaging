package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaType
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun RequestMediaPermissions(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        requestAccess(AVMediaTypeVideo)
        requestAccess(AVMediaTypeAudio)
        onFinished()
    }
}

private suspend fun requestAccess(type: AVMediaType?): Boolean = suspendCoroutine { continuation ->
    AVCaptureDevice.requestAccessForMediaType(type) { granted -> continuation.resume(granted) }
}
