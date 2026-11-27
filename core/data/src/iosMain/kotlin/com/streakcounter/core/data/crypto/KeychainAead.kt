package com.streakcounter.core.data.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.providers.cryptokit.CryptoKit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/**
 * AES-256-GCM through CryptoKit with a 256-bit key kept in the Keychain
 * (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), generated on first use.
 *
 * CryptoKit draws a fresh random 12-byte nonce for every encryption and prepends it.
 */
class KeychainAead internal constructor(private val storage: KeyStorage) : Aead {

    constructor(
        service: String = DEFAULT_SERVICE,
        account: String = DEFAULT_ACCOUNT,
    ) : this(KeychainKeyStorage(service, account))

    // Thread-safe; a failed load throws and is retried on the next call.
    private val cipher: IvAuthenticatedCipher by lazy {
        // Everything here is key access (Keychain read/add, key size, key import). None of it can
        // tell whether the file is fine, so it is reported as KeyUnavailableException, never as a
        // decrypt failure. Tag checks happen later, in decrypt, and stay ordinary failures.
        try {
            val rawKey = loadOrCreateKey()
            try {
                CryptographyProvider.CryptoKit.get(AES.GCM)
                    .keyDecoder()
                    .decodeFromByteArrayBlocking(AES.Key.Format.RAW, rawKey)
                    .cipher()
            } finally {
                rawKey.fill(0)
            }
        } catch (e: Exception) {
            throw KeyUnavailableException(e)
        }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        cipher.encryptBlocking(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < Aead.NONCE_SIZE + Aead.TAG_SIZE) throw IllegalArgumentException("Ciphertext too short")
        return cipher.decryptBlocking(ciphertext, associatedData)
    }

    /**
     * Reads the stored key; only when none exists generates one. If another caller stored a key
     * first (duplicate item), that key wins and is read back. A stored key is never replaced.
     */
    private fun loadOrCreateKey(): ByteArray {
        storage.read()?.let { return it.requireKeySize() }
        val fresh = randomKey()
        if (storage.add(fresh)) return fresh
        fresh.fill(0)
        return checkNotNull(storage.read()) { "Key missing after duplicate add" }.requireKeySize()
    }

    private fun ByteArray.requireKeySize(): ByteArray {
        check(size == KEY_SIZE) { "Stored key has unexpected size" }
        return this
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun randomKey(): ByteArray {
        val key = ByteArray(KEY_SIZE)
        val status = key.usePinned { SecRandomCopyBytes(kSecRandomDefault, KEY_SIZE.toULong(), it.addressOf(0)) }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed with OSStatus $status" }
        return key
    }

    companion object {
        const val DEFAULT_SERVICE: String = "com.streakcounter.key"
        const val DEFAULT_ACCOUNT: String = "streaks_v1"
        private const val KEY_SIZE = 32
    }
}
