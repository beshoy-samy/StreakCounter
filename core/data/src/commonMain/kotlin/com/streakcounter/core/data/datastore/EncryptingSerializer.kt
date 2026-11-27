package com.streakcounter.core.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import com.streakcounter.core.data.crypto.Aead
import com.streakcounter.core.data.crypto.KeyUnavailableException
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException

/** First byte of every encrypted file. Bump it when the envelope layout changes. */
const val ENVELOPE_VERSION: Byte = 1

/**
 * Wraps [delegate] so the file on disk is `[ENVELOPE_VERSION][nonce(12)][ciphertext][tag(16)]`.
 *
 * The AEAD associated data is [associatedData] (the file name) plus [ENVELOPE_VERSION], so a
 * file cannot be swapped for another file or replayed under another envelope version.
 *
 * Only a zero-byte file reads as [defaultValue]. Anything else that cannot be decrypted or parsed
 * throws [CorruptionException], so DataStore never treats an unreadable file as empty and never
 * overwrites it. Exception messages carry the error type only, never file content.
 *
 * [KeyUnavailableException] from [aead] passes through unchanged: a missing key says nothing about
 * the file, so it must not look like corruption (which offers "Start fresh" and deletes the file).
 * Any other encrypt failure becomes a plain IOException (a failed write), never a crash.
 */
class EncryptingSerializer<T>(
    private val delegate: OkioSerializer<T>,
    private val aead: Aead,
    associatedData: ByteArray,
) : OkioSerializer<T> {

    private val boundAssociatedData: ByteArray = associatedData + ENVELOPE_VERSION

    override val defaultValue: T
        get() = delegate.defaultValue

    override suspend fun readFrom(source: BufferedSource): T {
        val envelope = source.readByteArray()
        if (envelope.isEmpty()) return delegate.defaultValue
        if (envelope.size < MIN_ENVELOPE_SIZE) throw CorruptionException("Encrypted file is too short")
        if (envelope[0] != ENVELOPE_VERSION) throw CorruptionException("Unknown envelope version")

        val plaintext = try {
            aead.decrypt(envelope.copyOfRange(1, envelope.size), boundAssociatedData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: KeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw CorruptionException("Could not decrypt file (${e.typeName()})")
        }
        try {
            return delegate.readFrom(Buffer().write(plaintext))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The cause is dropped on purpose: parser messages often quote the decrypted text.
            throw CorruptionException("Could not parse decrypted file (${e.typeName()})")
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        val plaintext = Buffer().also { delegate.writeTo(t, it) }.readByteArray()
        try {
            val sealed = try {
                aead.encrypt(plaintext, boundAssociatedData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyUnavailableException) {
                throw e
            } catch (e: Exception) {
                // A failed write, not corruption: DataStore keeps the old file and the caller
                // reports WRITE_FAILED instead of crashing. No cause, like the read path.
                throw IOException("Could not encrypt file (${e.typeName()})")
            }
            sink.writeByte(ENVELOPE_VERSION.toInt())
            sink.write(sealed)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun Exception.typeName(): String = this::class.simpleName ?: "Exception"

    private companion object {
        const val MIN_ENVELOPE_SIZE = 1 + Aead.NONCE_SIZE + Aead.TAG_SIZE
    }
}
