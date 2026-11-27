package com.streakcounter.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.streakcounter.core.data.crypto.Aead
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path

/** Sub-directory of the app's private files that holds every DataStore file. */
const val DATASTORE_DIRECTORY: String = "datastore"

/**
 * A DataStore whose file at [path] is encrypted with [aead]; the AEAD associated data is the file
 * name, so files can't be swapped for each other.
 *
 * Deliberately no corruption handler: a file that can't be decrypted or parsed fails every read
 * and write with CorruptionException and is never replaced by an empty value.
 *
 * Create exactly one instance per file (a Koin `single`).
 */
fun <T> createEncryptedDataStore(
    serializer: OkioSerializer<T>,
    aead: Aead,
    scope: CoroutineScope,
    path: Path,
    fileSystem: FileSystem = systemFileSystem(),
): DataStore<T> = DataStoreFactory.create(
    storage = OkioStorage(
        fileSystem = fileSystem,
        serializer = EncryptingSerializer(serializer, aead, path.name.encodeToByteArray()),
        producePath = { path },
    ),
    scope = scope,
)
