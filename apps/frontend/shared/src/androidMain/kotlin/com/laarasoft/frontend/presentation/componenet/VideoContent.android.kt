package com.laarasoft.frontend.presentation.componenet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.laarasoft.frontend.config.media.AndroidEgl
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.getNative
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
actual fun VideoContent(
    track: WebRtcMedia.VideoTrack,
    mirror: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(AndroidEgl.base.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }
    DisposableEffect(track, renderer) {
        val native = track.getNative()
        native.addSink(renderer)
        // The track may already be disposed if the call ended first.
        onDispose { runCatching { native.removeSink(renderer) } }
    }
    AndroidView(
        factory = { renderer },
        update = { it.setMirror(mirror) },
        modifier = modifier,
    )
}
