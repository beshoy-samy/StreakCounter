package com.streakcounter.core.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.streakcounter.core.data.crypto.KeyUnavailableException
import com.streakcounter.core.testing.FakeAead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test

class EncryptingSerializerTest {

    private val fileName = "streaks.enc".encodeToByteArray()
    private val serializer = EncryptingSerializer(StringSerializer, FakeAead(), fileName)

    @Test
    fun roundTrip_returns_same_value() = runTest {
        val bytes = serializer.encode("No smoking")

        assertThat(serializer.decode(bytes)).isEqualTo("No smoking")
    }

    @Test
    fun empty_file_returns_default() = runTest {
        assertThat(serializer.decode(ByteArray(0))).isEqualTo(StringSerializer.defaultValue)
    }

    @Test
    fun envelope_is_version_then_nonce_ciphertext_tag_with_fresh_nonce() = runTest {
        val first = serializer.encode("No smoking")
        val second = serializer.encode("No smoking")

        assertThat(first[0]).isEqualTo(ENVELOPE_VERSION)
        assertThat(first.size).isEqualTo(1 + 12 + "No smoking".length + 16)
        assertThat(first.toList()).isNotEqualTo(second.toList())
    }

    @Test
    fun tampered_byte_throws_CorruptionException() = runTest {
        val bytes = serializer.encode("No smoking")

        // Every byte after the version byte is covered by the tag.
        for (index in 1 until bytes.size) {
            val tampered = bytes.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFailure { serializer.decode(tampered) }.isInstanceOf<CorruptionException>()
        }
    }

    @Test
    fun wrong_key_throws_CorruptionException() = runTest {
        val bytes = serializer.encode("No smoking")
        val otherKey = EncryptingSerializer(StringSerializer, FakeAead(key = ByteArray(32) { 9 }), fileName)

        assertFailure { otherKey.decode(bytes) }.isInstanceOf<CorruptionException>()
    }

    @Test
    fun other_associated_data_throws_CorruptionException() = runTest {
        val bytes = serializer.encode("No smoking")
        val otherFile = EncryptingSerializer(StringSerializer, FakeAead(), "other.enc".encodeToByteArray())

        assertFailure { otherFile.decode(bytes) }.isInstanceOf<CorruptionException>()
    }

    @Test
    fun unknown_envelope_version_throws() = runTest {
        val bytes = serializer.encode("No smoking")

        for (version in listOf<Byte>(0, 2, -1)) {
            val other = bytes.copyOf().also { it[0] = version }
            assertFailure { serializer.decode(other) }.isInstanceOf<CorruptionException>()
        }
    }

    @Test
    fun short_input_throws_CorruptionException() = runTest {
        // Version byte only, and one byte less than version + nonce + tag.
        for (size in listOf(1, 1 + 12 + 16 - 1)) {
            val short = ByteArray(size).also { it[0] = ENVELOPE_VERSION }
            assertFailure { serializer.decode(short) }.isInstanceOf<CorruptionException>()
        }
    }

    @Test
    fun ciphertext_does_not_contain_plaintext() = runTest {
        val bytes = serializer.encode("No smoking")

        assertThat(bytes.indexOf("No smoking".encodeToByteArray())).isEqualTo(-1)
    }

    @Test
    fun delegate_failure_throws_CorruptionException_without_content() = runTest {
        val leaky = EncryptingSerializer(LeakyFailingSerializer, FakeAead(), fileName)
        val bytes = leaky.encode("No smoking")

        assertFailure { leaky.decode(bytes) }.isInstanceOf<CorruptionException>().all {
            messageContains("IllegalArgumentException")
            prop(Throwable::message).transform { it.orEmpty() }.doesNotContain("No smoking")
            prop(Throwable::cause).isNull()
        }
    }

    @Test
    fun decrypt_failure_message_has_no_content() = runTest {
        val bytes = serializer.encode("No smoking")
        val otherKey = EncryptingSerializer(StringSerializer, FakeAead(key = ByteArray(32) { 9 }), fileName)

        assertFailure { otherKey.decode(bytes) }.isInstanceOf<CorruptionException>().all {
            prop(Throwable::message).transform { it.orEmpty() }.doesNotContain("No smoking")
            prop(Throwable::cause).isNull()
        }
    }

