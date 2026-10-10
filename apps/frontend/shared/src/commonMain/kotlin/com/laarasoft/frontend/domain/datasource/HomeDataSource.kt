package com.laarasoft.frontend.domain.datasource

import com.laarasoft.frontend.config.network.DataError
import com.laarasoft.frontend.config.network.Result
import com.laarasoft.frontend.data.dto.MeetDto

interface HomeDataSource {
    suspend fun createMeet() : Result<MeetDto, DataError.Remote>
}