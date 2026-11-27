package com.streakcounter.core.data.crypto

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Uses the real simulator Keychain under a test-only account. */
class KeychainAeadTest {

    private val service = "com.streakcounter.key.test"
    private val account = "streaks_test"
    private val aad = "streaks.enc".encodeToByteArray()
    private val plaintext = "No smoking".encodeToByteArray()

    @BeforeTest
    @AfterTest
    fun deleteTestKey() {
        KeychainKeyStorage(service, account).delete()
    }

    @Test
    fun roundTrip_and_key_persists_across_instances() {
        val sealed = KeychainAead(service, account).encrypt(plaintext, aad)

        assertThat(sealed.size).isEqualTo(12 + plaintext.size + 16)
        assertThat(KeychainAead(service, account).decrypt(sealed, aad).decodeToString()).isEqualTo("No smoking")
    }

    @Test
    fun stored_key_is_32_bytes() {
        KeychainAead(service, account).encrypt(plaintext, aad)

        assertThat(KeychainKeyStorage(service, account).read()?.size).isEqualTo(32)
    }

    @Test
    fun second_add_reports_duplicate() {
        val storage = KeychainKeyStorage(service, account)
        storage.add(ByteArray(32) { 1 })

        assertThat(storage.add(ByteArray(32) { 2 })).isFalse()
        assertThat(storage.read()?.toList()).isEqualTo(ByteArray(32) { 1 }.toList())
    }

    @Test
    fun other_associated_data_throws() {
        val aead = KeychainAead(service, account)
        val sealed = aead.encrypt(plaintext, aad)

        assertFailure { aead.decrypt(sealed, "other.enc".encodeToByteArray()) }
    }
}
