package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.media.AndroidCallAudioSession
import com.laarasoft.frontend.config.media.AndroidEgl
import com.laarasoft.frontend.config.media.AndroidScreenCapturer
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import com.laarasoft.frontend.config.media.CallAudioSession
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.webrtc.AndroidWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.client.webrtc.media.AndroidMediaDevices
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    // Shared by the WebRTC client and screen capture, so a screen track can
    // replace the camera track on the same peer connection.
    single { AndroidMediaDevices(androidContext(), AndroidEgl.base) }
    single<WebRtcClient> {
        val devices: AndroidMediaDevices = get()
        WebRtcClient(AndroidWebRtc) {
            this.context = androidContext()
            mediaTrackFactory = devices
        }
    }
    single<ScreenCapturer> { AndroidScreenCapturer(androidContext(), get()) }
    single<CallAudioSession> { AndroidCallAudioSession(androidContext()) }
}
