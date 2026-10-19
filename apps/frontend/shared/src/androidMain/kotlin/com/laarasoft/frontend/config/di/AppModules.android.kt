package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.media.AndroidCallAudioSession
import com.laarasoft.frontend.config.media.AndroidEgl
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
    single<WebRtcClient> {
        val context = androidContext()
        WebRtcClient(AndroidWebRtc) {
            this.context = context
            mediaTrackFactory = AndroidMediaDevices(context, AndroidEgl.base)
        }
    }
    single<CallAudioSession> { AndroidCallAudioSession(androidContext()) }
}
