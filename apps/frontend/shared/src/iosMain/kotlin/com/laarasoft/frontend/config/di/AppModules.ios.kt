package com.laarasoft.frontend.config.di

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
    single<WebRtcClient> { WebRtcClient(IosWebRtc) }
    single<CallAudioSession> { IosCallAudioSession }
}
