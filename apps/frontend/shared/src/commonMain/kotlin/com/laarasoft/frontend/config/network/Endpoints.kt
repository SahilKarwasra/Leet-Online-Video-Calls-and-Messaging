package com.laarasoft.frontend.config.network

object Endpoints {
    private var customHost: String? = null
    var host: String
        get() = customHost ?: PLATFORM_HOST
        set(value) {
            customHost = value
        }

    val BASE_URL: String get() = "http://$host:8080/api/v1"
    val CREATE_MEET: String get() = "$BASE_URL/meeting"
    val WS_BASE_URL: String get() = "ws://$host:8080"
}