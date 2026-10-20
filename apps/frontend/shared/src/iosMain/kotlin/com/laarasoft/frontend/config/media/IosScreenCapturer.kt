package com.laarasoft.frontend.config.media

import WebRTC.RTCCVPixelBuffer
import WebRTC.RTCVideoCapturer
import WebRTC.RTCVideoFrame
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.media.IosMediaDevices
import io.ktor.client.webrtc.media.IosVideoTrack
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
class IosScreenCapturer(private val devices: IosMediaDevices) : ScreenCapturer {

    override suspend fun createTrack(grant: ScreenCaptureGrant, onEnded: () -> Unit): WebRtcMedia.VideoTrack {
        require(grant is IosBroadcastGrant) { "Unexpected screen capture grant $grant" }
        val broadcast = checkNotNull(IosScreenShare.broadcast) { "Screen sharing is not set up" }

        val factory = devices.peerConnectionFactory
        val source = factory.videoSourceForScreenCast(true)
        val capturer = RTCVideoCapturer(source)
        IosScreenShareSession.frameSink = { pixelBuffer, rotation, timestampNs ->
            val frame = RTCVideoFrame(
                buffer = RTCCVPixelBuffer(pixelBuffer),
                rotation = rotation.toLong(),
                timeStampNs = timestampNs,
            )
            source.capturer(capturer, didCaptureVideoFrame = frame)
        }
        IosScreenShareSession.onEndedHandler = onEnded

        val track = factory.videoTrackWithSource(source, "ios-screen-${NSUUID().UUIDString()}")
        return IosVideoTrack(track) {
            IosScreenShareSession.frameSink = null
            IosScreenShareSession.onEndedHandler = null
            broadcast.requestStop()
            broadcast.stopListening()
        }
    }
}
