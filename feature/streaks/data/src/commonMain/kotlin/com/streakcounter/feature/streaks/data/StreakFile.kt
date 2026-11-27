package com.streakcounter.feature.streaks.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.streakcounter.core.data.crypto.Aead
import com.streakcounter.core.data.datastore.createEncryptedDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import com.streakcounter.core.data.datastore.systemFileSystem
import okio.FileSystem
import okio.Path

const val STREAK_FILE_NAME = "streaks.enc"

/** Bump when the stored shape changes, and migrate older versions in [StreakFileSerializer.readFrom]. */
const val STREAK_SCHEMA_VERSION = 1

@Serializable
data class StreakFile(val schemaVersion: Int = STREAK_SCHEMA_VERSION, val streaks: List<StreakDto> = emptyList())

/**
 * The encrypted DataStore holding every streak. [path] is `…/datastore/streaks.enc` (see
 * `dataStorePath`). One instance per process.
 */
fun createStreakDataStore(
    aead: Aead,
    scope: CoroutineScope,
    path: Path,
    fileSystem: FileSystem = systemFileSystem(),
): DataStore<StreakFile> = createEncryptedDataStore(StreakFileSerializer, aead, scope, path, fileSystem)

/**
 * JSON inside the encryption envelope. Any failure here becomes a CorruptionException in
 * EncryptingSerializer, so a file this version can't fully understand is never overwritten:
 * unknown keys, a newer [StreakFile.schemaVersion] (an app downgrade), or a malformed id or date.
 */
internal object StreakFileSerializer : OkioSerializer<StreakFile> {
    private val json = Json { encodeDefaults = true }

    override val defaultValue: StreakFile = StreakFile()

    override suspend fun readFrom(source: BufferedSource): StreakFile {
        val file = json.decodeFromString(StreakFile.serializer(), source.readUtf8())
        require(file.schemaVersion == STREAK_SCHEMA_VERSION) { "Unsupported schema version" }
        file.streaks.forEach { it.toStreak() }
        return file
    }

    override suspend fun writeTo(t: StreakFile, sink: BufferedSink) {
        sink.writeUtf8(json.encodeToString(StreakFile.serializer(), t))
    }
}
