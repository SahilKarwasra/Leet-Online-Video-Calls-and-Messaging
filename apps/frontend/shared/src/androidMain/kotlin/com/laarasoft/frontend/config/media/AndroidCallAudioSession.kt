package com.laarasoft.frontend.config.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.webrtc.EglBase

// One GL context shared by the WebRTC codecs and every SurfaceViewRenderer.
object AndroidEgl {
    val base: EglBase by lazy { EglBase.create() }
}

class AndroidCallAudioSession(private val context: Context) : CallAudioSession {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun start() {
        CallForegroundService.startCall(context)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    override fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        CallForegroundService.endCall(context)
    }
}
