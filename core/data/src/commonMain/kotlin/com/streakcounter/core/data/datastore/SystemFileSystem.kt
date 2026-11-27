package com.streakcounter.core.data.datastore

import okio.FileSystem

/** The platform file system. Okio's `FileSystem.SYSTEM` is not visible from common metadata compilation. */
expect fun systemFileSystem(): FileSystem
