package com.streakcounter.feature.streaks.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.streakcounter.core.data.datastore.EncryptingSerializer
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.core.testing.FakeAead
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.feature.streaks.domain.CelebrationMark
import com.streakcounter.feature.streaks.domain.EndedNote
import com.streakcounter.feature.streaks.domain.NewStreak
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakEdit
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.domain.valueOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.uuid.Uuid

class DataStoreStreakRepositoryTest {

    private val today = FakeToday(LocalDate(2026, 11, 27))
    private val aead = FakeAead()
    private val fileSystem = FlakyFileSystem(FakeFileSystem())

    // DataStore allows one active instance per path in a process; a fresh directory per test keeps them apart.
    private val path: Path = "/data-${Random.nextLong().toULong()}/datastore/$STREAK_FILE_NAME".toPath()

    @Test
    fun create_then_read_returns_streak_at_bottom() = runTest {
        val repository = repository()

        val first = repository.execute(StreakCommand.Create(new("Gym")))
        repository.execute(StreakCommand.Create(new("Water", StreakType.TIMES)))

        assertThat(first).isEqualTo(Result.Success(null))
        val streaks = repository.list()
        assertThat(streaks.map { it.name }).containsExactly("Gym", "Water")
        assertThat(streaks.map { it.position }).containsExactly(0, 1)
    }

    @Test
    fun increment_persists_across_new_datastore_instance() = runTest {
        val firstScope = childScope()
        val first = repository(firstScope)
        val id = first.create(new("Water", StreakType.TIMES, start = 3))
        first.execute(StreakCommand.Increment(id))
        firstScope.coroutineContext.job.cancelAndJoin()

        val second = repository()

        assertThat(second.list().single().valueOn(today.current)).isEqualTo(4)
    }

    @Test
    fun file_on_disk_is_encrypted() = runTest {
        repository().create(new("No smoking", StreakType.DAYS_SINCE))

        assertThat(fileBytes().decodeToString().contains("No smoking")).isFalse()
        assertThat(fileBytes().decodeToString().contains("DAYS_SINCE")).isFalse()
    }

    @Test
    fun delete_returns_snapshot_and_restore_puts_it_back_at_same_position() = runTest {
        val repository = repository()
        repository.create(new("A"))
        val b = repository.create(new("B"))
        repository.create(new("C"))
        val original = repository.list()

        val deleted = repository.execute(StreakCommand.Delete(b))

        val snapshot = (deleted as Result.Success).data
        assertThat(snapshot).isEqualTo(original[1])
        assertThat(repository.list().map { it.name }).containsExactly("A", "C")
        assertThat(repository.list().map { it.position }).containsExactly(0, 1)

        assertThat(repository.execute(StreakCommand.Restore(snapshot!!))).isEqualTo(Result.Success(null))
        assertThat(repository.list()).isEqualTo(original)
    }

    @Test
    fun reset_returns_snapshot_and_restore_undoes_it() = runTest {
        val repository = repository()
        val id = repository.create(new("Water", StreakType.TIMES, start = 5))
        val original = repository.list().single()

        val reset = repository.execute(StreakCommand.Reset(id))

        assertThat(reset).isEqualTo(Result.Success(original))
        assertThat(repository.list().single().count).isEqualTo(0)
        // Restore replaces the streak with the same id; the replaced state is returned.
        val restored = repository.execute(StreakCommand.Restore(original))
        assertThat((restored as Result.Success).data?.count).isEqualTo(0)
        assertThat(repository.list()).containsExactly(original)
    }

    @Test
    fun reorder_rewrites_positions() = runTest {
        val repository = repository()
        val a = repository.create(new("A"))
        val b = repository.create(new("B"))
        val c = repository.create(new("C"))

        val result = repository.execute(StreakCommand.Reorder(listOf(c, a, b)))

        assertThat(result).isEqualTo(Result.Success(null))
        assertThat(repository.list().map { it.name }).containsExactly("C", "A", "B")
        assertThat(repository.list().map { it.position }).containsExactly(0, 1, 2)
        // Persisted, not just cached.
        assertThat(readFileDirectly().streaks.map { it.name }).containsExactly("C", "A", "B")
    }

