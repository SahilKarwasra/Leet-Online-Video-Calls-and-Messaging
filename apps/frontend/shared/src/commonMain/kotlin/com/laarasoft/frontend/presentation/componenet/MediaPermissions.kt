package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable

// Asks for camera + microphone access, then calls [onFinished] whatever the user chose.
// Denied devices are simply not published (see KtorWebRtcDataSource.open).
@Composable
expect fun RequestMediaPermissions(onFinished: () -> Unit)
