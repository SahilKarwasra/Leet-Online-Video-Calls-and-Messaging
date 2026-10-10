package com.laarasoft.frontend.data.repository

import com.laarasoft.frontend.config.network.DataError
import com.laarasoft.frontend.config.network.Result
import com.laarasoft.frontend.config.network.map
import com.laarasoft.frontend.data.dto.toMeet
import com.laarasoft.frontend.domain.api.HomeApi
import com.laarasoft.frontend.domain.models.Meet
import com.laarasoft.frontend.domain.repository.HomeRepository

class HomeRepositoryImpl(
    private val api: HomeApi,
): HomeRepository {

    override suspend fun createMeet(): Result<Meet, DataError.Remote> {
        return api.createMeet().map { dto ->
            dto.toMeet()
        }
    }

}