package com.streakcounter.core.data.datastore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.numberWithBool

/**
 * `Application Support/datastore/[fileName]`, with the `datastore` directory created and excluded
 * from iCloud and device backups.
 *
 * The flag is set on the directory, not the file: DataStore replaces the file on every write
 * (write a scratch file, then rename), and a per-file flag would be lost with the old file.
 * Excluding a directory excludes everything in it.
 *
 * Why no backup: the key is in the Keychain with `ThisDeviceOnly`, so a restored copy could never
 * be decrypted and would only show the "Couldn't open your streaks" screen. Setting the flag is
 * best effort (see below); creating the directory is required.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun dataStorePath(fileName: String): Path {
    val fileManager = NSFileManager.defaultManager
    val supportDirectory = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Application Support directory unavailable")
    val directory: NSURL = supportDirectory.URLByAppendingPathComponent(DATASTORE_DIRECTORY, isDirectory = true)
        ?: error("Could not build the datastore directory URL")
    check(fileManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
        "Could not create the datastore directory"
    }
    // Best effort: if this fails the backup holds only ciphertext whose key (ThisDeviceOnly) never
    // leaves the device. Crashing on every launch would be worse. Tried again on every launch.
    directory.setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val directoryPath = directory.path ?: error("datastore directory has no path")
    return directoryPath.toPath() / fileName
}
