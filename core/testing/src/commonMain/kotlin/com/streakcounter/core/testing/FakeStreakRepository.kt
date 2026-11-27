package com.streakcounter.core.testing

import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.EmptyResult
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.applyCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/**
 * In-memory [StreakRepository] with the real command semantics ([applyCommand]: normalize first,
 * Restore at snapshot.position, execute returns the before-snapshot).
 *
 * Set [shouldReturnError] to make [execute] and [startFresh] fail without changing anything (like
 * a failed write: the stored list stays). Use [emitReadError] to make [streaks] emit an error.
 */
class FakeStreakRepository(
    private val today: Today,
    initial: List<Streak> = emptyList(),
    private val newId: () -> Uuid = { Uuid.random() },
) : StreakRepository {

    var shouldReturnError: DataError.Local? = null

    private val state = MutableStateFlow<Result<List<Streak>, DataError.Local>>(Result.Success(initial))
    override val streaks: Flow<Result<List<Streak>, DataError.Local>> = state

    private val _executed = mutableListOf<StreakCommand>()

    /** Every command passed to [execute], including failed ones. */
    val executed: List<StreakCommand> get() = _executed

    var startFreshCalls: Int = 0
        private set

    /** The stored list, or null while [streaks] shows an error. */
    val current: List<Streak>? get() = (state.value as? Result.Success)?.data

    fun emitReadError(error: DataError.Local) {
        state.value = Result.Error(error)
    }

    fun setStreaks(streaks: List<Streak>) {
        state.value = Result.Success(streaks.sortedBy { it.position })
    }

    override suspend fun execute(command: StreakCommand): Result<Streak?, DataError.Local> {
        _executed += command
        shouldReturnError?.let { return Result.Error(it) }
        val stored = when (val value = state.value) {
            is Result.Success -> value.data
            // Like the real repository: never write over a file that can't be read.
            is Result.Error -> return Result.Error(value.error)
        }
        val outcome = stored.applyCommand(command, today.date.value, newId)
        state.value = Result.Success(outcome.streaks)
        return Result.Success(outcome.before)
    }

    override suspend fun startFresh(): EmptyResult<DataError.Local> {
        startFreshCalls++
        shouldReturnError?.let { return Result.Error(it) }
        state.value = Result.Success(emptyList())
        return Result.Success(Unit)
    }
}
