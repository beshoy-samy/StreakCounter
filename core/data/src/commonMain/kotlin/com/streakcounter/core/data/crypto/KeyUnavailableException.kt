package com.streakcounter.core.data.crypto

import okio.IOException

/**
 * The encryption key could not be loaded or created (Keystore or Keychain failure).
 *
 * This is not corruption: the file may be fine and readable once the key is reachable again, so
 * callers must never treat it as an unreadable file and must never write while it is thrown.
 * The message is fixed and carries no content; [cause] is the platform error, which holds no
 * streak data either.
 */
class KeyUnavailableException(cause: Throwable? = null) : IOException("Encryption key unavailable", cause)
