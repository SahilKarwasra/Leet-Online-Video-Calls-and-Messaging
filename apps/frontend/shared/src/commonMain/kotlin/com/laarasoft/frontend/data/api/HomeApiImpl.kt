package com.laarasoft.frontend.data.api

import com.laarasoft.frontend.config.network.DataError
import com.laarasoft.frontend.config.network.Endpoints
import com.laarasoft.frontend.config.network.Result
import com.laarasoft.frontend.config.network.safeCall
import com.laarasoft.frontend.data.dto.MeetDto
import com.laarasoft.frontend.domain.api.HomeApi
import io.ktor.client.HttpClient
import io.ktor.client.request.post

class HomeApiImpl(
    private val client: HttpClient,
): HomeApi {
    override suspend fun createMeet(): Result<MeetDto, DataError.Remote> =
        safeCall<MeetDto> {
            client.post(Endpoints.CREATE_MEET)
        }
}