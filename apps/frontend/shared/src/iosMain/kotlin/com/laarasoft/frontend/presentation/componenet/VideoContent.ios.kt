package com.laarasoft.frontend.presentation.componenet

import WebRTC.RTCMTLVideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.getNative
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIViewContentMode

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoContent(
    track: WebRtcMedia.VideoTrack,
    mirror: Boolean,
    modifier: Modifier,
    fit: Boolean,
) {
    val view = remember {
        RTCMTLVideoView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            clipsToBounds = true
        }
    }
    DisposableEffect(track, view) {
        val native = track.getNative()
        native.addRenderer(view)
        onDispose { native.removeRenderer(view) }
    }
    UIKitView(
        factory = { view },
        update = {
            it.transform = CGAffineTransformMakeScale(if (mirror) -1.0 else 1.0, 1.0)
            it.videoContentMode = if (fit) UIViewContentMode.UIViewContentModeScaleAspectFit
            else UIViewContentMode.UIViewContentModeScaleAspectFill
        },
        modifier = modifier,
    )
}
