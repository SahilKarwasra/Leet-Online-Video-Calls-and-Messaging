package com.laarasoft.frontend.config.media

// Desktop uses the system default input/output devices; nothing to route.
object JvmCallAudioSession : CallAudioSession {
    override fun start() = Unit
    override fun stop() = Unit
}
