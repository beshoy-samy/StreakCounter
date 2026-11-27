package com.streakcounter.core.data.crypto

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import android.security.keystore.KeyInfo
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class KeystoreAeadTest {

    private val alias = "streaks_key_test"
    private val aad = "streaks.enc".encodeToByteArray()
    private val plaintext = "No smoking".encodeToByteArray()

    @BeforeTest
    @AfterTest
    fun deleteTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun roundTrip_and_fresh_nonce() {
        val aead = KeystoreAead(alias)

        val first = aead.encrypt(plaintext, aad)
        val second = aead.encrypt(plaintext, aad)

        assertThat(first.size).isEqualTo(12 + plaintext.size + 16)
        assertThat(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12))).isFalse()
        assertThat(first.toList()).isNotEqualTo(second.toList())
        assertThat(aead.decrypt(first, aad).decodeToString()).isEqualTo("No smoking")
        assertThat(aead.decrypt(second, aad).decodeToString()).isEqualTo("No smoking")
    }

    @Test
    fun key_is_generated_once_and_persists_across_instances() {
        val sealed = KeystoreAead(alias).encrypt(plaintext, aad)

        assertThat(keyStore().containsAlias(alias)).isTrue()
        assertThat(KeystoreAead(alias).decrypt(sealed, aad).decodeToString()).isEqualTo("No smoking")
    }

    @Test
    fun tampered_ciphertext_throws() {
        val aead = KeystoreAead(alias)
        val sealed = aead.encrypt(plaintext, aad)

        for (index in sealed.indices) {
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFailure { aead.decrypt(tampered, aad) }
        }
    }

    @Test
    fun other_associated_data_throws() {
        val aead = KeystoreAead(alias)
        val sealed = aead.encrypt(plaintext, aad)

        assertFailure { aead.decrypt(sealed, "other.enc".encodeToByteArray()) }
    }

    @Test
    fun short_input_throws() {
        assertFailure { KeystoreAead(alias).decrypt(ByteArray(27), aad) }
    }

    @Test
    fun key_is_aes_gcm_256_without_user_auth_and_not_exportable() {
        KeystoreAead(alias).encrypt(plaintext, aad)
        val key = keyStore().getKey(alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo

        assertThat(key.algorithm).isEqualTo("AES")
        assertThat(info.keySize).isEqualTo(256)
        assertThat(info.blockModes.toList()).containsExactly("GCM")
        assertThat(info.isUserAuthenticationRequired).isFalse()
        assertThat(key.encoded == null).isTrue()
    }

    @Test
    fun deleteKey_removes_the_entry_and_a_new_key_works() {
        val aead = KeystoreAead(alias)
        val sealed = aead.encrypt(plaintext, aad)

        aead.deleteKey()

        assertThat(keyStore().containsAlias(alias)).isFalse()
        val fresh = aead.encrypt(plaintext, aad)
        assertThat(aead.decrypt(fresh, aad).decodeToString()).isEqualTo("No smoking")
        assertFailure { aead.decrypt(sealed, aad) }
    }

    @Test
    fun deleteKey_without_a_key_is_a_noop() {
        KeystoreAead(alias).deleteKey()

        assertThat(keyStore().containsAlias(alias)).isFalse()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
