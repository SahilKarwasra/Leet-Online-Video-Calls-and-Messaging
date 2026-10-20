package com.laarasoft.frontend.config.media

import com.laarasoft.frontend.domain.models.ScreenCaptureGrant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.CoreVideo.CVPixelBufferRef

// iOS can only capture the whole screen from a ReplayKit Broadcast Upload
// Extension, which runs in its own process. The extension (iosApp/LeetScreenShare)
// sends frames over a Unix socket in the shared App Group folder; the Swift
// side of the app (iosApp/iosApp/ScreenShareBroadcast.swift) receives them and
// implements this interface. It is registered at app start.
interface IosScreenBroadcast {
    // False on the simulator, or when the App Group isn't set up for signing.
    val isAvailable: Boolean
    fun startListening(listener: IosScreenFrameListener)
    fun stopListening()
    // Shows the system "Start Broadcast" sheet for our extension.
    fun showPicker()
    // Asks the extension to end the broadcast (it can't be stopped from here directly).
    fun requestStop()
}

@OptIn(ExperimentalForeignApi::class)
interface IosScreenFrameListener {
    fun onStarted()
    // [rotation] is in degrees: 0, 90, 180 or 270.
    fun onFrame(pixelBuffer: CVPixelBufferRef?, rotation: Int, timestampNs: Long)
    fun onEnded()
}

object IosScreenShare {
    var broadcast: IosScreenBroadcast? = null
}

// The broadcast has started; frames are coming in.
data object IosBroadcastGrant : ScreenCaptureGrant

// Routes the Swift callbacks to whoever currently needs them: the launcher
// waits for the start, the capturer for frames and the end.
@OptIn(ExperimentalForeignApi::class)
internal object IosScreenShareSession : IosScreenFrameListener {
    private val main = MainScope()

    @kotlin.concurrent.Volatile var onStartedHandler: (() -> Unit)? = null
    @kotlin.concurrent.Volatile var frameSink: ((CVPixelBufferRef, Int, Long) -> Unit)? = null
    @kotlin.concurrent.Volatile var onEndedHandler: (() -> Unit)? = null

    override fun onStarted() {
        val handler = onStartedHandler ?: return
        onStartedHandler = null
        main.launch { handler() }
    }

    override fun onFrame(pixelBuffer: CVPixelBufferRef?, rotation: Int, timestampNs: Long) {
        if (pixelBuffer != null) frameSink?.invoke(pixelBuffer, rotation, timestampNs)
    }

    override fun onEnded() {
        onStartedHandler = null
        val handler = onEndedHandler ?: return
        main.launch { handler() }
    }
}
