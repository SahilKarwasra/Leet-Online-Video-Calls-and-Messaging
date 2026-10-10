package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.data.api.HomeApiImpl
import com.laarasoft.frontend.data.repository.HomeRepositoryImpl
import com.laarasoft.frontend.domain.api.HomeApi
import com.laarasoft.frontend.domain.repository.HomeRepository
import com.laarasoft.frontend.presentation.HomeViewModel
import com.laarasoft.frontend.presentation.MeetViewModel
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformModule: Module

val appModules = module {
    includes(platformModule)
    viewModelOf(::MeetViewModel)

    single<HttpClient> {
        val json: Json = get()
        val engine = getOrNull<HttpClientEngine>()
        val config: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = platformHttpLogger
                level = LogLevel.ALL
            }
            install(WebSockets)
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }
    single<Json> {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    singleOf(::HomeApiImpl).bind<HomeApi>()
    singleOf(::HomeRepositoryImpl).bind<HomeRepository>()
    viewModelOf(::HomeViewModel)
}