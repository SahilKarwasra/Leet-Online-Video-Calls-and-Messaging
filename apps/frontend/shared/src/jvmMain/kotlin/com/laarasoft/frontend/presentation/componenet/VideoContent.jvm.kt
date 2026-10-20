package com.laarasoft.frontend.presentation.componenet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoTrackSink
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.getNative
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage

// webrtc-java has no view, so frames are converted to bitmaps and drawn by Compose.
@Composable
actual fun VideoContent(
    track: WebRtcMedia.VideoTrack,
    mirror: Boolean,
    modifier: Modifier,
    fit: Boolean,
) {
    var bitmap by remember(track) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(track) {
        val native = track.getNative()
        var pixels = ByteArray(0)
        val sink = VideoTrackSink { frame ->
            val width = frame.buffer.width
            val height = frame.buffer.height
            val size = width * height * 4
            if (pixels.size != size) pixels = ByteArray(size)
            runCatching {
                // libyuv "ARGB" is B,G,R,A in memory, which is Skia's BGRA_8888.
                VideoBufferConverter.convertFromI420(frame.buffer, pixels, FourCC.ARGB)
                val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                bitmap = SkiaImage.makeRaster(info, pixels, width * 4).toComposeImageBitmap()
            }
        }
        native.addSink(sink)
        onDispose { runCatching { native.removeSink(sink) } }
    }

    Box(modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.matchParentSize().scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f),
            )
        }
    }
}
