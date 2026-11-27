package com.streakcounter.core.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable key in the Android Keystore, generated on first use.
 *
 * No user authentication is required, so widgets can read the data after the first unlock.
 * The Keystore picks a fresh random 12-byte IV for every encryption.
 */
class KeystoreAead(private val alias: String = DEFAULT_ALIAS) : Aead {

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        if (nonce.size != Aead.NONCE_SIZE) throw GeneralSecurityException("Unexpected nonce size")
        return nonce + sealed
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < Aead.NONCE_SIZE + Aead.TAG_SIZE) throw GeneralSecurityException("Ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, ciphertext, 0, Aead.NONCE_SIZE))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext, Aead.NONCE_SIZE, ciphertext.size - Aead.NONCE_SIZE)
    }

    /**
     * Returns the stored key, or generates it when the alias has no entry. A failing lookup
     * throws and never generates a new key, so an existing key is never replaced.
     */
    private fun key(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as SecretKey?) ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "streaks_key_v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = Aead.TAG_SIZE * 8
        private val lock = Any()
    }
}
