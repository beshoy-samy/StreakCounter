package com.streakcounter.core.data.datastore

import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.FileSystem
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class DataStorePathTest {

    @Test
    fun path_is_in_application_support_datastore_and_directory_is_excluded_from_backup() {
        val path = dataStorePath("streaks.enc")

        assertThat(path.name).isEqualTo("streaks.enc")
        assertThat(path.parent!!.toString()).endsWith("/Application Support/datastore")
        assertThat(FileSystem.SYSTEM.metadataOrNull(path.parent!!)?.isDirectory == true).isTrue()
        assertThat(isExcludedFromBackup(NSURL.fileURLWithPath(path.parent.toString(), isDirectory = true))).isTrue()
    }

    private fun isExcludedFromBackup(url: NSURL): Boolean = memScoped {
        val value = alloc<ObjCObjectVar<Any?>>()
        url.getResourceValue(value.ptr, forKey = NSURLIsExcludedFromBackupKey, error = null)
        (value.value as? NSNumber)?.boolValue == true
    }
}
