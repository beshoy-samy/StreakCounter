package com.streakcounter.feature.streaks.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** On-disk shape of a streak. Dates are ISO `yyyy-MM-dd` strings, the id is the Uuid string. */
@Serializable
data class StreakDto(
    val id: String,
    val name: String?,
    val type: StreakTypeDto,
    val autoReset: Boolean,
    val goal: Int?,
    val position: Int,
    val count: Int,
    val lastCheckIn: String?,
    val startDate: String?,
    val countDate: String?,
    val goalReachedOn: String?,
    val celebrated: CelebrationMarkDto?,
    val endedNote: EndedNoteDto?,
)

/** Stable names on disk, so renaming the domain enum never breaks a stored file. */
@Serializable
enum class StreakTypeDto {
    @SerialName("check_in") CHECK_IN,
    @SerialName("days_since") DAYS_SINCE,
    @SerialName("times") TIMES,
}

@Serializable
data class CelebrationMarkDto(val goal: Int, val day: String?)

@Serializable
data class EndedNoteDto(val endedOn: String, val previousCount: Int)
