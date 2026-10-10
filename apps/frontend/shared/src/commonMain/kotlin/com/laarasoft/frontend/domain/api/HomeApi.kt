package com.laarasoft.frontend.domain.api

import com.laarasoft.frontend.config.network.DataError
import com.laarasoft.frontend.config.network.Result
import com.laarasoft.frontend.data.dto.MeetDto

interface HomeApi {
    suspend fun createMeet() : Result<MeetDto, DataError.Remote>
}