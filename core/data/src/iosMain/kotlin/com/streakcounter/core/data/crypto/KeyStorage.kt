package com.streakcounter.core.data.crypto

/** Where [KeychainAead] keeps its raw key. A seam so the AES part is testable without the Keychain. */
internal interface KeyStorage {
    /** The stored key, or null when no key is stored. Throws on any other failure. */
    fun read(): ByteArray?

    /** Stores [key]. Returns false when a key is already stored (it is left unchanged). Throws on any other failure. */
    fun add(key: ByteArray): Boolean
}
