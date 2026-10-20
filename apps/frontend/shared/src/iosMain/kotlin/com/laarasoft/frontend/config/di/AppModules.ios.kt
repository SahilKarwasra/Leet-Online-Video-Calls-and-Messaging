package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.media.IosScreenCapturer
import com.laarasoft.frontend.domain.datasource.ScreenCapturer
import io.ktor.client.webrtc.media.IosMediaDevices

import com.laarasoft.frontend.config.media.CallAudioSession
import com.laarasoft.frontend.config.media.IosCallAudioSession
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.webrtc.IosWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
actual val platformModule = module {
    single<HttpClientEngine> {
        Darwin.create {
            configureRequest {
                setTimeoutInterval(30.0)
                setAllowsCellularAccess(true)
            }
        }
    }
    // Shared by the WebRTC client and screen capture, so a screen track can
    // replace the camera track on the same peer connection.
    single { IosMediaDevices() }
    single<WebRtcClient> {
        val devices: IosMediaDevices = get()
        WebRtcClient(IosWebRtc) { mediaTrackFactory = devices }
    }
    single<ScreenCapturer> { IosScreenCapturer(get()) }
    single<CallAudioSession> { IosCallAudioSession }
}
