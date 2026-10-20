package com.laarasoft.frontend.presentation.componenet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    fit: Boolean,
) {
    val context = LocalContext.current
    // Width / height of the incoming frames, after rotation; 0 until the first frame.
    var aspectRatio by remember { mutableFloatStateOf(0f) }
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(AndroidEgl.base.eglBaseContext, object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() = Unit
                override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                    val rotated = rotation % 180 != 0
                    val w = if (rotated) height else width
                    val h = if (rotated) width else height
                    post { aspectRatio = if (h > 0) w.toFloat() / h else 0f }
                }
            })
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

    // SurfaceViewRenderer always fills the size it is given, so to show the
    // whole frame (shared screens) the view itself gets the frame's shape.
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { renderer },
            update = { it.setMirror(mirror) },
            modifier = if (fit && aspectRatio > 0f) Modifier.aspectRatio(aspectRatio) else Modifier.fillMaxSize(),
        )
    }
}
