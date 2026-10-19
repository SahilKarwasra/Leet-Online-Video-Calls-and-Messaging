package com.laarasoft.frontend.config.media

import WebRTC.RTCAudioSessionConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionModeVideoChat

// WebRTC defaults to "voiceChat" mode which plays through the earpiece; video calls want the speaker.
@OptIn(ExperimentalForeignApi::class)
object IosCallAudioSession : CallAudioSession {
    override fun start() {
        val config = RTCAudioSessionConfiguration.webRTCConfiguration()
        config.mode = requireNotNull(AVAudioSessionModeVideoChat)
        config.categoryOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or
            AVAudioSessionCategoryOptionAllowBluetooth
        RTCAudioSessionConfiguration.setWebRTCConfiguration(config)
    }

    override fun stop() = Unit
}
