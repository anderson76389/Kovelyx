package com.nuvio.app.core.auth

/**
 * No persistent iOS cloud tokens until a native Keychain-backed implementation
 * exists. In particular: never write a bearer token into NSUserDefaults.
 */
internal actual object CloudflareCredentialStore {
    actual fun load(): String? = null
    actual fun save(value: String) = Unit
    actual fun clear() = Unit
}