    @Test
    fun command_normalizes_before_applying() = runTest {
        today.current = LocalDate(2026, 11, 20)
        val repository = repository()
        val gym = repository.create(new("Gym", StreakType.CHECK_IN, start = 5, autoReset = true))
        val water = repository.create(new("Water", StreakType.TIMES))
        today.current = LocalDate(2026, 11, 27)

        // A command on another streak still records the ended check-in streak.
        repository.execute(StreakCommand.Increment(water))

        val ended = repository.list().first { it.id == gym }
        assertThat(ended.endedNote).isEqualTo(EndedNote(LocalDate(2026, 11, 20), 5))
        assertThat(ended.count).isEqualTo(0)
        assertThat(ended.lastCheckIn).isNull()
    }

    @Test
    fun snapshot_is_taken_after_normalize() = runTest {
        today.current = LocalDate(2026, 11, 20)
        val repository = repository()
        val gym = repository.create(new("Gym", StreakType.CHECK_IN, start = 5, autoReset = true))
        today.current = LocalDate(2026, 11, 27)

        val before = repository.execute(StreakCommand.Increment(gym))

        assertThat((before as Result.Success).data?.endedNote).isEqualTo(EndedNote(LocalDate(2026, 11, 20), 5))
        val now = repository.list().single()
        assertThat(now.count).isEqualTo(1)
        assertThat(now.lastCheckIn).isEqualTo(today.current)
        assertThat(now.endedNote).isNull()
    }

    @Test
    fun unknown_id_is_a_noop_returning_null() = runTest {
        val repository = repository()
        repository.create(new("Gym"))
        val before = repository.list()

        val result = repository.execute(StreakCommand.Increment(Uuid.random()))

        assertThat(result).isEqualTo(Result.Success(null))
        assertThat(repository.list()).isEqualTo(before)
    }

    @Test
    fun update_decrement_and_markCelebrated_apply_the_domain_rules() = runTest {
        val repository = repository()
        val id = repository.create(new("Water", StreakType.TIMES, start = 3, goal = 3))

        repository.execute(StreakCommand.Update(id, StreakEdit("Glasses", count = 4, goal = 3, autoReset = false, goalChanged = false)))
        repository.execute(StreakCommand.Decrement(id))
        repository.execute(StreakCommand.MarkCelebrated(id))

        val streak = repository.list().single()
        assertThat(streak.name).isEqualTo("Glasses")
        assertThat(streak.count).isEqualTo(3)
        assertThat(streak.celebrated).isEqualTo(CelebrationMark(goal = 3, day = null))
        // Reached on create day, recorded by normalize.
        assertThat(streak.goalReachedOn).isEqualTo(today.current)
    }

    @Test
    fun streaks_emits_the_new_list_after_each_command() = runTest {
        val repository = repository()

        repository.streaks.test {
            assertThat(awaitItem()).isEqualTo(Result.Success(emptyList()))
            repository.create(new("Gym"))
            assertThat((awaitItem() as Result.Success).data.map { it.name }).containsExactly("Gym")
        }
    }

    @Test
    fun unreadable_file_emits_UNREADABLE_and_commands_do_not_overwrite() = runTest {
        val garbage = byteArrayOf(1) + Random.nextBytes(80)
        writeFile(garbage)
        val repository = repository()

        repository.streaks.test {
            assertThat(awaitItem()).isEqualTo(Result.Error(DataError.Local.UNREADABLE))
            awaitComplete()
        }
        assertThat(repository.execute(StreakCommand.Increment(Uuid.random())))
            .isEqualTo(Result.Error(DataError.Local.UNREADABLE))
        assertThat(repository.execute(StreakCommand.Create(new("Gym"))))
            .isEqualTo(Result.Error(DataError.Local.UNREADABLE))
        assertThat(repository.execute(StreakCommand.NormalizeAll))
            .isEqualTo(Result.Error(DataError.Local.UNREADABLE))
        assertThat(fileBytes().toList()).isEqualTo(garbage.toList())
    }

    @Test
    fun file_from_a_newer_schema_is_unreadable_and_not_overwritten() = runTest {
        val newer = encrypt(StreakFile(schemaVersion = STREAK_SCHEMA_VERSION + 1))
        writeFile(newer)
        val repository = repository()

        assertThat(repository.streaks.first()).isEqualTo(Result.Error(DataError.Local.UNREADABLE))
        assertThat(repository.execute(StreakCommand.Create(new("Gym"))))
            .isEqualTo(Result.Error(DataError.Local.UNREADABLE))
        assertThat(fileBytes().toList()).isEqualTo(newer.toList())
    }

