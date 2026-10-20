package com.laarasoft.frontend.config.media

// Platform side of a call: routes call audio (e.g. loudspeaker + voice-call
// mode on mobile) and, on Android, keeps the call running in the background.
interface CallAudioSession {
    fun start()
    fun stop()
}
