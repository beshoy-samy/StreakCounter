package com.streakcounter.core.data.crypto

/**
 * Authenticated encryption with associated data (AES-256-GCM on both platforms).
 *
 * Implementations must use a fresh random nonce for every [encrypt] call and never log
 * plaintext or key material.
 */
interface Aead {
    /** Returns nonce(12) + ciphertext + tag(16). */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** Takes the [encrypt] output. Throws if the tag does not verify (tampering, wrong key, wrong [associatedData]). */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray

    companion object {
        const val NONCE_SIZE: Int = 12
        const val TAG_SIZE: Int = 16
    }
}