    @Test
    fun file_with_an_invalid_streak_is_unreadable_and_not_overwritten() = runTest {
        val valid = StreakFile(streaks = listOf(streakDto()))
        for (broken in listOf(
            valid.copy(streaks = listOf(streakDto().copy(id = "not-a-uuid"))),
            valid.copy(streaks = listOf(streakDto().copy(lastCheckIn = "27.11.2026"))),
        )) {
            val bytes = encrypt(broken)
            writeFile(bytes)
            val scope = childScope()
            val repository = repository(scope)

            assertThat(repository.streaks.first()).isEqualTo(Result.Error(DataError.Local.UNREADABLE))
            assertThat(repository.execute(StreakCommand.NormalizeAll))
                .isEqualTo(Result.Error(DataError.Local.UNREADABLE))
            assertThat(fileBytes().toList()).isEqualTo(bytes.toList())
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun write_failure_returns_WRITE_FAILED() = runTest {
        val repository = repository()
        val id = repository.create(new("Water", StreakType.TIMES, start = 1))
        val onDisk = fileBytes()
        fileSystem.failWrites = true

        val result = repository.execute(StreakCommand.Increment(id))

        assertThat(result).isEqualTo(Result.Error(DataError.Local.WRITE_FAILED))
        assertThat(fileBytes().toList()).isEqualTo(onDisk.toList())
        // The screen keeps showing what is on disk.
        assertThat(repository.list().single().count).isEqualTo(1)

        // Try again replays the command against the current file.
        fileSystem.failWrites = false
        assertThat(repository.execute(StreakCommand.Increment(id))).isInstanceOf<Result.Success<*>>()
        assertThat(repository.list().single().count).isEqualTo(2)
    }

    @Test
    fun startFresh_deletes_file_then_reads_empty() = runTest {
        writeFile(byteArrayOf(1) + Random.nextBytes(80))
        val repository = repository()
        assertThat(repository.streaks.first()).isEqualTo(Result.Error(DataError.Local.UNREADABLE))

        val result = repository.startFresh()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(fileSystem.exists(path)).isFalse()
        assertThat(repository.streaks.first()).isEqualTo(Result.Success(emptyList()))
        repository.create(new("Gym"))
        assertThat(repository.list().map { it.name }).containsExactly("Gym")
    }

    @Test
    fun startFresh_on_a_readable_file_also_clears_the_cached_list() = runTest {
        val repository = repository()
        repository.create(new("Gym"))

        repository.startFresh()

        assertThat(repository.list()).isEqualTo(emptyList())
        assertThat(readFileDirectly().streaks).isEqualTo(emptyList())
    }

    @Test
    fun startFresh_never_deletes_when_the_key_is_unavailable() = runTest {
        val firstScope = childScope()
        repository(firstScope).create(new("Gym"))
        firstScope.coroutineContext.job.cancelAndJoin()
        val onDisk = fileBytes()
        aead.keyUnavailable = true
        val repository = repository()

        assertThat(repository.startFresh()).isEqualTo(Result.Error(DataError.Local.KEY_UNAVAILABLE))
        assertThat(fileBytes().toList()).isEqualTo(onDisk.toList())
    }

    @Test
    fun key_unavailable_on_read_maps_to_KEY_UNAVAILABLE_and_never_writes() = runTest {
        val firstScope = childScope()
        val id = repository(firstScope).create(new("Gym"))
        firstScope.coroutineContext.job.cancelAndJoin()
        val onDisk = fileBytes()
        aead.keyUnavailable = true
        val repository = repository()

        assertThat(repository.streaks.first()).isEqualTo(Result.Error(DataError.Local.KEY_UNAVAILABLE))
        assertThat(repository.execute(StreakCommand.Increment(id)))
            .isEqualTo(Result.Error(DataError.Local.KEY_UNAVAILABLE))
        assertThat(repository.execute(StreakCommand.Create(new("Water"))))
            .isEqualTo(Result.Error(DataError.Local.KEY_UNAVAILABLE))
        assertThat(fileBytes().toList()).isEqualTo(onDisk.toList())

        // Try again once the key is back: nothing was lost.
        aead.keyUnavailable = false
        assertThat(repository.list().map { it.name }).containsExactly("Gym")
    }

    @Test
    fun key_unavailable_on_write_maps_to_KEY_UNAVAILABLE_and_leaves_the_file() = runTest {
        val repository = repository()
        val id = repository.create(new("Gym"))
        val onDisk = fileBytes()
        aead.keyUnavailable = true

        assertThat(repository.execute(StreakCommand.Increment(id)))
            .isEqualTo(Result.Error(DataError.Local.KEY_UNAVAILABLE))
        assertThat(fileBytes().toList()).isEqualTo(onDisk.toList())
    }

    // Review Focus 1: a widget tap and an app tap at the same moment.
    @Test
    fun concurrent_checkins_add_one() = runTest {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val repository = repository(scope)
            val checkIn = repository.create(new("Gym", StreakType.CHECK_IN, start = 3))
            val times = repository.create(new("Water", StreakType.TIMES, start = 3))

            val results = withContext(Dispatchers.Default) {
                listOf(checkIn, checkIn, times, times)
                    .map { id -> async { repository.execute(StreakCommand.Increment(id)) } }
                    .awaitAll()
            }

            assertThat(results).each { it.isInstanceOf<Result.Success<*>>() }
            val streaks = repository.list()
            assertThat(streaks.first { it.id == checkIn }.valueOn(today.current)).isEqualTo(4)
            assertThat(streaks.first { it.id == times }.valueOn(today.current)).isEqualTo(5)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun many_concurrent_increments_are_all_kept() = runTest {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val repository = repository(scope)
            val times = repository.create(new("Water", StreakType.TIMES))

            withContext(Dispatchers.Default) {
                List(50) { async { repository.execute(StreakCommand.Increment(times)) } }.awaitAll()
            }

            assertThat(repository.list().single().count).isEqualTo(50)
            assertThat(readFileDirectly().streaks.single().count).isEqualTo(50)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    // --- helpers ---

    private fun TestScope.childScope(): CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + Job())

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope): StreakRepository =
        DataStoreStreakRepository(
            dataStore = createStreakDataStore(aead, scope, path, fileSystem),
            path = path,
            fileSystem = fileSystem,
            today = today,
        )

    private fun new(
        name: String?,
        type: StreakType = StreakType.CHECK_IN,
        start: Int = 0,
        goal: Int? = null,
        autoReset: Boolean = false,
    ) = NewStreak(name, type, start, goal, autoReset)

    private suspend fun StreakRepository.list(): List<Streak> {
        val result = streaks.first()
        assertThat(result).isInstanceOf<Result.Success<List<Streak>>>()
        return (result as Result.Success).data
    }

    /** Creates [new] and returns its id (new streaks go to the bottom). */
    private suspend fun StreakRepository.create(new: NewStreak): Uuid {
        assertThat(execute(StreakCommand.Create(new))).isEqualTo(Result.Success(null))
        return list().last().id
    }

    private fun fileBytes(): ByteArray = fileSystem.read(path) { readByteArray() }

    private fun writeFile(bytes: ByteArray) {
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { write(bytes) }
    }

    private val fileSerializer = EncryptingSerializer(StreakFileSerializer, aead, STREAK_FILE_NAME.encodeToByteArray())

    private suspend fun encrypt(file: StreakFile): ByteArray =
        Buffer().also { fileSerializer.writeTo(file, it) }.readByteArray()

    private suspend fun readFileDirectly(): StreakFile = fileSerializer.readFrom(Buffer().write(fileBytes()))

    private fun streakDto() = StreakDto(
        id = Uuid.random().toString(),
        name = "Gym",
        type = StreakTypeDto.CHECK_IN,
        autoReset = false,
        goal = null,
        position = 0,
        count = 1,
        lastCheckIn = "2026-11-26",
        startDate = null,
        countDate = null,
        goalReachedOn = null,
        celebrated = null,
        endedNote = null,
    )

    /** Fails every file write while [failWrites] is set, like a full disk. Reads still work. */
    private class FlakyFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
        var failWrites = false

        override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
            if (failWrites) throw IOException("No space left on device")
            return super.openReadWrite(file, mustCreate, mustExist)
        }

        override fun sink(file: Path, mustCreate: Boolean): Sink {
            if (failWrites) throw IOException("No space left on device")
            return super.sink(file, mustCreate)
        }

        override fun atomicMove(source: Path, target: Path) {
            if (failWrites) throw IOException("No space left on device")
            super.atomicMove(source, target)
        }
    }
}
