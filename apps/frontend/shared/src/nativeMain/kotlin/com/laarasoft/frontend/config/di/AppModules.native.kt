package com.laarasoft.frontend.config.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

actual val platformModule = module {
    single<HttpClientEngine> {
        Darwin.create {
            configureRequest {
                setTimeoutInterval(30.0)
                setAllowsCellularAccess(true)
            }
        }
    }
}