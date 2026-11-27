package com.streakcounter.feature.streaks.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import com.streakcounter.core.data.crypto.KeyUnavailableException
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.EmptyResult
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.applyCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.Path
import kotlin.uuid.Uuid
import androidx.datastore.core.IOException as DataStoreIOException
import okio.IOException as OkioIOException

/**
 * [StreakRepository] on the encrypted DataStore file at [path].
 *
 * Every command is one `updateData` transaction: DataStore runs them one at a time on the latest
 * file content, so a widget tap and an app tap never overwrite each other. Nothing is written
 * unless the file was read successfully first: an unreadable file or an unavailable key fails the
 * command and leaves the file untouched.
 *
 * Never logs names or counts.
 */
class DataStoreStreakRepository(
    private val dataStore: DataStore<StreakFile>,
    private val path: Path,
    private val fileSystem: FileSystem,
    private val today: Today,
    private val newId: () -> Uuid = { Uuid.random() },
) : StreakRepository {

    override val streaks: Flow<Result<List<Streak>, DataError.Local>> = dataStore.data
        .map<StreakFile, Result<List<Streak>, DataError.Local>> { file ->
            Result.Success(file.streaks.map { it.toStreak() }.sortedBy { it.position })
        }
        .catch { e -> emit(Result.Error(e.toReadError() ?: throw e)) }

    override suspend fun execute(command: StreakCommand): Result<Streak?, DataError.Local> {
        var before: Streak? = null
        return try {
            dataStore.updateData { file ->
                val outcome = file.streaks
                    .map { it.toStreak() }
                    .applyCommand(command, today.date.value, newId)
                before = outcome.before
                file.copy(streaks = outcome.streaks.map { it.toStreakDto() })
            }
            Result.Success(before)
        } catch (e: Exception) {
            Result.Error(e.toWriteError() ?: throw e)
        }
    }

    /**
     * Empties the store.
     *
     * A readable file is emptied through DataStore, so its cached copy follows (deleting the file
     * behind DataStore's back would leave the cached list on screen and let the next write bring
     * it back). Only a file that fails with CorruptionException is deleted; DataStore then reads
     * the missing file as empty. An unavailable key fails with KEY_UNAVAILABLE and deletes nothing:
     * the file may be fine once the key is back.
     */
    override suspend fun startFresh(): EmptyResult<DataError.Local> = try {
        try {
            dataStore.updateData { StreakFile() }
        } catch (e: CorruptionException) {
            fileSystem.delete(path, mustExist = false)
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.toWriteError() ?: throw e)
    }

    private fun Throwable.toReadError(): DataError.Local? = when (this) {
        is KeyUnavailableException -> DataError.Local.KEY_UNAVAILABLE
        is CorruptionException -> DataError.Local.UNREADABLE
        // A transient read failure says nothing about the file: retry, never "Start fresh".
        is OkioIOException, is DataStoreIOException -> DataError.Local.UNKNOWN
        else -> null
    }

    private fun Throwable.toWriteError(): DataError.Local? = when (this) {
        is KeyUnavailableException -> DataError.Local.KEY_UNAVAILABLE
        is CorruptionException -> DataError.Local.UNREADABLE
        is OkioIOException, is DataStoreIOException -> DataError.Local.WRITE_FAILED
        else -> null
    }
}
