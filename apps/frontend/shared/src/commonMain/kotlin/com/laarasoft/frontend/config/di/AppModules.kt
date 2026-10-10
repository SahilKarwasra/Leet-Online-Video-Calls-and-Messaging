package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.config.network.Endpoints
import com.laarasoft.frontend.config.network.platformHttpLogger
import com.laarasoft.frontend.data.datasource.HomeDataSourceImpl
import com.laarasoft.frontend.data.datasource.KtorWsDataSourceImpl
import com.laarasoft.frontend.data.mapper.MeetingEventMapper
import com.laarasoft.frontend.data.repository.HomeRepositoryImpl
import com.laarasoft.frontend.data.repository.WebSocketRepositoryImpl
import com.laarasoft.frontend.domain.datasource.HomeDataSource
import com.laarasoft.frontend.domain.datasource.KtorWsDataSource
import com.laarasoft.frontend.domain.repository.HomeRepository
import com.laarasoft.frontend.domain.repository.WebSocketRepository
import com.laarasoft.frontend.presentation.HomeViewModel
import com.laarasoft.frontend.presentation.MeetViewModel
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.bind

expect val platformModule: Module

val websocketModule: Module = module {
    single(named("wsBaseUrl")) { Endpoints.WS_BASE_URL }

    single<KtorWsDataSource> {
        KtorWsDataSourceImpl(
            httpClient = get(),
            json = get(),
            baseUrl = get(named("wsBaseUrl"))
        )
    }
    singleOf(::MeetingEventMapper)
    singleOf(::WebSocketRepositoryImpl).bind<WebSocketRepository>()
}

val appModules = module {
    includes(platformModule, websocketModule)
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

    singleOf(::HomeDataSourceImpl).bind<HomeDataSource>()
    singleOf(::HomeRepositoryImpl).bind<HomeRepository>()
    viewModelOf(::HomeViewModel)
}