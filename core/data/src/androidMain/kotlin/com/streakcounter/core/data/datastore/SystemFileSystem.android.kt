package com.streakcounter.core.data.datastore

import okio.FileSystem

actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM
