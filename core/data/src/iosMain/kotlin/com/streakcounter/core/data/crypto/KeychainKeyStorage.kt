package com.streakcounter.core.data.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keeps the key as a generic password item, readable after the first unlock and never synced or
 * migrated to another device. Errors carry the OSStatus only, never key bytes.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainKeyStorage(
    private val service: String,
    private val account: String,
) : KeyStorage {

    override fun read(): ByteArray? = memScoped {
        val query = query(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        when (val status = SecItemCopyMatching(query, result.ptr)) {
            errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
            errSecItemNotFound -> null
            else -> throw KeychainException("SecItemCopyMatching", status)
        }
    }

    override fun add(key: ByteArray): Boolean = memScoped {
        val data = key.toNSData()
        val query = query(
            kSecValueData to retain(data),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        when (val status = SecItemAdd(query, null)) {
            errSecSuccess -> true
            errSecDuplicateItem -> false
            else -> throw KeychainException("SecItemAdd", status)
        }
    }

    override fun delete(): Unit = memScoped {
        when (val status = SecItemDelete(query())) {
            errSecSuccess, errSecItemNotFound -> Unit
            else -> throw KeychainException("SecItemDelete", status)
        }
    }

    /** Builds class + service + account + [extra]; everything is released when the scope ends. */
    private fun MemScope.query(vararg extra: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef? {
        val entries = listOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to retain(service),
            kSecAttrAccount to retain(account),
        ) + extra
        val dictionary = CFDictionaryCreateMutable(
            null,
            entries.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        defer { CFRelease(dictionary) }
        return dictionary
    }

    private fun MemScope.retain(value: Any): CFTypeRef? =
        CFBridgingRetain(value).also { ref -> defer { CFRelease(ref) } }

    private fun NSData.toByteArray(): ByteArray =
        bytes?.readBytes(length.convert()) ?: ByteArray(0)

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}

internal class KeychainException(operation: String, status: Int) :
    IllegalStateException("$operation failed with OSStatus $status")
