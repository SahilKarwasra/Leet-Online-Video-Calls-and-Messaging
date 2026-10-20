package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.media.CallAudioSession
import com.laarasoft.frontend.config.media.JvmCallAudioSession
import com.laarasoft.frontend.config.media.JvmScreenCapturer
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import dev.onvoid.webrtc.media.MediaDevices
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.webrtc.JvmWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.client.webrtc.media.JvmMediaDevices
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.core.module.Module
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<JvmMediaDevices> {
        JvmMediaDevices().apply {
            MediaDevices.getDefaultAudioRenderDevice()?.let { audioModule.setPlayoutDevice(it) }
        }
    }
    single<WebRtcClient> {
        val devices: JvmMediaDevices = get()
        WebRtcClient(JvmWebRtc) { mediaTrackFactory = devices }
    }
    single<CallAudioSession> { JvmCallAudioSession }
    single<ScreenCapturer> { JvmScreenCapturer(get()) }
}
