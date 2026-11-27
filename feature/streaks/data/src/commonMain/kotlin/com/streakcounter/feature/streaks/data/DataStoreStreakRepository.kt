package com.streakcounter.feature.streaks.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import com.streakcounter.core.data.crypto.Aead
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
import kotlinx.coroutines.flow.first
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
    private val aead: Aead,
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
     * "Start fresh" for a file that can't be read. Writes nothing itself.
     *
     * - The file reads fine: changes nothing and succeeds (the caller re-subscribes and shows the
     *   list). Good data is never wiped, even if the screen was stale.
     * - CorruptionException: deletes the file. DataStore re-reads the missing file as empty.
     * - Unavailable key or another read failure: deletes nothing; the file may be fine.
     */
    override suspend fun startFresh(): EmptyResult<DataError.Local> {
        try {
            dataStore.data.first()
            return Result.Success(Unit)
        } catch (e: CorruptionException) {
            // Unreadable: the only case that deletes.
        } catch (e: Exception) {
            return Result.Error(e.toReadError() ?: throw e)
        }
        return try {
            fileSystem.delete(path, mustExist = false)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toWriteError() ?: throw e)
        }
    }

    override suspend fun resetUnreadableStorage(): EmptyResult<DataError.Local> {
        try {
            dataStore.data.first()
            // Readable: never delete the file or the key.
            return Result.Success(Unit)
        } catch (e: CorruptionException) {
            // Unreadable: reset.
        } catch (e: KeyUnavailableException) {
            // The key stayed unreachable through the retries: reset.
        } catch (e: Exception) {
            return Result.Error(e.toReadError() ?: throw e)
        }
        return try {
            // Key first: if the file delete then fails, the file is sealed with a key that is gone,
            // reads as UNREADABLE, and "Start fresh" can still remove it. The other order could
            // leave an empty file next to a broken key, which reads fine and never offers a reset.
            aead.deleteKey()
            fileSystem.delete(path, mustExist = false)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toWriteError() ?: throw e)
        }
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
