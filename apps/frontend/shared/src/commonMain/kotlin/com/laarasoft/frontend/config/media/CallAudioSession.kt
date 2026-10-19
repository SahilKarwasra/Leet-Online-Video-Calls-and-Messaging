package com.laarasoft.frontend.config.media

// Routes call audio for the platform (e.g. loudspeaker + voice-call mode on mobile).
interface CallAudioSession {
    fun start()
    fun stop()
}
