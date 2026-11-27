package com.streakcounter.core.data.crypto

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

/** AES-GCM and key handling of [KeychainAead] behind the [KeyStorage] seam, without the Keychain. */
class KeyStorageAeadTest {

    private val aad = "streaks.enc".encodeToByteArray()
    private val plaintext = "No smoking".encodeToByteArray()

    @Test
    fun roundTrip_and_fresh_nonce() {
        val aead = KeychainAead(InMemoryKeyStorage())

        val first = aead.encrypt(plaintext, aad)
        val second = aead.encrypt(plaintext, aad)

        assertThat(first.size).isEqualTo(12 + plaintext.size + 16)
        assertThat(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12))).isFalse()
        assertThat(first.toList()).isNotEqualTo(second.toList())
        assertThat(aead.decrypt(first, aad).decodeToString()).isEqualTo("No smoking")
    }

    @Test
    fun key_persists_across_instances_sharing_storage() {
        val storage = InMemoryKeyStorage()
        val sealed = KeychainAead(storage).encrypt(plaintext, aad)

        assertThat(KeychainAead(storage).decrypt(sealed, aad).decodeToString()).isEqualTo("No smoking")
    }

    @Test
    fun generates_32_byte_key_once() {
        val storage = InMemoryKeyStorage()
        KeychainAead(storage).encrypt(plaintext, aad)
        val key = storage.read()

        KeychainAead(storage).encrypt(plaintext, aad)

        assertThat(key?.size).isEqualTo(32)
        assertThat(storage.read()?.toList()).isEqualTo(key?.toList())
        assertThat(storage.adds).isEqualTo(1)
    }

    @Test
    fun duplicate_add_race_uses_the_stored_key() {
        // Another instance stored its key between our read and our add.
        val winner = ByteArray(32) { 5 }
        val storage = InMemoryKeyStorage(raceKey = winner)
        val sealed = KeychainAead(storage).encrypt(plaintext, aad)

        assertThat(storage.read()?.toList()).isEqualTo(winner.toList())
        assertThat(KeychainAead(InMemoryKeyStorage(winner)).decrypt(sealed, aad).decodeToString())
            .isEqualTo("No smoking")
    }

    @Test
    fun wrong_key_throws() {
        val sealed = KeychainAead(InMemoryKeyStorage(ByteArray(32) { 1 })).encrypt(plaintext, aad)

        assertFailure { KeychainAead(InMemoryKeyStorage(ByteArray(32) { 2 })).decrypt(sealed, aad) }
    }

    @Test
    fun tampered_ciphertext_throws() {
        val aead = KeychainAead(InMemoryKeyStorage())
        val sealed = aead.encrypt(plaintext, aad)

        for (index in sealed.indices) {
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFailure { aead.decrypt(tampered, aad) }
        }
    }

    @Test
    fun stored_key_with_wrong_size_throws_and_is_not_replaced() {
        val storage = InMemoryKeyStorage(ByteArray(16))

        assertFailure { KeychainAead(storage).encrypt(plaintext, aad) }.isInstanceOf<KeyUnavailableException>()
        assertThat(storage.read()?.size).isEqualTo(16)
    }

    @Test
    fun keychain_read_failure_throws_KeyUnavailable_and_never_adds_a_key() {
        val storage = FailingKeyStorage(failRead = true)
        val aead = KeychainAead(storage)

        assertFailure { aead.encrypt(plaintext, aad) }.isInstanceOf<KeyUnavailableException>()
        assertFailure { aead.decrypt(ByteArray(40), aad) }.isInstanceOf<KeyUnavailableException>()
        assertThat(storage.adds).isEqualTo(0)
    }

    @Test
    fun keychain_add_failure_throws_KeyUnavailable() {
        val storage = FailingKeyStorage(failRead = false)

        assertFailure { KeychainAead(storage).encrypt(plaintext, aad) }.isInstanceOf<KeyUnavailableException>()
        assertThat(storage.adds).isEqualTo(1)
    }

    @Test
    fun keychain_failure_is_retried_on_the_next_call() {
        val storage = FailingKeyStorage(failRead = true)
        val aead = KeychainAead(storage)
        assertFailure { aead.encrypt(plaintext, aad) }

        storage.failRead = false
        storage.failAdd = false

        assertThat(aead.decrypt(aead.encrypt(plaintext, aad), aad).decodeToString()).isEqualTo("No smoking")
    }

    /** Keychain errors such as errSecInteractionNotAllowed (device locked since boot). */
    private class FailingKeyStorage(var failRead: Boolean, var failAdd: Boolean = true) : KeyStorage {
        private var key: ByteArray? = null
        var adds = 0
            private set

        override fun read(): ByteArray? {
            if (failRead) throw KeychainException("SecItemCopyMatching", -25308)
            return key?.copyOf()
        }

        override fun add(key: ByteArray): Boolean {
            adds++
            if (failAdd) throw KeychainException("SecItemAdd", -25308)
            this.key = key.copyOf()
            return true
        }
    }

    private class InMemoryKeyStorage(
        private var key: ByteArray? = null,
        private val raceKey: ByteArray? = null,
    ) : KeyStorage {
        var adds = 0
            private set

        override fun read(): ByteArray? = key?.copyOf()

        override fun add(key: ByteArray): Boolean {
            adds++
            if (raceKey != null && this.key == null) {
                this.key = raceKey.copyOf()
                return false
            }
            if (this.key != null) return false
            this.key = key.copyOf()
            return true
        }
    }
}
