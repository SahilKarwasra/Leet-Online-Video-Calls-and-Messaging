package com.laarasoft.frontend.data.dto

import com.laarasoft.frontend.domain.models.Meet
import kotlinx.serialization.Serializable

@Serializable
data class MeetDto(
    val code: String,
    val id: String,
    val status: String
)

fun MeetDto.toMeet(): Meet = Meet(
    meetId = this.code
)