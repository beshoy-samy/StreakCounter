package com.streakcounter.core.testing

import com.streakcounter.core.data.crypto.Aead
import kotlin.random.Random

/**
 * Test-only [Aead] with the same output layout as the real ones: nonce(12) + ciphertext + tag(16).
 *
 * NOT secure. The "cipher" XORs with a stream derived from [key] and the nonce, and the tag is a
 * keyed checksum over key, nonce, associated data and ciphertext. That is enough for tests to see
 * that tampering, a wrong key or wrong associated data make [decrypt] throw.
 */
class FakeAead(
    private val key: ByteArray = DEFAULT_KEY,
    private val random: Random = Random.Default,
) : Aead {

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = random.nextBytes(NONCE_SIZE)
        val body = xorStream(plaintext, nonce)
        return nonce + body + tag(nonce, body, associatedData)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < NONCE_SIZE + TAG_SIZE) throw FakeAeadException("Ciphertext too short")
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val body = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size - TAG_SIZE)
        val tag = ciphertext.copyOfRange(ciphertext.size - TAG_SIZE, ciphertext.size)
        if (!tag.contentEquals(tag(nonce, body, associatedData))) throw FakeAeadException("Authentication failed")
        return xorStream(body, nonce)
    }

    private fun xorStream(input: ByteArray, nonce: ByteArray): ByteArray =
        ByteArray(input.size) { i ->
            val stream = key[i % key.size].toInt() xor nonce[i % NONCE_SIZE].toInt() xor (i * 31 + 0x5A)
            (input[i].toInt() xor stream).toByte()
        }

    /** Two FNV-1a 64-bit hashes with different seeds. Any single changed byte changes the result. */
    private fun tag(nonce: ByteArray, body: ByteArray, associatedData: ByteArray): ByteArray {
        val parts = listOf(key, nonce, lengthOf(associatedData), associatedData, body)
        val high = fnv1a(SEED_HIGH, parts)
        val low = fnv1a(SEED_LOW, parts)
        return ByteArray(TAG_SIZE) { i ->
            val word = if (i < 8) high else low
            (word ushr (8 * (i % 8))).toByte()
        }
    }

    private fun fnv1a(seed: Long, parts: List<ByteArray>): Long {
        var hash = seed
        for (part in parts) for (byte in part) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }

    private fun lengthOf(bytes: ByteArray): ByteArray =
        ByteArray(4) { i -> (bytes.size ushr (8 * i)).toByte() }

    class FakeAeadException(message: String) : Exception(message)

    companion object {
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 16
        val DEFAULT_KEY: ByteArray = ByteArray(32) { (it * 7 + 3).toByte() }
        private const val FNV_PRIME = 0x100000001b3L
        private const val SEED_HIGH = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
        private const val SEED_LOW = 0x1234_5678_9ABC_DEF1L
    }
}
