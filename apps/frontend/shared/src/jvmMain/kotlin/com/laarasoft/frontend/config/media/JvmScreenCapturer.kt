package com.laarasoft.frontend.config.media

import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import dev.onvoid.webrtc.media.video.VideoDesktopSource
import dev.onvoid.webrtc.media.video.desktop.DesktopCapturer
import dev.onvoid.webrtc.media.video.desktop.DesktopSource
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer as NativeScreenCapturer
import dev.onvoid.webrtc.media.video.desktop.WindowCapturer
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.JvmMediaDevices
import io.ktor.client.webrtc.media.JvmVideoTrack
import java.util.UUID

// A screen or window the user picked in the share dialog.
data class DesktopScreenGrant(
    val sourceId: Long,
    val isWindow: Boolean,
) : ScreenCaptureGrant

data class DesktopShareSource(
    val title: String,
    val grant: DesktopScreenGrant,
)

class JvmScreenCapturer(private val devices: JvmMediaDevices) : ScreenCapturer {

    override suspend fun createTrack(grant: ScreenCaptureGrant, onEnded: () -> Unit): WebRtcMedia.VideoTrack {
        require(grant is DesktopScreenGrant) { "Unexpected screen capture grant $grant" }
        val source = VideoDesktopSource().apply {
            setSourceId(grant.sourceId, grant.isWindow)
            setFrameRate(FRAME_RATE)
            setMaxFrameSize(MAX_WIDTH, MAX_HEIGHT)
            start()
        }
        val track = devices.peerConnectionFactory.createVideoTrack("jvm-screen-${UUID.randomUUID()}", source)
        return JvmVideoTrack(track) {
            source.stop()
            // dispose() throws while the native side still holds a reference.
            runCatching { track.dispose() }
            runCatching { source.dispose() }
        }
    }

    companion object {
        private const val FRAME_RATE = 15
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1080

        // Whole screens first, then windows that have a title. The native
        // side reports a null title for some sources, so titles are nullable here.
        fun listSources(): List<DesktopShareSource> {
            val screens = listFrom(NativeScreenCapturer()).mapIndexed { index, source ->
                DesktopShareSource(
                    title = titleOf(source).ifBlank { "Screen ${index + 1}" },
                    grant = DesktopScreenGrant(source.id, isWindow = false),
                )
            }
            val windows = listFrom(WindowCapturer())
                .filter { titleOf(it).isNotBlank() }
                .map { DesktopShareSource(titleOf(it), DesktopScreenGrant(it.id, isWindow = true)) }
            return screens + windows
        }

        private fun titleOf(source: DesktopSource): String = (source.title as String?).orEmpty()

        private fun listFrom(capturer: DesktopCapturer) = try {
            capturer.desktopSources.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        } finally {
            capturer.dispose()
        }
    }
}
