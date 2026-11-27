package com.streakcounter.core.data.datastore

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * `filesDir/datastore/[fileName]`. DataStore creates the directory on the first write.
 *
 * The app's backup rules (`data_extraction_rules.xml`, `backup_rules.xml`) exclude this file:
 * its key lives in the Keystore and never leaves the device, so a restored copy could not be read.
 */
fun Context.dataStorePath(fileName: String): Path =
    filesDir.resolve(DATASTORE_DIRECTORY).resolve(fileName).toOkioPath()