    @Test
    fun dataStore_never_overwrites_an_unreadable_file() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "encrypting-serializer-${Random.nextLong()}"
        val file = dir / "streaks.enc"
        FileSystem.SYSTEM.createDirectories(dir)
        try {
            // Written with another key: unreadable for this store.
            val foreign = EncryptingSerializer(StringSerializer, FakeAead(key = ByteArray(32) { 9 }), fileName)
                .encode("No smoking")
            FileSystem.SYSTEM.write(file) { write(foreign) }
            val store = DataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, serializer) { file.toString().toPath() },
                scope = backgroundScope,
            )

            assertFailure { store.data.first() }.isInstanceOf<CorruptionException>()
            assertFailure { store.updateData { "overwritten" } }.isInstanceOf<CorruptionException>()
            assertThat(FileSystem.SYSTEM.read(file) { readByteArray() }.toList()).isEqualTo(foreign.toList())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test
    fun key_unavailable_on_read_propagates_and_is_not_corruption() = runTest {
        val aead = FakeAead()
        val keyed = EncryptingSerializer(StringSerializer, aead, fileName)
        val bytes = keyed.encode("No smoking")
        aead.keyUnavailable = true

        assertFailure { keyed.decode(bytes) }.isInstanceOf<KeyUnavailableException>()
    }

    @Test
    fun key_unavailable_on_write_propagates() = runTest {
        val aead = FakeAead().apply { keyUnavailable = true }
        val keyed = EncryptingSerializer(StringSerializer, aead, fileName)

        assertFailure { keyed.encode("No smoking") }.isInstanceOf<KeyUnavailableException>()
    }

    @Test
    fun other_encrypt_failure_becomes_IOException_not_corruption_and_has_no_content() = runTest {
        val aead = FakeAead().apply { encryptFailure = IllegalStateException("No smoking") }
        val keyed = EncryptingSerializer(StringSerializer, aead, fileName)

        assertFailure { keyed.encode("No smoking") }.all {
            isInstanceOf<IOException>()
            transform { it is CorruptionException }.isFalse()
            prop(Throwable::message).transform { it.orEmpty() }.doesNotContain("No smoking")
            prop(Throwable::cause).isNull()
        }
    }

    @Test
    fun dataStore_never_overwrites_when_the_key_is_unavailable() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "encrypting-serializer-${Random.nextLong()}"
        val file = dir / "streaks.enc"
        FileSystem.SYSTEM.createDirectories(dir)
        try {
            val aead = FakeAead()
            val keyed = EncryptingSerializer(StringSerializer, aead, fileName)
            val good = keyed.encode("No smoking")
            FileSystem.SYSTEM.write(file) { write(good) }
            aead.keyUnavailable = true
            val store = DataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, keyed) { file.toString().toPath() },
                scope = backgroundScope,
            )

            assertFailure { store.data.first() }.isInstanceOf<KeyUnavailableException>()
            assertFailure { store.updateData { "overwritten" } }.isInstanceOf<KeyUnavailableException>()
            assertThat(FileSystem.SYSTEM.read(file) { readByteArray() }.toList()).isEqualTo(good.toList())

            // The key is back: the same file reads fine, nothing was lost.
            aead.keyUnavailable = false
            assertThat(store.data.first()).isEqualTo("No smoking")
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    private suspend fun <T> EncryptingSerializer<T>.encode(value: T): ByteArray =
        Buffer().also { writeTo(value, it) }.readByteArray()

    private suspend fun <T> EncryptingSerializer<T>.decode(bytes: ByteArray): T =
        readFrom(Buffer().write(bytes))

    private fun ByteArray.indexOf(needle: ByteArray): Int =
        (0..size - needle.size).firstOrNull { start ->
            needle.indices.all { this[start + it] == needle[it] }
        } ?: -1

    private object StringSerializer : OkioSerializer<String> {
        override val defaultValue: String = "<default>"
        override suspend fun readFrom(source: BufferedSource): String = source.readUtf8()
        override suspend fun writeTo(t: String, sink: BufferedSink) {
            sink.writeUtf8(t)
        }
    }

    /** A delegate whose parse error message contains the decrypted text, like many JSON errors do. */
    private object LeakyFailingSerializer : OkioSerializer<String> {
        override val defaultValue: String = ""
        override suspend fun readFrom(source: BufferedSource): String =
            throw IllegalArgumentException("Unexpected token in: ${source.readUtf8()}")
        override suspend fun writeTo(t: String, sink: BufferedSink) {
            sink.writeUtf8(t)
        }
    }
}
