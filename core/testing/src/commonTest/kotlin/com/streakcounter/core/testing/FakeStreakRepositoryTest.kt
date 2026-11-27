package com.streakcounter.core.testing

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.feature.streaks.domain.NewStreak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class FakeStreakRepositoryTest {

    private val today = FakeToday(LocalDate(2026, 11, 27))
    private val repository = FakeStreakRepository(today)

    private suspend fun create(name: String) =
        repository.execute(StreakCommand.Create(NewStreak(name, StreakType.TIMES, 0, null, false)))

    @Test
    fun delete_returns_snapshot_and_restore_reinserts_at_its_position() = runTest {
        create("A"); create("B"); create("C")
        val b = repository.current!![1]

        val deleted = repository.execute(StreakCommand.Delete(b.id))
        repository.execute(StreakCommand.Restore((deleted as Result.Success).data!!))

        assertThat(deleted.data).isEqualTo(b)
        assertThat(repository.current!!.map { it.name }).containsExactly("A", "B", "C")
    }

    @Test
    fun shouldReturnError_fails_commands_without_changing_the_list() = runTest {
        create("A")
        repository.shouldReturnError = DataError.Local.WRITE_FAILED

        val result = repository.execute(StreakCommand.Increment(repository.current!!.single().id))

        assertThat(result).isEqualTo(Result.Error(DataError.Local.WRITE_FAILED))
        assertThat(repository.current!!.single().count).isEqualTo(0)
        assertThat(repository.executed.size).isEqualTo(2)
    }

    @Test
    fun commands_use_the_fake_today() = runTest {
        create("A")
        today.advanceDays()

        repository.execute(StreakCommand.Increment(repository.current!!.single().id))

        assertThat(repository.current!!.single().countDate).isNotNull().isEqualTo(LocalDate(2026, 11, 28))
    }
}
