package com.laarasoft.frontend.domain.repository

import com.laarasoft.frontend.config.network.DataError
import com.laarasoft.frontend.config.network.Result
import com.laarasoft.frontend.domain.models.Meet

interface HomeRepository {
    suspend fun createMeet(): Result<Meet, DataError.Remote>
}