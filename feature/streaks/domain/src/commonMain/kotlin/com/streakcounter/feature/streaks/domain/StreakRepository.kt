package com.streakcounter.feature.streaks.domain

import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.EmptyResult
import com.streakcounter.core.domain.Result
import kotlinx.coroutines.flow.Flow

/**
 * The single source of truth for the app and the widgets.
 *
 * Errors: [DataError.Local.UNREADABLE] (file can't be decrypted or parsed: offer "Start fresh"),
 * [DataError.Local.KEY_UNAVAILABLE] (key can't be reached: retry only, never start fresh),
 * [DataError.Local.WRITE_FAILED] (a command could not be saved; disk is unchanged),
 * [DataError.Local.UNKNOWN] (another read failure: retry only).
 */
interface StreakRepository {
    /** Sorted by position. Never emits an empty list for a file that can't be read. Completes after an error. */
    val streaks: Flow<Result<List<Streak>, DataError.Local>>

    /** Runs [command] atomically. Returns the streak as it was before the change (undo), else null. */
    suspend fun execute(command: StreakCommand): Result<Streak?, DataError.Local>

    /**
     * The explicit "Start fresh" on an unreadable file: deletes only a file that fails with
     * UNREADABLE, so the list reads as empty. A file that reads fine is left unchanged (Success).
     * Never deletes on KEY_UNAVAILABLE or UNKNOWN (returns that error).
     */
    suspend fun startFresh(): EmptyResult<DataError.Local>
}
