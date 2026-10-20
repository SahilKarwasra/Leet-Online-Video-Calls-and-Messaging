package com.laarasoft.frontend.config.media

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.AndroidMediaDevices
import io.ktor.client.webrtc.media.AndroidVideoTrack
import io.ktor.utils.io.ExperimentalKtorApi
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

// The result Intent of the system "Start recording or casting?" dialog.
class AndroidScreenGrant(val data: Intent) : ScreenCaptureGrant

@OptIn(ExperimentalKtorApi::class)
class AndroidScreenCapturer(
    private val context: Context,
    private val devices: AndroidMediaDevices,
) : ScreenCapturer {

    override suspend fun createTrack(grant: ScreenCaptureGrant, onEnded: () -> Unit): WebRtcMedia.VideoTrack {
        require(grant is AndroidScreenGrant) { "Unexpected screen capture grant $grant" }
        CallForegroundService.startScreenShare(context)

        val factory = devices.peerConnectionFactory
        val source = factory.createVideoSource(/* isScreencast = */ true)
        val helper = SurfaceTextureHelper.create("ScreenCapture", AndroidEgl.base.eglBaseContext)
        val capturer = ScreenCapturerAndroid(grant.data, object : MediaProjection.Callback() {
            // Also called when the user taps "Stop sharing" in the system UI.
            override fun onStop() = onEnded()
        })
        try {
            capturer.initialize(helper, context, source.capturerObserver)
            val (width, height) = captureSize()
            capturer.startCapture(width, height, FRAME_RATE)
        } catch (e: Exception) {
            capturer.dispose()
            helper.dispose()
            source.dispose()
            CallForegroundService.stopScreenShare(context)
            throw e
        }

        val track = factory.createVideoTrack("android-screen-${UUID.randomUUID()}", source)
        return AndroidVideoTrack(track) {
            capturer.stopCapture()
            capturer.dispose()
            helper.dispose()
            source.dispose()
            CallForegroundService.stopScreenShare(context)
        }
    }

    // The display size, scaled so the long side is at most MAX_SIDE pixels.
    private fun captureSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val scale = MAX_SIDE.toFloat() / max(metrics.widthPixels, metrics.heightPixels)
        val factor = if (scale < 1f) scale else 1f
        // Encoders want even dimensions.
        fun even(value: Int) = ((value * factor).roundToInt() / 2) * 2
        return even(metrics.widthPixels) to even(metrics.heightPixels)
    }

    private companion object {
        const val FRAME_RATE = 15
        const val MAX_SIDE = 1280
    }
}
